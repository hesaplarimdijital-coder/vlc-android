#!/bin/sh
# Nağmedar 1.0: the app's Baseline Profile and startup profile, collected on the connected device or
# emulator by application/benchmark's BaselineProfileGenerator against the app's benchmark build.
#
#   buildsystem/benchmark/baseline-profile.sh [-s SERIAL] [--apks DIR] [--music DIR] [--feed URL] [--out DIR]
#
# DIR holds the benchmark APKs (the app's Nagmedar-*-benchmark-*.apk and benchmark-benchmark.apk);
# without it, Gradle's outputs of :application:app:assembleBenchmark and
# :application:benchmark:assembleBenchmark. --music: audio files pushed to the device's Music
# folder first, so that the Music has tracks to scroll and play. --feed: a podcast feed the app
# subscribes to when it follows none, for the episode page. --out (benchmark-results/profile-DATE):
# the device's raw files in device/, the instrumentation's output, and the merged profiles at
# application/app/src/main/baselineProfiles/{baseline,startup}-prof.txt inside it, as the
# repository lays them out. The CI emulator workflow runs it (input baseline_profile); README.md
# says how the result reaches the app. Exits 1 when the generator failed or wrote no profile.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
TARGET=app.nagmedar.benchmark
TESTS=app.nagmedar.macrobenchmark

APKS=""
MUSIC=""
FEED=""
OUT=""
while [ $# -gt 0 ]; do
    case "$1" in
        -s) export ANDROID_SERIAL="$2"; shift 2 ;;
        --apks) APKS="$2"; shift 2 ;;
        --music) MUSIC="$2"; shift 2 ;;
        --feed) FEED="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
        *) echo "Bilinmeyen seçenek: $1" >&2; exit 2 ;;
    esac
done

command -v adb >/dev/null || { echo "adb bulunamadı (Android SDK platform-tools)." >&2; exit 2; }
adb get-state >/dev/null 2>&1 || { echo "Bağlı cihaz yok." >&2; exit 2; }

SEARCH=${APKS:-$ROOT/application}
APP_APK=$(find "$SEARCH" -name 'Nagmedar-*-benchmark-*.apk' 2>/dev/null | head -n 1)
TEST_APK=$(find "$SEARCH" -name 'benchmark-benchmark.apk' 2>/dev/null | head -n 1)
if [ -z "$APP_APK" ] || [ -z "$TEST_APK" ]; then
    echo "APK'lar bulunamadı: ./gradlew :application:app:assembleBenchmark :application:benchmark:assembleBenchmark" >&2
    exit 2
fi
OUT=${OUT:-$ROOT/benchmark-results/profile-$(date +%Y%m%d-%H%M%S)}
mkdir -p "$OUT/device"
echo "Uygulama: $APP_APK"
echo "Testler:  $TEST_APK"

adb install -r -g "$APP_APK" >/dev/null
adb install -r -g "$TEST_APK" >/dev/null
for perm in android.permission.READ_MEDIA_AUDIO android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$TARGET" "$perm" 2>/dev/null || true
done
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true
adb shell settings put global stay_on_while_plugged_in 7 2>/dev/null || true

if [ -n "$MUSIC" ] && [ -d "$MUSIC" ]; then
    adb shell mkdir -p /sdcard/Music/nagmedar-profile
    for f in "$MUSIC"/*; do
        [ -f "$f" ] || continue
        adb push "$f" "/sdcard/Music/nagmedar-profile/$(basename "$f")" >/dev/null
    done
    echo "Müzik: $(ls "$MUSIC" | wc -l) dosya"
fi

adb logcat -G 32M 2>/dev/null || true
adb logcat -c 2>/dev/null || true
# earlier runs' files would be merged into this one's
adb shell rm -rf "/sdcard/Android/media/$TESTS" 2>/dev/null || true

set -- -w -r -e class "$TESTS.BaselineProfileGenerator#diagSubscribeLoop" -e androidx.benchmark.suppressErrors EMULATOR,LOW-BATTERY,UNLOCKED
[ -n "$FEED" ] && set -- "$@" -e nagmedar.feed "$FEED"
echo "Profil toplanıyor; on-yirmi dakika sürer, cihaza dokunmayın…"
adb shell am instrument "$@" "$TESTS/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' > "$OUT/instrument.txt" || true
adb pull "/sdcard/Android/media/$TESTS" "$OUT/device" >/dev/null 2>&1 || true
adb shell wm size reset 2>/dev/null || true
adb shell wm density reset 2>/dev/null || true

adb logcat -d -s NagmedarProfile:V | tr -d '\r' > "$OUT/steps.txt" || true
adb logcat -d -v time -b main,system,crash | grep -E 'NagDiag|NagmedarProfile|AndroidRuntime|ActivityTaskManager|ActivityManager.*nagmedar|FATAL|lowmemorykiller|nagmedar.*(Kill|died)' | tr -d '\r' > "$OUT/diag.txt" || true
adb logcat -d -v time -b events | grep -E 'nagmedar' | tr -d '\r' > "$OUT/events.txt" || true

# the three tests' profiles in one, each rule once, the cold start's first (its test writes only a
# startup profile, whose rules belong to the Baseline Profile too); the startup profile from the
# cold start's test alone
DEST="$OUT/application/app/src/main/baselineProfiles"
mkdir -p "$DEST"
# (awk, not cat: a profile file may end without a newline, and cat would join two rules)
{ find "$OUT/device" -name '*-startup-prof*.txt' | sort; find "$OUT/device" -name '*-baseline-prof*.txt' | sort; } \
    | tr '\n' '\0' | xargs -0 -r awk 'NF && !seen[$0]++' > "$DEST/baseline-prof.txt"
find "$OUT/device" -name '*-startup-prof*.txt' | sort | tr '\n' '\0' | xargs -0 -r awk 'NF && !seen[$0]++' > "$DEST/startup-prof.txt"
[ -s "$DEST/startup-prof.txt" ] || rm -f "$DEST/startup-prof.txt"

RULES=$(wc -l < "$DEST/baseline-prof.txt" | tr -d ' ')
APP_RULES=$(grep -c 'app/nagmedar/\|org/videolan/' "$DEST/baseline-prof.txt" || true)
STARTUP=$( [ -f "$DEST/startup-prof.txt" ] && wc -l < "$DEST/startup-prof.txt" | tr -d ' ' || echo 0)
{
    echo "# Baseline Profile"
    echo
    echo "- APK: \`$(basename "$APP_APK")\`"
    echo "- rules: $RULES ($APP_RULES of Nağmedar's and VLC's own code), startup profile: $STARTUP"
    echo "- tests: $(grep -E '^(OK|FAILURES|Tests run)' "$OUT/instrument.txt" | tr '\n' ' ')"
    echo "- screens missing an awaited element: $(find "$OUT/device" -name 'missing-*.xml' -exec basename {} .xml \; | sort | tr '\n' ' ')"
    echo
    echo "Steps reached (logcat NagmedarProfile):"
    echo
    echo '```'
    grep -o 'NagmedarProfile: .*' "$OUT/steps.txt" | sort | uniq -c || true
    echo '```'
    echo
    echo '```'
    grep -E 'INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE: -' "$OUT/instrument.txt" | head -n 60 || true
    echo '```'
} > "$OUT/report.md"
cat "$OUT/report.md"

if ! grep -q '^OK (' "$OUT/instrument.txt"; then
    echo "Profil üreticisi başarısız (instrument.txt)." >&2
    exit 1
fi
if [ "$APP_RULES" -lt 100 ]; then
    echo "Profil neredeyse boş: $RULES kural." >&2
    exit 1
fi
echo "Profil: $DEST"
