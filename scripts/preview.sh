#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Accept the host/port arguments supplied by a managed development preview.
while (($#)); do
  case "$1" in
    --port) export PORT="$2"; shift 2 ;;
    --port=*) export PORT="${1#*=}"; shift ;;
    --host) shift; if (($#)) && [[ "$1" != --* ]]; then export SERVER_ADDRESS="$1"; shift; fi ;;
    --host=*) export SERVER_ADDRESS="${1#*=}"; shift ;;
    --strictPort) shift ;;
    *) echo "Unsupported preview argument: $1" >&2; exit 1 ;;
  esac
done
if [[ ! -f target/classpath.txt || ! -d target/test-classes ]]; then
  echo 'Build the local demo first: bash scripts/demo.sh' >&2
  exit 1
fi
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$java_bin" -cp "target/test-classes:target/classes:$(cat target/classpath.txt)" dev.mustafa.issues.LocalDemo
