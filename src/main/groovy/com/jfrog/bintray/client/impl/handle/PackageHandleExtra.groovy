package com.jfrog.bintray.client.impl.handle

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.api.handle.PackageHandle
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpRequestBase

class PackageHandleExtra extends PackageHandleImpl {
    private BintrayImpl bintrayHandle
    private RepositoryHandleImpl repositoryHandle
    private String name

    // For Mocking
    PackageHandleExtra() {
        super(null, null, null)
    }

    PackageHandleExtra(PackageHandleImpl impl) {
        super(impl.bintrayHandle, impl.repositoryHandle, impl.name)

        this.bintrayHandle = impl.bintrayHandle
        this.repositoryHandle = impl.repositoryHandle
        this.name = impl.name
    }


    @Override
    PackageHandle update(PackageDetails packageBuilder) {
        def requestBody = [desc: packageBuilder.description, labels: packageBuilder.labels,
                licenses: packageBuilder.licenses]
        if (packageBuilder instanceof PackageDetailsExtra) {
            requestBody['vcs_url'] = packageBuilder.vcsUrl
        }


        // Instead of calling bintrayHandle.patch in PackageHandle.update
        def path = "packages/${repositoryHandle.owner().name()}/${repositoryHandle.name()}/$name"
        bintrayHandle.restClient.patch([path: path, body: requestBody]);

        this
    }
}
