package netflix.ensure

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.api.handle.Bintray
import com.jfrog.bintray.client.api.handle.PackageHandle
import com.jfrog.bintray.client.api.handle.RepositoryHandle
import com.jfrog.bintray.client.api.handle.SubjectHandle
import com.jfrog.bintray.client.api.model.Pkg
import com.jfrog.bintray.client.impl.BintrayClient
import com.jfrog.bintray.client.impl.handle.PackageHandleExtra
import com.jfrog.bintray.client.impl.handle.PackageHandleImpl
import com.jfrog.bintray.client.impl.handle.RepositoryHandleExtra
import org.eclipse.egit.github.core.Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * <ul>
 *     <li>From https://bintray.com/nebula/gradle-plugins, click Add Package, name it
 *     <li>Set name, description, license (to Apache-2.0), tags (gradle, plugin, nebula), website (https://github.com/nebula-plugins/REPO), issues (https://github.com/nebula-plugins/REPO/issues), version control (https://github.com/nebula-plugins/REPO), and make download stats public.
 *     <li>On next page, fill in Github repo, nebula-plugins/REPO, save changes
 *     <li>On next page, fill in GitHub release notes file, CHANGELOG.md, save changes
 *     <li>From https://bintray.com/nebula/gradle-plugins/REPO, Click "Add to JCenter". Click "Host my snapshots..." and fill in group id as com.netflix.nebula, click Send. This will take a day to process.
 * </ul>
 */
class EnsureBintray {
    final static Logger logger = LoggerFactory.getLogger(EnsureBintray.class);

    boolean dryRun
    String username
    String apiKey
    String subject
    String repository
    String githubOrg
    String githubReleaseNotes = 'CHANGELOG.md'
    List<String> labels
    List<String> licenses

    Bintray client
    SubjectHandle subjectHandle
    RepositoryHandle repositoryHandle
    RepositoryHandleExtra repositoryHandleExtra

    EnsureBintray(boolean dryRun, String username, String apiKey, String subject, String repository, List<String> labels, List<String> licenses, String githubOrg) {
        this.dryRun = dryRun
        this.username = username
        this.apiKey = apiKey
        this.subject = subject
        this.repository = repository
        this.githubOrg = githubOrg

        this.labels = labels
        this.licenses = licenses

        client = BintrayClient.create(username, apiKey)
        subjectHandle = client.subject(subject)
        repositoryHandle = subjectHandle.repository(repository)
        repositoryHandleExtra = new RepositoryHandleExtra(repositoryHandle)
    }

    def ensure(List<Repository> repos) {
        logger.info("Ensure bintray for ${subjectHandle.name()}")

        def names = repositoryHandleExtra.getPackageNames()

        // Can only search by attribute, which we don't have
        // List<Pkg> packages = ((ArtibutesSearchQueryImpl) repositoryHandle.searchForPackage()).search()
        // Map<String, Pkg> packageMap = packages.collectEntries { Pkg pkg -> [pkg.name(), pkg] }

        repos.collect { Repository repo ->
            logger.info("Ensuring repo ${repo.name}")
            ensurePackage(names, repo)
        }
    }

    def ensurePackage(List<String> packageNames, Repository repo) {
        PackageDetailsExtra ideal = packageFromRepo(repo)
        PackageHandle handle
        if (!packageNames.contains(repo.name)) {
            logger.info("Creating package ${ideal.name}")
            // TODO Set Github release notes, website, issue tracker

            if (!dryRun) {
                handle = repositoryHandleExtra.createPkg(ideal)
            }
        } else {
            handle = repositoryHandle.pkg(repo.name)
            Pkg pkg = handle.get()
            logger.debug("Inspecting package ${pkg.name()}")

            def handleExtra = new PackageHandleExtra( (PackageHandleImpl) handle)
            if ( isPackageOutOfDate(pkg, repo) || areAttributesDifferent(handleExtra.attributes(), ideal) ) {
                logger.info("Updating ${pkg.name()}")
                if (!dryRun) {
                    handleExtra.update(ideal)
                }
            }
        }
        
        handle
    }

    boolean isPackageOutOfDate(Pkg pkg, Repository repo) {
        def description = pkg.description != repo.description
        def labels = pkg.labels().containsAll(labels)

        description || labels
    }

    boolean areAttributesDifferent(attrs, PackageDetailsExtra ideal) {
        attrs['website_url'] != ideal.website ||
        attrs['issue_tracker_url'] != ideal.issueTracker ||
        attrs['github_repo'] != ideal.githubRepo ||
        attrs['vcs_url'] != ideal.vcsUrl ||
        attrs['github_release_notes_file'] != ideal.githubReleaseNotes
    }

    PackageDetails packageFromRepo(Repository repo) {
        def githubRepo = "${githubOrg}/${repo.name}".toString()
        def website = "http://github.com/${githubRepo}".toString()
        def issueTracker = "${website}/issues".toString()

        def ideal = new PackageDetailsExtra(repo.name).vcsUrl(repo.gitUrl).website(website).issueTracker(issueTracker).githubRepo(githubRepo)
                .githubReleaseNotes(githubReleaseNotes).description(repo.description).labels(labels).licenses(licenses)
        
        ideal
    }
}
