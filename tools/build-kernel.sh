#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/jars.sh
mkdir -p build/kernel-classes
find src/kernel/java -name '*.java' | sort > build/kernel-sources.txt
if [ ! -s build/kernel-sources.txt ]; then echo "no kernel sources"; exit 0; fi
javac --release 21 -Xlint:all -Werror -cp "$KERNEL_CP" -d build/kernel-classes @build/kernel-sources.txt
