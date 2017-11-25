#### Publishing a new SDK version

1. Update stitch/build.gradle with the new version number in the ext block.
2. Update gradle.properties with the new version number in the POM_VERSION_NAME property.
3. Run `./gradlew bintrayUpload`
