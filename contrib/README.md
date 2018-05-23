#### Contribution Guide

### Summary

This project follows [Semantic Versioning 2.0](https://semver.org/). In general, every release is associated with a tag and a changelog. `master` serves as the mainline branch for the project and represent the latest state of development.

### Publishing a New SDK version
```bash
# run bump_version.bash with either patch, minor, or major
./bump_version.bash <snapshot|beta|patch|minor|major>

# send an email detailing the changes to the https://groups.google.com/d/forum/mongodb-stitch-announce mailing list
```

#### Configuring Bintray Upload
For the `./gradlew bintrayUpload` command to work properly, you must locally specify your Bintray credentials. You can do this by adding the following lines to your `local.properties` file:

```
publish.bintray.user=<bintray_user>
publish.bintray.apiKey=<bintray_api_key>
publish.bintray.gpgPassphrase=<gpg_passphrase># optional
publish.bintray.mavenSyncUser=<maven_central_sync_user> # optional
publish.bintray.mavenSyncPassword=<maven_central_sync_password> # optional
```

### Snapshot Versions

A snapshot is a Java specific packaging pattern that allows us to publish the unstable state of a version. The general publishing flow can be followed using `snapshot` as the bump type in `bump_version`. This will not do any tagging but will only publish the artifacts and docs.

### Patch Versions

The work for a patch version should happen on a "support branch" (e.g. 1.2.x). The purpose of this is to be able to support a minor release while excluding changes from the mainstream (`master`) branch. If the changes in the patch are relevant to other branches, including `master`, they should be backported there. The general publishing flow can be followed using `patch` as the bump type in `bump_version`.

### Minor Versions

The general publishing flow can be followed using `minor` as the bump type in `bump_version`.

### Major Versions

The general publishing flow can be followed using `major` as the bump type in `bump_version`. In addition to this, the release on GitHub should be edited for a more readable format of key changes and include any migration steps needed to go from the last major version to this one.