#!/bin/sh
# encrypt_apk.sh <apk> <pubkey> <outdir>: what CI runs to deliver the personal release APK privately.
# envelope.bin = RSA-OAEP-SHA256("<random password>\n<sha256 of the APK>\n") with the delivery public key;
# apk.enc.part* = AES-256-CBC (PBKDF2, 100000 iterations, salted) of the APK, in 90 MB parts (GitHub's 100 MB file limit).
set -e
APK=$1; PUB=$2; OUT=$3
mkdir -p "$OUT"
PASS=$(openssl rand -hex 32)
SUM=$(sha256sum "$APK" | cut -d' ' -f1)
printf '%s\n%s\n' "$PASS" "$SUM" | openssl pkeyutl -encrypt -pubin -inkey "$PUB" -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -out "$OUT/envelope.bin"
openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -pass "pass:$PASS" -in "$APK" | split -b 90m -d - "$OUT/apk.enc.part"
