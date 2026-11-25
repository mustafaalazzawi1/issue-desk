#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$java_bin" -cp "target/test-classes:target/classes:$(cat target/classpath.txt)" dev.mustafa.issues.PackagedSmoke
