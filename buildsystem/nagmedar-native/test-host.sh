#!/usr/bin/env bash
# Host tests of the native lab code (CI): needs `build.sh host <prefix>` first, plus ffmpeg,
# mkvmerge and dovi_tool on PATH.
#   - S-802: the media probe reads Dolby Vision (a profile 7 MEL stream with its enhancement
#     layer, built from dovi_tool's test assets with audio, subtitles and chapters), HDR10, HLG
#     and HDR10+ streams, audio codecs, languages and flags, subtitles and chapters; a missing
#     file is an error, not a crash.
#   - SP-05: whisper_lab_cli decodes and transcribes a tone with whisper.cpp's test model.
set -euo pipefail
trap 'echo "test-host.sh: line $LINENO failed: $BASH_COMMAND"' ERR
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
PREFIX=$(realpath -m "${1:?prefix}")
WORK=${NAGMEDAR_NATIVE_WORK:-$ROOT/native/.work}
ASSETS="$WORK/src/dovi_tool/assets/hevc_tests"
# NAGMEDAR_TEST_DIR keeps the test files (CI pushes them to ci/debug-native when a test fails)
if [ -n "${NAGMEDAR_TEST_DIR:-}" ]; then
    T=$NAGMEDAR_TEST_DIR
    rm -rf "$T" && mkdir -p "$T"
else
    T=$(mktemp -d)
    trap 'rm -rf "$T"' EXIT
fi

# one annotation line: GitHub reads %0A as a line break
annotate() { sed 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}'; }

echo "== S-802: profile 7 test stream"
dovi_tool inject-rpu -i "$ASSETS/regular_start_code_4_muxed_el.hevc" --rpu-in "$ASSETS/regular_rpu_mel.bin" -o "$T/p7.hevc" > /dev/null
ffmpeg -v error -f lavfi -i "sine=f=440:d=11" -c:a aac "$T/audio.m4a"
printf '1\n00:00:01,000 --> 00:00:03,000\nAltyazı korunmalı\n' > "$T/sub.srt"
cat > "$T/chapters.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Chapters><EditionEntry>
<ChapterAtom><ChapterTimeStart>00:00:00.000</ChapterTimeStart><ChapterDisplay><ChapterString>Bir</ChapterString></ChapterDisplay></ChapterAtom>
<ChapterAtom><ChapterTimeStart>00:00:05.000</ChapterTimeStart><ChapterDisplay><ChapterString>İki</ChapterString></ChapterDisplay></ChapterAtom>
</EditionEntry></Chapters>
XML
mkvmerge -q -o "$T/p7.mkv" --default-duration 0:24000/1001p "$T/p7.hevc" "$T/audio.m4a" --language 0:tur "$T/sub.srt" --chapters "$T/chapters.xml"

echo "== S-802: media probe"
# HDR10 with Matroska colour metadata, HLG, and HDR10+ (an ST 2094-40 SEI before every picture)
ffmpeg -v error -f lavfi -i "testsrc2=size=640x360:rate=24000/1001" -t 1 -pix_fmt yuv420p10le -c:v libx265 \
    -x265-params "log-level=error:hdr10=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400" \
    -f hevc "$T/hdr10.hevc"
mkvmerge -q -o "$T/hdr10.mkv" --colour-transfer-characteristics 0:16 --colour-primaries 0:9 --colour-matrix-coefficients 0:9 \
    --max-content-light 0:1000 --max-frame-light 0:400 --max-luminance 0:1000 --min-luminance 0:0.0001 "$T/hdr10.hevc"
ffmpeg -v error -f lavfi -i "testsrc2=size=640x360:rate=25" -t 1 -pix_fmt yuv420p10le -c:v libx265 \
    -x265-params "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc" -f hevc "$T/hlg.hevc"
mkvmerge -q -o "$T/hlg.mkv" --colour-transfer-characteristics 0:18 --colour-primaries 0:9 "$T/hlg.hevc"
python3 - "$T/hdr10.hevc" "$T/hdr10plus.hevc" <<'PY'
import sys
data = open(sys.argv[1], 'rb').read()
# prefix SEI: user_data_registered_itu_t_t35 (USA, Samsung, oriented code 1, application 4, version 1)
sei = bytes([0, 0, 0, 1, 0x4E, 0x01, 0x04, 0x08, 0xB5, 0x00, 0x3C, 0x00, 0x01, 0x04, 0x01, 0x40, 0x80])
starts, i = [], 0
while (j := data.find(b'\x00\x00\x01', i)) >= 0:
    starts.append(j)
    i = j + 3
out, prev = bytearray(), 0
for s in starts:
    begin = s - 1 if s > 0 and data[s - 1] == 0 else s
    out += data[prev:begin]
    if (data[s + 3] >> 1) & 0x3F < 32 and data[s + 5] & 0x80:  # first slice of a picture
        out += sei
    prev = begin
out += data[prev:]
open(sys.argv[2], 'wb').write(out)
PY
mkvmerge -q -o "$T/hdr10plus.mkv" "$T/hdr10plus.hevc"
# SDR H.264 with E-AC-3, DTS, a TrueHD commentary, 96 kHz FLAC and two subtitle tracks
printf '1\n00:00:00,500 --> 00:00:01,500\nZorunlu\n' > "$T/forced.srt"
ffmpeg -v error -f lavfi -i "testsrc2=size=1920x1080:rate=25" -f lavfi -i "sine=f=440:d=2:sample_rate=48000" \
    -f lavfi -i "sine=f=660:d=2:sample_rate=96000" -t 2 \
    -filter_complex "[1:a]pan=5.1|c0=c0|c1=c0|c2=c0|c3=c0|c4=c0|c5=c0,asplit=3[a0][a1][a2]" \
    -map 0:v -map "[a0]" -map "[a1]" -map "[a2]" -map 2:a -c:v libx264 -pix_fmt yuv420p -preset ultrafast \
    -c:a:0 eac3 -c:a:1 dca -c:a:2 truehd -c:a:3 flac -strict -2 \
    -metadata:s:a:0 language=tur -metadata:s:a:1 language=eng -metadata:s:a:2 language=eng -metadata:s:a:2 title=Yorum \
    -metadata:s:a:3 language=jpn -disposition:a:0 default -disposition:a:1 0 -disposition:a:2 comment -disposition:a:3 0 \
    "$T/av.mkv"
mkvmerge -q -o "$T/sdr.mkv" "$T/av.mkv" --language 0:tur "$T/sub.srt" --language 0:eng --forced-display-flag 0:1 "$T/forced.srt"
for name in p7.mkv hdr10.mkv hlg.mkv hdr10plus.mkv sdr.mkv; do
    "$PREFIX/bin/mediaprobe" "$T/$name" > "$T/probe-$name.json" 2> /dev/null
done
"$PREFIX/bin/mediaprobe" "$T/missing.mkv" > "$T/probe-missing.json" 2> /dev/null
python3 - "$T" <<'PY'
import json, sys
t = sys.argv[1]
problems = []
def probe(name):
    return json.load(open(f'{t}/probe-{name}.json'))
def check(ok, what):
    if not ok:
        problems.append(what)
def video(name):
    p = probe(name)
    check(not p['error'], f'{name}: {p["error"]}')
    return p, p.get('video') or {}
p, v = video('p7.mkv')
check(v.get('codec') == 'hevc' and v.get('profile') == 'Main 10' and v.get('bit_depth') == 10, f'p7 video {v}')
check(v.get('dv_profile') == 7 and v.get('dv_el') == 'MEL' and v.get('dv_el_present') and v.get('dv_rpu'), f'p7 dv {v}')
check(p['chapters'] == 2 and p['subtitles'] and p['subtitles'][0]['language'] == 'tur', f'p7 tracks {p["chapters"]} {p["subtitles"]}')
p, v = video('hdr10.mkv')
check(v.get('transfer') == 'smpte2084' and v.get('primaries') == 'bt2020', f'hdr10 colour {v}')
check(abs(v.get('max_luminance', 0) - 1000) < 1 and v.get('max_cll') == 1000 and v.get('max_fall') == 400, f'hdr10 metadata {v}')
check(not v.get('hdr10plus') and v.get('dv_profile') == 0, f'hdr10 extras {v}')
p, v = video('hlg.mkv')
check(v.get('transfer') == 'arib-std-b67', f'hlg {v}')
p, v = video('hdr10plus.mkv')
check(v.get('hdr10plus') is True, f'hdr10plus {v}')
p, v = video('sdr.mkv')
check(v.get('codec') == 'h264' and v.get('width') == 1920 and v.get('height') == 1080 and abs(v.get('fps', 0) - 25) < 0.01, f'sdr video {v}')
check(v.get('transfer') != 'smpte2084' and v.get('dv_profile') == 0 and v.get('bit_depth') == 8, f'sdr colour {v}')
audio = p['audio']
check([a['codec'] for a in audio] == ['eac3', 'dts', 'truehd', 'flac'], f'audio codecs {[a["codec"] for a in audio]}')
check([a['language'] for a in audio] == ['tur', 'eng', 'eng', 'jpn'], f'audio languages {[a["language"] for a in audio]}')
check(audio[0]['channels'] == 6 and audio[0]['default'] and not audio[1]['default'], f'audio flags {audio[:2]}')
check(audio[2]['commentary'] and audio[2]['title'] == 'Yorum', f'commentary {audio[2]}')
check(audio[3]['sample_rate'] == 96000, f'flac {audio[3]}')
subs = p['subtitles']
check([(s['codec'], s['language'], s['forced']) for s in subs] == [('subrip', 'tur', False), ('subrip', 'eng', True)], f'subtitles {subs}')
missing = probe('missing')
check(missing['error'].startswith('open:') and missing['version'] == 1, f'missing {missing}')
if problems:
    print('::error title=S-802 probe::' + '; '.join(problems))
    sys.exit(1)
print('ok: media probe (Dolby Vision 7 MEL, HDR10, HLG, HDR10+, audio, subtitles, errors)')
PY

echo "== SP-05: whisper pipeline smoke test"
ffmpeg -v error -f lavfi -i "sine=f=440:d=6" -ac 2 -c:a libmp3lame "$T/tone.mp3" 2> /dev/null || ffmpeg -v error -f lavfi -i "sine=f=440:d=6" -ac 2 "$T/tone.wav"
input=$(ls "$T"/tone.* | head -1)
"$PREFIX/bin/whisper_lab_cli" "$WORK/src/whisper/models/for-tests-ggml-tiny.bin" "$input" tr 5 > "$T/whisper.json" 2> /dev/null
python3 - "$T/whisper.json" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
problems = []
if r['error']:
    problems.append('error: ' + r['error'])
if not 4.9 <= r['audio_seconds'] <= 5.1:
    problems.append(f"audio_seconds {r['audio_seconds']}")
if r['inference_seconds'] <= 0:
    problems.append('no inference time')
if problems:
    print('::error title=SP-05 whisper::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: whisper decoded {r['audio_seconds']} s, inference {r['inference_seconds']:.2f} s, {r['system']}")
PY

echo "== S-901: music engine"
# the test files go with the lab's, for the ci/debug-native branch when something fails
NAGMEDAR_TEST_DIR="${NAGMEDAR_TEST_DIR:-$T}" "$HERE/test-audio.sh" "$PREFIX" 2>&1 | tee "$T/audio-tests.log" || {
    echo "::error title=S-901 ses motoru::$(grep -E "FAIL|failed" "$T/audio-tests.log" | annotate)"
    exit 1
}
