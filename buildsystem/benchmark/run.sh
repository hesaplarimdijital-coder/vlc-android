#!/bin/sh
# S-1404: Nağmedar's macrobenchmarks on the connected device, checked against thresholds.json.
#
#   buildsystem/benchmark/run.sh [-s SERIAL] [--apks DIR] [--seconds N]
#                                [--only StartupBenchmark|ScrollBenchmark|PlaybackTest]
#
# DIR holds the locally built benchmark APKs: the app's benchmark APK
# (app.nagmedar.benchmark, beside the installed app) and the tests' APK. Without it, Gradle's
# outputs of :application:app:assembleBenchmark and :application:benchmark:assembleBenchmark.
# N: how long PlaybackTest plays a track of the Music before it checks that it still plays (10 s);
# the Music's scroll and playback tests are skipped while the benchmark build's library has no music.
#
# The device: unlocked, its screen on, its battery above 25 %. The results land in
# benchmark-results/DATE: the macrobenchmarks' JSON and traces, the app's NagmedarPerf lines,
# the run's output and check.py's table (summary.md).
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
TARGET=app.nagmedar.benchmark
TESTS=app.nagmedar.macrobenchmark

APKS=""
SECONDS_TO_PLAY=10
ONLY=""
while [ $# -gt 0 ]; do
    case "$1" in
        -s) export ANDROID_SERIAL="$2"; shift 2 ;;
        --apks) APKS="$2"; shift 2 ;;
        --seconds) SECONDS_TO_PLAY="$2"; shift 2 ;;
        --only) ONLY="$2"; shift 2 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
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
    echo "APK'lar bulunamadı: --apks ile yerelde derlenen benchmark APK'larının klasörünü verin ya da" >&2
    echo "./gradlew :application:app:assembleBenchmark :application:benchmark:assembleBenchmark derleyin." >&2
    exit 2
fi
echo "Uygulama: $APP_APK"
echo "Testler:  $TEST_APK"
adb install -r -g "$APP_APK" >/dev/null
adb install -r -g "$TEST_APK" >/dev/null
# the screen on and unlocked (a PIN or pattern still has to be entered by hand)
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true

OUT="$ROOT/benchmark-results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
adb logcat -c
set -- -w -r -e nagmedar.seconds "$SECONDS_TO_PLAY"
# the Baseline Profile's generator is not a measure (baseline-profile.sh runs it)
if [ -n "$ONLY" ]; then set -- "$@" -e class "$TESTS.$ONLY"; else set -- "$@" -e notClass "$TESTS.BaselineProfileGenerator"; fi
echo "Ölçülüyor; birkaç dakika sürer, cihaza dokunmayın…"
adb shell am instrument "$@" "$TESTS/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' > "$OUT/instrument.txt" || true
adb logcat -d -s NagmedarPerf:I NagmedarBench:I | tr -d '\r' > "$OUT/perf.log" || true
adb pull "/sdcard/Android/media/$TESTS" "$OUT/device" >/dev/null 2>&1 || true

echo
python3 "$HERE/check.py" "$OUT"
