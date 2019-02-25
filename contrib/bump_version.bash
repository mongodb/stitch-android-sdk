#!/bin/sh

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
cd ..

BUMP_TYPE=$1
if [ "$BUMP_TYPE" != "snapshot" ] && [ "$BUMP_TYPE" != "beta" ] && [ "$BUMP_TYPE" != "patch" ] && [ "$BUMP_TYPE" != "minor" ] && [ "$BUMP_TYPE" != "major" ] && [ "$BUMP_TYPE" != "release" ]; then
	echo $"Usage: $0 <snapshot|beta|patch|minor|major|release>"
	exit 1
fi

# Get current package version
LAST_VERSION=`cat gradle.properties | grep VERSION | sed s/VERSION=//`
LAST_VERSION_MAJOR=$(echo $LAST_VERSION | cut -d. -f1)
LAST_VERSION_MINOR=$(echo $LAST_VERSION | cut -d. -f2)
LAST_VERSION_PATCH=$(echo $LAST_VERSION | cut -d. -f3 | cut -d- -f1)
LAST_VERSION_QUALIFIER=$(echo $LAST_VERSION | cut -d- -f2)
LAST_VERSION_QUALIFIER_INC=$(echo $LAST_VERSION | cut -d- -f3)

NEW_VERSION_MAJOR=$LAST_VERSION_MAJOR
NEW_VERSION_MINOR=$LAST_VERSION_MINOR
NEW_VERSION_PATCH=$LAST_VERSION_PATCH
NEW_VERSION_QUALIFIER=$LAST_VERSION_QUALIFIER
NEW_VERSION_QUALIFIER_INC=$LAST_VERSION_QUALIFIER_INC

if [ "$BUMP_TYPE" == "snapshot" ]; then
	NEW_VERSION_QUALIFIER="SNAPSHOT"
	NEW_VERSION_QUALIFIER_INC=""
elif [ "$BUMP_TYPE" == "beta" ]; then
	NEW_VERSION_QUALIFIER="beta"
	if [ -z "$LAST_VERSION_QUALIFIER_INC" ] || [ "$LAST_VERSION_QUALIFIER" != "beta" ]; then
		NEW_VERSION_QUALIFIER_INC=1
	else
		NEW_VERSION_QUALIFIER_INC=$(($LAST_VERSION_QUALIFIER_INC+1))
	fi
elif [ "$BUMP_TYPE" == "patch" ]; then
	NEW_VERSION_PATCH=$(($LAST_VERSION_PATCH+1))
	NEW_VERSION_QUALIFIER=""
	NEW_VERSION_QUALIFIER_INC=""
elif [ "$BUMP_TYPE" == "minor" ]; then
	NEW_VERSION_MINOR=$(($LAST_VERSION_MINOR+1))
	NEW_VERSION_PATCH=0
	NEW_VERSION_QUALIFIER=""
	NEW_VERSION_QUALIFIER_INC=""
elif [ "$BUMP_TYPE" == "minor" ]; then
	NEW_VERSION_MAJOR=$(($LAST_VERSION_MAJOR+1))
	NEW_VERSION_MINOR=0
	NEW_VERSION_PATCH=0
	NEW_VERSION_QUALIFIER=""
	NEW_VERSION_QUALIFIER_INC=""
else
	NEW_VERSION_QUALIFIER=""
	NEW_VERSION_QUALIFIER_INC=""
fi

NEW_VERSION=$NEW_VERSION_MAJOR.$NEW_VERSION_MINOR.$NEW_VERSION_PATCH
if [ ! -z "$NEW_VERSION_QUALIFIER" ]; then
	NEW_VERSION=$NEW_VERSION-$NEW_VERSION_QUALIFIER
fi
if [ ! -z "$NEW_VERSION_QUALIFIER_INC" ]; then
	NEW_VERSION=$NEW_VERSION-$NEW_VERSION_QUALIFIER_INC
fi

echo "Bumping $LAST_VERSION to $NEW_VERSION ($BUMP_TYPE)"

read -p "Are you sure you want to push, publish, and upload docs? [yes/no]: " -r
if [[ ! $REPLY = "yes" ]]
then
    exit 1
fi

echo "Updating gradle.properties"
sed -i "" "s/^VERSION=.*$/VERSION=$NEW_VERSION/" gradle.properties

git add gradle.properties

if [[ ! $NEW_VERSION_QUALIFIER = "SNAPSHOT" ]]
then
	git commit -m "Release $NEW_VERSION"
    LAST_TAGGED_VERSION=`git describe --tags --abbrev=0 HEAD^`
	BODY=$(git log --no-merges $LAST_TAGGED_VERSION..HEAD --pretty="format:%s (%h); %an")
	BODY="Changelog since $LAST_TAGGED_VERSION:
$BODY"

	git tag -a "$NEW_VERSION" -m "$BODY"

	echo "pushing to git..."
	git push upstream && git push upstream $NEW_VERSION
else
	set +e
	git commit -m "Update $NEW_VERSION"
	if [ $? -eq 0 ]; then
		set -e
		git push upstream
	fi
	set -e
fi

echo "uploading to bintray..."
./gradlew bintrayUpload

echo "pushing to docs..."
./gradlew allJavadocs -Panalytics

if ! which aws; then
   echo "aws CLI not found. see: https://docs.aws.amazon.com/cli/latest/userguide/installing.html"
   exit 1
fi

if [ -z "$NEW_VERSION_QUALIFIER" ]; then
	# Publish to MAJOR, MAJOR.MINOR
	aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$NEW_VERSION_MAJOR --recursive --acl public-read
	aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$NEW_VERSION_MAJOR.$NEW_VERSION_MINOR --recursive --acl public-read
fi

# Publish to full version
aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/$NEW_VERSION --recursive --acl public-read

BRANCH_NAME=`git branch | grep -e "^*" | cut -d' ' -f 2`
aws s3 cp ./build/docs/javadoc s3://stitch-sdks/stitch-sdks/java/branch/$BRANCH_NAME --recursive --acl public-read
