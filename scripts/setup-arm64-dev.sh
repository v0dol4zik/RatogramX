#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Upstream 1807 builds out of tree; dependencies must be initialized first.
exec ./gradlew :app:patchOpus :app:patchJetpackMediaLatest \
  :app:buildLibvpxLatestArm64 :app:buildFfmpegLatestArm64 \
  -PratogramxDevPackage=true --max-workers=2 "$@"
