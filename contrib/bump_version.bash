#!/bin/sh

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
cd ..

# Ensure that 'hub' is installed
which hub || (echo "hub is not installed. Please see contrib/README.md for more info" && exit 1)

BUMP_TYPE=$1
if [ "$BUMP_TYPE" != "snapshot" ] && [ "$BUMP_TYPE" != "beta" ] && [ "$BUMP_TYPE" != "patch" ] && [ "$BUMP_TYPE" != "minor" ] && [ "$BUMP_TYPE" != "major" ] && [ "$BUMP_TYPE" != "release" ]; then
	echo $"Usage: $0 <snapshot|beta|patch|minor|major|release>"
	exit 1
fi

JIRA_TICKET=$2
if [ -z "$JIRA_TICKET" ]
    then
        echo $"Usage: must provide Jira ticket number (Ex: STITCH-1234, or 1234)"
        exit 1
fi

if [[ $JIRA_TICKET != *"-"* ]] ; then
    JIRA_TICKET="STITCH-$JIRA_TICKET"
fi
echo "Jira Ticket: $JIRA_TICKET"


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

read -p "Are you sure you want to open a PR to bump the version of the SDK? [yes/no]: " -r
if [[ ! $REPLY = "yes" ]]
then
    exit 1
fi

echo "Updating gradle.properties"
sed -i "" "s/^VERSION=.*$/VERSION=$NEW_VERSION/" gradle.properties

git add gradle.properties
git checkout -b "Release-$NEW_VERSION"
if [[ ! $NEW_VERSION_QUALIFIER = "SNAPSHOT" ]]
then
	git commit -m "Release $NEW_VERSION"

    echo "creating pull request in github..."
    git push -u upstream "Release-$NEW_VERSION"
    hub pull-request -m "$JIRA_TICKET: Release $NEW_VERSION" --base mongodb:master --head mongodb:"Release-$NEW_VERSION"
else
    set +e
    git commit -m "$JIRA_TICKET: Update $NEW_VERSION"
    if [ $? -eq 0 ]; then
	    echo "creating pull request in github..."
        set -e
        git push -u upstream "Release-$NEW_VERSION"
        hub pull-request -m "$JIRA_TICKET: Release $NEW_VERSION" --base mongodb:master --head mongodb:"Release-$NEW_VERSION"
	fi
	set -e
fi
