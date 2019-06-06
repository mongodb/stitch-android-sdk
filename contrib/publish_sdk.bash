#!/bin/sh

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
cd ..

# Get current package version
VERSION=`cat gradle.properties | grep VERSION | sed s/VERSION=//`
VERSION_MAJOR=$(echo $VERSION | cut -d. -f1)
VERSION_MINOR=$(echo $VERSION | cut -d. -f2)
VERSION_QUALIFIER=$(echo $VERSION | cut -d- -f2)

git tag "$VERSION"
git push upstream $VERSION

echo "uploading to bintray..."
./gradlew bintrayUpload

echo "pushing to docs..."
./contrib/generate_docs.sh analytics

if ! which aws; then
   echo "aws CLI not found. see: https://docs.aws.amazon.com/cli/latest/userguide/installing.html"
   exit 1
fi

if [ -z "$VERSION_QUALIFIER" ]; then
	# Publish to MAJOR, MAJOR.MINOR
	aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$VERSION_MAJOR --recursive --acl public-read
	aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$VERSION_MAJOR.$VERSION_MINOR --recursive --acl public-read
fi

# Publish to full version
aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$VERSION --recursive --acl public-read

BRANCH_NAME=`git branch | grep -e "^*" | cut -d' ' -f 2`
aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/branch/$BRANCH_NAME --recursive --acl public-read
