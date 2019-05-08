#### Contribution Guide

### Summary

This project follows [Semantic Versioning 2.0](https://semver.org/). In general, every release is associated with a tag and a changelog. `master` serves as the mainline branch for the project and represent the latest state of development.

### 1. Incrementing the SDK version
```bash
# run bump_version.bash with either patch, minor, or major followed by the JIRA ticket number (you may omit the STITCH keyword if you would like).
./bump_version.bash <snapshot|beta|patch|minor|major> <STITCH-1234|1234>
```

* go to [Android SDK](https://github.com/mongodb/stitch-android-sdk/pulls) and request a reviewer on the pull request (mandatory) before merging and deleting the release branch

#### Configuring Hub
For `bump_version.bash` to work properly, you must have ```hub``` installed. Please see [Hub](https://github.com/github/hub) for installation details.

### 2. Publishing the new SDK

Once the Pull Request created by `bump_version.bash` is successfully merged into Github publish the SDK using the following command:
```bash
./publish_sdk.bash
```

#### Generating the documentation

A custom doclet is used for the javadoc output. This doclet is written for Java 11 and its version of javadoc.
The source is located in contrib/doclet and will be built as part of the generate_docs.sh script.
Please set your JAVA_HOME to your Java 11 installation before building.

On macOS, you can install Java 11 with brew if it is not already installed:

```bash
brew cask install java11
```

Then set the JAVA_HOME environment variable:

```bash
# Minor version (i.e. .0.2) may differ on your machine. Please adjust accordingly.
export JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-11.0.2.jdk/Contents/Home
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

### 3. Publish the release on Github
Publish a release for the new SDK version on the GitHub repository and include relevant release notes. See https://help.github.com/en/articles/creating-releases for context, and follow the general format of our previous releases.

### Snapshot Versions

A snapshot is a Java specific packaging pattern that allows us to publish the unstable state of a version. The general publishing flow can be followed using `snapshot` as the bump type in `bump_version`. This will not do any tagging but will only publish the artifacts and docs.

### Patch Versions

The work for a patch version should happen on a "support branch" (e.g. 1.2.x). The purpose of this is to be able to support a minor release while excluding changes from the mainstream (`master`) branch. If the changes in the patch are relevant to other branches, including `master`, they should be backported there. The general publishing flow can be followed using `patch` as the bump type in `bump_version`.

### Minor Versions

The general publishing flow can be followed using `minor` as the bump type in `bump_version`.

### Major Versions

The general publishing flow can be followed using `major` as the bump type in `bump_version`. In addition to this, the release on GitHub should be edited for a more readable format of key changes and include any migration steps needed to go from the last major version to this one.

### Testing (MongoDB Internal Contributors Only)

* Before committing, the ```connectedDebugAndroidTest``` suite of integration tests must succeed.
* The tests require the following setup:
    * You must enable clear text traffic in the core Android application locally (**do not commit this change**)
        * In file *android/core/src/main/AndroidManifest.xml*, change the ```application``` XML tag as follows:
            ```<application android:usesCleartextTraffic="true">```
    * You must run at least one ```mongod``` instance with replica sets initiated or a ```mongos``` instance with same locally on port 26000
    * You must run the Stitch server locally using the Android-specific configuration:
        ```--configFile ./etc/configs/test_config_sdk_base.json --configFile ./etc/configs/test_config_sdk_android.json```
    * For example, here's how to start mongod (using mlaunch), start the Stitch server, then run the tests:
```
mlaunch init --replicaset --port 26000

# in stitch source directory
go run cmd/server/main.go --configFile ./etc/configs/test_config_sdk_base.json --configFile ./etc/configs/test_config_sdk_android.json

# in android SDK directory
./gradlew connectedDebugAndroidTest
```
