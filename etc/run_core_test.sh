set -e

MODULE=$1

cd stitch-java-sdk
SDK_HOME=`pwd`/.android
export JAVA_HOME="/opt/java/jdk8"
export ANDROID_HOME=$SDK_HOME
export ANDROID_SDK_ROOT=$SDK_HOME
export ANDROID_SDK_HOME=$SDK_HOME

./gradlew MODULE jacocoTestReport --info --continue --warning-mode=all --stacktrace < /dev/null
