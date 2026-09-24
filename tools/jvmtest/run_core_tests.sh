#!/bin/bash
# Prevede in zažene JVM teste jedra (brez Androida, brez Gradla -> deluje tudi z malo RAM-a).
set -e
cd "$(dirname "$0")"
JAVA=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java
CACHE_BASE=${GRADLE_CACHE:-${GRADLE_USER_HOME:-"$ARENA_WORKSPACE"/.gradle-home}/caches}
CACHE=$CACHE_BASE/modules-2/files-2.1
# Fallback (CI / sveze okolje): kotlin-compiler-embeddable + stdlib z Maven Central
if [ ! -d "$CACHE" ] || [ -z "$(find "$CACHE" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | head -1)" ]; then
  echo "Kotlin compiler ni v cacheu -> prenesem z Maven Central ..."
  mkdir -p tools_jars
  curl -sL -o tools_jars/kc.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.0.21/kotlin-compiler-embeddable-2.0.21.jar
  curl -sL -o tools_jars/std.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar
  curl -sL -o tools_jars/cor.jar https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar
  curl -sL -o tools_jars/ann.jar https://repo1.maven.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar
  curl -sL -o tools_jars/trove.jar https://repo1.maven.org/maven2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar
  curl -sL -o tools_jars/reflect.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/2.0.21/kotlin-reflect-2.0.21.jar
  curl -sL -o tools_jars/script.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-script-runtime/2.0.21/kotlin-script-runtime-2.0.21.jar
  curl -sL -o tools_jars/daemon.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-daemon-embeddable/2.0.21/kotlin-daemon-embeddable-2.0.21.jar
  CACHE=$(pwd)/tools_jars_flat
  mkdir -p "$CACHE/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.0.21/x" "$CACHE/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/x" "$CACHE/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/x" "$CACHE/org/jetbrains/annotations/23.0.0/x"
  cp tools_jars/kc.jar "$CACHE/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.0.21/x/kotlin-compiler-embeddable-2.0.21.jar"
  cp tools_jars/std.jar "$CACHE/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/x/kotlin-stdlib-2.0.21.jar"
  cp tools_jars/cor.jar "$CACHE/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/x/kotlinx-coroutines-core-jvm-1.9.0.jar"
  cp tools_jars/ann.jar "$CACHE/org/jetbrains/annotations/23.0.0/x/annotations-23.0.0.jar"
  mkdir -p "$CACHE/org/jetbrains/intellij/deps/trove4j/1.0.20200330/x" "$CACHE/org/jetbrains/kotlin/kotlin-reflect/2.0.21/x" "$CACHE/org/jetbrains/kotlin/kotlin-script-runtime/2.0.21/x" "$CACHE/org/jetbrains/kotlin/kotlin-daemon-embeddable/2.0.21/x"
  cp tools_jars/trove.jar "$CACHE/org/jetbrains/intellij/deps/trove4j/1.0.20200330/x/trove4j-1.0.20200330.jar"
  cp tools_jars/reflect.jar "$CACHE/org/jetbrains/kotlin/kotlin-reflect/2.0.21/x/kotlin-reflect-2.0.21.jar"
  cp tools_jars/script.jar "$CACHE/org/jetbrains/kotlin/kotlin-script-runtime/2.0.21/x/kotlin-script-runtime-2.0.21.jar"
  cp tools_jars/daemon.jar "$CACHE/org/jetbrains/kotlin/kotlin-daemon-embeddable/2.0.21/x/kotlin-daemon-embeddable-2.0.21.jar"
fi
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
