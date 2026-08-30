#!/bin/bash
set -e

TARGET_FLAVORS="${FLAVORS:-latest}"
TARGET_ABIS="${ABIS:-arm64-v8a}"

# shellcheck source=set-env.sh
source "$(pwd)/scripts/set-env.sh"
export FLAVORS="$TARGET_FLAVORS"
export ABIS="$TARGET_ABIS"

patch-opus-impl.sh
patch-androidx-media-impl.sh
build-vpx-impl.sh
build-ffmpeg-impl.sh

echo "ARM64 development dependencies are ready for: ${FLAVORS}"
