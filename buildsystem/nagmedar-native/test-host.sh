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
#   - S-1206, S-1209: the remux engine keeps every video packet identical (its hash, and the raw
#     stream byte for byte), writes the streams in the planned order with the planned flags,
#     keeps chapters, attachments and the duration; the profile 7 MEL stream becomes 8.1 with
#     its base layer identical; a corrupted or truncated output fails the verification; B-frame
#     H.264 and AV1 go through; the source is refused as output; a file over 4 GB goes through
#     (when the disk has room for it).
#   - S-1207: compatibility tracks: TrueHD into E-AC-3 made default, DTS into LPCM, a 96 kHz 7.1
#     FLAC downmixed into E-AC-3 5.1 and kept whole in LPCM (its very samples), each beside an
#     original copied packet for packet, starting with it and lasting as long within a frame.
#   - Embedded subtitles: SubRip, ASS, MP4 timed text and WebVTT tracks written out as files (the
#     same line twice kept once, styles kept, never over a file there); many lines at the same
#     time take no longer than others; a packet over the text's cap is refused before its text is
#     made, in about its own memory.
#   - S-1206: cover pictures (JPEG and PNG in Matroska, an MP4's covr) and fonts come out as
#     attachments, byte for byte, under their names and types.
#   - S-1206, S-1202, SP-03: a TS whose PMT names a third stream halfway through is remuxed,
#     verified, analysed and converted, the late stream left out, under AddressSanitizer and UBSan
#     when the host build made the *-asan commands.
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

echo "== S-1202: bitrate analysis"
# the 2-second SDR file: every byte of its packets lands in one of its seconds; the first second alone when asked
"$PREFIX/bin/bitrate" "$T/sdr.mkv" > "$T/bitrate-sdr.json" 2> /dev/null
"$PREFIX/bin/bitrate" "$T/sdr.mkv" 1 > "$T/bitrate-sdr-1s.json" 2> /dev/null
"$PREFIX/bin/bitrate" "$T/missing.mkv" > "$T/bitrate-missing.json" 2> /dev/null
python3 - "$T" <<'PY'
import json, os, sys
t = sys.argv[1]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
b = json.load(open(f'{t}/bitrate-sdr.json'))
check(not b['error'] and b['complete'] and b['version'] == 1, f'sdr {b.get("error")} {b.get("complete")}')
n = b['seconds']
check(2 <= n <= 3 and len(b['total']) == n and len(b['video']) == n, f'seconds {n} {len(b["total"])} {len(b["video"])}')
packets = sum(s['bytes'] for s in b['streams'])
# a second's kilobits are rounded: within 63 bytes a second of the packets' bytes
check(abs(sum(b['total']) * 125 - packets) <= 63 * n, f'total {sum(b["total"]) * 125} vs packets {packets}')
check(0 < packets <= os.path.getsize(f'{t}/sdr.mkv'), f'packets {packets} size {os.path.getsize(f"{t}/sdr.mkv")}')
check(b['peak_1s_kbps'] == max(b['total']) and abs(b['peak_5s_kbps'] - sum(b['total']) / n) <= 1, f'peaks {b["peak_1s_kbps"]} {b["peak_5s_kbps"]} {b["total"]}')
check(b['video_peak_1s_kbps'] == max(b['video']) and 0 < b['video_average_kbps'] <= b['average_kbps'], f'video {b["video"]} {b["video_average_kbps"]} {b["average_kbps"]}')
types = [s['type'] for s in b['streams']]
check(types[:5] == ['video', 'audio', 'audio', 'audio', 'audio'] and types.count('subtitle') == 2, f'streams {types}')
check(b['streams'][0]['codec'] == 'h264' and abs(b['streams'][0]['bytes'] - sum(b['video']) * 125) <= 63 * n, f'video stream {b["streams"][0]}')
one = json.load(open(f'{t}/bitrate-sdr-1s.json'))
check(one['seconds'] == 1 and one['total'] == b['total'][:1] and one['video'] == b['video'][:1], f'one second {one["total"]} {one["video"]} vs {b["total"]} {b["video"]}')
missing = json.load(open(f'{t}/bitrate-missing.json'))
check(missing['error'].startswith('open:'), f'missing {missing}')
if problems:
    print('::error title=S-1202 bitrate::' + '; '.join(problems))
    sys.exit(1)
print('ok: bitrate analysis (seconds, peaks, streams, the first second alone, errors)')
PY

echo "== S-1204: disk test"
"$PREFIX/bin/disktest" "$T/sdr.mkv" > "$T/disk.json"
"$PREFIX/bin/disktest" "$T/sdr.mkv" 1 > "$T/disk-1mb.json"
python3 - "$T" <<'PY'
import json, os, sys
t = sys.argv[1]
problems = []
whole = json.load(open(f'{t}/disk.json'))
part = json.load(open(f'{t}/disk-1mb.json'))
size = os.path.getsize(f'{t}/sdr.mkv')
if whole['error'] or whole['bytes'] != size or whole['mb_per_s'] <= 0 or not whole['complete']:
    problems.append(f'whole {whole} size {size}')
if part['error'] or part['bytes'] != min(size, 1000000):
    problems.append(f'1 MB {part}')
if problems:
    print('::error title=S-1204 disk test::' + '; '.join(problems))
    sys.exit(1)
print(f'ok: disk test ({whole["mb_per_s"]} MB/s here)')
PY

echo "== S-1206: remux"
# the SDR film with a font attached: the streams reordered, the Turkish DTS made default, the forced flag cleared
printf 'AaBb' > "$T/font.ttf"
mkvmerge -q -o "$T/sdr-att.mkv" "$T/sdr.mkv" --attachment-mime-type font/ttf --attachment-name DejaVu.ttf --attach-file "$T/font.ttf" --chapters "$T/chapters.xml"
printf 'dv\tkeep\nstream\tv:0\nstream\ta:1\tdefault=1\tlang=tur\nstream\ta:0\tdefault=0\ttitle=Ana\nstream\ta:2\nstream\ts:1\tforced=0\tdefault=0\nstream\ts:0\ttitle=\n' > "$T/plan-sdr.txt"
"$PREFIX/bin/remux" run "$T/sdr-att.mkv" "$T/remux-sdr.mkv" "$T/plan-sdr.txt" > "$T/remux-sdr.json" 2> "$T/remux-sdr.log" || {
    echo "::error title=S-1206 remux::$( { cat "$T/remux-sdr.json"; tail -c 1500 "$T/remux-sdr.log"; } | annotate)"; exit 1; }
echo "stats: $(cat "$T/remux-sdr.json")"
hash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-sdr.json")
"$PREFIX/bin/remux" verify "$T/remux-sdr.mkv" nal_units "$hash" 50 > "$T/verify-sdr.json" 2> /dev/null
python3 - "$T/remux-sdr.mkv" "$T/corrupt.mkv" "$T/trunc.mkv" <<'PY'
import sys
data = bytearray(open(sys.argv[1], 'rb').read())
data[len(data) * 2 // 3] ^= 0x01  # one bit of a video packet
open(sys.argv[2], 'wb').write(data)
open(sys.argv[3], 'wb').write(data[: len(data) // 2])
PY
"$PREFIX/bin/remux" verify "$T/corrupt.mkv" nal_units "$hash" 50 > "$T/verify-corrupt.json" 2> /dev/null || true
"$PREFIX/bin/remux" verify "$T/trunc.mkv" nal_units "$hash" 50 > "$T/verify-trunc.json" 2> /dev/null || true
# the raw video stream of the source and the remux, byte for byte
ffmpeg -v error -y -i "$T/sdr-att.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/src.h264"
ffmpeg -v error -y -i "$T/remux-sdr.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/out.h264"
cmp "$T/src.h264" "$T/out.h264"
ffprobe -v error -print_format json -show_streams -show_chapters -show_format "$T/remux-sdr.mkv" > "$T/probe-remux-sdr.json"
ffprobe -v error -print_format json -show_format "$T/sdr-att.mkv" > "$T/probe-sdr-att.json"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts,duration,flags "$T/sdr-att.mkv" > "$T/pkt-sdr.json"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts,duration,flags "$T/remux-sdr.mkv" > "$T/pkt-remux-sdr.json"
# the profile 7 MEL film converted to 8.1 on the way
printf 'dv\tconvert81\nstream\tv:0\nstream\ta:0\nstream\ts:0\n' > "$T/plan-dv.txt"
"$PREFIX/bin/remux" run "$T/p7.mkv" "$T/remux-dv.mkv" "$T/plan-dv.txt" > "$T/remux-dv.json" 2> "$T/remux-dv.log" || {
    echo "::error title=S-1206 remux dv::$( { cat "$T/remux-dv.json"; tail -c 1500 "$T/remux-dv.log"; } | annotate)"; exit 1; }
echo "stats: $(cat "$T/remux-dv.json")"
dvhash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-dv.json")
"$PREFIX/bin/remux" verify "$T/remux-dv.mkv" base_layer_nal_units "$dvhash" 259 8 > "$T/verify-dv.json" 2> /dev/null
# the source, hashed the same way, must give the same hash: the base layer is what was copied
"$PREFIX/bin/remux" verify "$T/p7.mkv" base_layer_nal_units "$dvhash" 259 0 > "$T/verify-dv-source.json" 2> /dev/null
ffprobe -v error -print_format json -show_streams -show_chapters "$T/remux-dv.mkv" > "$T/probe-remux-dv.json"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/remux-dv.mkv" > "$T/pts-remux-dv.json"
ffmpeg -v error -y -i "$T/p7.mkv" -map 0:v:0 -c copy -bsf:v hevc_mp4toannexb -f hevc "$T/src-dv.hevc"
ffmpeg -v error -y -i "$T/remux-dv.mkv" -map 0:v:0 -c copy -bsf:v hevc_mp4toannexb -f hevc "$T/out-dv.hevc"
dovi_tool extract-rpu -i "$T/out-dv.hevc" -o "$T/rpu-remux.bin" > /dev/null
dovi_tool info -s -i "$T/rpu-remux.bin" > "$T/rpu-remux.txt"
python3 - "$T" <<'PY'
import json, re, sys
t = sys.argv[1]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def load(name):
    return json.load(open(f'{t}/{name}'))
# the plain remux: planned order and flags, every packet, chapters, attachment, tags, duration
stats = load('remux-sdr.json')
check(not stats['error'] and stats['hash_scope'] == 'nal_units' and stats['video_packets'] == 50, f'sdr stats {stats}')
check([s['ref'] for s in stats['streams']] == ['v:0', 'a:1', 'a:0', 'a:2', 's:1', 's:0'], f"refs {[s['ref'] for s in stats['streams']]}")
check(stats['attachments'] == 1 and stats['chapters'] == 2, f"attachments {stats['attachments']} chapters {stats['chapters']}")
probe = load('probe-remux-sdr.json')
streams = probe['streams']
check([s.get('codec_name', s['codec_type']) for s in streams] == ['h264', 'dts', 'eac3', 'truehd', 'subrip', 'subrip', 'attachment'], f"order {[s.get('codec_name', s['codec_type']) for s in streams]}")
check([s['disposition']['default'] for s in streams[:6]] == [0, 1, 0, 0, 0, 1], f"default {[s['disposition']['default'] for s in streams[:6]]}")
check([s['disposition']['forced'] for s in streams[:6]] == [0, 0, 0, 0, 0, 0], f"forced {[s['disposition']['forced'] for s in streams[:6]]}")
check(streams[1]['tags'].get('language') == 'tur' and streams[2]['tags'].get('title') == 'Ana', f'tags {streams[1].get("tags")} {streams[2].get("tags")}')
check(streams[3]['disposition']['comment'] == 1 and streams[3]['tags'].get('title') == 'Yorum', f'commentary {streams[3]}')
check('title' not in streams[5].get('tags', {}), f'title not cleared {streams[5].get("tags")}')
check(streams[6]['tags'].get('filename') == 'DejaVu.ttf' and streams[6]['tags'].get('mimetype') == 'font/ttf', f'attachment {streams[6].get("tags")}')
check(len(probe['chapters']) == 2 and probe['chapters'][1]['tags']['title'] == 'İki', f"chapters {probe['chapters']}")
check(probe['format']['duration'] == load('probe-sdr-att.json')['format']['duration'], f"duration {probe['format']['duration']}")
pin = load('pkt-sdr.json')['packets']
pout = load('pkt-remux-sdr.json')['packets']
check([(p['pts'], p['duration'], p['flags']) for p in pin] == [(p['pts'], p['duration'], p['flags']) for p in pout], 'video timestamps or key frames changed')
v = load('verify-sdr.json')
check(v['ok'] and v['hash'] == stats['video_hash'] and v['packets'] == 50, f'verify {v}')
check(not load('verify-corrupt.json')['ok'], 'a corrupted output passed')
check(not load('verify-trunc.json')['ok'], 'a truncated output passed')
# the Dolby Vision conversion: profile 8.1, every RPU, the base layer identical
dv = load('remux-dv.json')
check(not dv['error'] and dv['hash_scope'] == 'base_layer_nal_units' and dv['video_packets'] == 259, f'dv stats {dv}')
check(dv['dv']['input_profile'] == 7 and dv['dv']['input_el_type'] == 'MEL' and dv['dv']['output_profile'] == 8, f"dv profiles {dv['dv']}")
check(dv['dv']['rpus_converted'] == 259 and dv['dv']['rpus_failed'] == 0 and dv['dv']['el_nals_dropped'] > 0, f"dv rpus {dv['dv']}")
vd = load('verify-dv.json')
check(vd['ok'] and vd['dv_profile'] == 8 and vd['dv_compatibility'] == 1 and vd['dv_el_present'] == 0, f'dv verify {vd}')
check(vd['rpus'] == 259 and vd['rpus_bad'] == 0 and vd['packets_without_rpu'] == 0, f'dv rpus {vd}')
check(load('verify-dv-source.json')['ok'], 'the source base layer hashes differently')
dp = load('probe-remux-dv.json')
conf = [d for s in dp['streams'] if s['codec_type'] == 'video' for d in s.get('side_data_list', []) if d.get('side_data_type') == 'DOVI configuration record']
check(conf and conf[0]['dv_profile'] == 8 and conf[0]['dv_bl_signal_compatibility_id'] == 1 and conf[0]['el_present_flag'] == 0, f'record {conf}')
check([s['codec_type'] for s in dp['streams']] == ['video', 'audio', 'subtitle'] and len(dp['chapters']) == 2, f'dv streams {[s["codec_type"] for s in dp["streams"]]}')
check(len(load('pts-remux-dv.json')['packets']) == 259, 'dv frames')
rpu = open(f'{t}/rpu-remux.txt').read()
check('Profile: 8' in rpu and 'Frames: 259' in rpu, 'rpu summary: ' + ' '.join(rpu.split())[:200])
# independent of the engine's hash: the NAL units of both raw streams, without RPU and enhancement layer
def base_layer(path):
    data = open(path, 'rb').read()
    starts = [m.start() for m in re.finditer(b'\x00\x00\x01', data)]
    units = []
    for i, s in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else len(data)
        nal = data[s + 3:end].rstrip(b'\x00')
        kind, layer = (nal[0] >> 1) & 0x3F, ((nal[0] & 1) << 5) | (nal[1] >> 3)
        if kind in (62, 63) or layer > 0:
            continue
        units.append(nal)
    return units
src, out = base_layer(f'{t}/src-dv.hevc'), base_layer(f'{t}/out-dv.hevc')
check(len(src) == len(out) and src == out, f'base layer differs: {len(src)} vs {len(out)} units')
if problems:
    print('::error title=S-1206 remux::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: remux ({stats['video_packets']} packets kept, dv {dv['dv']['rpus_converted']} RPUs, {len(src)} base layer units)")
PY

echo "== S-1206: B-frame H.264, AV1, refused outputs"
# a Blu-ray-like H.264 with B-frames in Matroska: no decoding timestamps in the container, the engine derives them from the SPS
ffmpeg -v error -y -f lavfi -i "testsrc2=size=640x360:rate=25" -t 2 -c:v libx264 -preset veryfast -bf 3 -b-pyramid normal -pix_fmt yuv420p -f matroska "$T/bframes.mkv"
printf 'stream\tv:0\n' > "$T/plan-video.txt"
"$PREFIX/bin/remux" run "$T/bframes.mkv" "$T/remux-bframes.mkv" "$T/plan-video.txt" > "$T/remux-bframes.json" 2> /dev/null || {
    echo "::error title=S-1206 B-frames::$(cat "$T/remux-bframes.json" | annotate)"; exit 1; }
bhash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-bframes.json")
"$PREFIX/bin/remux" verify "$T/remux-bframes.mkv" nal_units "$bhash" 50 > "$T/verify-bframes.json" 2> /dev/null
ffmpeg -v error -y -i "$T/bframes.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/src.264"
ffmpeg -v error -y -i "$T/remux-bframes.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/out.264"
cmp "$T/src.264" "$T/out.264"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/bframes.mkv" > "$T/pts-bframes.json"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/remux-bframes.mkv" > "$T/pts-remux-bframes.json"
# AV1 (when the encoder is here): Matroska drops the temporal delimiters, the hash looks past them
av1=0
if ffmpeg -v error -y -f lavfi -i "testsrc2=size=320x180:rate=25" -t 2 -c:v libsvtav1 -preset 12 "$T/av1.mp4" 2> /dev/null; then
    av1=1
    ffmpeg -v error -y -i "$T/av1.mp4" -c copy -bsf:v av1_metadata=td=insert "$T/av1-td.mkv"
    "$PREFIX/bin/remux" run "$T/av1-td.mkv" "$T/remux-av1.mkv" "$T/plan-video.txt" > "$T/remux-av1.json" 2> /dev/null || {
        echo "::error title=S-1206 AV1::$(cat "$T/remux-av1.json" | annotate)"; exit 1; }
    ahash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-av1.json")
    "$PREFIX/bin/remux" verify "$T/remux-av1.mkv" av1_obus "$ahash" 50 > "$T/verify-av1.json" 2> /dev/null
    "$PREFIX/bin/remux" verify "$T/av1.mp4" av1_obus "$ahash" 50 > "$T/verify-av1-mp4.json" 2> /dev/null
fi
# the same stream with its SPS cut before the VUI: no reorder depth to read, the deepest assumed
ffmpeg -v error -y -i "$T/bframes.mkv" -c copy -bsf:v h264_mp4toannexb -f h264 "$T/bframes.h264"
python3 - "$T/bframes.h264" "$T/bframes-novui.h264" <<'PY'
# The H.264 stream with its SPS cut before the VUI: the engine then knows no reorder depth (S-1206 host test).
import sys
data = open(sys.argv[1], 'rb').read()

class Bits:
    def __init__(self, b): self.b, self.pos = b, 0
    def u(self, n):
        v = 0
        for _ in range(n):
            v = (v << 1) | ((self.b[self.pos >> 3] >> (7 - (self.pos & 7))) & 1); self.pos += 1
        return v
    def ue(self):
        z = 0
        while self.u(1) == 0: z += 1
        return (1 << z) - 1 + self.u(z)

def unescape(nal):
    out, i = bytearray(), 0
    while i < len(nal):
        if i + 2 < len(nal) and nal[i] == 0 and nal[i + 1] == 0 and nal[i + 2] == 3:
            out += b'\0\0'; i += 3
        else:
            out.append(nal[i]); i += 1
    return bytes(out)

def escape(rbsp):
    out, zeros = bytearray(), 0
    for x in rbsp:
        if zeros >= 2 and x <= 3:
            out.append(3); zeros = 0
        out.append(x)
        zeros = zeros + 1 if x == 0 else 0
    return bytes(out)

def strip_vui(sps):
    r = unescape(sps[1:])
    b = Bits(r)
    profile = b.u(8); b.u(16); b.ue()
    if profile in (100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135):
        chroma = b.ue()
        if chroma == 3: b.u(1)
        b.ue(); b.ue(); b.u(1)
        if b.u(1):
            for i in range(8 if chroma != 3 else 12):
                if b.u(1):
                    last = nxt = 8
                    for j in range(16 if i < 6 else 64):
                        if nxt != 0:
                            ue = b.ue(); delta = (ue + 1) // 2 * (1 if ue & 1 else -1); nxt = (last + delta + 256) % 256
                        last = last if nxt == 0 else nxt
    b.ue(); poc = b.ue()
    if poc == 0: b.ue()
    elif poc == 1:
        b.u(1); b.ue(); b.ue()
        for _ in range(b.ue()): b.ue()
    b.ue(); b.u(1); b.ue(); b.ue()
    if not b.u(1): b.u(1)
    b.u(1)
    if b.u(1):
        for _ in range(4): b.ue()
    flag_pos = b.pos
    assert b.u(1) == 1, 'no VUI to strip'
    # the bits up to the flag, the flag cleared, the stop bit, zero padding
    bits = ''.join(format(x, '08b') for x in r)[:flag_pos] + '0' + '1'
    bits += '0' * (-len(bits) % 8)
    return sps[:1] + escape(bytes(int(bits[i:i + 8], 2) for i in range(0, len(bits), 8)))

out, i, patched = bytearray(), 0, 0
starts = []
while (j := data.find(b'\x00\x00\x01', i)) >= 0:
    starts.append(j); i = j + 3
for k, s in enumerate(starts):
    end = starts[k + 1] if k + 1 < len(starts) else len(data)
    head = s - 1 if s > 0 and data[s - 1] == 0 else s
    nal_end = end - 1 if k + 1 < len(starts) and data[end - 1] == 0 else end
    nal = data[s + 3:nal_end]
    out += data[head:s + 3]
    if nal and (nal[0] & 0x1F) == 7:
        nal = strip_vui(nal); patched += 1
    out += nal
assert patched, 'no SPS'
open(sys.argv[2], 'wb').write(bytes(out))
print(f'{patched} SPS patched')
PY
mkvmerge -q -o "$T/bframes-novui.mkv" --default-duration 0:25p "$T/bframes-novui.h264"
"$PREFIX/bin/remux" run "$T/bframes-novui.mkv" "$T/remux-novui.mkv" "$T/plan-video.txt" > "$T/remux-novui.json" 2> /dev/null || {
    echo "::error title=S-1206 no VUI::$(cat "$T/remux-novui.json" | annotate)"; exit 1; }
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/bframes-novui.mkv" > "$T/pts-novui.json"
ffprobe -v error -print_format json -select_streams v:0 -show_entries packet=pts "$T/remux-novui.mkv" > "$T/pts-remux-novui.json"
ffmpeg -v error -y -i "$T/remux-novui.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/out-novui.264" 2> /dev/null
ffmpeg -v error -y -i "$T/bframes-novui.mkv" -map 0:v:0 -c copy -bsf:v h264_mp4toannexb -f h264 "$T/src-novui.264" 2> /dev/null
cmp "$T/src-novui.264" "$T/out-novui.264"
# the output must never be the source, nor a file that already holds something
"$PREFIX/bin/remux" run "$T/bframes.mkv" "$T/bframes.mkv" "$T/plan-video.txt" > "$T/remux-same.json" 2> /dev/null || true
cp "$T/sub.srt" "$T/taken.mkv"
"$PREFIX/bin/remux" run "$T/bframes.mkv" "$T/taken.mkv" "$T/plan-video.txt" > "$T/remux-taken.json" 2> /dev/null || true
cmp "$T/sub.srt" "$T/taken.mkv"
python3 - "$T" "$av1" <<'PY'
import json, os, sys
t, av1 = sys.argv[1], sys.argv[2] == '1'
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def load(name):
    return json.load(open(f'{t}/{name}'))
b = load('remux-bframes.json')
check(not b['error'] and b['video_packets'] == 50, f'bframes {b}')
check(load('verify-bframes.json')['ok'], 'bframes verify')
check([p['pts'] for p in load('pts-bframes.json')['packets']] == [p['pts'] for p in load('pts-remux-bframes.json')['packets']], 'bframes pts')
if av1:
    a = load('remux-av1.json')
    check(not a['error'] and a['hash_scope'] == 'av1_obus' and a['video_packets'] == 50, f'av1 {a}')
    check(load('verify-av1.json')['ok'], 'av1 verify')
    check(load('verify-av1-mp4.json')['ok'], 'the MP4 source hashes differently without its temporal delimiters')
n = load('remux-novui.json')
check(not n['error'] and n['video_packets'] == 50, f'no VUI {n}')
check([p['pts'] for p in load('pts-novui.json')['packets']] == [p['pts'] for p in load('pts-remux-novui.json')['packets']], 'no VUI pts')
same = load('remux-same.json')
check(same['refused'] == 'same_file' and same['error'].startswith('the output is the source itself'), f'same file {same["error"]}')
check(os.path.getsize(f'{t}/bframes.mkv') > 10000, 'the source was truncated')
taken = load('remux-taken.json')
check(taken['refused'] == 'output_not_empty', f'taken {taken["error"]}')
if problems:
    print('::error title=S-1206 formats::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: B-frame H.264 kept (dts derived, and assumed without a VUI), AV1 {'kept' if av1 else 'skipped (no encoder)'}, the source and a taken file refused as output")
PY

# the remux, the bitrate analysis and the converter under AddressSanitizer and UBSan where build.sh made them
sanitized() { if [ -x "$PREFIX/bin/$1-asan" ]; then echo "$PREFIX/bin/$1-asan"; else echo "$PREFIX/bin/$1"; fi; }
# a sanitizer's report fails the command; its first lines go into the annotation
failed_run() { # title json log
    echo "::error title=$1::$( { cat "$2" 2> /dev/null; tr '\r' '\n' < "$3" | grep -v ' MB$' | grep -v '^ *[0-9]* %$' | head -c 2500; } | annotate)"
    exit 1
}

echo "== S-1206: covers and fonts kept as attachments"
# Matroska's demuxer makes every picture attachment a video stream of one picture: a JPEG and a PNG cover beside
# a font, and an MP4's covr picture, come out as attachments, byte for byte, under their names and types
ffmpeg -v error -y -f lavfi -i "testsrc2=size=300x300" -frames:v 1 "$T/cover.jpg"
ffmpeg -v error -y -f lavfi -i "testsrc2=size=120x120" -frames:v 1 "$T/small_cover.png"
mkvmerge -q -o "$T/covers.mkv" "$T/bframes.mkv" \
    --attachment-mime-type image/jpeg --attachment-name cover.jpg --attach-file "$T/cover.jpg" \
    --attachment-mime-type font/ttf --attachment-name DejaVu.ttf --attach-file "$T/font.ttf" \
    --attachment-mime-type image/png --attachment-name small_cover.png --attachment-description "Small cover" --attach-file "$T/small_cover.png"
ffmpeg -v error -y -i "$T/bframes.mkv" -i "$T/cover.jpg" -map 0 -map 1 -c copy -disposition:v:1 attached_pic "$T/covr.mp4"
"$(sanitized remux)" run "$T/covers.mkv" "$T/remux-covers.mkv" "$T/plan-video.txt" > "$T/remux-covers.json" 2> "$T/remux-covers.log" ||
    failed_run "S-1206 covers" "$T/remux-covers.json" "$T/remux-covers.log"
"$(sanitized remux)" run "$T/covr.mp4" "$T/remux-covr.mkv" "$T/plan-video.txt" > "$T/remux-covr.json" 2> "$T/remux-covr.log" ||
    failed_run "S-1206 covr" "$T/remux-covr.json" "$T/remux-covr.log"
mkvmerge -J "$T/remux-covers.mkv" > "$T/ident-covers.json"
mkvmerge -J "$T/remux-covr.mkv" > "$T/ident-covr.json"
mkdir -p "$T/attached"
mkvextract "$T/remux-covers.mkv" attachments 1:"$T/attached/1" 2:"$T/attached/2" 3:"$T/attached/3" > /dev/null
mkvextract "$T/remux-covr.mkv" attachments 1:"$T/attached/covr" > /dev/null
python3 - "$T" <<'PY'
import json, sys
t = sys.argv[1]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def load(name):
    return json.load(open(f'{t}/{name}'))
def same(a, b):
    return open(f'{t}/{a}', 'rb').read() == open(f'{t}/{b}', 'rb').read()
stats = load('remux-covers.json')
check(not stats['error'] and stats['attachments'] == 3 and stats['video_packets'] == 50, f"covers {stats['error']} {stats['attachments']}")
found = [(a['file_name'], a['content_type'], a.get('description', '')) for a in load('ident-covers.json').get('attachments', [])]
check(found == [('cover.jpg', 'image/jpeg', ''), ('DejaVu.ttf', 'font/ttf', ''), ('small_cover.png', 'image/png', 'Small cover')], f'attachments {found}')
tracks = [x['type'] for x in load('ident-covers.json')['tracks']]
check(tracks == ['video'], f'tracks {tracks}: a cover is no track')
check(same('attached/1', 'cover.jpg') and same('attached/2', 'font.ttf') and same('attached/3', 'small_cover.png'), 'the attachments changed')
covr = load('remux-covr.json')
check(not covr['error'] and covr['attachments'] == 1, f"covr {covr['error']} {covr['attachments']}")
found = [(a['file_name'], a['content_type']) for a in load('ident-covr.json').get('attachments', [])]
check(found == [('cover.jpg', 'image/jpeg')], f'covr attachment {found}')
check(same('attached/covr', 'cover.jpg'), 'the covr picture changed')
if problems:
    print('::error title=S-1206 covers::' + '; '.join(problems))
    sys.exit(1)
print('ok: two covers and a font, and an MP4 cover, kept as attachments byte for byte')
PY

echo "== S-1206, S-1202, SP-03: a stream that comes in halfway through a TS"
# a recording whose programme gains a track for a while: the PMT names a third stream in the middle third only. The
# remux, the bitrate analysis and the converter know the streams of the file's start; the late one's packets are
# left out, never looked up past the end of their tables
ffmpeg -v error -y -f lavfi -i "testsrc2=size=320x240:rate=25" -f lavfi -i "sine=f=440:sample_rate=48000" \
    -f lavfi -i "sine=f=880:sample_rate=48000" -t 12 -map 0:v -map 1:a -map 2:a -c:v libx264 -preset ultrafast -g 25 \
    -pix_fmt yuv420p -c:a mp2 -f mpegts "$T/late-src.ts"
ffmpeg -v error -y -f lavfi -i "testsrc2=size=320x240:rate=25" -f lavfi -i "sine=f=440:sample_rate=48000" \
    -f lavfi -i "sine=f=880:sample_rate=48000" -t 12 -map 0:v -map 1:a -map 2:a -c:v libx265 -preset ultrafast \
    -x265-params log-level=error:keyint=25 -pix_fmt yuv420p -c:a mp2 -f mpegts "$T/late-hevc-src.ts"
python3 - "$T/late-src.ts" "$T/late.ts" "$T/late-hevc-src.ts" "$T/late-hevc.ts" <<'PY'
# The TS with its last stream in its middle third only: before and after it the PMT does not name it (the
# section rewritten, its CRC with it) and its packets are left out.
import sys


def crc32(b):
    crc = 0xFFFFFFFF
    for x in b:
        crc ^= x << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04C11DB7) & 0xFFFFFFFF if crc & 0x80000000 else (crc << 1) & 0xFFFFFFFF
    return crc


def pid(p):
    return ((p[1] & 0x1F) << 8) | p[2]


def section(p):
    """Where the section a packet starts begins, or None."""
    if not p[1] & 0x40:
        return None
    afc = (p[3] >> 4) & 3
    off = 4 + (1 + p[4] if afc & 2 else 0)
    return off + 1 + p[off] if afc & 1 else None


def entries(sec):
    """The PMT's streams: (pid, start, end) of each entry."""
    end = 3 + (((sec[1] & 0x0F) << 8) | sec[2]) - 4
    i = 12 + (((sec[10] & 0x0F) << 8) | sec[11])
    found = []
    while i < end:
        n = 5 + (((sec[i + 3] & 0x0F) << 8) | sec[i + 4])
        found.append((((sec[i + 1] & 0x1F) << 8) | sec[i + 2], i, i + n))
        i += n
    return found


def hide_last_stream(src, dst):
    data = open(src, 'rb').read()
    assert len(data) % 188 == 0 and data[0] == 0x47, 'not a TS'
    packets = [data[i:i + 188] for i in range(0, len(data), 188)]
    pat = next(p for p in packets if pid(p) == 0 and section(p) is not None)
    s = section(pat)
    programs = [(pat[s + 8 + k] << 8 | pat[s + 9 + k], (pat[s + 10 + k] & 0x1F) << 8 | pat[s + 11 + k])
                for k in range(0, (((pat[s + 1] & 0x0F) << 8) | pat[s + 2]) - 9, 4)]
    pmt = next(pmt_pid for number, pmt_pid in programs if number)
    first = next(p for p in packets if pid(p) == pmt and section(p) is not None)
    late = entries(first[section(first):])[-1][0]

    def without_late(p):
        s = section(p)
        sec = bytearray(p[s:])
        kept = bytearray(sec[:12 + (((sec[10] & 0x0F) << 8) | sec[11])])
        for es, a, b in entries(sec):
            if es != late:
                kept += sec[a:b]
        length = len(kept) - 3 + 4
        kept[1] = (kept[1] & 0xF0) | (length >> 8)
        kept[2] = length & 0xFF
        kept += crc32(kept).to_bytes(4, 'big')
        out = bytes(p[:s]) + bytes(kept)
        return out + b'\xff' * (188 - len(out))

    n, out, shown = len(packets), [], 0
    for i, p in enumerate(packets):
        middle = n // 3 <= i < 2 * n // 3
        if pid(p) == late and not middle:
            continue
        if pid(p) == pmt and section(p) is not None and not middle:
            p = without_late(p)
        shown += pid(p) == late
        out.append(p)
    assert shown > 100, f'too little of the late stream: {shown} packets'
    open(dst, 'wb').write(b''.join(out))


for k in range(1, len(sys.argv), 2):
    hide_last_stream(sys.argv[k], sys.argv[k + 1])
PY
printf 'stream\tv:0\nstream\ta:0\n' > "$T/plan-late.txt"
"$(sanitized remux)" run "$T/late.ts" "$T/remux-late.mkv" "$T/plan-late.txt" > "$T/remux-late.json" 2> "$T/remux-late.log" ||
    failed_run "S-1206 late stream" "$T/remux-late.json" "$T/remux-late.log"
lhash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-late.json")
"$(sanitized remux)" verify "$T/remux-late.mkv" nal_units "$lhash" 300 > "$T/verify-late.json" 2> "$T/verify-late.log" ||
    failed_run "S-1209 late stream" "$T/verify-late.json" "$T/verify-late.log"
"$(sanitized bitrate)" "$T/late.ts" > "$T/bitrate-late.json" 2> "$T/bitrate-late.log" ||
    failed_run "S-1202 late stream" "$T/bitrate-late.json" "$T/bitrate-late.log"
"$(sanitized dvconvert)" "$T/late-hevc.ts" "$T/dv-late.mkv" > "$T/dv-late.json" 2> "$T/dv-late.log" ||
    failed_run "SP-03 late stream" "$T/dv-late.json" "$T/dv-late.log"
ffprobe -v error -print_format json -show_streams "$T/remux-late.mkv" > "$T/probe-remux-late.json"
ffprobe -v error -print_format json -show_streams "$T/dv-late.mkv" > "$T/probe-dv-late.json"
python3 - "$T" "$(sanitized remux)" <<'PY'
import json, sys
t, asan = sys.argv[1], sys.argv[2].endswith('-asan')
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def load(name):
    return json.load(open(f'{t}/{name}'))
stats = load('remux-late.json')
check(not stats['error'] and stats['video_packets'] == 300 and [s['ref'] for s in stats['streams']] == ['v:0', 'a:0'], f"remux {stats['error']} {stats['video_packets']}")
check(load('verify-late.json')['ok'], 'the remux of the TS does not verify')
check([s['codec_type'] for s in load('probe-remux-late.json')['streams']] == ['video', 'audio'], 'the late stream got into the remux')
b = load('bitrate-late.json')
# two streams: the third was not known at the start, and its packets were read past
check(not b['error'] and b['complete'] and [s['type'] for s in b['streams']] == ['video', 'audio'] and 12 <= b['seconds'] <= 14, f"bitrate {b.get('error')} {b.get('seconds')} {[s['type'] for s in b['streams']]}")
dv = load('dv-late.json')
# a stream without Dolby Vision: converted all the same, and said so
check(dv['video_packets'] == 300 and dv['streams_copied'] == 2 and dv['streams_skipped'] == 0 and 'no Dolby Vision RPU' in dv['error'], f'dvconvert {dv}')
check([s['codec_type'] for s in load('probe-dv-late.json')['streams']] == ['video', 'audio'], 'the late stream got into the conversion')
for log in ('remux-late.log', 'verify-late.log', 'bitrate-late.log', 'dv-late.log'):
    text = open(f'{t}/{log}', errors='replace').read()
    check('AddressSanitizer' not in text and 'runtime error' not in text, f'{log}: a sanitizer report')
if problems:
    print('::error title=Late TS stream::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: a stream that comes in halfway left out by the remux, the verification, the bitrate analysis and the converter{' under AddressSanitizer and UBSan' if asan else ''}")
PY

echo "== S-1207: compatibility tracks"
# the TrueHD commentary with an E-AC-3 track after it, made default; the DTS with LPCM beside it;
# a 96 kHz 7.1 FLAC downmixed into E-AC-3 5.1 and kept whole in LPCM; every original copied untouched
ffmpeg -v error -y -f lavfi -i "sine=f=330:d=2:sample_rate=96000" \
    -af "pan=7.1|c0=c0|c1=0.9*c0|c2=0.8*c0|c3=0.7*c0|c4=0.6*c0|c5=0.5*c0|c6=0.4*c0|c7=0.3*c0" -c:a flac "$T/flac71.mka"
mkvmerge -q -o "$T/compat-src.mkv" "$T/sdr.mkv" --language 0:tur "$T/flac71.mka"
printf 'stream\tv:0\nstream\ta:2\nencode\ta:2\teac3\tdefault=1\ttitle=Uyumluluk izi (E-AC-3 5.1)\nstream\ta:1\nencode\ta:1\tlpcm\nstream\ta:4\nencode\ta:4\teac3\nencode\ta:4\tlpcm\nstream\ta:0\n' > "$T/plan-compat.txt"
"$PREFIX/bin/remux" run "$T/compat-src.mkv" "$T/compat.mkv" "$T/plan-compat.txt" > "$T/remux-compat.json" 2> "$T/remux-compat.log" || {
    echo "::error title=S-1207 compatibility tracks::$( { cat "$T/remux-compat.json"; tail -c 1500 "$T/remux-compat.log"; } | annotate)"; exit 1; }
echo "stats: $(cat "$T/remux-compat.json")"
chash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-compat.json")
"$PREFIX/bin/remux" verify "$T/compat.mkv" nal_units "$chash" 50 > "$T/verify-compat.json" 2> /dev/null
ffprobe -v error -print_format json -show_streams "$T/compat.mkv" > "$T/probe-compat.json"
ffprobe -v error -print_format json -show_streams "$T/compat-src.mkv" > "$T/probe-compat-src.json"
# the copied originals packet by packet, and every audio stream's packet times
for pair in "2:1" "1:3" "4:5" "0:8"; do
    ffmpeg -v error -y -i "$T/compat-src.mkv" -map "0:a:${pair%%:*}" -c copy -f framemd5 "$T/src-a${pair%%:*}.md5"
    ffmpeg -v error -y -i "$T/compat.mkv" -map "0:${pair##*:}" -c copy -f framemd5 "$T/out-${pair##*:}.md5"
done
ffprobe -v error -print_format json -show_entries packet=stream_index,pts_time,duration_time "$T/compat-src.mkv" > "$T/pkt-compat-src.json"
ffprobe -v error -print_format json -show_entries packet=stream_index,pts_time,duration_time "$T/compat.mkv" > "$T/pkt-compat.json"
# the LPCM of the FLAC decodes to the FLAC's very samples; every new stream decodes without an error
ffmpeg -v error -i "$T/compat-src.mkv" -map 0:a:4 -f md5 - > "$T/md5-flac.txt"
ffmpeg -v error -i "$T/compat.mkv" -map 0:7 -f md5 - > "$T/md5-lpcm.txt"
for i in 2 4 6 7; do
    ffmpeg -v error -xerror -i "$T/compat.mkv" -map "0:$i" -f null - 2> "$T/decode-$i.log" || { echo "::error title=S-1207 decode::stream $i: $(annotate < "$T/decode-$i.log")"; exit 1; }
done
python3 - "$T" <<'PY'
import json, sys
t = sys.argv[1]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def load(name):
    return json.load(open(f'{t}/{name}'))
stats = load('remux-compat.json')
check(not stats['error'] and stats['video_packets'] == 50, f"stats {stats['error']}")
check(load('verify-compat.json')['ok'], 'the video changed')
streams = load('probe-compat.json')['streams']
codecs = [s['codec_name'] for s in streams]
check(codecs == ['h264', 'truehd', 'eac3', 'dts', 'pcm_s24le', 'flac', 'eac3', 'pcm_s16le', 'eac3'], f'order {codecs}')
check([s['disposition']['default'] for s in streams[1:]] == [0, 1, 0, 0, 0, 0, 0, 0], f"default {[s['disposition']['default'] for s in streams[1:]]}")
compat = streams[2]
check(compat['channels'] == 6 and compat['sample_rate'] == '48000' and compat['tags'].get('title') == 'Uyumluluk izi (E-AC-3 5.1)'
      and compat['tags'].get('language') == 'eng' and compat['disposition']['comment'] == 1, f'compat {compat}')
check(streams[4]['channels'] == 6 and streams[4]['tags'].get('title') == 'LPCM 5.1', f'lpcm of dts {streams[4]}')
check(streams[6]['channels'] == 6 and streams[6]['channel_layout'] == '5.1(side)' and streams[6]['sample_rate'] == '48000'
      and streams[6]['tags'].get('title') == 'E-AC-3 5.1' and streams[6]['tags'].get('language') == 'tur', f'downmix {streams[6]}')
check(streams[7]['channels'] == 8 and streams[7]['sample_rate'] == '96000', f'lpcm 7.1 {streams[7]}')
# the originals byte for byte, with their timestamps
for src, out in ((2, 1), (1, 3), (4, 5), (0, 8)):
    a = [l.split(',')[2:] for l in open(f'{t}/src-a{src}.md5') if not l.startswith('#')]
    b = [l.split(',')[2:] for l in open(f'{t}/out-{out}.md5') if not l.startswith('#')]
    check(a and a == b, f'a:{src} not copied untouched ({len(a)} vs {len(b)} packets)')
check(open(f'{t}/md5-flac.txt').read() == open(f'{t}/md5-lpcm.txt').read(), 'the LPCM of the FLAC is not its samples')
# each new stream starts with its source and lasts as long, within one frame
def span(packets, index):
    p = [x for x in packets if x['stream_index'] == index]
    first = min(float(x['pts_time']) for x in p)
    return first, max(float(x['pts_time']) + float(x.get('duration_time', 0)) for x in p) - first
src_packets = load('pkt-compat-src.json')['packets']
out_packets = load('pkt-compat.json')['packets']
audio = [s['index'] for s in load('probe-compat-src.json')['streams'] if s['codec_type'] == 'audio']
for out, source in ((2, 2), (4, 1), (6, 4), (7, 4)):
    (s0, sd), (o0, od) = span(src_packets, audio[source]), span(out_packets, out)
    # one frame of E-AC-3 (1536 samples at 48 kHz), and of the engine's LPCM blocks: 32 ms
    check(abs(o0 - s0) <= 0.006 and abs(od - sd) <= 0.034, f'stream {out}: starts {o0} lasts {od}, its source a:{source} {s0} {sd}')
encoded = [s for s in stats['streams'] if 'encoded' in s]
check(len(encoded) == 4 and all(e['packets'] > 0 and e['encoded']['decode_errors'] == 0 for e in encoded), f'encoded {encoded}')
if problems:
    print('::error title=S-1207 compatibility tracks::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: {len(encoded)} compatibility tracks beside their originals, copied untouched; the LPCM of the FLAC is its samples")
PY

echo "== S-1206: a file over 4 GB"
# lossless H.264 of 1080p noise: about 650 Mbps, 4.5 GB in a minute; skipped where the disk has no room for it twice
if [ "$(df --output=avail -B1 "$T" | tail -1)" -gt 12000000000 ]; then
    ffmpeg -v error -y -f lavfi -i "nullsrc=size=1920x1080:rate=25,geq=lum='random(1)*255':cb=128:cr=128" \
        -f lavfi -i "sine=f=440:sample_rate=48000" -t 56 -c:v libx264 -preset ultrafast -qp 0 -pix_fmt yuv420p -c:a flac "$T/big.mkv"
    printf 'stream\tv:0\nstream\ta:0\n' > "$T/plan-big.txt"
    "$PREFIX/bin/remux" run "$T/big.mkv" "$T/remux-big.mkv" "$T/plan-big.txt" > "$T/remux-big.json" 2> /dev/null || {
        echo "::error title=S-1206 big file::$(cat "$T/remux-big.json" | annotate)"; exit 1; }
    bighash=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_hash'])" "$T/remux-big.json")
    bigpackets=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['video_packets'])" "$T/remux-big.json")
    "$PREFIX/bin/remux" verify "$T/remux-big.mkv" nal_units "$bighash" "$bigpackets" > "$T/verify-big.json" 2> /dev/null
    python3 - "$T" <<'PY'
import json, os, sys
t = sys.argv[1]
stats = json.load(open(f'{t}/remux-big.json'))
v = json.load(open(f'{t}/verify-big.json'))
size = os.path.getsize(f'{t}/remux-big.mkv')
problems = []
if stats['error'] or stats['video_packets'] != 1400 or stats['hash_scope'] != 'nal_units':
    problems.append(f'stats {stats}')
if size < 4_300_000_000 or abs(size - os.path.getsize(f'{t}/big.mkv')) > 1_000_000:
    problems.append(f'size {size}')
if not v['ok'] or v['packets'] != 1400:
    problems.append(f'verify {v}')
if stats['peak_rss_kb'] > 400_000:
    problems.append(f"memory {stats['peak_rss_kb']} kB")
if problems:
    print('::error title=S-1206 big file::' + '; '.join(problems))
    sys.exit(1)
print(f"ok: {size / 1e9:.2f} GB remuxed in {stats['wall_seconds']:.1f} s, verified, peak memory {stats['peak_rss_kb'] // 1024} MB")
PY
    rm -f "$T/big.mkv" "$T/remux-big.mkv"
else
    echo "skipped: not enough room on the disk for two files of 4.5 GB"
fi

echo "== Embedded subtitles: extraction"
printf '1\n00:00:01,000 --> 00:00:03,000\nAltyazı <i>korunmalı</i>\n\n2\n00:00:01,000 --> 00:00:03,000\nAltyazı <i>korunmalı</i>\n\n3\n00:00:02,000 --> 00:00:04,500\nİkinci satır\nüst üste\n\n' > "$T/subs.srt"
mkvmerge -q -o "$T/subs-srt.mkv" "$T/audio.m4a" --language 0:tur "$T/subs.srt"
cat > "$T/subs.ass" <<'ASS'
[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,60,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,40,1
Style: Yorum,Arial,48,&H0000FFFF,&H000000FF,&H00000000,&H00000000,0,1,0,0,100,100,0,0,1,2,0,8,10,10,40,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:03.00,0:00:04.00,Yorum,,0,0,0,,Sonra gelen
Dialogue: 0,0:00:01.00,0:00:02.50,Default,Kemal,0,0,0,,{\i1}Merhaba{\i0}\Nikinci satır
ASS
mkvmerge -q -o "$T/subs-ass.mkv" "$T/audio.m4a" "$T/subs.ass"
printf '1\n00:00:01,000 --> 00:00:03,000\n<b>Kalın</b> ve <i>eğik</i>\n\n2\n00:00:03,500 --> 00:00:05,000\nDüz\n\n' > "$T/styled.srt"
ffmpeg -v error -y -i "$T/audio.m4a" -i "$T/styled.srt" -map 0 -map 1 -c:a copy -c:s mov_text "$T/subs-tx3g.mp4"
printf 'WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n<v Kemal><i>Selam</i> &amp; <c.yellow>hoş</c> geldin\n\n00:00:03.500 --> 00:00:05.000\n<b>Kalın</b>\n\n' > "$T/subs.vtt"
ffmpeg -v error -y -i "$T/subs.vtt" -c:s webvtt "$T/subs-vtt.mkv"
# 200 000 lines at the same time (each line looked at every other one of its time before), and one line of 70 MB
python3 - "$T" <<'PY'
import sys
t = sys.argv[1]
with open(f'{t}/same.srt', 'w') as f:
    for i in range(200_000):
        f.write(f'{i + 1}\n00:00:01,000 --> 00:00:02,000\nAynı anda {i}\n\n')
with open(f'{t}/huge.srt', 'w') as f:
    f.write('1\n00:00:01,000 --> 00:00:02,000\nKısa satır\n\n')
    f.write('2\n00:00:03,000 --> 00:00:04,000\n' + 'x' * (70 * 1024 * 1024) + '\n\n')
PY
ffmpeg -v error -y -i "$T/same.srt" -c:s copy "$T/subs-same.mkv"
ffmpeg -v error -y -i "$T/huge.srt" -c:s copy "$T/subs-huge.mkv"
rm -f "$T/same.srt" "$T/huge.srt"
python3 - "$PREFIX/bin/subextract" "$T" <<'PY'
import json, os, resource, subprocess, sys, time
exe, t = sys.argv[1], sys.argv[2]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def extract(name, out):
    started = time.monotonic()
    p = subprocess.run([exe, f'{t}/{name}', '0', f'{t}/{out}'], capture_output=True, text=True)
    return json.loads(p.stdout), time.monotonic() - started
def text(out):
    return open(f'{t}/{out}', 'rb').read()
r, _ = extract('subs-srt.mkv', 'out.srt')
check(not r['error'] and r['format'] == 'srt' and r['events'] == 2, f'srt {r}')
check(text('out.srt') == ('\ufeff1\r\n00:00:01,000 --> 00:00:03,000\r\nAltyazı <i>korunmalı</i>\r\n\r\n'
                          '2\r\n00:00:02,000 --> 00:00:04,500\r\nİkinci satır\r\nüst üste\r\n\r\n').encode(), f'srt text {text("out.srt")[:200]}')
# never over a file that is there
r, _ = extract('subs-srt.mkv', 'out.srt')
check(r['error'] == 'write', f'written over a file {r}')
r, _ = extract('subs-ass.mkv', 'out.ass')
ass = text('out.ass').decode('utf-8-sig').replace('\r\n', '\n')
check(not r['error'] and r['format'] == 'ass' and r['events'] == 2, f'ass {r}')
check('Style: Yorum,Arial,48' in ass and ass.index('Sonra gelen') < ass.index('Merhaba'), f'ass header or order {ass[-400:]}')
check('Dialogue: 0,0:00:01.00,0:00:02.50,Default,Kemal,0,0,0,,{\\i1}Merhaba{\\i0}\\Nikinci satır' in ass, f'ass line {ass[-300:]}')
r, _ = extract('subs-tx3g.mp4', 'out-tx3g.srt')
check(not r['error'] and r['codec'] == 'mov_text' and r['events'] == 2, f'tx3g {r}')
check('<b>Kalın</b> ve <i>eğik</i>' in text('out-tx3g.srt').decode('utf-8-sig'), f'tx3g styles {text("out-tx3g.srt")[:200]}')
r, _ = extract('subs-vtt.mkv', 'out-vtt.srt')
vtt = text('out-vtt.srt').decode('utf-8-sig')
check(not r['error'] and r['codec'] == 'webvtt' and '<i>Selam</i> & hoş geldin' in vtt and '<b>Kalın</b>' in vtt, f'webvtt {r} {vtt[:200]}')
r, took = extract('subs-same.mkv', 'out-same.srt')
same = text('out-same.srt').decode('utf-8-sig').split('\r\n\r\n')
check(not r['error'] and r['events'] == 200_000 and took < 20, f'200 000 lines at once: {r} in {took:.1f} s')
check(same[0].endswith('Aynı anda 0') and same[199_999].endswith('Aynı anda 199999'), f'their order {same[:2]} {same[-2:]}')
r, took = extract('subs-huge.mkv', 'out-huge.srt')
peak_mb = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss // 1024
check(r['error'] == 'too_large' and not os.path.exists(f'{t}/out-huge.srt'), f'70 MB line {r}')
check(peak_mb < 140, f'a 70 MB packet took {peak_mb} MB')
if problems:
    print('::error title=Embedded subtitles::' + '; '.join(problems))
    sys.exit(1)
print(f'ok: SRT, ASS, MP4 timed text and WebVTT written out; 200 000 lines at once; a 70 MB line refused in {peak_mb} MB')
PY
rm -f "$T/subs-same.mkv" "$T/subs-huge.mkv"

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
