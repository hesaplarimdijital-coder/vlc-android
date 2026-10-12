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
#   - DSD (S-908): a modulated sine as DSF and DFF (dsd_gen.py) decodes to PCM as ffmpeg does,
#     the same from both containers, at its level; as DoP the words carry the file's bits under
#     the 0x05/0xFA markers, the same from both
#   - the formats (S-911): WavPack, TTA, ALAC, WMA, Vorbis, AIFF, WAV float, CAF and Matroska
#     audio, each what ffmpeg decodes (the engine's own FFmpeg build must carry them)
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
check "dsf to pcm as ffmpeg" "$(ffmpeg -v error -i sine.dsf -f f32le - | md5_of)" "$(engine_md5 f32 sine.dsf 2> /dev/null)"
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

if [ "$failures" -gt 0 ]; then
    echo "$failures audio test(s) failed"
    exit 1
fi
echo "audio tests passed"
