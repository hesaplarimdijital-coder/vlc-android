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
#   - the DSP chain (S-906): identity and bypass bit-exact, the EQs equal to dsp_model.py's,
#     the limiter never past its threshold, the auto preamp level, the clip meter
#   - ReplayGain (S-907): the scanner against ffmpeg's ebur128 and the standard's sine, the
#     tags written and read back in four formats with the audio untouched, the playback gains
#   - tempo, pitch, balance and mono (S-909): a sine keeps its frequency through a tempo change
#     and its length through a pitch change, at the levels asked; balance and mono exact
#   - DSD (S-908): a modulated sine as DSF and DFF (dsd_gen.py) decodes to PCM the same from
#     both containers, at its level; as DoP the words carry the file's bits under the
#     0x05/0xFA markers, the same from both
#   - the formats (S-911): WavPack, TTA, ALAC, WMA, Vorbis, AIFF, WAV float, CAF and Matroska
#     audio, each what ffmpeg decodes (the engine's own FFmpeg build must carry them); the
#     tracker modules (testmedia/tools/tracker.py's MOD, XM, S3M and IT) rendered by libopenmpt
#     as ffmpeg's libopenmpt demuxer renders them, with their length and kind known, and a seek
#     (at open or while playing) landing on the sample asked: what follows is the straight render
#     from there within -80 dBFS (libopenmpt's state after a seek is not quite the played one)
#   - the library's probe (S-803): the composer from FLAC, MP3, M4A and Opus; the waveform
#     (S-1001): each bucket's peak and RMS as the decoded samples give them
#   - the A/B test (S-912): the chain's level change measured within 0.1 dB of the model's
#     response, side B bypassed at that level, exactly; the chain running on through side B,
#     B delayed as the chain delays, the switch crossfaded
#   - the null test (S-1405): null_test.py finds a capture's lead-in and reports no difference
#     for a true capture, the one differing sample for a broken one
#   - the lyrics (S-1003): USLT, SYLT, LYRICS and ©lyr read from MP3, FLAC, M4A and Opus; written
#     (S-1005) into MP3 (USLT and SYLT), FLAC, M4A, Opus and Vorbis and read back, replaced,
#     removed, the other tags and the audio untouched
#   - the covers (S-1006): the front cover of FLAC, M4A, MP3 (of two pictures) and Opus; a tag by name
#   - CUE parts (S-808): a file's parts, bounded in CUE frames (1/75 s), played as a queue give
#     the whole file back, sample for sample in FLAC and MP3, within -60 dBFS in AAC and Opus
#     (whose decoders settle after a seek, as ffmpeg's own do); a part alone is ffmpeg's trim
#     of the file; a seek within a part counts from the part's start; and a seek in MP3 lands
#     on the sample asked (the trimmed encoder delay accounted for)
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

echo "== S-905: crossfade"
# a one-second crossfade over the end of part 1: cos/sin gains over its last 44100 frames, exactly
"$NAGAUDIO" decode part1.flac -f f64 -o a.raw > /dev/null
"$NAGAUDIO" decode part2.flac -f f64 -o b.raw > /dev/null
"$NAGAUDIO" decode part1.flac part2.flac -f f64 --crossfade 1000 -o xf.raw > /dev/null
"$NAGAUDIO" decode part1.flac part2.flac -f f64 --crossfade 1000 --linear -o xl.raw > /dev/null
python3 - <<'PY' || failures=$((failures + 1))
import math, struct, sys
def frames(path):
    data = open(path, "rb").read()
    return [struct.unpack_from("<2d", data, i) for i in range(0, len(data), 16)]
a, b, x, l = frames("a.raw"), frames("b.raw"), frames("xf.raw"), frames("xl.raw")
fade = 44100
if len(x) != len(a) + len(b) - fade or len(l) != len(x):
    print("FAIL crossfade length %d, expected %d" % (len(x), len(a) + len(b) - fade))
    sys.exit(1)
worst = worst_l = 0.0
for i in range(fade):
    t = (i + 1) / fade
    ea = a[len(a) - fade + i]
    eb = b[i]
    for c in range(2):
        worst = max(worst, abs(ea[c] * math.cos(t * math.pi / 2) + eb[c] * math.sin(t * math.pi / 2) - x[len(a) - fade + i][c]))
        worst_l = max(worst_l, abs(ea[c] * (1 - t) + eb[c] * t - l[len(a) - fade + i][c]))
if x[:len(a) - fade] != a[:len(a) - fade] or x[len(a):] != b[fade:]:
    print("FAIL crossfade: the parts outside the fade changed")
    sys.exit(1)
ok = worst < 1e-12 and worst_l < 1e-12
print("%s crossfade equal-power and linear, worst error %.1e / %.1e" % ("ok  " if ok else "FAIL", worst, worst_l))
sys.exit(0 if ok else 1)
PY

echo "== S-906: DSP chain"
MODEL="$HERE/dsp_model.py"
json() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1], {"d": d}))' "$1"; }
# a chain with nothing in it, and a bypassed one with everything in it, leave the samples alone
check "dsp identity bit-exact" "$(engine_md5 s16 flac16.flac)" "$(engine_md5 s16 flac16.flac --preamp 0 --graphic 0,0,0,0,0 --peq "pk:1000:0:1" --limiter-release 50)"
check "dsp bypass bit-exact" "$(engine_md5 s16 flac16.flac)" "$(engine_md5 s16 flac16.flac --preamp -3 --graphic 2,2,2 --limiter -1 --bypass)"
"$NAGAUDIO" decode flac16.flac -f f64 -o plain.raw > /dev/null
# the EQs: the engine's doubles against the Python model, to rounding
EQ=(--preamp -2.5 --graphic 3,-2,0,4,0,0,-6,0,0,2,0,0,5,0,0,-3,0,0,1,2 --peq "pk:1000:6:1.2;ls:120:-3:0.7;hs:8000:2.5:0.7;hp:30:0:0.7" --profile "pk:3300:-4:2;lp:16000:0:0.7" --profile-preamp -1.5)
"$NAGAUDIO" decode flac16.flac -f f64 -o eq.raw "${EQ[@]}" > eq.json
python3 "$MODEL" plain.raw model.raw 44100 2 "${EQ[@]}"
python3 - <<'PY' || failures=$((failures + 1))
import struct, sys
a = open("eq.raw", "rb").read()
b = open("model.raw", "rb").read()
n = len(a) // 8
x, y = struct.unpack("<%dd" % n, a), struct.unpack("<%dd" % n, b)
worst = max(abs(x[i] - y[i]) for i in range(n)) if n and len(a) == len(b) else float("inf")
print("%s dsp: 15 EQ bands and two preamps against the model, worst error %.1e" % ("ok  " if worst < 1e-12 else "FAIL", worst))
sys.exit(0 if worst < 1e-12 else 1)
PY
check "dsp stages in the signal path" "graphic_eq:9 parametric_eq:4 profile:2" "$(json '" ".join("%s:%d" % (s["stage"], s["bands"]) for s in d["signal_path"]["dsp"] if "bands" in s)' < eq.json)"
check "dsp is why not bit-perfect" "dsp" "$(json 'd["signal_path"]["not_bit_perfect_because"]' < eq.json)"
# the limiter: a full-scale 1 kHz sine, 6 dB up, never passes -1 dBFS; without it the meter counts the clipping
ffmpeg -v error -y -f lavfi -i "sine=f=1000:r=48000:d=2,aformat=channel_layouts=stereo" -af "volume=11.3,aformat=sample_fmts=s32" -c:a flac hot.flac
"$NAGAUDIO" decode hot.flac -f f64 -o hot_lim.raw --preamp 6 --limiter -1 > lim.json
"$NAGAUDIO" decode hot.flac -f f64 -o hot_clip.raw --preamp 6 > clip.json
check "limiter latency 5 ms at 48 kHz" "239" "$(json 'd["dsp_latency"]' < lim.json)"
check "clip meter counts without the limiter" "120000" "$(json 'd["meter"]["clipped"]' < clip.json)"
check "clip meter clean with the limiter" "0" "$(json 'd["meter"]["clipped"]' < lim.json)"
python3 - <<'PY' || failures=$((failures + 1))
import json, math, struct, sys
data = open("hot_lim.raw", "rb").read()
x = struct.unpack("<%dd" % (len(data) // 8), data)
threshold = 10 ** (-1 / 20)
peak = max(abs(v) for v in x)
reduction = json.load(open("lim.json"))["meter"]["reduction_db"]
ok = peak <= threshold and threshold / peak < 1.001 and 6.9 < reduction < 7.1
print("%s limiter: peak %.12f against %.12f, reduction %.2f dB" % ("ok  " if ok else "FAIL", peak, threshold, reduction))
sys.exit(0 if ok else 1)
PY
# the auto preamp takes a +6 dB peak at 1 kHz back: the sine comes out at its own level
"$NAGAUDIO" decode hot.flac -f f64 -o hot_auto.raw --peq "pk:1000:6:1" --auto-preamp > auto.json
check "auto preamp -6 dB" "-6.0" "$(json '"%.1f" % [s for s in d["signal_path"]["dsp"] if s["stage"] == "auto_preamp"][0]["db"]' < auto.json)"
"$NAGAUDIO" decode hot.flac -f f64 -o hot_plain.raw > /dev/null
python3 - <<'PY' || failures=$((failures + 1))
import math, struct, sys
def tail_peak(path):
    data = open(path, "rb").read()
    x = struct.unpack("<%dd" % (len(data) // 8), data)
    return max(abs(v) for v in x[len(x) // 2:])
diff = 20 * math.log10(tail_peak("hot_auto.raw") / tail_peak("hot_plain.raw"))
print("%s auto preamp: the sine's level changed by %.4f dB" % ("ok  " if abs(diff) < 0.01 else "FAIL", diff))
sys.exit(0 if abs(diff) < 0.01 else 1)
PY
# a seek forgets the chain's memory: what follows it equals a start from that time
"$NAGAUDIO" decode flac16.flac -f f64 -o seek_start.raw --seek 1000 --limiter -1 --preamp 3 > /dev/null
"$NAGAUDIO" decode flac16.flac -f f64 -o seek_mid.raw --seek 1000 --seek-after 5000 --limiter -1 --preamp 3 > /dev/null
check "dsp reset at a seek" "$(md5_of < seek_start.raw)" "$(tail -c $(stat -c %s seek_start.raw) seek_mid.raw | md5_of)"

echo "== S-907: EBU R128 scanner"
# ITU-R BS.1770: a 997 Hz sine at -23 dBFS in both channels reads -23.0 LUFS
ffmpeg -v error -y -f lavfi -i "sine=f=997:r=48000:d=5,aformat=channel_layouts=stereo" -af "volume=$(python3 -c 'print(10**(-23/20)/0.0888)'),aformat=sample_fmts=s32" -c:a flac sine23.flac
# a 12 kHz sine at 48 kHz, 45 degrees off the samples: its true peak is 0.5, its sample peak 0.354
ffmpeg -v error -y -f lavfi -i "aevalsrc=0.5*sin(2*PI*12000*t+PI/4)|0.5*sin(2*PI*12000*t+PI/4):s=48000:d=2" -c:a flac -sample_fmt s32 isp.flac
"$NAGAUDIO" scan sine23.flac isp.flac flac16.flac mp3.mp3 six.flac --album > scan.json
ffmpeg_lufs() { ffmpeg -nostats -i "$1" -af ebur128 -f null - 2>&1 | grep -A3 "Integrated loudness" | sed -n 's/.*I: *\(-*[0-9.]*\) LUFS.*/\1/p'; }
python3 - "$(ffmpeg_lufs flac16.flac)" "$(ffmpeg_lufs mp3.mp3)" "$(ffmpeg_lufs six.flac)" <<'PY' || failures=$((failures + 1))
import json, math, sys
r = json.load(open("scan.json"))
sine, isp, flac, mp3, six = r["tracks"]
problems = []
if abs(sine["loudness_lufs"] + 23.0) > 0.1:
    problems.append("the -23 dBFS sine reads %.2f LUFS" % sine["loudness_lufs"])
if abs(20 * math.log10(isp["true_peak"] / 0.5)) > 0.1 or abs(isp["sample_peak"] - 0.353553) > 1e-4:
    problems.append("true peak %.4f, sample peak %.4f of the 12 kHz sine" % (isp["true_peak"], isp["sample_peak"]))
for name, ours, theirs in (("flac16", flac, sys.argv[1]), ("mp3", mp3, sys.argv[2]), ("5.1", six, sys.argv[3])):
    if abs(ours["loudness_lufs"] - float(theirs)) > 0.15:
        problems.append("%s: %.2f LUFS here, %s in ffmpeg" % (name, ours["loudness_lufs"], theirs))
if abs(sine["gain_db"] - (-18 - sine["loudness_lufs"])) > 1e-6:
    problems.append("gain %.3f dB for %.3f LUFS" % (sine["gain_db"], sine["loudness_lufs"]))
album = r["album"]
if not (-30 < album["loudness_lufs"] < 0) or album["true_peak"] < max(t["true_peak"] for t in r["tracks"]):
    problems.append("album %s" % album)
if problems:
    print("FAIL scanner: " + "; ".join(problems))
    sys.exit(1)
print("ok   scanner: sine %.2f LUFS, 12 kHz true peak %.4f, flac16/mp3/5.1 within 0.15 LU of ffmpeg, album %.2f LUFS" % (sine["loudness_lufs"], isp["true_peak"], album["loudness_lufs"]))
PY

echo "== S-907: ReplayGain tags"
for f in flac16.flac mp3.mp3 aac.m4a opus.opus; do
    cp "$f" "tagged_$f"
    "$NAGAUDIO" tag "tagged_$f" --track-gain -3.25 --track-peak 0.987654 --album-gain -4.5 --album-peak 0.999 --track-lufs -14.75 --album-lufs -13.5 > /dev/null
    check "tags read back from $f" "-3.25 0.987654 -4.5 0.999" "$("$NAGAUDIO" info "tagged_$f" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["track_gain_db"], "%.6f" % d["track_peak"], d["album_gain_db"], d["album_peak"])')"
    fmt=f32le; case "$f" in flac16.flac) fmt=s16le ;; esac
    check "audio of $f untouched by the tags" "$(ffmpeg -v error -i "$f" -f $fmt - | md5_of)" "$(ffmpeg -v error -i "tagged_$f" -f $fmt - | md5_of)"
done
check "opus R128 gains" "-2432 -2112" "$(ffprobe -v error -show_entries stream_tags=R128_ALBUM_GAIN,R128_TRACK_GAIN -of default=nw=1:nk=1 tagged_opus.opus | tr '\n' ' ' | sed 's/ $//')"

echo "== S-907: ReplayGain playback"
"$NAGAUDIO" decode tagged_flac16.flac -f f64 -o rg_track.raw --replaygain track > rg_track.json
"$NAGAUDIO" decode tagged_flac16.flac -f f64 -o rg_album.raw --replaygain album --rg-preamp 2 > /dev/null
"$NAGAUDIO" decode flac16.flac -f f64 -o rg_untagged.raw --replaygain track --rg-untagged -6 > /dev/null
"$NAGAUDIO" decode tagged_flac16.flac -f f64 -o rg_clip.raw --replaygain track --rg-preamp 12 > rg_clip.json
check "replaygain off bit-exact" "$(engine_md5 s16 flac16.flac)" "$(engine_md5 s16 tagged_flac16.flac --replaygain off)"
check "replaygain is why not bit-perfect" "replaygain" "$(json 'd["signal_path"]["not_bit_perfect_because"]' < rg_track.json)"
check "clipping prevented" "True 0.11" "$(json 'str(d["signal_path"]["replaygain"]["clipping_prevented"]) + " %.2f" % d["signal_path"]["replaygain"]["gain_db"]' < rg_clip.json)"
python3 - <<'PY' || failures=$((failures + 1))
import struct, sys
def load(path):
    data = open(path, "rb").read()
    return struct.unpack("<%dd" % (len(data) // 8), data)
plain = load("plain.raw")
worst = 0.0
for name, db in (("rg_track", -3.25), ("rg_album", -4.5 + 2), ("rg_untagged", -6.0)):
    x = load(name + ".raw")
    gain = 10 ** (db / 20)
    worst = max(worst, max(abs(x[i] - plain[i] * gain) for i in range(len(plain))))
clip = load("rg_clip.raw")
held = 1 / 0.987654
worst = max(worst, max(abs(clip[i] - plain[i] * held) for i in range(len(plain))))
print("%s replaygain track, album with preamp, untagged and clip-prevented gains, worst error %.1e" % ("ok  " if worst == 0 else "FAIL", worst))
sys.exit(0 if worst == 0 else 1)
PY

echo "== S-909: tempo, pitch, balance, mono"
check "tempo 1 and pitch 0 stay out of the way" "$(engine_md5 s16 flac16.flac)" "$(engine_md5 s16 flac16.flac --tempo 1 --pitch 0)"
"$NAGAUDIO" decode sine23.flac -f f64 -o tempo2.raw --tempo 2 > tempo2.json
"$NAGAUDIO" decode sine23.flac -f f64 -o tempo05.raw --tempo 0.5 > /dev/null
"$NAGAUDIO" decode sine23.flac -f f64 -o pitch12.raw --pitch 12 > /dev/null
"$NAGAUDIO" decode sine23.flac -f f64 -o pitchm7.raw --pitch -7 > /dev/null
"$NAGAUDIO" decode sine23.flac -f f64 -o both.raw --tempo 1.5 --pitch -5 > /dev/null
check "tempo is why not bit-perfect" "tempo" "$(json 'd["signal_path"]["not_bit_perfect_because"]' < tempo2.json)"
python3 - <<'PY' || failures=$((failures + 1))
import math, struct, sys
def left(path):
    data = open(path, "rb").read()
    return struct.unpack("<%dd" % (len(data) // 8), data)[0::2]
def measure(path):
    x = left(path)
    a, b = len(x) // 10, len(x) * 9 // 10
    crossings = sum(1 for i in range(a + 1, b) if x[i - 1] < 0 <= x[i])
    return len(x) / 48000.0, crossings / ((b - a) / 48000.0), max(abs(v) for v in x)
plain_peak = 0.0705
problems = []
for name, seconds, hz in (("tempo2", 2.5, 997), ("tempo05", 10.0, 997), ("pitch12", 5.0, 1994), ("pitchm7", 5.0, 997 * 2 ** (-7 / 12)), ("both", 5.0 / 1.5, 997 * 2 ** (-5 / 12))):
    s, f, p = measure(name + ".raw")
    # the end of the queue goes out unstretched: up to 0.3 s short at the slowest tempo
    if not (seconds - 0.35 <= s <= seconds + 0.05):
        problems.append("%s: %.2f s, expected %.2f" % (name, s, seconds))
    if abs(f - hz) > hz * 0.005:
        problems.append("%s: %.1f Hz, expected %.1f" % (name, f, hz))
    if p > plain_peak * 1.05:
        problems.append("%s: peak %.4f over the sine's %.4f" % (name, p, plain_peak))
if problems:
    print("FAIL tempo and pitch: " + "; ".join(problems))
    sys.exit(1)
print("ok   tempo 2 and 0.5 keep 997 Hz; pitch +12 and -7 and tempo 1.5 with pitch -5 land on their frequencies and lengths")
PY
"$NAGAUDIO" decode flac16.flac -f f64 -o balance.raw --balance 0.5 > /dev/null
"$NAGAUDIO" decode flac16.flac -f f64 -o mono.raw --mono > mono.json
check "balance is why not bit-perfect" "balance" "$(json 'd["signal_path"]["not_bit_perfect_because"]' < mono.json)"
python3 - <<'PY' || failures=$((failures + 1))
import struct, sys
def load(path):
    data = open(path, "rb").read()
    return struct.unpack("<%dd" % (len(data) // 8), data)
plain, balance, mono = load("plain.raw"), load("balance.raw"), load("mono.raw")
n = len(plain) // 2
worst = max(max(abs(balance[2 * i] - plain[2 * i] * 0.5), abs(balance[2 * i + 1] - plain[2 * i + 1])) for i in range(n))
worst = max(worst, max(abs(mono[2 * i] - (plain[2 * i] + plain[2 * i + 1]) / 2) + abs(mono[2 * i + 1] - mono[2 * i]) for i in range(n)))
print("%s balance 0.5 (left at half) and mono (both sides the average), worst error %.1e" % ("ok  " if worst == 0 else "FAIL", worst))
sys.exit(0 if worst == 0 else 1)
PY

echo "== S-908: DSD"
python3 "$HERE/dsd_gen.py" sine.dsf sine.dff
check "dsd info" "2822400 352800" "$("$NAGAUDIO" info sine.dsf 2> /dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["dsd_rate"], d["rate"])')"
# (the engine's own FFmpeg and the runner's may filter DSD differently; the level below is the check)
"$NAGAUDIO" decode sine.dsf -f f64 -o dsf_pcm.raw 2> /dev/null > dsf_pcm.json
"$NAGAUDIO" decode sine.dff -f f64 -o dff_pcm.raw 2> /dev/null > /dev/null
check "dsd to pcm is why not bit-perfect" "dsd_pcm" "$(json 'd["signal_path"]["not_bit_perfect_because"]' < dsf_pcm.json)"
# the decoders' first frames differ in their filter's start; from frame 64 on the two containers agree
check "dsf and dff decode alike" "$(tail -c +$((64 * 16 + 1)) dsf_pcm.raw | md5_of)" "$(tail -c +$((64 * 16 + 1)) dff_pcm.raw | md5_of)"
python3 - <<'PY' || failures=$((failures + 1))
import math, struct, sys
data = open("dsf_pcm.raw", "rb").read()
x = struct.unpack("<%dd" % (len(data) // 8), data)[0::2]
rate = 352800
# the sine's amplitude at 1 kHz, by correlation over the middle of the file
a, b = len(x) // 4, len(x) * 3 // 4
w = 2 * math.pi * 1000 / rate
re = sum(x[i] * math.cos(w * i) for i in range(a, b))
im = sum(x[i] * math.sin(w * i) for i in range(a, b))
amplitude = 2 * math.hypot(re, im) / (b - a)
ok = abs(amplitude - 0.5) < 0.03
print("%s dsd decoded: the 1 kHz sine at %.3f (modulated at 0.5)" % ("ok  " if ok else "FAIL", amplitude))
sys.exit(0 if ok else 1)
PY
"$NAGAUDIO" decode sine.dsf -f s32 -o dop.raw --dop 2> /dev/null > dop.json
"$NAGAUDIO" decode sine.dff -f s32 -o dop_dff.raw --dop 2> /dev/null > /dev/null
check "dop source" "176400 24 True" "$(json 'str(d["signal_path"]["source"]["rate"]) + " " + str(d["signal_path"]["source"]["bits"]) + " " + str(d["signal_path"]["source"]["dop"])' < dop.json)"
check "dop from dff is dop from dsf" "$(md5_of < dop.raw)" "$(md5_of < dop_dff.raw)"
python3 - <<'PY' || failures=$((failures + 1))
import struct, sys
raw = open("dop.raw", "rb").read()
words = struct.unpack("<%di" % (len(raw) // 4), raw)
bits = [bytearray(), bytearray()]
markers_ok = True
for i in range(0, len(words), 2):
    expect = 0x05 if (i // 2) % 2 == 0 else 0xFA
    for c in range(2):
        word = (words[i + c] >> 8) & 0xFFFFFF
        markers_ok = markers_ok and (word >> 16) == expect
        bits[c] += bytes(((word >> 8) & 0xFF, word & 0xFF))
# the DSF's bytes: blocks of 4096 per channel, bits oldest first in the LSB, reversed for DoP
dsf = open("sine.dsf", "rb").read()
block = struct.unpack_from("<I", dsf, 28 + 4 + 8 + 4 * 6 + 8)[0]
payload = dsf[28 + 52 + 12:]
reverse = [int("{:08b}".format(b)[::-1], 2) for b in range(256)]
expected = [bytearray(), bytearray()]
for off in range(0, len(payload), 2 * block):
    expected[0] += bytes(reverse[b] for b in payload[off:off + block])
    expected[1] += bytes(reverse[b] for b in payload[off + block:off + 2 * block])
same = all(bytes(bits[c]) == bytes(expected[c][:len(bits[c])]) and len(bits[c]) == len(expected[c]) for c in range(2))
ok = markers_ok and same
print("%s dop: %d frames, markers alternate %s, the file's bits %s" % ("ok  " if ok else "FAIL", len(words) // 2, markers_ok, same))
sys.exit(0 if ok else 1)
PY

echo "== S-902: the output's depth as the owner asks"
# the format is the engine's own choice here (no -f): the range decides it
chosen_md5() { "$NAGAUDIO" decode "$@" --md5 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["format"], d["md5"])'; }
check "16-bit file widened to s32" "s32 $(ffmpeg -v error -i flac16.flac -f s32le - | md5_of)" "$(chosen_md5 flac16.flac --min-format s32)"
check "16-bit file as s24" "s24 $(ffmpeg -v error -i flac16.flac -f s24le - | md5_of)" "$(chosen_md5 flac16.flac --min-format s24 --max-format s24)"
check "24-bit file capped at s24" "s24 $(ffmpeg -v error -i flac24.flac -f s24le - | md5_of)" "$(chosen_md5 flac24.flac --max-format s24)"
check "24-bit file capped at s16, dithered" "s16" "$(chosen_md5 flac24.flac --max-format s16 | cut -d" " -f1)"

echo "== S-912: A/B level match"
# a preamp of 6 dB: the level change is 6.00 dB and side B is the file at 6 dB, the first half the chain's
"$NAGAUDIO" decode flac16.flac -f f64 -o ab_b.raw --preamp 6 --ab-after 88200 > ab.json
"$NAGAUDIO" decode flac16.flac -f f64 -o ab_a.raw --preamp 6 > /dev/null
check "chain level +6.00 dB" "6.0000 6.0000" "$(json '"%.4f %.4f" % (d["dsp_level_db"], d["ab_gain_db"])' < ab.json)"
python3 - <<'PY' || failures=$((failures + 1))
import math, struct, sys
def load(path):
    data = open(path, "rb").read()
    return struct.unpack("<%dd" % (len(data) // 8), data)
a, b, plain = load("ab_a.raw"), load("ab_b.raw"), load("plain.raw")
half = 88200 * 2
gain = 10 ** (6.0 / 20)
first = a[:half] == b[:half]
worst = max(abs(b[i] - plain[i] * gain) for i in range(half, len(plain)))
ok = first and worst < 1e-12
print("%s side A the chain, side B the file at the chain's level, worst error %.1e" % ("ok  " if ok else "FAIL", worst))
sys.exit(0 if ok else 1)
PY
# a 1 kHz boost on the 997 Hz sine: the level change equals the model's response there
"$NAGAUDIO" decode sine23.flac -f f64 -o ab_eq.raw --peq "pk:1000:6:1" > ab_eq.json
python3 - "$(json 'd["dsp_level_db"]' < ab_eq.json)" "$(python3 "$MODEL" --response 48000 997 --peq "pk:1000:6:1")" <<'PY' || failures=$((failures + 1))
import sys
measured, expected = float(sys.argv[1]), float(sys.argv[2])
ok = abs(measured - expected) < 0.1
print("%s chain level on the sine %.2f dB, the model's response %.2f dB" % ("ok  " if ok else "FAIL", measured, expected))
sys.exit(0 if ok else 1)
PY
# nothing but the sound tells the sides apart: with a peak and the limiter (whose lookahead
# delays the chain), side B from about 1.5 s on. The chain runs on B too (the meter counts every
# frame); the switch crossfades over 10 ms (441 frames), each frame the two sides weighed; then B
# is the file delayed by the chain's latency, at B's gain
"$NAGAUDIO" decode flac16.flac -f f64 -o abl_b.raw --peq "pk:1000:6:1" --limiter -1 --ab-after 66150 > abl.json
"$NAGAUDIO" decode flac16.flac -f f64 -o abl_a.raw --peq "pk:1000:6:1" --limiter -1 > /dev/null
python3 - "$(json '"%d %d %.4f" % (d["meter"]["frames"], d["dsp_latency"], d["ab_gain_db"])' < abl.json)" <<'PY' || failures=$((failures + 1))
import math, struct, sys
frames, latency, gain_db = sys.argv[1].split()
frames, latency = int(frames), int(latency)
def load(path):
    data = open(path, "rb").read()
    return struct.unpack("<%dd" % (len(data) // 8), data)
a, b, plain = load("abl_a.raw"), load("abl_b.raw"), load("plain.raw")
C, F = 2, 441
total = len(plain) // C
first = next(i for i in range(len(b)) if b[i] != a[i]) // C
start = first - 1  # the crossfade's first frame weighs B at 0: still A's
# B's gain as the output holds it (the summary rounds it): at the loudest sample after the crossfade
loudest = max(range((start + F) * C, total * C), key=lambda i: abs(plain[i - latency * C]))
gain = b[loudest] / plain[loudest - latency * C]
assert abs(20 * math.log10(gain) - float(gain_db)) < 1e-4
def side_b(f, c):
    return plain[(f - latency) * C + c] * gain
worst_fade = max(abs(b[(start + k) * C + c] - (a[(start + k) * C + c] * (1 - k / F) + side_b(start + k, c) * (k / F)))
                 for k in range(F) for c in range(C))
worst_b = max(abs(b[f * C + c] - side_b(f, c)) for f in range(start + F, total) for c in range(C))
ok = frames == total and latency > 0 and 66150 <= start < 66150 + 1024 and worst_fade < 1e-12 and worst_b < 1e-12
print("%s chain on both sides (%d of %d frames metered), the switch at frame %d crossfaded (worst %.1e), "
      "side B the file %d frames late at %+.2f dB (worst %.1e)"
      % ("ok  " if ok else "FAIL", frames, total, start, worst_fade, latency, float(gain_db), worst_b))
sys.exit(0 if ok else 1)
PY

echo "== S-1405: null test"
"$NAGAUDIO" decode flac24.flac -f s24 -o ref24.raw > /dev/null
python3 - <<'PY'
import random
ref = open("ref24.raw", "rb").read()
random.seed(1)
lead = bytearray()
for i in range(1234 * 2):
    v = random.randint(-3, 3) & 0xFFFFFF
    lead += bytes((v & 0xFF, (v >> 8) & 0xFF, (v >> 16) & 0xFF))
open("cap_ok.raw", "wb").write(bytes(lead) + ref + bytes(6 * 500))
bad = bytearray(bytes(lead) + ref)
bad[len(lead) + 6 * 100000] ^= 1
open("cap_bad.raw", "wb").write(bytes(bad))
PY
check "null test on a true capture" "1234 0 True" "$(python3 "$HERE/null_test.py" ref24.raw cap_ok.raw --format s24 --rate 96000 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["offset_frames"], d["samples_differing"], d["bit_perfect"])')"
check "null test on a broken capture" "1 1 100000" "$(python3 "$HERE/null_test.py" ref24.raw cap_bad.raw --format s24 --rate 96000 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["samples_differing"], d["largest_difference_lsb"], d["first_difference_frame"])' || true)"

echo "== S-911: formats"
for spec in "wv:-c:a wavpack" "tta:-c:a tta" "m4a:-c:a alac" "aiff:-c:a pcm_s24be" "wav:-c:a pcm_f32le" "caf:-c:a pcm_s24le" "mka:-c:a flac" "oga:-c:a libvorbis -q:a 5"; do
    ext=${spec%%:*}
    codec=${spec#*:}
    # shellcheck disable=SC2086
    ffmpeg -v error -y -i flac24.flac $codec "fmt.$ext"
    check "$ext decodes as ffmpeg" "$(ffmpeg -v error -i "fmt.$ext" -f f32le - | md5_of)" "$(engine_md5 f32 "fmt.$ext")"
done
ffmpeg -v error -y -i flac16.flac -c:a wmav2 -b:a 128k fmt.wma
check "wma decodes as ffmpeg" "$(ffmpeg -v error -i fmt.wma -f f32le - | md5_of)" "$(engine_md5 f32 fmt.wma)"

echo "== S-911: tracker modules (libopenmpt)"
# the same piece in four formats (15.36 s: two patterns of 64 rows at 125 BPM), each rendered as ffmpeg's demuxer renders it
for ext in mod xm s3m it; do
    python3 "$HERE/../../testmedia/tools/tracker.py" "tracker.$ext"
    check "$ext is a tracker module to the engine" "tracker $ext 48000 2 15360" "$("$NAGAUDIO" info "tracker.$ext" | json '" ".join(str(x) for x in (d["codec"], d["container"], d["rate"], d["channels"], d["duration_ms"]))')"
    if [ -n "$(ffmpeg -hide_banner -demuxers 2>/dev/null | grep libopenmpt)" ]; then
        check "$ext renders as ffmpeg's libopenmpt" "$(ffmpeg -v error -i "tracker.$ext" -f f32le - | md5_of)" "$(engine_md5 f32 "tracker.$ext")"
    else
        echo "skip $ext against ffmpeg: this ffmpeg has no libopenmpt"
    fi
done
# a seek lands on the sample asked, not on the row libopenmpt seeks to: the rest is the straight render from there
"$NAGAUDIO" decode tracker.it -f f32 -o tracker_whole.raw > /dev/null
"$NAGAUDIO" decode tracker.it -f f32 -o tracker_seek.raw --seek 5030 > /dev/null
"$NAGAUDIO" decode tracker.xm -f f32 -o tracker_xm.raw > /dev/null
"$NAGAUDIO" decode tracker.xm -f f32 -o tracker_xm_seek.raw --seek 5000 --seek-after 48000 > /dev/null
python3 - <<'PY' || failures=$((failures + 1))
import struct, sys
def frames(name):
    data = open(name, "rb").read()
    return struct.unpack("<%df" % (len(data) // 4), data)
ok = True
whole, seek = frames("tracker_whole.raw"), frames("tracker_seek.raw")
start = 5030 * 48000 // 1000 * 2
rest = whole[start:]
worst = max(abs(a - b) for a, b in zip(rest, seek)) if len(rest) == len(seek) else float("inf")
print("%s seek at open: %d frames after 5.03 s, worst difference %.1e" % ("ok  " if worst < 1e-4 else "FAIL", len(seek) // 2, worst))
ok = ok and worst < 1e-4
whole, seek = frames("tracker_xm.raw"), frames("tracker_xm_seek.raw")
rest = whole[5000 * 48000 // 1000 * 2:]
tail = seek[len(seek) - len(rest):]
worst = max(abs(a - b) for a, b in zip(rest, tail))
print("%s seek while playing: %d frames after 5 s, worst difference %.1e" % ("ok  " if worst < 1e-4 else "FAIL", len(rest) // 2, worst))
sys.exit(0 if ok and worst < 1e-4 else 1)
PY

echo "== S-803, S-1001: the library's probe and the waveform"
# the composer, as each format keeps it (ID3 TCOM, Vorbis COMPOSER, MP4 ©wrt), in Turkish and quoted
for spec in "flac:-c:a flac" "mp3:-c:a libmp3lame" "m4a:-c:a aac" "opus:-c:a libopus"; do
    ext=${spec%%:*}
    codec=${spec#*:}
    # shellcheck disable=SC2086
    ffmpeg -v error -y -f lavfi -i "sine=f=440:r=48000:d=1" -metadata composer='Hammâmîzâde İsmâil Dede Efendi "Dede"' $codec "composer.$ext"
    check "$ext composer" 'Hammâmîzâde İsmâil Dede Efendi "Dede"' "$("$NAGAUDIO" info "composer.$ext" | json 'd.get("composer")')"
done
check "no composer" "None" "$("$NAGAUDIO" info flac16.flac | json 'd.get("composer")')"
# the waveform: each bucket's peak and RMS over both channels, as the decode's samples give them
"$NAGAUDIO" wave flac16.flac --points 50 > wave.json
python3 - <<'PY' || failures=$((failures + 1))
import json, math, struct, sys
w = json.load(open("wave.json"))
data = open("plain.raw", "rb").read()
x = struct.unpack("<%dd" % (len(data) // 8), data)
C, points = 2, w["points"]
total = len(x) // C  # 4 s at 44.1 kHz: the length the file says
peak, squares, count = [0.0] * points, [0.0] * points, [0] * points
for f in range(total):
    b = min(f * points // total, points - 1)
    for c in range(C):
        v = x[f * C + c]
        peak[b] = max(peak[b], min(abs(v), 1.0))
        squares[b] += v * v
        count[b] += 1
rms = [math.sqrt(squares[b] / count[b]) for b in range(points)]
worst = max(max(abs(w["peak"][b] - peak[b]), abs(w["rms"][b] - rms[b])) for b in range(points))
ok = w["filled"] == points and worst < 1e-6
print("%s waveform of %d buckets, worst error %.1e" % ("ok  " if ok else "FAIL", points, worst))
sys.exit(0 if ok else 1)
PY

echo "== S-1003: embedded lyrics"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a flac -metadata LYRICS='[00:01.00]Birinci satır
[00:01.50]İkinci "satır"' lyrics.flac
check "flac LYRICS, escaped" '{"plain":"[00:01.00]Birinci satır\n[00:01.50]İkinci \"satır\""}' "$("$NAGAUDIO" lyrics lyrics.flac)"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a libmp3lame lyrics.mp3
python3 - <<'PY'
from mutagen.id3 import ID3, SYLT, USLT, Encoding
tags = ID3()
tags.add(USLT(encoding=Encoding.UTF8, lang="tur", desc="", text="Düz söz\nikinci"))
tags.add(SYLT(encoding=Encoding.UTF8, lang="tur", format=2, type=1, text=[("Gece", 0), ("Yolcusu", 1200), ("şarkı", 1800)]))
tags.save("lyrics.mp3")
PY
check "mp3 USLT and SYLT" '{"plain":"Düz söz\nikinci","synced":[[0,"Gece"],[1200,"Yolcusu"],[1800,"şarkı"]]}' "$("$NAGAUDIO" lyrics lyrics.mp3)"
# ffmpeg writes "lyrics" to ID3v2 as a TXXX called USLT
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a libmp3lame -metadata lyrics="ffmpeg'in sözü" lyrics_txxx.mp3
check "mp3 TXXX:USLT" '{"plain":"ffmpeg'"'"'in sözü"}' "$("$NAGAUDIO" lyrics lyrics_txxx.mp3)"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a aac -metadata lyrics="MP4 sözü" lyrics.m4a
check "m4a ©lyr" '{"plain":"MP4 sözü"}' "$("$NAGAUDIO" lyrics lyrics.m4a)"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=48000:d=1" -c:a libopus -metadata LYRICS="Opus sözü" lyrics.opus
check "opus LYRICS" '{"plain":"Opus sözü"}' "$("$NAGAUDIO" lyrics lyrics.opus)"
check "no lyrics" '{}' "$("$NAGAUDIO" lyrics flac16.flac)"

echo "== S-1005: lyrics written"
LRC='[00:01.00]Birinci satır
[00:01.50]İkinci "satır" 🎵'
printf '%s' "$LRC" > lrc.txt
printf '%s' 'Düz söz
ikinci' > plain.txt
printf '%s' '[[0,"Gece"],[1200,"Yolcusu\n"],[1800,"\ud83c\udfb5 şarkı"]]' > synced.json
EXPECT_LRC='{"plain":"[00:01.00]Birinci satır\n[00:01.50]İkinci \"satır\" 🎵"}'
for ext_codec in "flac:flac" "m4a:aac" "opus:libopus" "ogg:libvorbis"; do
    ext=${ext_codec%%:*}
    codec=${ext_codec#*:}
    rate=44100; [ "$ext" = opus ] && rate=48000
    ffmpeg -v error -y -f lavfi -i "sine=f=440:r=$rate:d=1" -c:a "$codec" -metadata title="Başlık" -metadata LYRICS="eski söz" "wl.$ext"
    audio_before=$(ffmpeg -v error -i "wl.$ext" -map 0:a -f md5 - 2>&1)
    "$NAGAUDIO" setlyrics "wl.$ext" --text lrc.txt >/dev/null
    check "$ext LRC written and read back" "$EXPECT_LRC" "$("$NAGAUDIO" lyrics "wl.$ext")"
    check "$ext title untouched" "Başlık" "$("$NAGAUDIO" prop "wl.$ext" title)"
    check "$ext audio untouched" "$audio_before" "$(ffmpeg -v error -i "wl.$ext" -map 0:a -f md5 - 2>&1)"
    "$NAGAUDIO" setlyrics "wl.$ext" >/dev/null
    check "$ext lyrics removed" '{}' "$("$NAGAUDIO" lyrics "wl.$ext")"
done
# MP3: USLT and SYLT, the old ones replaced, the other frames kept
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a libmp3lame -metadata title="Başlık" wl.mp3
python3 - <<'PY2'
from mutagen.id3 import ID3, SYLT, USLT, Encoding
tags = ID3("wl.mp3")
tags.add(USLT(encoding=Encoding.UTF8, lang="tur", desc="", text="eski söz"))
tags.add(SYLT(encoding=Encoding.UTF8, lang="tur", format=2, type=1, text=[("eski", 0)]))
tags.save("wl.mp3")
PY2
audio_before=$(ffmpeg -v error -i wl.mp3 -map 0:a -f md5 - 2>&1)
"$NAGAUDIO" setlyrics wl.mp3 --text plain.txt --synced synced.json >/dev/null
check "mp3 USLT and SYLT written" '{"plain":"Düz söz\nikinci","synced":[[0,"Gece"],[1200,"Yolcusu\n"],[1800,"🎵 şarkı"]]}' "$("$NAGAUDIO" lyrics wl.mp3)"
check "mp3 title untouched" "Başlık" "$("$NAGAUDIO" prop wl.mp3 title)"
python3 - <<'PY2'
from mutagen.id3 import ID3
tags = ID3("wl.mp3")
sylt = tags.getall("SYLT")
uslt = tags.getall("USLT")
ok = len(sylt) == 1 and len(uslt) == 1 and sylt[0].format == 2 and sylt[0].type == 1 and uslt[0].text == "Düz söz\nikinci"
print("%s one SYLT in milliseconds and one USLT" % ("ok  " if ok else "FAIL"))
raise SystemExit(0 if ok else 1)
PY2
check "mp3 audio untouched" "$audio_before" "$(ffmpeg -v error -i wl.mp3 -map 0:a -f md5 - 2>&1)"
"$NAGAUDIO" setlyrics wl.mp3 --text plain.txt >/dev/null
check "mp3 SYLT removed, USLT kept" '{"plain":"Düz söz\nikinci"}' "$("$NAGAUDIO" lyrics wl.mp3)"
"$NAGAUDIO" setlyrics wl.mp3 >/dev/null
check "mp3 lyrics removed" '{}' "$("$NAGAUDIO" lyrics wl.mp3)"
check "mp3 title still there" "Başlık" "$("$NAGAUDIO" prop wl.mp3 title)"
# a file with no tags at all gets an ID3v2 tag; malformed synced lyrics are refused
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a libmp3lame -write_id3v2 0 -write_xing 0 bare.mp3
"$NAGAUDIO" setlyrics bare.mp3 --text lrc.txt >/dev/null
check "mp3 without tags" "$EXPECT_LRC" "$("$NAGAUDIO" lyrics bare.mp3)"
printf '%s' '[[0,"x"' > broken.json
if "$NAGAUDIO" setlyrics bare.mp3 --synced broken.json >/dev/null 2>&1; then
    check "malformed synced lyrics refused" "refused" "written"
else
    check "malformed synced lyrics refused" "refused" "refused"
fi
check "refused write leaves the lyrics" "$EXPECT_LRC" "$("$NAGAUDIO" lyrics bare.mp3)"

echo "== S-1006: embedded covers"
ffmpeg -v error -y -f lavfi -i "color=c=0x3366cc:s=64x64:d=1" -frames:v 1 front.png
ffmpeg -v error -y -f lavfi -i "color=c=0xcc3333:s=32x32:d=1" -frames:v 1 back.png
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -i front.png -map 0 -map 1 -c:a flac -c:v png \
    -disposition:v attached_pic -metadata MUSICBRAINZ_ALBUMID=0f3ad0d6-1111-4b5e-9c7c-2a4cd1c6c0de cover.flac
"$NAGAUDIO" cover cover.flac cover_flac.out >/dev/null
check "flac PICTURE" "same" "$(cmp -s cover_flac.out front.png && echo same || echo different)"
check "flac MusicBrainz release" "0f3ad0d6-1111-4b5e-9c7c-2a4cd1c6c0de" "$("$NAGAUDIO" prop cover.flac musicbrainz_albumid)"
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -i front.png -map 0 -map 1 -c:a aac -c:v copy -disposition:v attached_pic cover.m4a
"$NAGAUDIO" cover cover.m4a cover_m4a.out >/dev/null
check "m4a covr" "same" "$(cmp -s cover_m4a.out front.png && echo same || echo different)"
# the back cover first, the front cover second: the front is the cover
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=44100:d=1" -c:a libmp3lame cover.mp3
ffmpeg -v error -y -f lavfi -i "sine=f=440:r=48000:d=1" -c:a libopus cover.opus
python3 - <<'PY'
import base64
from mutagen.id3 import ID3, APIC
from mutagen.oggopus import OggOpus
from mutagen.flac import Picture
front = open("front.png", "rb").read()
back = open("back.png", "rb").read()
tags = ID3()
tags.add(APIC(mime="image/png", type=4, desc="arka", data=back))
tags.add(APIC(mime="image/png", type=3, desc="ön", data=front))
tags.save("cover.mp3")
opus = OggOpus("cover.opus")
picture = Picture()
picture.type, picture.mime, picture.data = 3, "image/png", front
opus["METADATA_BLOCK_PICTURE"] = [base64.b64encode(picture.write()).decode("ascii")]
opus.save()
PY
"$NAGAUDIO" cover cover.mp3 cover_mp3.out >/dev/null
check "mp3 APIC, the front of two" "same" "$(cmp -s cover_mp3.out front.png && echo same || echo different)"
"$NAGAUDIO" cover cover.opus cover_opus.out >/dev/null
check "opus METADATA_BLOCK_PICTURE" "same" "$(cmp -s cover_opus.out front.png && echo same || echo different)"
check "no cover" "none" "$("$NAGAUDIO" cover flac16.flac none.out)"
check "no such tag" "" "$("$NAGAUDIO" prop flac16.flac MUSICBRAINZ_ALBUMID)"

echo "== S-808: CUE parts"
# the gapless album's whole (6 s at 44.1 kHz): parts at 0, 2 s (150 CUE frames) and 4 s 1/3 (325), as a CUE sheet cuts it
check "flac parts as the whole" "$(ffmpeg -v error -i whole.flac -f s16le - | md5_of)" "$(engine_md5 s16 "whole.flac#cue=0,150" "whole.flac#cue=150,325" "whole.flac#cue=325" --no-dither)"
# 150 CUE frames = 88 200 samples, 325 = 191 100
check "flac part alone" "$(ffmpeg -v error -i whole.flac -af 'atrim=start_sample=88200:end_sample=191100' -f s16le - | md5_of)" "$(engine_md5 s16 "whole.flac#cue=150,325" --no-dither)"
check "seek within a part" "$(ffmpeg -v error -i whole.flac -af 'atrim=start_sample=132300:end_sample=191100' -f s16le - | md5_of)" "$(engine_md5 s16 "whole.flac#cue=150,325" --no-dither --seek 1000)"
check "part length" "2333" "$("$NAGAUDIO" decode "whole.flac#cue=150,325" -f s16 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["frames"] * 1000 // d["rate"])')"
ffmpeg -v error -y -i whole.flac -c:a libmp3lame -b:a 192k whole.mp3
ffmpeg -v error -y -i whole.flac -c:a aac -b:a 160k whole.m4a
ffmpeg -v error -y -i whole.flac -c:a libopus -b:a 128k whole.opus
check "mp3 parts as the whole" "$(engine_md5 f32 whole.mp3)" "$(engine_md5 f32 "whole.mp3#cue=0,150" "whole.mp3#cue=150,325" "whole.mp3#cue=325")"
# AAC's and Opus's decoders settle after a seek, as ffmpeg's own -ss shows: the parts are the whole within -60 dBFS, at the same length
for f in whole.m4a whole.opus; do
    "$NAGAUDIO" decode "$f" -f f32 -o whole_lossy.raw > /dev/null
    "$NAGAUDIO" decode "$f#cue=0,150" "$f#cue=150,325" "$f#cue=325" -f f32 -o parts_lossy.raw > /dev/null
    check "$f parts as the whole" "same" "$(python3 - <<'PY'
import struct
def samples(name):
    data = open(name, "rb").read()
    return struct.unpack("<%df" % (len(data) // 4), data)
w, p = samples("whole_lossy.raw"), samples("parts_lossy.raw")
n = min(len(w), len(p))
worst = max(abs(a - b) for a, b in zip(w, p)) if n else 0
print("same" if len(w) == len(p) and n > 0 and worst <= 1e-3 else "length %d vs %d, worst %g" % (len(w), len(p), worst))
PY
)"
done
# MP3's timestamps start after the trimmed encoder delay: a seek lands on the sample asked all the same
check "mp3 seek exact" "$(ffmpeg -v error -i whole.mp3 -af 'atrim=start_sample=88200' -f f32le - | md5_of)" "$(engine_md5 f32 whole.mp3 --seek 2000)"

if [ "$failures" -gt 0 ]; then
    echo "$failures audio test(s) failed"
    exit 1
fi
echo "audio tests passed"
