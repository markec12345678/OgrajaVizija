#!/bin/bash
# Prevede in zažene JVM teste jedra (brez Androida, brez Gradla -> deluje tudi z malo RAM-a).
set -e
cd "$(dirname "$0")"
JAVA=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java
CACHE=${GRADLE_CACHE:-${GRADLE_USER_HOME:-"$ARENA_WORKSPACE"/.gradle-home}/caches}/modules-2/files-2.1
find_jar() { find "$CACHE" -name "$1" 2>/dev/null | head -1; }
KC=$(find_jar "kotlin-compiler-embeddable-*.jar")
STD=$(find_jar "kotlin-stdlib-2.0.21.jar")
CP="$KC:$STD:$(find_jar 'kotlin-reflect-*.jar'):$(find_jar 'kotlin-script-runtime-*.jar'):$(find_jar 'kotlin-daemon-embeddable-*.jar'):$(find_jar 'trove4j*.jar'):$(find_jar 'annotations-2*.jar'):$(find_jar 'kotlinx-coroutines-core-jvm-*.jar')"
SRC=../../android/app/src/main/java/si/ograjavizija/app/imaging
rm -rf classes out && mkdir -p classes out
echo "Compiling core + tests..."
"$JAVA" -Xmx700m -XX:+UseSerialGC -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$STD" -d classes \
  "$SRC/Homography.kt" "$SRC/PureCore.kt" CoreTest.kt 2>&1 | grep -v "^warning:" || true
echo "Running tests..."
"$JAVA" -Xmx700m -cp "classes:$STD" test.CoreTest "$@"
