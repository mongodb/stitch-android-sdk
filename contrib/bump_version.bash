#!/bin/sh

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
cd ..

BUMP_TYPE=$1
if [ "$BUMP_TYPE" != "patch" ] && [ "$BUMP_TYPE" != "minor" ] && [ "$BUMP_TYPE" != "major" ]; then
	echo $"Usage: $0 <patch|minor|major>"
	exit 1
fi

# Get current package version
LAST_VERSION=`cat gradle.properties | grep POM_VERSION_NAME | sed s/POM_VERSION_NAME=//`
LAST_VERSION_MAJOR=$(echo $LAST_VERSION | cut -d. -f1)
LAST_VERSION_MINOR=$(echo $LAST_VERSION | cut -d. -f2)
LAST_VERSION_PATCH=$(echo $LAST_VERSION | cut -d. -f3)

NEW_VERSION_MAJOR=$LAST_VERSION_MAJOR
NEW_VERSION_MINOR=$LAST_VERSION_MINOR
NEW_VERSION_PATCH=$LAST_VERSION_PATCH

if [ "$BUMP_TYPE" == "patch" ]; then
	NEW_VERSION_PATCH=$(($LAST_VERSION_PATCH+1))
elif [ "$BUMP_TYPE" == "minor" ]; then
	NEW_VERSION_MINOR=$(($LAST_VERSION_MINOR+1))
	NEW_VERSION_PATCH=0
else
	NEW_VERSION_MAJOR=$(($LAST_VERSION_MAJOR+1))
	NEW_VERSION_MINOR=0
	NEW_VERSION_PATCH=0
fi

NEW_VERSION=$NEW_VERSION_MAJOR.$NEW_VERSION_MINOR.$NEW_VERSION_PATCH

echo "Bumping $LAST_VERSION to $NEW_VERSION ($BUMP_TYPE)"

echo "Updating gradle.properties"
sed -i "" "s/^POM_VERSION_NAME=.*$/POM_VERSION_NAME=$NEW_VERSION/" gradle.properties

echo "Updating stitch/build.gradle"
VERSION_CODE=`date -u "+%Y%m%d"`
sed -i "" "s/libraryVersion.*$/libraryVersion = '$NEW_VERSION'/" stitch/build.gradle
sed -i "" "s/versionName.*$/versionName \"$NEW_VERSION\"/" stitch/build.gradle
sed -i "" "s/versionCode.*$/versionCode $VERSION_CODE/" stitch/build.gradle

git add gradle.properties stitch/build.gradle
git commit -m "Release $NEW_VERSION"
BODY=`git log --no-merges $LAST_VERSION..HEAD --pretty="format:%s (%h); %an"`
BODY="Changelog since $LAST_VERSION:
$BODY"
git tag -a "$NEW_VERSION" -m "$BODY"
