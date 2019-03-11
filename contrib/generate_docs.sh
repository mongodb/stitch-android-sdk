#!/bin/bash

# Usage: ./generate_docs [analytics]
# 
# Pass the literal string 'analytics' as the argument 
# to generate with the analytics. (You probably don't
# want to do this for local docs builds.)

cd "$(dirname "$0")"/..

analytics=
if [ "$1" == "analytics" ]; then
    analytics=-Panalytics
fi

pushd contrib/doclet > /dev/null
echo "Building doclet..."
make
if [ $? != 0 ]; then
  echo "Failed to build doclet. Please advise the docs team."
  exit 1
fi
popd > /dev/null

./gradlew allJavadocs $analytics

if [ $? != 0 ]; then
  exit 1
fi

pip show beautifulsoup4 > /dev/null
if [ $? != 0 ]; then
  echo "Installing code example paster dependencies..."
  pip install beautifulsoup4
  if [ $? != 0 ]; then
    echo "Couldn't install dependencies for code example paster script!"
    exit 1
  fi
fi

# Paste code examples
python contrib/paste-examples/paste.py \
  ./build/docs/examplesManifest.json \
  ./ \
  ./build/docs/javadoc/

