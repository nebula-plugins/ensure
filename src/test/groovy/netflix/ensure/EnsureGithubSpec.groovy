package netflix.ensure

import netflix.ensure.githubextra.RepositoryServiceExtra
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.Team
import org.eclipse.egit.github.core.service.TeamService
import spock.lang.Specification

import java.util.regex.Pattern

class EnsureGithubSpec extends Specification {
    Collection<Pattern> regexMock = []
    TeamService teamService = Mock()
    RepositoryServiceExtra repoService = Mock()
    EnsureGithub ensure = new EnsureGithub(false, null, 'fakeOrg', 'fake-contrib', true, regexMock)

    def setup() {
        ensure.repoService = repoService
        ensure.teamService = teamService
    }

    def 'create team'() {
        when:
        ensure.createTeam('fakeTeam', 'pull')

        then:
        1 * teamService.createTeam('fakeOrg', { it.permission == 'pull' && it.name == 'fakeTeam' })
        0 * _
    }

    def 'existing team for repo'() {
        Repository repo = Mock()
        repo.getName() >> 'fake'
        Team contribTeam = new Team().setName('fake-contrib').setId(111)
        def teams = [contribTeam]

        when:
        teamService.getRepositories(111) >> [repo]
        ensure.ensureRepoTeam(repo, teams, 'fake-contrib', 'pull')

        then:
        0 * teamService._

        when:
        teamService.getRepositories(111) >> []
        teamService.createTeam('fakeOrg', {it.name == 'fake-contrib'} ) >> contribTeam
        ensure.ensureRepoTeam(repo, [], 'fake-contrib', 'pull')

        then:
        1 * teamService.addRepository(111, repo)
    }

    def 'ensure hook'() {
        Repository repo = Mock()

        when:
        ensure.ensureHook(repo, [], 'http://hook', ['all'] as String[])

        then:
        1 * repoService.createHook(repo, {
            it.name == 'web' && it.events[0] == 'all' && it.config['url'] == 'http://hook'
        })
    }

    def 'match repositories'() {
        Repository repoA = new Repository().setName('fake-A')
        Repository repoB = new Repository().setName('fake-B')
        Repository repoC = new Repository().setName('real-C')
        regexMock << /fake-.*/

        when:
        def repos = ensure.matchRepositories([repoA, repoB, repoC])

        then:
        repos.contains(repoA)
        repos.contains(repoB)
        !repos.contains(repoC)

    }
}
