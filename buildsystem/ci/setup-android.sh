#!/bin/sh
# Prepares the hosted runner's Android SDK for compile.sh: platform, build tools
# and the NDK version libVLC 4 expects. Exports ANDROID_SDK / ANDROID_NDK.
set -e
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
[ -n "$SDK" ] || { echo "ANDROID_HOME is not set" >&2; exit 1; }
NDK_VERSION=28.2.13676358
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --install "platforms;android-36" "build-tools;36.0.0" "ndk;$NDK_VERSION" > /dev/null
if [ -n "$GITHUB_ENV" ]; then
    echo "ANDROID_SDK=$SDK" >> "$GITHUB_ENV"
    echo "ANDROID_NDK=$SDK/ndk/$NDK_VERSION" >> "$GITHUB_ENV"
fi
export ANDROID_SDK="$SDK" ANDROID_NDK="$SDK/ndk/$NDK_VERSION"
echo "Android SDK: $ANDROID_SDK"
echo "Android NDK: $ANDROID_NDK"
