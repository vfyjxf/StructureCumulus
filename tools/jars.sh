#!/usr/bin/env bash
# Resolve the jars the sandbox can read but Gradle cannot write. The shipping build uses Gradle.
set -euo pipefail
CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"
JSPECIFY="$(find "$CACHE/org.jspecify/jspecify/1.0.1" -name 'jspecify-1.0.1.jar' | head -1)"
JUPITER_API="$(find "$CACHE/org.junit.jupiter/junit-jupiter-api/5.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
JUPITER_ENGINE="$(find "$CACHE/org.junit.jupiter/junit-jupiter-engine/5.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
JUPITER_PARAMS="$(find "$CACHE/org.junit.jupiter/junit-jupiter-params/5.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
PLATFORM_COMMONS="$(find "$CACHE/org.junit.platform/junit-platform-commons/1.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
PLATFORM_ENGINE="$(find "$CACHE/org.junit.platform/junit-platform-engine/1.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
PLATFORM_LAUNCHER="$(find "$CACHE/org.junit.platform/junit-platform-launcher/1.10.3" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
OPENTEST="$(find "$CACHE/org.opentest4j/opentest4j/1.3.0" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
APIGUARDIAN="$(find "$CACHE/org.apiguardian/apiguardian-api/1.1.2" -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)"
export JSPECIFY JUPITER_API JUPITER_ENGINE JUPITER_PARAMS PLATFORM_COMMONS PLATFORM_ENGINE PLATFORM_LAUNCHER OPENTEST APIGUARDIAN
export KERNEL_CP="$JSPECIFY"
export TEST_CP="$JSPECIFY:$JUPITER_API:$JUPITER_ENGINE:$JUPITER_PARAMS:$PLATFORM_COMMONS:$PLATFORM_ENGINE:$PLATFORM_LAUNCHER:$OPENTEST:$APIGUARDIAN"
