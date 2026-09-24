#!/bin/bash
# Hitri TIPNI pregled CELOTNE Kotlin kode brez Gradla (deluje tudi z 1 GB RAM).
# Uporabi kotlinc-embeddable iz Gradle cache-a (ali ga prenese z Maven Central).
set -e
cd "$(dirname "$0")/.."
G=${GRADLE_USER_HOME:-"$ARENA_WORKSPACE"/.gradle-home}/caches/modules-2/files-2.1
JAVA=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/java
[ -x "$JAVA" ] || JAVA=$(command -v java)
KC=$(find "$G" -name "kotlin-compiler-embeddable-2.0.21.jar" 2>/dev/null | head -1)
STD=$(find "$G" -name "kotlin-stdlib-2.0.21.jar" 2>/dev/null | head -1)
if [ -z "$KC" ]; then
  echo "kotlin-compiler ni v cacheu -> prenesem z Maven Central"
  mkdir -p tools/jvmtest/tools_jars
  curl -sL -o tools/jvmtest/tools_jars/kc.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.0.21/kotlin-compiler-embeddable-2.0.21.jar
  KC=tools/jvmtest/tools_jars/kc.jar
  curl -sL -o tools/jvmtest/tools_jars/std.jar https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar
  STD=tools/jvmtest/tools_jars/std.jar
fi
PLUG=$(find "$G" -name "kotlin-compose-compiler-plugin-embeddable-2.0.21.jar" 2>/dev/null | head -1)
SPLUG=$(find "$G" -name "kotlin-serialization-compiler-plugin-embeddable-2.0.21.jar" 2>/dev/null | head -1)
EXTRA="$STD:$(find "$G" -name 'kotlin-reflect-*.jar' | head -1):$(find "$G" -name 'kotlin-script-runtime-2.0.21.jar' | head -1):$(find "$G" -name 'kotlin-daemon-embeddable-2.0.21.jar' | head -1):$(find "$G" -name 'trove4j*.jar' | head -1):$(find "$G" -name 'annotations-2*.jar' | head -1):$(find "$G" -name 'kotlinx-coroutines-core-jvm-*.jar' | head -1)"
ANDROID_JAR=${ANDROID_HOME:-"$ARENA_WORKSPACE"/android-sdk}/platforms/android-35/android.jar
CP="$ANDROID_JAR:$(find ${GRADLE_USER_HOME:-"$ARENA_WORKSPACE"/.gradle-home} -name '*.jar' 2>/dev/null | grep -v sources | grep -v tasks-vision | tr '\n' ':')$(ls tools/aarx/*.jar 2>/dev/null | tr '\n' ':')"
"$JAVA" -Xmx640m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -cp "$KC:$EXTRA" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$CP" ${PLUG:+-Xplugin="$PLUG"} ${SPLUG:+-Xplugin="$SPLUG"} \
  -d /tmp/kcheck_out $(find android/app/src/main/java -name '*.kt' | sort)
echo "KCHECK OK: 0 napak"
