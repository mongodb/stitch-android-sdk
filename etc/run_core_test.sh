set -e

MODULE=$1

SDK_HOME=`pwd`/.android
export JAVA_HOME="/opt/java/jdk8"
export ANDROID_HOME=$SDK_HOME
export ANDROID_SDK_ROOT=$SDK_HOME
export ANDROID_SDK_HOME=$SDK_HOME
export ADB_INSTALL_TIMEOUT=30

cd stitch-java-sdk

echo "test.stitch.baseURL=http://10.0.2.2:9090" >> local.properties

./gradlew $MODULE jacocoTestReport --info --continue --warning-mode=all --stacktrace < /dev/null
