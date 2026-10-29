#!/bin/sh
# Nağmedar 1.0: opens what CI's personal_release job delivered (encrypt_apk.sh's output).
#
#   buildsystem/release/decrypt_apk.sh <dir> <delivery.key> <out.apk>
#
# <dir> holds envelope.bin (RSA-OAEP-SHA256 of "<password>\n<sha256>\n") and apk.enc.part*
# (AES-256-CBC, PBKDF2 100000 iterations, salted, in 90 MB parts): a checkout of the
# ci/personal-release branch. <delivery.key> is the private half of delivery.pub; it stays on
# the owner's computer and never enters the repository. The APK's SHA-256 is checked.
set -eu
DIR=$1
KEY=$2
OUT=$3
ENVELOPE=$(openssl pkeyutl -decrypt -inkey "$KEY" -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -in "$DIR/envelope.bin")
PASS=$(printf '%s\n' "$ENVELOPE" | sed -n 1p)
SUM=$(printf '%s\n' "$ENVELOPE" | sed -n 2p)
cat "$DIR"/apk.enc.part* | openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -pass "pass:$PASS" -out "$OUT"
if command -v sha256sum >/dev/null; then GOT=$(sha256sum "$OUT" | cut -d' ' -f1); else GOT=$(shasum -a 256 "$OUT" | cut -d' ' -f1); fi
if [ "$GOT" != "$SUM" ]; then
    echo "SHA-256 tutmuyor: $GOT, beklenen $SUM" >&2
    rm -f "$OUT"
    exit 1
fi
echo "Tamam: $OUT ($GOT)"
