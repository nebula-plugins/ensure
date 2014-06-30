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
 "website_url": "http://jfrog.com",
 "issue_tracker_url": "https://github.com/bintray/bintray-client-java/issues",
 "github_repo": "", (publishers only)
 "github_release_notes_file": "", (publishers only)
 "public_download_numbers": false, (publishers only)
 "public_stats": true, (publishers only)
 "linked_to_repos": [],
 "versions": ["0.9", "1.0", "1.0.1", ...],
 "latest_version": "1.2.5",
 "updated": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
 "vcs_url": "https://github.com/bintray/bintray-client-java.git"
 }


 */
public class PackageDetailsExtra extends PackageDetails {

    String githubRepo
    String githubReleaseNotes

    PackageDetailsExtra(String name) {
        super(name)
    }

    PackageDetailsExtra githubRepo(String repo) {
        this.githubRepo = repo

        this
    }

    PackageDetailsExtra githubReleaseNotes(String releaseNotes) {
        this.githubReleaseNotes = releaseNotes

        this
    }

/*    public String getName() {
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

    public String getVcsUrl() {
        super.vcsUrl
    }

    public String getWebsiteUrl() {
        super.websiteUrl
    }

    public String getIssueTrackerUrl() {
        super.issueTrackerUrl
    }

    public getPublicDownloadNumbers() {
        super.publicDownloadNumbers
    }*/
}
