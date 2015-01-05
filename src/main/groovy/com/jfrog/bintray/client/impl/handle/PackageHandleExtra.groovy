package com.jfrog.bintray.client.impl.handle

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.api.handle.PackageHandle
import com.jfrog.bintray.client.api.model.Pkg

class PackageHandleExtra extends PackageHandleImpl {
    private BintrayImpl bintrayHandle
    private RepositoryHandleImpl repositoryHandle
    private String name
    private Pkg pkg

    // For Mocking
    PackageHandleExtra() {
        super(null, null, null)
    }

    PackageHandleExtra(PackageHandleImpl impl) {
        super(impl.bintrayHandle, impl.repositoryHandle, impl.name)

        this.bintrayHandle = impl.bintrayHandle
        this.repositoryHandle = impl.repositoryHandle
        this.name = impl.name
        this.pkg = impl.get()
    }

    /**
     * We're re-writing the PackageHandleImpl because their update leaves out vcs, website and issue tracker.
     * And then we're using this call to update the attributes on the package too.
     */
    @Override
    PackageHandle update(PackageDetails packageBuilder) {
        def allLabels = pkg.labels()
        packageBuilder.labels.each {
            if (!allLabels.contains(it)) {
                allLabels.add(it)
            }
        }
        def requestBody = [desc: packageBuilder.description, labels: allLabels,
                licenses: packageBuilder.licenses, public_download_numbers: true, vcs_url: packageDetails.vcsUrl,
                website_url: packageDetails.websiteUrl, issue_tracker_url: packageDetails.issueTrackerUrl]
        if (packageBuilder instanceof PackageDetailsExtra) {
            requestBody['github_repo'] = packageBuilder.githubRepo
            requestBody['github_release_notes_file'] = packageBuilder.githubReleaseNotes
        }

        def commonPath = "${repositoryHandle.owner().name()}/${repositoryHandle.name()}/$name"
        // Instead of calling bintrayHandle.patch in PackageHandle.update
        def path = "packages/$commonPath"
        bintrayHandle.restClient.patch([path: path, body: requestBody])

        this
    }

    Map<String, List<String>> attributes() {
        def data = bintrayHandle.get("packages/${repositoryHandle.owner().name()}/${repositoryHandle.name()}/$name/attributes").data
        createAttributesFromJsonMap(data)
    }

    private static Map<String, List<String>> createAttributesFromJsonMap(data) {
        data.collectEntries {
            [it.name, it.values]
        }
    }

}
