# Generation harness

The shipping build is Gradle. The sandbox that regenerated the kernel could not let Gradle write
its wrapper cache under `~/.gradle`, so these scripts compile and test the kernel directly with the
same jars Gradle resolves.

```bash
tools/build-kernel.sh    # javac --release 21, JSpecify only, -Werror, src/kernel/java
tools/test-kernel.sh     # compile src/kernelTest and run JUnit 5
python3 tools/spec-check.py
```

`jars.sh` finds JSpecify and JUnit 5 in the Gradle cache; `RunTests.java` is a tiny launcher for the
JUnit Platform. On a normal machine, prefer:

```bash
./gradlew compileKernelJava kernelPurity kernelTest
./gradlew spotlessApply   # the project formatter owns import order and line width
```

The commands that constitute a green build are listed in
[design/06-verification.md](../docs/design/06-verification.md) §1.1, "Green build".

These scripts are development aids, not part of the kernel source set.
