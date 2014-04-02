package netflix.ensure

import netflix.ensure.githubextra.RepositoryHookExtra
import netflix.ensure.githubextra.RepositoryServiceExtra
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.RepositoryHook
import org.eclipse.egit.github.core.Team
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.client.GsonUtils
import org.eclipse.egit.github.core.service.TeamService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

/**
 * Ensure a certain state in Github
 */
class EnsureGithub {
    final static Logger logger = LoggerFactory.getLogger(EnsureGithub.class);

    public static final String PULL_REQUEST_URL = 'https://netflixoss.ci.cloudbees.com/github-pull-request-hook/'
    public static final String WEB_HOOK_URL = 'https://netflixoss.ci.cloudbees.com/github-webhook/'

    boolean dryRun
    String oauthToken
    String orgName
    String orgContribTeamName
    String orgOwnersTeamName
    Collection<Pattern> repoRegexs

    // Make it easier to intercept these calls
    GitHubClient client
    RepositoryServiceExtra repoService
    TeamService teamService

    EnsureGithub(boolean dryRun, String oauthToken, String orgName, String orgContribTeamName, Collection<Pattern> repoRegexs = []) {
        this.dryRun = dryRun
        this.oauthToken = oauthToken
        this.orgName = orgName
        this.orgContribTeamName = orgContribTeamName
        this.repoRegexs = repoRegexs
        this.orgOwnersTeamName = 'Owners' // Not sure this will ever be changed

        client = new GitHubClient()
        client.setOAuth2Token(oauthToken)
        repoService = new RepositoryServiceExtra(client)
        teamService = new TeamService(client)
    }

    def ensureOrg() {
        logger.info("Ensuring organization ${orgName}")
        List<Team> teams = teamService.getTeams(orgName)
        Team ownerTeam = teams.find { it.name == orgOwnersTeamName }

        Team contribTeam = ensureOrgContribTeam(teams)
        List<Repository> contribRepos = teamService.getRepositories(contribTeam.id)

        def allRepositories = repoService.getOrgRepositories(orgName)
        List<Repository> repositories = matchRepositories(allRepositories)
        List<Team> managedTeams = []
        repositories.each { Repository repo ->
            logger.info("Visiting ${repo.name}")
            managedTeams.addAll ensureRepo(repo, teams)

            ensureRepoInContrib(contribTeam, contribRepos, repo)
        }

        def leftoverRepos = allRepositories - repositories
        leftoverRepos.each {
            logger.info("Unaccounted for repo: ${it.name}")
        }

        def leftoverTeams = teams - managedTeams - contribTeam - ownerTeam
        leftoverTeams.each {
            logger.info("Unaccounted for team: ${it.name}")
        }

        logger.info("Github rate: ${client.remainingRequests}/${client.requestLimit}")
    }

    List<Repository> findRepositories() {
        def allRepositories = repoService.getOrgRepositories(orgName)
        List<Repository> repositories = matchRepositories(allRepositories)
        return repositories
    }

    List<Repository> matchRepositories(List<Repository> repositories) {
        repositories.findAll { repo ->
            repoRegexs.isEmpty() || repoRegexs.any { repo.name =~ it }
        }
    }

    Team ensureOrgContribTeam(List<Team> teams) {
        def contribTeam = teams.find { Team team -> team.name == orgContribTeamName }
        if (!contribTeam) {
            contribTeam = createTeam(orgContribTeamName, 'pull')
        }
        // TODO Ensure permissions are correct on team
        return contribTeam
    }

    Team createTeam(String name, String permission) {
        Team contribTeam = new Team()
                .setPermission(permission)
                .setName(name)
        logger.info("Creating team $name")
        logger.debug(GsonUtils.getGson().toJson(contribTeam))
        if (!dryRun) {
            return teamService.createTeam(orgName, contribTeam)
        } else {
            return contribTeam
        }
    }

    /**
     *
     * @param repo
     * @param teams
     * @return List<Team> managed teams
     */
    def ensureRepo(Repository repo, List<Team> teams) {

        // Establish WebHooks. Make a single call to get list of hooks
        List<RepositoryHook> hooks = repoService.getHooksExtra(repo)
        ensureHook(repo, hooks, PULL_REQUEST_URL, ['pull_request'] as String[])
        ensureHook(repo, hooks, WEB_HOOK_URL, ['push'] as String[])
        // TODO Report hooks which we didn't configure

        // Establish teams
        def managedTeams = []
        managedTeams << ensureRepoTeam(repo, teams, "${repo.name}-contrib", 'admin')
        //managedTeams << ensureRepoTeam(repo, teams, "${repo.name}-contrib", 'push')
        //ensureRepoTeam(teams, teamService, orgName, repo.name, "${repo.name}-admin", 'admin')

        return managedTeams
    }

    def ensureHook(Repository repo, List<RepositoryHook> hooks, String hookUrl, String[] events) {
        def webHook = hooks.find { RepositoryHook hook ->
            hook.config.get('url') == hookUrl
        }
        if (!webHook) {
            webHook = new RepositoryHookExtra()
                    .setEvents(events)
                    .setActive(true)
                    .setConfig([url: hookUrl, content_type: 'form'])
                    .setCreatedAt(new Date())
                    .setName('web')
                    .setUpdatedAt(new Date())
            logger.info("Creating hook for ${repo.name} to $hookUrl")
            logger.debug(GsonUtils.getGson().toJson(webHook))

            if (!dryRun) {
                repoService.createHook(repo, webHook)
            }
        } else {
            logger.debug("We have a hook for ${repo.name}: $hookUrl")
        }
        return webHook
    }

    // Create team for a single repository
    def ensureRepoTeam(Repository repository, List<Team> teams, String contribTeamName, String permission) {
        def foundTeam = teams.find { Team team -> team.name == contribTeamName }
        if (!foundTeam) {
            foundTeam = createTeam(contribTeamName, permission)
        } else {
            logger.debug("We have a team (${foundTeam.name}) for this repo (${repository.name})")
        }

        List<Repository> repos = foundTeam.id?teamService.getRepositories(foundTeam.id):[]
        def hasRepo = repos.any { Repository repo -> repo.name == repository.name }
        if (!hasRepo) {
            logger.info("Adding repository ${repository.name} to team ${foundTeam.name}")
            if (!dryRun) {
                teamService.addRepository(foundTeam.id, repository)
            }
        } else {
            logger.debug("This repository ${repository.name} is in team ${foundTeam.name}")
        }
        return foundTeam
    }

    // Ensure this repo is in the org-wide "contrib"
    def ensureRepoInContrib(Team contribTeam, List<Repository> contribRepos, Repository repo) {
        def foundRepo = contribRepos.find { Repository contribRepo ->  contribRepo.name == repo.name }
        if (!foundRepo) {
            // Add repo to team
            logger.info("Adding repository ${repo.name} to contrib team ${contribTeam.name}")
            if (!dryRun) {
                teamService.addRepository(contribTeam.id, repo)
            }
        } else {
            logger.debug("Repository (${repo.name}) is in contrib team (${contribTeam.name})")
        }
    }
}
