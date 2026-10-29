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
# Nağmedar 1.0: compileSdk 37 (Android 17); the SDK names its platform android-37.0 (minor level),
# older listings android-37
"$SDKMANAGER" --install "platforms;android-37.0" > /dev/null 2>&1 || "$SDKMANAGER" --install "platforms;android-37" > /dev/null
ls -d "$SDK"/platforms/android-37* >/dev/null 2>&1 || { echo "Android SDK platform 37 could not be installed" >&2; exit 1; }
if [ -n "$GITHUB_ENV" ]; then
    echo "ANDROID_SDK=$SDK" >> "$GITHUB_ENV"
    echo "ANDROID_NDK=$SDK/ndk/$NDK_VERSION" >> "$GITHUB_ENV"
fi
export ANDROID_SDK="$SDK" ANDROID_NDK="$SDK/ndk/$NDK_VERSION"
echo "Android SDK: $ANDROID_SDK"
echo "Android NDK: $ANDROID_NDK"
