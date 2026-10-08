#!/usr/bin/env bash
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"

echo "==> Publishing ktcad to mavenLocal"
(cd "$root/kt" && ./gradlew --console=plain :ktcad:publishToMavenLocal)

echo "==> Building example assets"
"$here/examples/generator/generate.sh" "$here/examples" "$here/assets/examples" "$@"

echo "==> Building site"
(cd "$here" && hugo --gc)
