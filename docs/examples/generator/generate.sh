#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <example.kt> <out-dir>" >&2
    exit 2
fi

here="$(cd "$(dirname "$0")" && pwd)"
input="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
mkdir -p "$2"
out="$(cd "$2" && pwd)"

exec "$here/gradlew" -p "$here" --console=plain exportAssets -Pinput="$input" -Pout="$out"
