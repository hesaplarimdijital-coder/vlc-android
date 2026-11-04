#!/bin/sh
: "${SCRATCH:?set SCRATCH to your scratchpad}" "${ORCH:=$SCRATCH/orch}" "${REPO:?set REPO to the vlc-android clone}" "${WT:=$HOME/wt}"
# decrypt.sh <dir> <out.apk>
# <dir> holds what CI's encrypt_apk.sh wrote: envelope.bin (RSA-OAEP-SHA256 of "<password>\n<sha256>\n"),
# apk.enc.part* (AES-256-CBC, PBKDF2 100000 iterations, salted, split in 90 MB parts).
set -e
DIR=$1; OUT=$2
KEY=$(dirname "$0")/delivery.key
ENV=$(openssl pkeyutl -decrypt -inkey "$KEY" -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -in "$DIR/envelope.bin")
PASS=$(printf '%s\n' "$ENV" | sed -n 1p)
SUM=$(printf '%s\n' "$ENV" | sed -n 2p)
cat "$DIR"/apk.enc.part* | openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -pass "pass:$PASS" -out "$OUT"
GOT=$(sha256sum "$OUT" | cut -d' ' -f1)
[ "$GOT" = "$SUM" ] || { echo "checksum mismatch: $GOT != $SUM" >&2; rm -f "$OUT"; exit 1; }
echo "ok $OUT $GOT"
