#!/usr/bin/env python3
"""Checks the generated test media against manifest.json and writes an index.

Usage: verify.py <media-dir> [--allow-missing id,id]

For every generated sample: the files exist, streams, durations, tags,
side data (HDR10, HDR10+, Dolby Vision), chapters, subtitle flags, text
encodings and ID3 frames are what the manifest promises. Writes
index.json (sha256 and size of every file, the result of every check) and
index.md (a table for the job summary and the README). Exits 1 when a
generated sample does not match.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))


def probe(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-print_format', 'json', '-show_streams', '-show_format', '-show_chapters', path],
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:300])
    return json.loads(out.stdout)


def frame_side_data(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-read_intervals', '%+#2', '-select_streams', 'v:0', '-show_frames',
                          '-show_entries', 'frame=side_data_list', '-print_format', 'json', path], capture_output=True, text=True)
    kinds = set()
    for frame in json.loads(out.stdout or '{}').get('frames', []):
        for side in frame.get('side_data_list', []):
            kinds.add(side.get('side_data_type'))
    return kinds


def lookup(obj, dotted):
    for part in dotted.split('.'):
        if not isinstance(obj, dict):
            return None
        # tags are case-insensitive (Vorbis comments, Matroska)
        match = next((k for k in obj if k.lower() == part.lower()), None)
        obj = obj.get(match) if match is not None else None
    return obj


def _fraction(value):
    num, _, den = str(value).partition('/')
    return float(num) / float(den or 1)


def same(expected, actual):
    if isinstance(expected, str) and '/' in expected and actual is not None and '/' in str(actual):
        try:  # frame rates: 24000/1001 and 2997/125 are the same rate
            return abs(_fraction(expected) - _fraction(actual)) < 1e-3
        except (ValueError, ZeroDivisionError):
            pass  # not a number: a MIME type such as font/ttf, compared as text below
    if isinstance(expected, list) and len(expected) == 2 and all(isinstance(v, (int, float)) for v in expected):
        try:
            return expected[0] <= float(actual) <= expected[1]
        except (TypeError, ValueError):
            return False
    return actual is not None and str(actual) == str(expected)


def stream_matches(expected, stream):
    return all(same(v, lookup(stream, k)) for k, v in expected.items())


def check_streams(expected_streams, info, problems, label=''):
    for want in expected_streams:
        if not any(stream_matches(want, s) for s in info.get('streams', [])):
            have = [{k: lookup(s, k) for k in want} for s in info.get('streams', [])]
            problems.append(f'{label}no stream like {want}; have {have}')


def check_id3(path, expected, problems):
    from mutagen.id3 import ID3
    tags = ID3(path)
    for frame, count in expected.items():
        found = len(tags.getall(frame))
        if found != count:
            problems.append(f'{os.path.basename(path)}: {found} {frame} frames, expected {count}')


def check_text(root, spec, problems):
    path = os.path.join(root, spec['file'])
    raw = open(path, 'rb').read()
    try:
        text = raw.decode(spec['encoding'])
    except UnicodeDecodeError as e:
        problems.append(f'{spec["file"]}: not {spec["encoding"]} ({e})')
        return
    if spec['encoding'] == 'utf-8-sig' and not raw.startswith(b'\xef\xbb\xbf'):
        problems.append(f'{spec["file"]}: UTF-8 BOM missing')
    for needle in spec.get('contains', []):
        if needle not in text:
            problems.append(f'{spec["file"]}: "{needle}" not found')
    if spec.get('crlf') and b'\r\n' not in raw:
        problems.append(f'{spec["file"]}: expected CRLF line ends')


def check_dovi(path, profile, problems):
    tool = shutil.which('dovi_tool')
    if not tool:
        problems.append('dovi_tool not on PATH')
        return
    with tempfile.TemporaryDirectory() as tmp:
        rpu = os.path.join(tmp, 'rpu.bin')
        r = subprocess.run([tool, 'extract-rpu', '-i', path, '-o', rpu], capture_output=True, text=True)
        if r.returncode != 0:
            problems.append('dovi_tool extract-rpu failed: ' + r.stderr.strip()[:200])
            return
        info = subprocess.run([tool, 'info', '-s', '-i', rpu], capture_output=True, text=True).stdout
        if f'Profile: {profile}' not in info:
            problems.append(f'RPU is not profile {profile}: ' + ' '.join(info.split())[:200])


def check_hdr10plus(path, frames, problems):
    tool = shutil.which('hdr10plus_tool')
    if not tool:
        problems.append('hdr10plus_tool not on PATH')
        return
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'meta.json')
        r = subprocess.run([tool, 'extract', '-i', path, '-o', out], capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(out):
            problems.append('hdr10plus_tool extract failed: ' + (r.stderr or r.stdout).strip()[:200])
            return
        count = len(json.load(open(out)).get('SceneInfo', []))
        if count != frames:
            problems.append(f'HDR10+ metadata for {count} frames, expected {frames}')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def verify_sample(root, sample):
    problems = []
    files = sample.get('files') or [sample['file']]
    for rel in files:
        if not os.path.exists(os.path.join(root, rel)):
            problems.append(f'{rel}: missing')
    if problems:
        return files, problems
    expect = sample.get('expect', {})
    media = [f for f in files if not f.endswith(('.srt', '.ass', '.lrc', '.m3u8', '.pls', '.cue', '.xml', '.json'))]
    probed = {}
    for rel in media:
        try:
            probed[rel] = probe(os.path.join(root, rel))
        except RuntimeError as e:
            problems.append(f'{rel}: ffprobe failed: {e}')
    shared = {k: v for k, v in expect.items() if k in ('duration', 'streams', 'tags', 'chapters', 'bit_rate_min')}
    for rel, info in probed.items():
        label = f'{rel}: ' if len(probed) > 1 else ''
        if 'duration' in shared and not same(shared['duration'], lookup(info, 'format.duration')):
            problems.append(f'{label}duration {lookup(info, "format.duration")} outside {shared["duration"]}')
        if 'streams' in shared:
            check_streams(shared['streams'], info, problems, label)
        if 'bit_rate_min' in shared and float(lookup(info, 'format.bit_rate') or 0) < shared['bit_rate_min']:
            problems.append(f'{label}bit rate {lookup(info, "format.bit_rate")} below {shared["bit_rate_min"]}')
        if 'chapters' in shared and len(info.get('chapters', [])) != shared['chapters']:
            problems.append(f'{label}{len(info.get("chapters", []))} chapters, expected {shared["chapters"]}')
        if 'tags' in shared and 'tags_file' not in expect:
            for key, value in shared['tags'].items():
                actual = lookup(info, 'format.tags.' + key)
                if actual is None or value not in actual:
                    problems.append(f'{label}tag {key}={actual!r}, expected {value!r}')
    for rel, spec in expect.get('per_file', {}).items():
        info = probed.get(rel)
        if info is None:
            continue
        stream_keys = {k: v for k, v in spec.items() if k not in ('duration', 'id3') and not k.startswith('tags.')}
        if stream_keys:
            check_streams([stream_keys], info, problems, f'{rel}: ')
        if 'duration' in spec and not same(spec['duration'], lookup(info, 'format.duration')):
            problems.append(f'{rel}: duration {lookup(info, "format.duration")} outside {spec["duration"]}')
        for key, value in spec.items():
            if key.startswith('tags.') and not same(value, lookup(info, 'format.' + key)):
                problems.append(f'{rel}: {key}={lookup(info, "format." + key)!r}, expected {value!r}')
        if 'id3' in spec:
            check_id3(os.path.join(root, rel), spec['id3'], problems)
    if 'tags_file' in expect:
        info = probed.get(expect['tags_file']) or probe(os.path.join(root, expect['tags_file']))
        for key, value in expect.get('tags', {}).items():
            actual = lookup(info, 'format.tags.' + key)
            if actual is None or value not in actual:
                problems.append(f'{expect["tags_file"]}: tag {key} does not contain {value!r}')
    main = os.path.join(root, files[0])
    if 'stream_side_data' in expect and files[0] in probed:
        for want in expect['stream_side_data']:
            found = any(all(same(v, side.get(k)) for k, v in want.items())
                        for s in probed[files[0]].get('streams', []) for side in s.get('side_data_list', []))
            if not found:
                have = [s.get('side_data_list') for s in probed[files[0]].get('streams', [])]
                problems.append(f'no stream side data like {want}; have {have}')
    if 'frame_side_data' in expect:
        kinds = frame_side_data(main)
        for kind in expect['frame_side_data']:
            if kind not in kinds:
                problems.append(f'frame side data "{kind}" missing; have {sorted(k for k in kinds if k)}')
    if 'dovi_profile' in expect:
        check_dovi(main, expect['dovi_profile'], problems)
    if 'hdr10plus_frames' in expect:
        check_hdr10plus(main, expect['hdr10plus_frames'], problems)
    for spec in expect.get('text', []):
        check_text(root, spec, problems)
    if 'xml' in expect:
        try:
            ET.parse(os.path.join(root, expect['xml']))
        except ET.ParseError as e:
            problems.append(f'{expect["xml"]}: {e}')
    if 'json' in expect:
        try:
            json.load(open(os.path.join(root, expect['json']), encoding='utf-8'))
        except ValueError as e:
            problems.append(f'{expect["json"]}: {e}')
    return files, problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('root')
    ap.add_argument('--allow-missing', default='', help='sample ids that may be absent (skipped heavy samples)')
    args = ap.parse_args()
    manifest = json.load(open(os.path.join(HERE, 'manifest.json'), encoding='utf-8'))
    allowed = set(filter(None, args.allow_missing.split(',')))
    results, failed = [], []
    for sample in manifest['samples']:
        entry = {'id': sample['id'], 'kind': sample['kind'], 'tests': sample['tests'], 'stories': sample.get('stories', [])}
        if sample['kind'] == 'manual':
            entry['how'] = sample['how']
            results.append(entry)
            continue
        files, problems = verify_sample(args.root, sample)
        if sample['id'] in allowed and all(p.endswith('missing') for p in problems):
            entry['skipped'] = True
            problems = []
        entry['files'] = []
        for rel in files:
            path = os.path.join(args.root, rel)
            if os.path.exists(path):
                entry['files'].append({'path': rel, 'bytes': os.path.getsize(path), 'sha256': sha256(path)})
        entry['ok'] = not problems
        entry['problems'] = problems
        results.append(entry)
        print(('OK   ' if not problems else 'FAIL ') + sample['id'] + ('' if not problems else ''.join('\n     - ' + p for p in problems)))
        if problems:
            failed.append(sample['id'])
    with open(os.path.join(args.root, 'index.json'), 'w', encoding='utf-8') as fh:
        json.dump({'version': manifest['version'], 'samples': results}, fh, ensure_ascii=False, indent=1)
    lines = ['| Örnek | Dosyalar | Ne test eder | Story | Durum |', '| --- | --- | --- | --- | --- |']
    for r in results:
        if r['kind'] == 'manual':
            state, files = 'elle eklenir', r['how']
        else:
            state = 'atlandı' if r.get('skipped') else ('doğrulandı' if r['ok'] else 'HATA: ' + '; '.join(r['problems'])[:200])
            files = '<br>'.join(f'`{f["path"]}` ({f["bytes"] / 1e6:.1f} MB)' for f in r['files'])
        lines.append(f'| {r["id"]} | {files} | {r["tests"]} | {", ".join(r["stories"])} | {state} |')
    with open(os.path.join(args.root, 'index.md'), 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines) + '\n')
    if failed:
        print('failed: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    main()
