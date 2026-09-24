#!/bin/bash
# Gradnja APK-ja. Uporabi: ./build.sh [debug|release|clean]
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export ANDROID_HOME=${ANDROID_HOME:-"$ARENA_WORKSPACE"/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$ARENA_WORKSPACE"/.gradle-home}
export PATH=$JAVA_HOME/bin:$PATH
TASK=${1:-debug}
case "$TASK" in
  clean)   cd android && /opt/gradle-8.10.2/bin/gradle --no-daemon clean ;;
  release) cd android && /opt/gradle-8.10.2/bin/gradle --no-daemon --max-workers=1 :app:assembleRelease ;;
  *)       cd android && /opt/gradle-8.10.2/bin/gradle --no-daemon --max-workers=1 :app:assembleDebug ;;
esac
