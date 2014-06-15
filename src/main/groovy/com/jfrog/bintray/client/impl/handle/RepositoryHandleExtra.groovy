package com.jfrog.bintray.client.impl.handle

import com.jfrog.bintray.client.api.details.PackageDetails
import com.jfrog.bintray.client.api.details.PackageDetailsExtra
import com.jfrog.bintray.client.api.handle.PackageHandle

class RepositoryHandleExtra extends RepositoryHandleImpl {
    private BintrayImpl bintrayHandle
    private SubjectHandleImpl owner
    private String name

    // For Mocking
    RepositoryHandleExtra() {
        super(null, null, null)
    }

    RepositoryHandleExtra(RepositoryHandleImpl impl) {
        super(impl.bintrayHandle, impl.owner, impl.name)
        this.bintrayHandle = impl.bintrayHandle
        this.owner = impl.owner
        this.name = impl.name
    }

    @SuppressWarnings("GroovyAccessibility")
    PackageHandle createPkg(PackageDetails packageDetails) {
        def requestBody = [name: packageDetails.name, desc: packageDetails.description, labels: packageDetails.labels,
                licenses: packageDetails.licenses]
        if (packageDetails instanceof PackageDetailsExtra) {
            requestBody['vcs_url'] = packageDetails.vcsUrl
            requestBody['website'] = packageDetails.website
            requestBody['issueTracker'] = packageDetails.issueTracker
        }
        bintrayHandle.post("packages/${this.owner().name()}/${this.name()}", requestBody)
        new PackageHandleImpl(bintrayHandle, this, packageDetails.name)
    }


    List<String> getPackageNames() {
        def data = bintrayHandle.get("repos/${owner.name()}/$name/packages").data

        return data.collect { it.name }
    }

}
