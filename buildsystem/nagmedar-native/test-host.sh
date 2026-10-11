#!/usr/bin/env bash
# Host tests of the native lab code (CI): needs `build.sh host <prefix>` first, plus ffmpeg,
# ffprobe, mkvmerge and dovi_tool on PATH.
#   - SP-03: a profile 7 MEL stream built from dovi_tool's test assets, with audio, subtitles
#     and chapters, is converted to MKV and MP4; the result must be profile 8.1 with the base
#     layer's timestamps intact, no enhancement layer and every other track kept.
#   - S-802: the media probe reads Dolby Vision (profile 7 MEL with its enhancement layer, the
#     converted 8.1), HDR10, HLG and HDR10+ streams, audio codecs, languages and flags,
#     subtitles and chapters; a missing file is an error, not a crash.
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

echo "== SP-03: profile 7 test stream"
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

for container in mkv mp4; do
    echo "== SP-03: convert to $container"
    extra=()
    [ "$container" = mp4 ] && extra=(--mp4)
    if ! "$PREFIX/bin/dvconvert" "$T/p7.mkv" "$T/out.$container" "${extra[@]}" > "$T/stats.json" 2> "$T/dvconvert.log"; then
        # the converter prints its stats (with the error) even when it fails; the log has FFmpeg's reason
        log=$(tr '\r' '\n' < "$T/dvconvert.log" | grep -v ' MB$' | tail -c 1500)
        echo "::error title=SP-03 $container::$( { cat "$T/stats.json"; echo "$log"; } | annotate)"
        exit 1
    fi
    echo "stats: $(cat "$T/stats.json")"
    ffprobe -v error -print_format json -show_streams -show_chapters "$T/out.$container" > "$T/probe.json"
    ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/p7.mkv" > "$T/pts_in.json"
    ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/out.$container" > "$T/pts_out.json"
    ffmpeg -v error -y -i "$T/out.$container" -map 0:v:0 -c copy -bsf:v hevc_mp4toannexb -f hevc "$T/out.hevc"
    dovi_tool extract-rpu -i "$T/out.hevc" -o "$T/rpu.bin" > /dev/null
    dovi_tool info -s -i "$T/rpu.bin" > "$T/rpu.txt"
    python3 - "$T" "$container" <<'PY'
import json, re, sys
t, container = sys.argv[1], sys.argv[2]
stats = json.load(open(f'{t}/stats.json'))
probe = json.load(open(f'{t}/probe.json'))
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
check(stats['input_profile'] == 7 and stats['input_el_type'] == 'MEL', f"input {stats['input_profile']} {stats['input_el_type']}")
check(stats['output_profile'] == 8, f"output profile {stats['output_profile']}")
check(stats['rpus_converted'] == 259 and stats['rpus_failed'] == 0, f"rpus {stats['rpus_converted']}/{stats['rpus_failed']}")
check(stats['el_nals_dropped'] > 0, 'no EL dropped')
check(not stats['error'], 'error: ' + stats['error'])
video = [s for s in probe['streams'] if s['codec_type'] == 'video']
conf = [d for s in video for d in s.get('side_data_list', []) if d.get('side_data_type') == 'DOVI configuration record']
check(conf and conf[0]['dv_profile'] == 8 and conf[0]['dv_bl_signal_compatibility_id'] == 1 and conf[0]['el_present_flag'] == 0,
      f'configuration record {conf}')
check(any(s['codec_type'] == 'audio' for s in probe['streams']), 'audio lost')
if container == 'mkv':
    check(any(s['codec_type'] == 'subtitle' for s in probe['streams']), 'subtitles lost')
    check(len(probe.get('chapters', [])) == 2, f"chapters {len(probe.get('chapters', []))}")
else:
    check(video and video[0].get('codec_tag_string') == 'hvc1', f"tag {video[0].get('codec_tag_string') if video else None}")
pts_in = sorted(p['pts'] for p in json.load(open(f'{t}/pts_in.json'))['packets'])
pts_out = sorted(p['pts'] for p in json.load(open(f'{t}/pts_out.json'))['packets'])
check(len(pts_in) == len(pts_out) == 259, f'frames {len(pts_in)} -> {len(pts_out)}')
data = open(f'{t}/out.hevc', 'rb').read()
types = {}
for m in re.finditer(b'\x00\x00\x01', data):
    b0, b1 = data[m.start() + 3], data[m.start() + 4]
    key = ((b0 >> 1) & 0x3F, ((b0 & 1) << 5) | (b1 >> 3))
    types[key] = types.get(key, 0) + 1
check(not any(k[0] == 63 or k[1] > 0 for k in types), f'enhancement layer left: {types}')
rpu = open(f'{t}/rpu.txt').read()
check('Profile: 8' in rpu and 'Frames: 259' in rpu, 'rpu summary: ' + ' '.join(rpu.split())[:200])
if problems:
    print(f'::error title=SP-03 {container}::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: {container}, {stats['rpus_converted']} RPUs, {stats['el_nals_dropped']} EL NALs dropped")
PY
done

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
for name in p7.mkv out.mkv out.mp4 hdr10.mkv hlg.mkv hdr10plus.mkv sdr.mkv; do
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
for name in ('out.mkv', 'out.mp4'):
    p, v = video(name)
    check(v.get('dv_profile') == 8 and v.get('dv_compatibility') == 1 and not v.get('dv_el_present') and v.get('dv_rpu'), f'{name} dv {v}')
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
print('ok: media probe (Dolby Vision 7 MEL / 8.1, HDR10, HLG, HDR10+, audio, subtitles, errors)')
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
