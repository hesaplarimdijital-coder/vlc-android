#!/usr/bin/env bash
# Host tests of the native lab code (CI): needs `build.sh host <prefix>` first, plus ffmpeg,
# ffprobe, mkvmerge and dovi_tool on PATH.
#   - SP-03: a profile 7 MEL stream built from dovi_tool's test assets, with audio, subtitles
#     and chapters, is converted to MKV and MP4; the result must be profile 8.1 with the base
#     layer's timestamps intact, no enhancement layer and every other track kept.
#   - SP-05: whisper_lab_cli decodes and transcribes a tone with whisper.cpp's test model.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
PREFIX=$(realpath -m "${1:?prefix}")
WORK=${NAGMEDAR_NATIVE_WORK:-$ROOT/native/.work}
ASSETS="$WORK/src/dovi_tool/assets/hevc_tests"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

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
    "$PREFIX/bin/dvconvert" "$T/p7.mkv" "$T/out.$container" "${extra[@]}" > "$T/stats.json" 2> /dev/null
    ffprobe -v error -print_format json -show_streams -show_chapters "$T/out.$container" > "$T/probe.json"
    ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/p7.mkv" > "$T/pts_in.json"
    ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/out.$container" > "$T/pts_out.json"
    ffmpeg -v error -i "$T/out.$container" -map 0:v:0 -c copy -bsf:v hevc_mp4toannexb -f hevc "$T/out.hevc"
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
