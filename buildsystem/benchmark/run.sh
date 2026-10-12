#!/bin/sh
# S-1404: Nağmedar's macrobenchmarks on the connected device, checked against thresholds.json.
#
#   buildsystem/benchmark/run.sh [-s SERIAL] [--apks DIR] [--video PATH]... [--seconds N]
#                                [--only StartupBenchmark|ScrollBenchmark|PlaybackTest]
#
# DIR is CI's "nagmedar-benchmark" artifact, unzipped: the app's benchmark APK
# (app.nagmedar.benchmark, beside the installed app) and the tests' APK. Without it, Gradle's
# outputs of :application:app:assembleBenchmark and :application:benchmark:assembleBenchmark.
# PATH is a film on the device to play (as many as wanted, without spaces in the path); without
# one, the test set's 4K HDR10 samples are looked for on the device (S-1401's artifact,
# unzipped there). N: how long a film longer than that plays (60 s).
#
# The device: unlocked, its screen on, its battery above 25 %. The results land in
# benchmark-results/DATE: the macrobenchmarks' JSON and traces, the app's NagmedarPerf lines,
# the run's output and check.py's table (summary.md).
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
TARGET=app.nagmedar.benchmark
TESTS=app.nagmedar.macrobenchmark
SAMPLES="hdr10_2160p23976_hevc10_eac3.mkv stress_2160p23976_hevc10_100mbps.mkv"

APKS=""
VIDEOS=""
SECONDS_TO_PLAY=60
ONLY=""
while [ $# -gt 0 ]; do
    case "$1" in
        -s) export ANDROID_SERIAL="$2"; shift 2 ;;
        --apks) APKS="$2"; shift 2 ;;
        --video) VIDEOS="${VIDEOS:+$VIDEOS,}$2"; shift 2 ;;
        --seconds) SECONDS_TO_PLAY="$2"; shift 2 ;;
        --only) ONLY="$2"; shift 2 ;;
        -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
        *) echo "Bilinmeyen seçenek: $1" >&2; exit 2 ;;
    esac
done

command -v adb >/dev/null || { echo "adb bulunamadı (Android SDK platform-tools)." >&2; exit 2; }
adb get-state >/dev/null 2>&1 || { echo "Bağlı cihaz yok: USB hata ayıklamayı açıp cihazı bağlayın." >&2; exit 2; }

# the APKs: the artifact's, or the build's
SEARCH=${APKS:-$ROOT/application}
APP_APK=$(find "$SEARCH" -name 'Nagmedar-*-benchmark-*.apk' 2>/dev/null | head -n 1)
TEST_APK=$(find "$SEARCH" -name 'benchmark-benchmark.apk' 2>/dev/null | head -n 1)
if [ -z "$APP_APK" ] || [ -z "$TEST_APK" ]; then
    echo "APK'lar bulunamadı: --apks ile CI'ın nagmedar-benchmark artefaktını verin ya da" >&2
    echo "./gradlew :application:app:assembleBenchmark :application:benchmark:assembleBenchmark derleyin." >&2
    exit 2
fi
echo "Uygulama: $APP_APK"
echo "Testler:  $TEST_APK"
adb install -r -g "$APP_APK" >/dev/null
adb install -r -g "$TEST_APK" >/dev/null
# all the files, as the app asks for them on Android 11 and later: the films play from their path
adb shell appops set "$TARGET" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
# the screen on and unlocked (a PIN or pattern still has to be entered by hand)
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true

# the films: given, or the test set's samples found on the device
if [ -z "$VIDEOS" ]; then
    for name in $SAMPLES; do
        found=$(adb shell "find /sdcard/ -name $name 2>/dev/null" | tr -d '\r' | head -n 1)
        [ -n "$found" ] && VIDEOS="${VIDEOS:+$VIDEOS,}$found"
    done
    [ -n "$VIDEOS" ] || echo "Test medya seti cihazda yok: oynatma testi atlanacak (README'ye bakın)."
fi
[ -n "$VIDEOS" ] && echo "Oynatılacak: $VIDEOS"

OUT="$ROOT/benchmark-results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
adb logcat -c
set -- -w -r -e nagmedar.seconds "$SECONDS_TO_PLAY"
[ -n "$VIDEOS" ] && set -- "$@" -e nagmedar.videos "$VIDEOS"
[ -n "$ONLY" ] && set -- "$@" -e class "$TESTS.$ONLY"
echo "Ölçülüyor; birkaç dakika sürer, cihaza dokunmayın…"
adb shell am instrument "$@" "$TESTS/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' > "$OUT/instrument.txt" || true
adb logcat -d -s NagmedarPerf:I NagmedarBench:I | tr -d '\r' > "$OUT/perf.log" || true
adb pull "/sdcard/Android/media/$TESTS" "$OUT/device" >/dev/null 2>&1 || true

echo
python3 "$HERE/check.py" "$OUT"
