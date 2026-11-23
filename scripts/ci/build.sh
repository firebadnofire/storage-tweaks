#!/usr/bin/env bash
set -euo pipefail

java -version 2>&1 | grep -E 'version "1\.8\.'
test -x "$JAVA_HOME/bin/javac"
sed 's/\r$//' gradlew > .ci-gradlew
trap 'rm -f .ci-gradlew' EXIT
bash .ci-gradlew --version | tee gradle-version.log
grep -F 'Gradle 4.9' gradle-version.log
bash .ci-gradlew --no-daemon --console=plain :common:test :forge-1.12.2:build 2>&1 | tee gradle-build.log
grep -E '^(> Task )?:forge-1\.12\.2:reobfJar( |$)' gradle-build.log
rm -rf ci-dist
python3 scripts/ci/artifact.py stage ci-dist
