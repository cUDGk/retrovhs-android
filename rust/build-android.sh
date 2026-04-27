#!/usr/bin/env bash
# Builds the ntscrs-jni Rust crate for all four Android architectures
# and copies the resulting .so files into app/src/main/jniLibs/.
#
# Requirements:
#   - Rust toolchain (rustup) with android targets
#   - cargo-ndk (cargo install cargo-ndk)
#   - Android NDK (set via ANDROID_NDK_HOME, or auto-detected from $ANDROID_HOME/ndk/)

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
        # Pick highest-numbered NDK directory.
        ndk_pick=$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -n1)
        export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$ndk_pick"
        echo "Using NDK: $ANDROID_NDK_HOME"
    else
        echo "ANDROID_NDK_HOME not set and ANDROID_HOME/ndk/ not found." >&2
        exit 1
    fi
fi

cd "$SCRIPT_DIR/ntscrs-jni"

cargo ndk \
    -t arm64-v8a \
    -t armeabi-v7a \
    -t x86_64 \
    -t x86 \
    -o "$PROJECT_ROOT/app/src/main/jniLibs" \
    build --release

echo "Built jniLibs at: $PROJECT_ROOT/app/src/main/jniLibs/"
