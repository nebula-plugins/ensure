package netflix.ensure

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.handle.Bintray
import com.jfrog.bintray.client.api.handle.PackageHandle
import com.jfrog.bintray.client.api.handle.RepositoryHandle
import com.jfrog.bintray.client.api.handle.SubjectHandle
import com.jfrog.bintray.client.api.model.Pkg
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.impl.handle.RepositoryHandleExtra
import org.eclipse.egit.github.core.Repository
import spock.lang.Specification

class EnsureBintraySpec extends Specification {

    List<String> labels
    List<String> licenses

    Bintray client = Mock()
    SubjectHandle subjectHandle = Mock()
    RepositoryHandle repositoryHandle = Mock()
    RepositoryHandleExtra repositoryHandleExtra = Mock()

    EnsureBintray ensure = new EnsureBintray(false, 'UserName', 'KEY', 'Account', 'Repository', labels, licenses)

    Repository repo = Mock()

    def setup() {
        ensure.client = client
        ensure.subjectHandle = subjectHandle
        ensure.repositoryHandle = repositoryHandle
        ensure.repositoryHandleExtra = repositoryHandleExtra

        repo.getName() >> 'Repo'
        repo.getGitUrl() >> 'git://'
        repo.getDescription() >> 'Description'
    }

    def 'package from repo'() {
        when:
        PackageDetails pkg = ensure.packageFromRepo(repo)

        then:
        pkg instanceof PackageDetailsExtra
        pkg.name == 'Repo'
        pkg.vcsUrl == 'git://'
        pkg.description == 'Description'
        pkg.labels == labels
        pkg.licenses == licenses
    }

    def 'ensure a new package'() {
        PackageHandle pkgHandle = Mock()
        repositoryHandleExtra.createPkg( {it.name == 'Repo'} ) >> pkgHandle

        when:
        def handle = ensure.ensurePackage([], repo)

        then:
        handle == pkgHandle
    }

    def 'ensure a existing package'() {
        Pkg pkg = Mock()
        pkg.name() >> 'Repo'

        PackageHandle pkgHandle = Mock()
        pkgHandle.get() >> pkg

        List<String> names = ['Repo']
        repositoryHandle.pkg('Repo') >> pkgHandle

        when:
        ensure.ensurePackage(names, repo)

        then:
        1 * pkgHandle.update(_)

    }
}
