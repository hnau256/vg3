#!/usr/bin/env bash
set -euo pipefail

size=512
compression=6
positional=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --size)
            size="$2"
            shift 2
            ;;
        --compression)
            compression="$2"
            shift 2
            ;;
        -h|--help)
            echo "usage: $0 <examples-dir> <out-dir> [--size <px>] [--compression <0-9>]" >&2
            exit 0
            ;;
        *)
            positional+=("$1")
            shift
            ;;
    esac
done

if [[ ${#positional[@]} -ne 2 ]]; then
    echo "usage: $0 <examples-dir> <out-dir> [--size <px>] [--compression <0-9>]" >&2
    exit 2
fi

here="$(cd "$(dirname "$0")" && pwd)"
examples="$(cd "${positional[0]}" && pwd)"
mkdir -p "${positional[1]}"
out="$(cd "${positional[1]}" && pwd)"

exec "$here/gradlew" -p "$here" --console=plain exportAssets \
    -Pexamples="$examples" \
    -Pout="$out" \
    -Psize="$size" \
    -Pcompression="$compression"
