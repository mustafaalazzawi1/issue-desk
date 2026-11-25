#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
exec bash scripts/preview.sh
