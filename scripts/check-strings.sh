#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
extract() { sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort -u; }
diff -u <(extract app/src/main/res/values/moex_strings.xml) <(extract app/src/main/res/values-ru/moex_strings.xml)
