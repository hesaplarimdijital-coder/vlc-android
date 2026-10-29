#!/usr/bin/env bash
# Host tests of the native lab code (CI): needs `build.sh host <prefix>` first, plus ffmpeg,
# ffprobe and mkvmerge on PATH.
#   - S-802: the media probe reads audio codecs, languages and flags, subtitles and chapters, and
#     leaves a film's video stream out (Nağmedar 1.0 plays sound only); a missing file is an
#     error, not a crash.
#   - S-901: the music engine (test-audio.sh).
set -euo pipefail
trap 'echo "test-host.sh: line $LINENO failed: $BASH_COMMAND"' ERR
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
PREFIX=$(realpath -m "${1:?prefix}")
WORK=${NAGMEDAR_NATIVE_WORK:-$ROOT/native/.work}
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

echo "== S-802: media probe"
ffmpeg -v error -f lavfi -i "sine=f=440:d=11" -c:a aac "$T/audio.m4a"
printf '1\n00:00:01,000 --> 00:00:03,000\nAltyazı korunmalı\n' > "$T/sub.srt"
cat > "$T/chapters.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Chapters><EditionEntry>
<ChapterAtom><ChapterTimeStart>00:00:00.000</ChapterTimeStart><ChapterDisplay><ChapterString>Bir</ChapterString></ChapterDisplay></ChapterAtom>
<ChapterAtom><ChapterTimeStart>00:00:05.000</ChapterTimeStart><ChapterDisplay><ChapterString>İki</ChapterString></ChapterDisplay></ChapterAtom>
</EditionEntry></Chapters>
XML
# an audio book's shape: AAC with a Turkish SRT and two chapters
mkvmerge -q -o "$T/chapters.mka" "$T/audio.m4a" --language 0:tur "$T/sub.srt" --chapters "$T/chapters.xml"
# E-AC-3, DTS, a TrueHD commentary and 96 kHz FLAC with two subtitle tracks, in a film (whose
# picture the probe leaves out) and alone
printf '1\n00:00:00,500 --> 00:00:01,500\nZorunlu\n' > "$T/forced.srt"
ffmpeg -v error -f lavfi -i "testsrc2=size=1920x1080:rate=25" -f lavfi -i "sine=f=440:d=2:sample_rate=48000" \
    -f lavfi -i "sine=f=660:d=2:sample_rate=96000" -t 2 \
    -filter_complex "[1:a]pan=5.1|c0=c0|c1=c0|c2=c0|c3=c0|c4=c0|c5=c0,asplit=3[a0][a1][a2]" \
    -map 0:v -map "[a0]" -map "[a1]" -map "[a2]" -map 2:a -c:v libx264 -pix_fmt yuv420p -preset ultrafast \
    -c:a:0 eac3 -c:a:1 dca -c:a:2 truehd -c:a:3 flac -strict -2 \
    -metadata:s:a:0 language=tur -metadata:s:a:1 language=eng -metadata:s:a:2 language=eng -metadata:s:a:2 title=Yorum \
    -metadata:s:a:3 language=jpn -disposition:a:0 default -disposition:a:1 0 -disposition:a:2 comment -disposition:a:3 0 \
    "$T/av.mkv"
mkvmerge -q -o "$T/film.mkv" "$T/av.mkv" --language 0:tur "$T/sub.srt" --language 0:eng --forced-display-flag 0:1 "$T/forced.srt"
mkvmerge -q -o "$T/sound.mka" --no-video "$T/film.mkv"
for name in chapters.mka film.mkv sound.mka; do
    "$PREFIX/bin/mediaprobe" "$T/$name" > "$T/probe-$name.json" 2> /dev/null
done
"$PREFIX/bin/mediaprobe" "$T/missing.mkv" > "$T/probe-missing.json" 2> /dev/null
python3 - "$T" <<'PY'
import json, sys
t = sys.argv[1]
problems = []
def check(ok, what):
    if not ok:
        problems.append(what)
def probe(name):
    p = json.load(open(f'{t}/probe-{name}.json'))
    check(not p['error'], f'{name}: {p["error"]}')
    check('video' not in p, f'{name}: a video stream reported {p.get("video")}')
    return p
p = probe('chapters.mka')
check(p['chapters'] == 2 and p['subtitles'] and p['subtitles'][0]['language'] == 'tur', f'chapters {p["chapters"]} {p["subtitles"]}')
check([a['codec'] for a in p['audio']] == ['aac'] and abs(p['duration_ms'] - 11000) < 500, f'chapters audio {p["audio"]} {p["duration_ms"]}')
for name in ('film.mkv', 'sound.mka'):
    p = probe(name)
    audio = p['audio']
    check([a['codec'] for a in audio] == ['eac3', 'dts', 'truehd', 'flac'], f'{name} audio codecs {[a["codec"] for a in audio]}')
    check([a['language'] for a in audio] == ['tur', 'eng', 'eng', 'jpn'], f'{name} audio languages {[a["language"] for a in audio]}')
    check(audio[0]['channels'] == 6 and audio[0]['default'] and not audio[1]['default'], f'{name} audio flags {audio[:2]}')
    check(audio[2]['commentary'] and audio[2]['title'] == 'Yorum', f'{name} commentary {audio[2]}')
    check(audio[3]['sample_rate'] == 96000, f'{name} flac {audio[3]}')
    subs = p['subtitles']
    check([(s['codec'], s['language'], s['forced']) for s in subs] == [('subrip', 'tur', False), ('subrip', 'eng', True)], f'{name} subtitles {subs}')
missing = json.load(open(f'{t}/probe-missing.json'))
check(missing['error'].startswith('open:') and missing['version'] == 1, f'missing {missing}')
if problems:
    print('::error title=S-802 probe::' + '; '.join(problems))
    sys.exit(1)
print('ok: media probe (audio, subtitles, chapters, no video, errors)')
PY

echo "== S-901: music engine"
# the test files go with the lab's, for the ci/debug-native branch when something fails
NAGMEDAR_TEST_DIR="${NAGMEDAR_TEST_DIR:-$T}" "$HERE/test-audio.sh" "$PREFIX" 2>&1 | tee "$T/audio-tests.log" || {
    echo "::error title=S-901 ses motoru::$(grep -E "FAIL|failed" "$T/audio-tests.log" | annotate)"
    exit 1
}
