package com.jfrog.bintray.client.impl.handle

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.api.handle.PackageHandle
import com.jfrog.bintray.client.api.model.Pkg
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpRequestBase

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


    @Override
    PackageHandle update(PackageDetails packageBuilder) {
        def allLabels = pkg.labels()
        packageBuilder.labels.each {
            if (!allLabels.contains(it)) {
                allLabels.add(it)
            }
        }
        def requestBody = [desc: packageBuilder.description, labels: allLabels,
                licenses: packageBuilder.licenses]
        if (packageBuilder instanceof PackageDetailsExtra) {
            requestBody['vcs_url'] = packageBuilder.vcsUrl
            requestBody['website'] = packageBuilder.website
            requestBody['issue_tracker'] = packageBuilder.issueTracker
        }


        def commonPath = "${repositoryHandle.owner().name()}/${repositoryHandle.name()}/$name"
        // Instead of calling bintrayHandle.patch in PackageHandle.update
        def path = "packages/$commonPath"
        bintrayHandle.restClient.patch([path: path, body: requestBody])

        // set attributes
        def attributePath = "/packages/$commonPath/attributes"
        def attributeBody = [public_download_numbers: true]
        if (packageBuilder instanceof PackageDetailsExtra) {
            attributeBody['website'] = packageBuilder.website
            attributeBody['issue_tracker'] = packageBuilder.issueTracker
            attributeBody['vcs_url'] = packageBuilder.vcsUrl
            attributeBody['github_repo'] = packageBuilder.githubRepo
            attributeBody['github_release_notes'] = packageBuilder.githubReleaseNotes

        }
        bintrayHandle.restClient.patch([path: attributePath, body: attributeBody])

        this
    }
}
