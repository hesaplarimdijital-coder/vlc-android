#!/bin/sh
# Nağmedar 1.0: the personal release's delivery (CI's personal_release job).
#
#   buildsystem/release/deliver_personal.sh <apk-dir> <out-dir> <source>
#
# Checks the arm64 personal APK under <apk-dir> (assemblePersonal) before anything leaves the
# runner: app.nagmedar.debug, not debuggable, signed with the repository's shared debug key (so it
# installs over the debug build), with the Baseline Profile and the music engine's library in it.
# Then encrypts it with delivery.pub (encrypt_apk.sh) into <out-dir>, with a README.md naming the
# version, the versionCode, <source> (the branch and commit built) and the APK's SHA-256. Only
# <out-dir> is published (the ci/personal-release branch); the APK itself never is.
# Needs ANDROID_HOME's build-tools (aapt2, apksigner), keytool, openssl and unzip.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
DIR=$1
OUT=$2
SOURCE=$3

fail() { echo "::error title=Kişisel sürüm::$*"; exit 1; }

APK=$(find "$DIR" -name 'Nagmedar-*-personal-arm64-v8a.apk' | head -n 1)
[ -n "$APK" ] || fail "arm64 personal APK not found under $DIR"
BT=$(ls -d "${ANDROID_HOME:?}"/build-tools/* | sort -V | tail -n 1)

BADGING=$("$BT/aapt2" dump badging "$APK")
PKG=$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
CODE=$(printf '%s\n' "$BADGING" | sed -n "s/^package: .*versionCode='\([0-9]*\)'.*/\1/p")
NAME=$(printf '%s\n' "$BADGING" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")
TARGET_SDK=$(printf '%s\n' "$BADGING" | sed -n "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p")
[ "$PKG" = app.nagmedar.debug ] || fail "package is '$PKG', not app.nagmedar.debug: it would not update the installed app"
if printf '%s\n' "$BADGING" | grep -q '^application-debuggable'; then fail "the APK is debuggable"; fi

WANT=$(keytool -list -v -keystore "$ROOT/buildsystem/ci/nagmedar-debug.keystore" -storepass android -alias androiddebugkey \
    | sed -n 's/.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')
GOT=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1)
[ -n "$WANT" ] && [ "$WANT" = "$GOT" ] || fail "signed with $GOT, not the shared debug key $WANT"

FILES=$(unzip -Z1 "$APK")
printf '%s\n' "$FILES" | grep -qx 'assets/dexopt/baseline.prof' || fail "no Baseline Profile (assets/dexopt/baseline.prof)"
printf '%s\n' "$FILES" | grep -qx 'lib/arm64-v8a/libnagmedar_audio.so' || fail "no music engine (lib/arm64-v8a/libnagmedar_audio.so)"
if printf '%s\n' "$FILES" | grep -q '^lib/' && printf '%s\n' "$FILES" | grep '^lib/' | grep -vq '^lib/arm64-v8a/'; then
    fail "native libraries of another ABI than arm64-v8a"
fi

SUM=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")
rm -rf "$OUT"
mkdir -p "$OUT"
sh "$HERE/encrypt_apk.sh" "$APK" "$HERE/delivery.pub" "$OUT"

MB=$(awk "BEGIN { printf \"%.1f\", $SIZE / 1048576 }" | tr . ,)
cat > "$OUT/README.md" <<EOF
# Nağmedar $NAME: kişisel sürüm (şifreli)

| | |
| --- | --- |
| Sürüm | $NAME |
| versionCode | $CODE |
| Kaynak | $SOURCE |
| APK SHA-256 | \`$SUM\` |
| APK boyutu | $MB MB ($SIZE bayt) |
| Uygulama kimliği | \`$PKG\`: yüklü debug derlemesinin üzerine güncelleme olarak kurulur |
| İmza | depodaki ortak debug anahtarı (SHA-256 \`$GOT\`) |
| ABI | arm64-v8a (Galaxy S24, Galaxy Tab S11 Ultra) |
| targetSdk | $TARGET_SDK |

Bu dalda yalnızca şifreli dosyalar durur: \`envelope.bin\` (APK'nın parolası ve SHA-256 özeti,
teslim anahtarının açık yarısıyla RSA-OAEP-SHA256) ve \`apk.enc.part*\` (APK, AES-256-CBC,
PBKDF2 100000 yineleme, 90 MB'lık parçalar). Açmak için \`delivery.key\` gerekir; adımlar
depodaki [buildsystem/release/README.md](https://github.com/hesaplarimdijital-coder/vlc-android/blob/master/buildsystem/release/README.md) dosyasında.
EOF

echo "Nağmedar $NAME ($CODE), $PKG, $SIZE bytes, sha256 $SUM"
ls -l "$OUT"
