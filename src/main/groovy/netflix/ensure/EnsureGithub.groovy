package netflix.ensure

import groovy.transform.Canonical
import groovyx.net.http.HTTPBuilder
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

import java.security.MessageDigest
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

    def ensureRepo(String repoName, String repoDescription) {
        logger.info("Ensuring repo ${repoName}")

        // Avoid an exception which we'd get if repo didn't exist, but getting all the repos and filtering the list down
        def allRepositories = repoService.getOrgRepositories(orgName)
        Repository repo = allRepositories.find { it.name == repoName }

        if (!repo) {
            // Need to create
            Repository newRepo = new Repository().setName(repoName)
            if (repoDescription) {
                newRepo.setDescription(repoDescription)
            }
            // TBD .setPrivate()

            logger.info("Creating repo ${repoName}")
            if (!dryRun) {
                // Make sure we're creating repos that match our pattern
                assert matchRepository(newRepo), "Repository name $repoName does not match the repository patterns"
                repo = repoService.createRepository(orgName, newRepo)
                pokeCloudbees()
            }
        } else {
            // Confirm description is correct
            if (repo.description != repoDescription) {
                repo.setDescription(repoDescription)
                logger.info("Updating description on ${repoName}")
                if (!dryRun) {
                    repoService.editRepository(repo)
                }
            }
        }

        List<Team> teams = teamService.getTeams(orgName)
        ensureRepo(repo, teams)
        def contrib = gatherContrib(teams)
        ensureRepoInContrib(contrib, repo)
    }

    /**
     * Poke CloudBees Job DSL SEED job
     */
    private void pokeCloudbees() {
        // JENKINS_URL/view/Nebula%20Plugins/job/nebula-plugins/job/SEED-nebula-plugins/build?token=TOKEN_NAME or /buildWithParameters?token=TOKEN_NAME
        // TODO Make sure configurable
        def seedJobName = 'SEED-nebula-plugins'
        logger.info("Poking cloudbees for ${seedJobName}")

        def seedJobHash = hashJobName(seedJobName)

        def http = new HTTPBuilder('https://netflixoss.ci.cloudbees.com')
        def html = http.post(path: "/job/$orgName/job/$seedJobName/build", query: [token: seedJobHash, delay: "0sec"])
    }

    String hashJobName(String seedJobName) {
        def cript = MessageDigest.getInstance("SHA1")
        cript.reset();
        cript.update(seedJobName.getBytes("utf8"))
        return new BigInteger(1, cript.digest()).toString(16)
    }

    @Canonical
    static class Contrib {
        Team team
        List<Repository> repos
    }

    Contrib gatherContrib(List<Team> teams) {
        Team contribTeam = ensureOrgContribTeam(teams)
        List<Repository> contribRepos = contribTeam?teamService.getRepositories(contribTeam.id):[]
        return new Contrib(contribTeam, contribRepos)
    }

    def ensureOrg() {
        logger.info("Ensuring organization ${orgName}")
        List<Team> teams = teamService.getTeams(orgName)
        Team ownerTeam = teams.find { it.name == orgOwnersTeamName }

        Contrib contrib = gatherContrib(teams)

        def allRepositories = repoService.getOrgRepositories(orgName)
        List<Repository> repositories = matchRepositories(allRepositories)
        List<Team> managedTeams = []
        repositories.each { Repository repo ->
            logger.info("Visiting ${repo.name}")
            managedTeams.addAll ensureRepo(repo, teams)

            ensureRepoInContrib(contrib, repo)
        }

        def leftoverRepos = allRepositories.findAll { Repository allRepo -> !repositories.any { it.name == allRepo.name } }
        leftoverRepos.each {
            logger.info("Unaccounted for repo: ${it.name} (${it.private?'Private':'Public'})")
        }

        def leftoverTeams = teams - managedTeams - team - ownerTeam
        leftoverTeams.each {
            logger.info("Unaccounted for team: ${it.name}")
        }

        logger.info("Github rate: ${client.remainingRequests}/${client.requestLimit}")
    }

    public List<Repository> findPublicRepositories() {
        def allRepositories = repoService.getOrgRepositories(orgName)
        List<Repository> repositories = matchRepositories(allRepositories)
        List<Repository> publicRepositories = repositories.findAll { !it.private }
        return publicRepositories
    }

    List<Repository> matchRepositories(List<Repository> repositories) {
        repositories.findAll { Repository repo ->
            matchRepository(repo)
        }
    }

    private boolean matchRepository(repo) {
        repoRegexs.isEmpty() || repoRegexs.any { repo.name =~ it }
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
        def managedHooks = []
        managedHooks << ensureHook(repo, hooks, PULL_REQUEST_URL, ['pull_request'] as String[])
        managedHooks << ensureHook(repo, hooks, WEB_HOOK_URL, ['push'] as String[])

        // Report hooks which we didn't configure
        def leftoverHooks = hooks - managedHooks
        leftoverHooks.findAll {it.active}.each { RepositoryHook hook ->
            if (hook.config && hook.config.get('url') ) {
                logger.info("Unaccounted for hook in ${repo.name}: ${hook.config.get('url')}")
            } else {
                logger.info("Unaccounted for hook in ${repo.name}: ${hook.name}")
                logger.debug(GsonUtils.getGson().toJson(hook))
            }
        }

        // Establish teams
        def managedTeams = []
        managedTeams << ensureRepoTeam(repo, teams, "${repo.name.toLowerCase()}-contrib", 'admin')
        //managedTeams << ensureRepoTeam(repo, teams, "${repo.name}-contrib", 'push')
        //ensureRepoTeam(teams, teamService, orgName, repo.name, "${repo.name}-admin", 'admin')

        return managedTeams
    }

    def ensureHook(Repository repo, List<RepositoryHook> hooks, String hookUrl, String[] events) {
        def webHook = hooks.find { RepositoryHook hook ->
            hook.config?.get('url') == hookUrl
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

            if (!dryRun) {
                webHook = repoService.createHook(repo, webHook)
            }
        } else {
            logger.debug("We have a hook for ${repo.name}: $hookUrl")
        }
        return webHook
    }

    // Create team for a single repository
    def ensureRepoTeam(Repository repository, List<Team> teams, String contribTeamName, String permission) {
        def foundTeam = teams.find { Team team -> team.name.toLowerCase() == contribTeamName }
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
    def ensureRepoInContrib(Contrib contrib, Repository repo) {
        def foundRepo = contrib.repos.find { Repository contribRepo ->  contribRepo.name == repo.name }
        if (!foundRepo) {
            // Add repo to team
            logger.info("Adding repository ${repo.name} to contrib team ${contrib.team.name}")
            if (!dryRun) {
                teamService.addRepository(contrib.team.id, repo)
            }
        } else {
            logger.debug("Repository (${repo.name}) is in contrib team (${contrib.team.name})")
        }
    }
}
