package netflix.ensure

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.util.StatusPrinter
import groovy.transform.Canonical
import org.eclipse.egit.github.core.Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

@Canonical
class Cli {
    final static Logger logger = LoggerFactory.getLogger(Cli.class);

    String repoPattern

    String githubOauth
    String githubOrg
    String githubOrgContribName

    String bintrayUsername
    String bintrayApiKey
    String bintraySubject
    String bintrayRepository
    List<String> bintrayLabels
    List<String> bintrayLicenses

    boolean dryRun
    // TODO Entry point to create repo, and initialize it. Easyist called from a bot

    /**
     * Ensure state matches what we ask in:
     * https://github.com/nebula-plugins/nebula-plugins.github.io/wiki/New-Plugins
     */
    def ensure() {
        EnsureGithub ensure = new EnsureGithub(dryRun, githubOauth, githubOrg, githubOrgContribName, getRepoRegexes())
        ensure.ensureOrg()
        // TODO Someway to ensure the users are in the correct groups, e.g. all netflix users in netflix-contrib
        // TODO Only look at public repos

        // Bintray
        List<Repository> repos = ensure.findPublicRepositories()
        EnsureBintray ensureBintray = new EnsureBintray(dryRun, bintrayUsername, bintrayApiKey, bintraySubject, bintrayRepository, bintrayLabels, bintrayLicenses)
        ensureBintray.ensure(repos)

        // Cloudbees (job.dsl should take care of this)
    }

    private List<Pattern> getRepoRegexes() {
        def repoPatterns = repoPattern.tokenize(',').collect { Pattern.compile(it) }
        repoPatterns
    }

    def ensureRepo(String repoName, String description) {
        assert repoName

        EnsureGithub ensure = new EnsureGithub(dryRun, githubOauth, githubOrg, githubOrgContribName, getRepoRegexes())
        ensure.ensureRepo(repoName, description)
    }

    public static void main(String[] args) {
        // assume SLF4J is bound to logback in the current environment
        //LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        //StatusPrinter.print(lc);

        def cliBuilder = new CliBuilder(usage:'ensure [options] [targets]', header:'Options:')

        cliBuilder.h(longOpt: 'help', 'Help')
        cliBuilder.a(longOpt: 'oauth', args: 1, argName: 'token', 'GitHub OAuth Token')
        cliBuilder.o(longOpt: 'org', args: 1, argName: 'organization', 'GitHub Organization')
        cliBuilder.c(longOpt: 'contrib', args: 1, argName: 'team name', 'Team name that all repos should belong to')
        cliBuilder.r(longOpt: 'repos', args: 1, argName: 'repo', 'Regular expression for applicable repositories')
        cliBuilder._(longOpt: 'repo', args: 1, argName: 'repository name', 'Name of repository to ensure')
        cliBuilder._(longOpt: 'description', args: 1, argName: 'licenses', 'Description of repository to ensure')

        // Bintray
        cliBuilder.u(longOpt: 'username', args: 1, argName: 'username', 'Bintray Username')
        cliBuilder.k(longOpt: 'apikey', args: 1, argName: 'key', 'Bintray Api Key')
        cliBuilder.s(longOpt: 'subject', args: 1, argName: 'subject', 'Bintray Subject')
        cliBuilder.t(longOpt: 'repository', args: 1, argName: 'reponame', 'Bintray Repository')
        cliBuilder.b(longOpt: 'labels', args: 1, argName: 'labels', 'Bintray labels to have on packages')
        cliBuilder.l(longOpt: 'licenses', args: 1, argName: 'licenses', 'Bintray licenses to have on packages')

        cliBuilder.d(longOpt: 'dryrun', 'Only log operations')

        // Start defining actions to take
        cliBuilder._(longOpt: 'ensureRepo', 'Instead of ensuring state of whole org, ensure the state of a repository')

        // Via expandArgumentFiles, a file can be provided
        def options = cliBuilder.parse(args)

        if (options.help|| options == null) {
            cliBuilder.usage()
            System.exit(0)
        }

        Cli cli = new Cli()
        cli.with {

            dryRun = (options.dryrun) as Boolean
            if (dryRun) {
                logger.warn("Running in DRY RUN mode")
            }

            repoPattern = options.repos

            // Github
            githubOauth = options.oauth
            githubOrg = options.org
            githubOrgContribName = (options.contrib?:'contrib')

            // Bintray
            bintrayUsername = options.username
            bintrayApiKey = options.apikey
            bintraySubject = options.subject
            bintrayRepository = options.repository
            bintrayLabels = options.labels ? options.labels.tokenize(',') : []
            bintrayLicenses = options.licenses ? options.licenses.tokenize(',') : []
        }

        if (options.ensureRepo) {
            def repoName = options.repo
            def description = options.description
            cli.ensureRepo(repoName, description)
        } else {
            // Assume ensureOrg which doesn't exist on command line
            cli.ensure()
        }
    }
}
