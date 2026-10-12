#!/usr/bin/env bash
# Host tests of the music engine (S-901, S-1405): needs `build.sh host <prefix>` first and
# ffmpeg on PATH. The engine's render output must be, byte for byte, what ffmpeg decodes:
#   - FLAC 16-bit/44.1 kHz and 24-bit/96 kHz, whole and from a sample-accurate seek
#   - a seek while playing: what follows it is the file from that time
#   - lossy codecs (MP3, AAC, Opus) in float, with their encoder delay and padding trimmed
#   - a two-file queue, gapless: the two files' decodes end to end
#   - a mono file on both channels, a 5.1 file mixed down within one LSB of ffmpeg's
#   - the output formats: s16, s24, s32 and f32 of a 16-bit and a 24-bit file
#   - the test set's gapless album (S-904): the three cuts equal the whole glissando
set -euo pipefail
trap 'echo "test-audio.sh: line $LINENO failed: $BASH_COMMAND"' ERR
HERE=$(cd "$(dirname "$0")" && pwd)
PREFIX=$(realpath -m "${1:?prefix}")
NAGAUDIO="$PREFIX/bin/nagaudio"
if [ -n "${NAGMEDAR_TEST_DIR:-}" ]; then
    T=$NAGMEDAR_TEST_DIR/audio
    rm -rf "$T" && mkdir -p "$T"
else
    T=$(mktemp -d)
    trap 'rm -rf "$T"' EXIT
fi
cd "$T"

failures=0
check() { # name expected actual
    if [ "$2" = "$3" ]; then
        echo "ok   $1"
    else
        echo "FAIL $1: expected $2, got $3"
        failures=$((failures + 1))
    fi
}
md5_of() { md5sum | cut -c1-32; }
# the md5 the engine prints for the files given, in a raw format
engine_md5() { # format args...
    local format=$1
    shift
    "$NAGAUDIO" decode "$@" -f "$format" --md5 | python3 -c 'import json,sys; print(json.load(sys.stdin)["md5"])'
}

echo "== S-901: test signals"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=4,aformat=channel_layouts=stereo" -f lavfi -i "anoisesrc=r=44100:c=pink:a=0.3:d=4:s=7" \
    -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0,aformat=sample_fmts=s16" -c:a flac flac16.flac
ffmpeg -v error -y -f lavfi -i "sine=f=1000:r=96000:d=3,aformat=channel_layouts=stereo" -f lavfi -i "anoisesrc=r=96000:c=white:a=0.2:d=3:s=11" \
    -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0,aformat=sample_fmts=s32" -c:a flac -sample_fmt s32 flac24.flac
ffmpeg -v error -y -i flac16.flac -c:a libmp3lame -b:a 192k mp3.mp3
ffmpeg -v error -y -i flac16.flac -c:a aac -b:a 160k aac.m4a
ffmpeg -v error -y -i flac16.flac -c:a libopus -b:a 128k opus.opus
ffmpeg -v error -y -f lavfi -i "sine=f=300:r=48000:d=2" -c:a flac mono.flac
ffmpeg -v error -y -f lavfi -i "anoisesrc=r=48000:c=white:a=0.2:d=2:s=3,aformat=channel_layouts=5.1,aformat=sample_fmts=s16" -c:a flac six.flac

info=$("$NAGAUDIO" info flac24.flac)
check "info 24/96" '{"codec":"flac","container":"flac","rate":96000,"channels":2,"bits":24,"float":false,"duration_ms":3000' "$(echo "$info" | cut -d, -f1-7)"

echo "== S-901: bit-exact decoding"
check "flac 16/44.1 s16" "$(ffmpeg -v error -i flac16.flac -f s16le - | md5_of)" "$(engine_md5 s16 flac16.flac)"
check "flac 24/96 s32" "$(ffmpeg -v error -i flac24.flac -f s32le - | md5_of)" "$(engine_md5 s32 flac24.flac)"
check "flac 24/96 s24" "$(ffmpeg -v error -i flac24.flac -f s24le - | md5_of)" "$(engine_md5 s24 flac24.flac)"
check "flac 24/96 f32" "$(ffmpeg -v error -i flac24.flac -f f32le - | md5_of)" "$(engine_md5 f32 flac24.flac)"
check "flac 16 as s32" "$(ffmpeg -v error -i flac16.flac -f s32le - | md5_of)" "$(engine_md5 s32 flac16.flac)"
check "seek at open" "$(ffmpeg -v error -ss 1.25 -i flac24.flac -f s32le - | md5_of)" "$(engine_md5 s32 flac24.flac --seek 1250)"
"$NAGAUDIO" decode flac24.flac -f s32 --seek 2000 --seek-after 12345 -o seek.raw > /dev/null
check "seek while playing" "$(ffmpeg -v error -ss 2.0 -i flac24.flac -f s32le - | md5_of)" "$(tail -c $((96000 * 8)) seek.raw | md5_of)"
check "mp3 f32 (gapless trim)" "$(ffmpeg -v error -i mp3.mp3 -f f32le - | md5_of)" "$(engine_md5 f32 mp3.mp3)"
check "aac f32" "$(ffmpeg -v error -i aac.m4a -f f32le - | md5_of)" "$(engine_md5 f32 aac.m4a)"
check "opus f32" "$(ffmpeg -v error -i opus.opus -f f32le - | md5_of)" "$(engine_md5 f32 opus.opus)"
check "queue flac+mp3 s16" "$( (ffmpeg -v error -i flac16.flac -f s16le -; ffmpeg -v error -i mp3.mp3 -f s16le -) | md5_of)" "$(engine_md5 s16 flac16.flac mp3.mp3 --no-dither)"
check "mono on both channels" "$(ffmpeg -v error -i mono.flac -af 'pan=stereo|c0=c0|c1=c0' -f s16le - | md5_of)" "$(engine_md5 s16 mono.flac)"

echo "== S-901: 5.1 downmix within one LSB"
ffmpeg -v error -y -i six.flac -ac 2 -af 'aresample=internal_sample_fmt=dblp' -f s16le ref_mix.raw
"$NAGAUDIO" decode six.flac -f s16 --no-dither -o our_mix.raw > /dev/null
python3 - <<'PY'
import sys
a = open("ref_mix.raw", "rb").read()
b = open("our_mix.raw", "rb").read()
assert len(a) == len(b) and len(a) > 0, (len(a), len(b))
worst = 0
for i in range(0, len(a), 2):
    x = int.from_bytes(a[i:i + 2], "little", signed=True)
    y = int.from_bytes(b[i:i + 2], "little", signed=True)
    worst = max(worst, abs(x - y))
print("ok   5.1 downmix, worst difference %d LSB" % worst if worst <= 1 else "FAIL 5.1 downmix differs by %d LSB" % worst)
sys.exit(0 if worst <= 1 else 1)
PY

echo "== S-904: gapless album"
# one glissando, cut into three FLAC parts at sample boundaries, must come back whole from the queue
ffmpeg -v error -y -f lavfi -i "sine=f=200:r=44100:d=6,aformat=channel_layouts=stereo" -af "vibrato=f=0.3:d=0.9,aformat=sample_fmts=s16" -c:a flac whole.flac
ffmpeg -v error -y -i whole.flac -af "atrim=start_sample=0:end_sample=88200" -c:a flac part1.flac
ffmpeg -v error -y -i whole.flac -af "atrim=start_sample=88200:end_sample=180000" -c:a flac part2.flac
ffmpeg -v error -y -i whole.flac -af "atrim=start_sample=180000" -c:a flac part3.flac
check "gapless flac parts" "$(ffmpeg -v error -i whole.flac -f s16le - | md5_of)" "$(engine_md5 s16 part1.flac part2.flac part3.flac)"
# the same album in a lossy codec: the encoder's delay and padding trimmed at every join
for part in 1 2 3; do ffmpeg -v error -y -i part$part.flac -c:a libmp3lame -b:a 320k part$part.mp3; done
check "gapless mp3 parts" "$( (ffmpeg -v error -i part1.mp3 -f f32le -; ffmpeg -v error -i part2.mp3 -f f32le -; ffmpeg -v error -i part3.mp3 -f f32le -) | md5_of)" "$(engine_md5 f32 part1.mp3 part2.mp3 part3.mp3)"

if [ "$failures" -gt 0 ]; then
    echo "$failures audio test(s) failed"
    exit 1
fi
echo "audio tests passed"
