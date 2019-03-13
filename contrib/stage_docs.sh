#!/bin/sh

set -ex

pushd "$(dirname "$0")"

USER=`whoami`

./generate_docs.sh

if ! which aws; then
    echo "aws CLI not found. see: https://docs.aws.amazon.com/cli/latest/userguide/installing.html"
    popd > /dev/null
    exit 1
fi

BRANCH_NAME=`git branch | grep -e "^*" | cut -d' ' -f 2`

USER_BRANCH="${USER}/${BRANCH_NAME}"

aws s3 --profile 10gen-noc cp ../build/docs/javadoc/ s3://docs-mongodb-org-staging/stitch/"$USER_BRANCH"/sdk/java/ --recursive --acl public-read

echo
echo "Staged URLs:"
echo "  https://docs-mongodbcom-staging.corp.mongodb.com/stitch/$USER_BRANCH/sdk/java/index.html"

popd > /dev/null
