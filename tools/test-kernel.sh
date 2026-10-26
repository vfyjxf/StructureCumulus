#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/jars.sh
tools/build-kernel.sh
rm -rf build/kernel-test-classes build/test-runner
mkdir -p build/kernel-test-classes build/test-runner
find src/kernelTest/java -name '*.java' | sort > build/kernel-test-sources.txt
if [ ! -s build/kernel-test-sources.txt ]; then echo "no kernel tests"; exit 0; fi
javac --release 21 -Xlint:all -cp "build/kernel-classes:$TEST_CP" -d build/kernel-test-classes @build/kernel-test-sources.txt
javac --release 21 -cp "$TEST_CP" -d build/test-runner tools/RunTests.java
java -cp "build/kernel-classes:build/kernel-test-classes:build/test-runner:$TEST_CP" RunTests dev.vfyjxf.cumulus.kernel
