package com.jfrog.bintray.client.api.details
/**
 {
 "name": "my-package",
 "repo": "repo",
 "owner": "user",
 "desc": "This package...",
 "labels": ["persistence", "database"],
 "attribute_names": ["licenses", "vcs", "github", ...],
 "rating": 8,
 "rating_count": 8,
 "followers_count": 82,
 "created": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
 "versions": ["0.9", "1.0", "1.0.1", ...],
 "latest_version": "1.2.5",
 "updated": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
 "linked_to_repo": "the repo this package was linked to (relevant to search results)"
 "vcs_url": "https://github.com/bintray/bintray-client-java.git",
 "website": "https://github.com/bintray/bintray-client-java",
 "issue_tracker": "https://github.com/bintray/bintray-client-java/issues"
 }

 I can't find the API for website, issue tracker, Github repo, Github release notes file, how to make the download numbers public
 */
public class PackageDetailsExtra extends PackageDetails {

    String vcsUrl
    String website
    String issueTracker

    PackageDetailsExtra(String name) {
        super(name)
    }

    public PackageDetailsExtra vcsUrl(String vcsUrl) {
        this.vcsUrl = vcsUrl

        return this
    }

    PackageDetailsExtra website(String website) {
        this.website = website

        this
    }

    PackageDetailsExtra issueTracker(String issueTracker) {
        this.issueTracker = issueTracker

        this
    }

    public String getName() {
        return super.name
    }

    public String getDescription() {
        return super.description
    }

    public List<String> getLicenses() {
        return super.licenses
    }

    public List<String> getLabels() {
        return super.labels
    }
}
