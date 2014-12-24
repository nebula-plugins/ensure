package netflix.ensure

import groovy.transform.Canonical
import org.eclipse.egit.github.core.Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

@Canonical
class Cli {
    final static Logger logger = LoggerFactory.getLogger(Cli.class);

    String configFileName

    String repoPattern

    String githubOauth
    String githubOrg
    String githubOrgContribName
    boolean githubContribTeamNameStyleLowercase

    String bintrayUsername
    String bintrayApiKey
    String bintraySubject
    String bintrayRepository
    List<String> bintrayLabels
    List<String> bintrayLicenses

    String jenkinsServer

    boolean dryRun
    // TODO Entry point to create repo, and initialize it. Easyist called from a bot

    /**
     * Ensure state matches what we ask in:
     * https://github.com/nebula-plugins/nebula-plugins.github.io/wiki/New-Plugins
     */
    def ensure() {
        EnsureGithub ensure = new EnsureGithub(dryRun, githubOauth, githubOrg, githubOrgContribName, githubContribTeamNameStyleLowercase, getRepoRegexes(), jenkinsServer)
        ensure.ensureOrg()
        // TODO Someway to ensure the users are in the correct groups, e.g. all netflix users in netflix-contrib
        // TODO Only look at public repos

        // Bintray
        List<Repository> repos = ensure.findPublicRepositoriesMatchingGradlePattern(/apply plugin: ('|")nebula.netflixoss('|")/)
        EnsureBintray ensureBintray = new EnsureBintray(dryRun, bintrayUsername, bintrayApiKey, bintraySubject, bintrayRepository, bintrayLabels, bintrayLicenses, githubOrg)
        ensureBintray.ensure(repos)

        // Cloudbees (job.dsl should take care of this)
    }

    private List<Pattern> getRepoRegexes() {
        def repoPatterns = repoPattern.tokenize(',').collect { Pattern.compile(it) }
        repoPatterns
    }

    def ensureRepo(String repoName, String description) {
        assert repoName

        EnsureGithub ensure = new EnsureGithub(dryRun, githubOauth, githubOrg, githubOrgContribName, githubContribTeamNameStyleLowercase, getRepoRegexes(), jenkinsServer)
        ensure.ensureRepo(repoName, description)
    }

    public static void main(String[] args) {
        // assume SLF4J is bound to logback in the current environment
        //LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        //StatusPrinter.print(lc);

        def cliBuilder = new CliBuilder(usage:'ensure [options] [targets]', header:'Options:')

        cliBuilder.h(longOpt: 'help', 'Help')
        cliBuilder.f(longOpt: 'configFile', args: 1, argName: 'configFileName', 'Config file')
        cliBuilder._(longOpt: 'repo', args: 1, argName: 'repository name', 'Name of repository to ensure')
        cliBuilder._(longOpt: 'description', args: 1, argName: 'text', 'Description of repository to ensure')

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
        Properties props = new Properties()
        cli.with {

            dryRun = (options.dryrun) as Boolean
            if (dryRun) {
                logger.warn("Running in DRY RUN mode")
            }
            configFileName = options.configFile

            props.load(new File(configFileName).newDataInputStream())

            repoPattern = props.get('repoPattern')

            // Github
            githubOauth = props.get('githubToken')
            githubOrg = props.get('githubOrg')
            githubOrgContribName = (props.get('githubOrgContribName')?:'contrib')
            githubContribTeamNameStyleLowercase = (props.get('githubOrgContribNameStyle') == 'lowercase')

            // Bintray
            bintrayUsername = props.get('bintrayUser')
            bintrayApiKey = props.get('bintrayKey')
            bintraySubject = props.get('bintraySubject')
            bintrayRepository = props.get('bintrayRepository')
            bintrayLabels = props.get('bintrayLabels') ? props.get('bintrayLabels').tokenize(',') : []
            bintrayLicenses = props.get('bintrayLicenses') ? props.get('bintrayLicenses').tokenize(',') : []

            // Cloudbees
            jenkinsServer = props.get('jenkinsServer')
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
