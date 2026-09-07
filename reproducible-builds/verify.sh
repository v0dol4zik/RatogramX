#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <reference.apk> [rebuilt.apk]" >&2
  echo "Set REPRO_BUILD=1 to build the second APK in Docker." >&2
  exit 2
fi
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
reference="$(realpath "$1")"
rebuilt="${2:-}"
if [[ -z "$rebuilt" && "${REPRO_BUILD:-0}" == "1" ]]; then
  docker build --pull -f "$root_dir/reproducible-builds/Dockerfile" -t ratogramx-repro "$root_dir"
  docker run --rm \
    -e TELEGRAM_API_ID="${TELEGRAM_API_ID:-12345}" \
    -e TELEGRAM_API_HASH="${TELEGRAM_API_HASH:-00000000000000000000000000000000}" \
    -v "$root_dir:/src" ratogramx-repro
  rebuilt="$(find "$root_dir/app/build/outputs/apk" -path '*/release/*.apk' -type f | head -n 1)"
fi
if [[ -z "$rebuilt" ]]; then
  echo "Pass a rebuilt APK or set REPRO_BUILD=1." >&2
  exit 2
fi
rebuilt="$(realpath "$rebuilt")"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
unzip -q "$reference" -d "$tmp_dir/reference"
unzip -q "$rebuilt" -d "$tmp_dir/rebuilt"
find "$tmp_dir" -type f -path '*/META-INF/*' -delete
(cd "$tmp_dir/reference" && find . -type f -print0 | sort -z | xargs -0 sha256sum) > "$tmp_dir/reference.sha256"
(cd "$tmp_dir/rebuilt" && find . -type f -print0 | sort -z | xargs -0 sha256sum) > "$tmp_dir/rebuilt.sha256"
diff -u "$tmp_dir/reference.sha256" "$tmp_dir/rebuilt.sha256"
echo "Reproducible APK payload verified (META-INF signature entries excluded)."
