#!/usr/bin/env python3
"""Builds Nağmedar's test media set (S-1401) from scratch.

Everything is synthesised (tones, our own cover and lyrics), so the set
carries no third-party content and can be published with the repository.
Samples that cannot be synthesised (DTS-HD MA, TrueHD Atmos) are listed in
manifest.json as "manual". 1.0.3: Nağmedar plays audio only since 1.0, so the
video, Dolby Vision, HDR and subtitle samples (and what only they needed:
x265, mkvmerge, dovi_tool, hdr10plus_tool, the PGS writer) are gone.

Usage: generate.py <out-dir> [--only id,id]

Needs ffmpeg/ffprobe (with libmp3lame and libopus) on PATH and Python with
Pillow and mutagen.
"""
import argparse
import json
import os
import subprocess
import sys
import textwrap

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'tools'))

from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import dsf  # noqa: E402
import tracker  # noqa: E402

REPO = os.path.dirname(HERE)
FONT_DIR = os.path.join(REPO, 'application', 'nagmedar-ui', 'src', 'main', 'res', 'font')
FONT = os.path.join(FONT_DIR, 'ibm_plex_sans_semibold.ttf')

ALBUM = {'artist': 'Nağmedar Test Orkestrası', 'album': 'Ölçüm Kayıtları', 'date': '2026', 'genre': 'Test'}


def log(msg):
    print(msg, flush=True)


def run(*cmd, **kw):
    cmd = [str(c) for c in cmd]
    log('  $ ' + ' '.join(cmd if len(' '.join(cmd)) < 400 else cmd[:12] + ['…']))
    result = subprocess.run(cmd, capture_output=True, text=True, errors='replace', **kw)
    if result.stdout:
        print(result.stdout, end='', flush=True)
    if result.stderr:
        print(result.stderr, end='', file=sys.stderr, flush=True)
    if result.returncode != 0:
        raise RuntimeError(f'{os.path.basename(cmd[0])} exited with {result.returncode}: '
                           + (result.stderr or result.stdout or '').strip()[-1500:])
    return result


def ffmpeg(*args):
    return run('ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', *args)



class Builder:
    def __init__(self, out, work):
        self.out = out
        self.work = work
        os.makedirs(out, exist_ok=True)
        os.makedirs(work, exist_ok=True)

    def path(self, rel):
        p = os.path.join(self.out, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        return p

    def tmp(self, name):
        return os.path.join(self.work, name)

    # ---------------------------------------------------------------- shared inputs

    def cover(self):
        """Square album cover (PNG), drawn here so the set has no third-party art."""
        p = self.tmp('cover.png')
        if os.path.exists(p):
            return p
        img = Image.new('RGB', (1000, 1000), (17, 20, 24))
        d = ImageDraw.Draw(img)
        for i in range(0, 1000, 40):
            d.line([(i, 0), (1000, 1000 - i)], fill=(38, 44, 52), width=2)
        d.ellipse([250, 250, 750, 750], outline=(138, 176, 255), width=24)
        d.ellipse([400, 400, 600, 600], fill=(255, 122, 110))
        font = ImageFont.truetype(FONT, 72)
        d.text((500, 860), 'Ölçüm Kayıtları', font=font, fill=(230, 233, 237), anchor='mm')
        img.save(p)
        return p

    def audio_wav(self, name, expr, rate, seconds, layout=None):
        p = self.tmp(name + '.wav')
        if not os.path.exists(p):
            src = f"aevalsrc='{expr}':s={rate}:d={seconds}"
            if layout:
                src += f':c={layout}'
            ffmpeg('-f', 'lavfi', '-i', src, '-c:a', 'pcm_s32le', p)
        return p

    def surround(self, rate=48000, seconds=10, layout='5.1'):
        """Each channel its own tone (channel identification): 220, 330, 440 … Hz."""
        n = {'5.1': 6, '7.1': 8}[layout]
        expr = '|'.join(f'0.3*sin(2*PI*{220 + 110 * i}*t)' for i in range(n))
        return self.audio_wav(f'surround_{layout}_{rate}', expr, rate, seconds, layout)

    # ---------------------------------------------------------------- audio

    def tag_args(self, title, track=None, total=None, extra=None):
        args = ['-metadata', f'title={title}']
        for key, value in ALBUM.items():
            args += ['-metadata', f'{key}={value}']
        args += ['-metadata', f'album_artist={ALBUM["artist"]}']
        if track:
            args += ['-metadata', f'track={track}/{total}' if total else f'track={track}']
        for key, value in (extra or {}).items():
            args += ['-metadata', f'{key}={value}']
        return args

    def flac(self):
        cover = self.cover()
        specs = [
            ('audio/flac_16bit_44100.flac', 44100, 16, '16 bit · 44,1 kHz'),
            ('audio/flac_24bit_96000.flac', 96000, 24, '24 bit · 96 kHz'),
            ('audio/flac_24bit_192000.flac', 192000, 24, '24 bit · 192 kHz'),
        ]
        for rel, rate, bits, title in specs:
            # left 1 kHz, right 440 Hz; from 15 s the right channel carries only a -110 dBFS 3 kHz tone,
            # which exists only above 16 bits (a null test shows truncation or dither)
            expr = ('0.5*sin(2*PI*1000*t)|'
                    'if(lt(t,15),0.5*sin(2*PI*440*t),0.00000316*sin(2*PI*3000*t))')
            wav = self.audio_wav(f'flac_src_{rate}', expr, rate, 20)
            sample = ['-sample_fmt', 's16'] if bits == 16 else ['-sample_fmt', 's32', '-bits_per_raw_sample', '24']
            ffmpeg('-i', wav, '-i', cover, '-map', '0:a', '-map', '1:v', '-c:a', 'flac', *sample,
                   '-c:v', 'png', '-disposition:v', 'attached_pic', '-metadata:s:v', 'comment=Cover (front)',
                   *self.tag_args(f'FLAC {title}'), self.path(rel))
        wav = self.surround(rate=96000, seconds=10)
        ffmpeg('-i', wav, '-c:a', 'flac', '-sample_fmt', 's32', '-bits_per_raw_sample', '24',
               *self.tag_args('FLAC 5.1 kanal tanıma (220 … 770 Hz)'), self.path('audio/flac_24bit_96000_5.1.flac'))

    def lossy_and_lossless(self):
        cover = self.cover()
        wav48 = self.audio_wav('st_48000', '0.5*sin(2*PI*1000*t)|0.5*sin(2*PI*440*t)', 48000, 20)
        wav44 = self.audio_wav('st_44100', '0.5*sin(2*PI*1000*t)|0.5*sin(2*PI*440*t)', 44100, 20)
        ffmpeg('-i', wav48, '-i', cover, '-map', '0:a', '-map', '1:v', '-c:a', 'alac', '-sample_fmt', 's32p',
               '-c:v', 'png', '-disposition:v', 'attached_pic', *self.tag_args('ALAC 24 bit · 48 kHz'),
               self.path('audio/alac_24bit_48000.m4a'))
        ffmpeg('-i', wav44, '-i', cover, '-map', '0:a', '-map', '1:v', '-c:a', 'aac', '-b:a', '256k',
               '-c:v', 'png', '-disposition:v', 'attached_pic', *self.tag_args('AAC-LC 256 kbit/s'),
               self.path('audio/aac_lc_256k_44100.m4a'))
        ffmpeg('-i', wav48, '-c:a', 'libopus', '-b:a', '160k', *self.tag_args('Opus 160 kbit/s'),
               self.path('audio/opus_160k_48000.opus'))
        mp3 = self.path('audio/mp3_320k_lyrics.mp3')
        ffmpeg('-i', wav44, '-i', cover, '-map', '0:a', '-map', '1:v', '-c:a', 'libmp3lame', '-b:a', '320k',
               '-c:v', 'mjpeg', '-disposition:v', 'attached_pic', '-id3v2_version', '3',
               *self.tag_args('MP3 320 kbit/s (sözlerle)'), mp3)
        self.add_mp3_lyrics(mp3)

    def surround_codecs(self):
        wav = self.surround(seconds=10)
        ffmpeg('-i', wav, '-c:a', 'ac3', '-b:a', '640k', self.path('audio/ac3_5.1_640k.mka'))
        ffmpeg('-i', wav, '-c:a', 'eac3', '-b:a', '768k', self.path('audio/eac3_5.1_768k.mka'))
        ffmpeg('-i', wav, '-strict', '-2', '-c:a', 'truehd', '-sample_fmt', 's32p', self.path('audio/truehd_5.1_48000.mka'))
        ffmpeg('-i', wav, '-strict', '-2', '-c:a', 'dca', '-b:a', '1509k', self.path('audio/dts_core_5.1_48000.mka'))

    def dsd(self):
        seconds = 10
        left = dsf.tone(1000, seconds)
        right = dsf.tone(440, seconds)
        dsf.write_dsf(self.path('audio/dsd64_stereo.dsf'), [left, right])

    LYRICS = [(1.0, 'Sabaha kadar yanar ışıklar'), (4.0, 'Gece mavisi bir şarkı çalar'),
              (8.0, 'Ölçülü, kayıpsız, bit bit aynı'), (12.0, 'Nağmedar’da her söz yerinde'),
              (16.0, '—'), (17.0, 'Son satır, on yedinci saniyede')]

    def lrc(self, words=False):
        lines = []
        for start, text in self.LYRICS:
            stamp = '[%02d:%05.2f]' % (start // 60, start % 60)
            if words and text != '—':
                parts = text.split(' ')
                step = 0.5
                text = ' '.join('<%02d:%05.2f>%s' % ((start + i * step) // 60, (start + i * step) % 60, w)
                                for i, w in enumerate(parts))
            lines.append(stamp + text)
        header = '[ar:Nağmedar Test Orkestrası]\n[al:Ölçüm Kayıtları]\n[ti:Sözlü test]\n[length:00:20.00]\n'
        return header + '\n'.join(lines) + '\n'

    def add_mp3_lyrics(self, path):
        from mutagen.id3 import ID3, SYLT, USLT
        tags = ID3(path)
        tags.add(USLT(encoding=3, lang='tur', desc='', text='\n'.join(t for _, t in self.LYRICS)))
        tags.add(SYLT(encoding=3, lang='tur', format=2, type=1, desc='',
                      text=[(t, int(s * 1000)) for s, t in self.LYRICS]))
        tags.save(v2_version=3)

    def lyrics(self):
        wav = self.audio_wav('lyrics_src', '0.4*sin(2*PI*523.25*t)|0.4*sin(2*PI*659.25*t)', 44100, 20)
        flac = self.path('audio/lyrics/sozlu_test.flac')
        ffmpeg('-i', wav, '-c:a', 'flac', '-sample_fmt', 's16', *self.tag_args('Sözlü test'),
               '-metadata', 'LYRICS=' + self.lrc(), flac)
        with open(self.path('audio/lyrics/sozlu_test.lrc'), 'w', encoding='utf-8') as fh:
            fh.write(self.lrc(words=True))

    def gapless(self):
        """A 30 s phase-continuous glide cut into three tracks at exact sample boundaries."""
        rate, total = 44100, 30
        expr = '0.5*sin(2*PI*(200*t+(2000-200)*t*t/(2*30)))'
        wav = self.audio_wav('glide', expr + '|' + expr, rate, total)
        names = ['Birinci', 'İkinci', 'Üçüncü']
        playlist = ['#EXTM3U']
        for i, name in enumerate(names):
            start, end = i * 10 * rate, (i + 1) * 10 * rate
            part = self.tmp(f'glide_{i}.wav')
            ffmpeg('-i', wav, '-af', f'atrim=start_sample={start}:end_sample={end},asetpts=PTS-STARTPTS', '-c:a', 'pcm_s16le', part)
            tags = self.tag_args(f'{i + 1}. {name} bölüm', i + 1, 3, {'album': 'Boşluksuz Albüm'})
            stem = f'audio/gapless/{i + 1:02d} {name} bölüm'
            ffmpeg('-i', part, '-c:a', 'flac', *tags, self.path(stem + '.flac'))
            ffmpeg('-i', part, '-c:a', 'libmp3lame', '-b:a', '256k', *tags, self.path(stem + '.mp3'))
            ffmpeg('-i', part, '-c:a', 'aac', '-b:a', '256k', *tags, self.path(stem + '.m4a'))
            ffmpeg('-i', part, '-c:a', 'libopus', '-b:a', '160k', *tags, self.path(stem + '.opus'))
            playlist += [f'#EXTINF:10,{ALBUM["artist"]} - {i + 1}. {name} bölüm', os.path.basename(stem) + '.flac']
        with open(self.path('audio/gapless/boşluksuz.m3u8'), 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(playlist) + '\n')

    def cue(self):
        rate = 44100
        expr = '0.5*sin(2*PI*(200*t+(2000-200)*t*t/(2*30)))'
        wav = self.audio_wav('glide', expr + '|' + expr, rate, 30)
        ffmpeg('-i', wav, '-c:a', 'flac', '-sample_fmt', 's16', *self.tag_args('CUE albümü'), self.path('audio/cue/album.flac'))
        artist = ALBUM['artist']
        cue = textwrap.dedent(f'''\
            REM GENRE Test
            REM DATE 2026
            PERFORMER "{artist}"
            TITLE "CUE albümü"
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Giriş"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Orta bölüm"
                INDEX 00 00:09:50
                INDEX 01 00:10:00
              TRACK 03 AUDIO
                TITLE "Çıkış"
                INDEX 01 00:20:00
            ''')
        with open(self.path('audio/cue/album.cue'), 'w', encoding='utf-8-sig') as fh:
            fh.write(cue)
        with open(self.path('audio/cue/album.cp1254.cue'), 'w', encoding='cp1254') as fh:
            fh.write(cue)

    def tracker_modules(self):
        # S-911: the same piece as MOD, XM, S3M and IT, written by tools/tracker.py
        for extension in ('mod', 'xm', 's3m', 'it'):
            tracker.write(self.path(f'audio/tracker/nagmedar.{extension}'))

    # ---------------------------------------------------------------- podcast and radio fixtures

    def podcast(self):
        from mutagen.id3 import CHAP, CTOC, ID3, TIT2, CTOCFlags
        wav = self.audio_wav('podcast', '0.3*sin(2*PI*220*t)*(1+0.5*sin(2*PI*2*t))', 44100, 60)
        mp3 = self.path('podcast/episode-01.mp3')
        ffmpeg('-i', wav, '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '96k', '-id3v2_version', '3',
               '-metadata', 'title=Bölüm 1: Kayıpsız ses', '-metadata', 'artist=Nağmedar Podcast',
               '-metadata', 'album=Nağmedar Podcast', mp3)
        tags = ID3(mp3)
        chapters = [('ch1', 0, 20000, 'Giriş'), ('ch2', 20000, 40000, 'Bit-perfect nedir?'), ('ch3', 40000, 60000, 'Kapanış')]
        tags.add(CTOC(element_id='toc', flags=CTOCFlags.TOP_LEVEL | CTOCFlags.ORDERED,
                      child_element_ids=[c[0] for c in chapters], sub_frames=[TIT2(text=['Bölümler'])]))
        for cid, start, end, title in chapters:
            tags.add(CHAP(element_id=cid, start_time=start, end_time=end, sub_frames=[TIT2(text=[title])]))
        tags.save(v2_version=3)
        size = os.path.getsize(mp3)
        feed = textwrap.dedent(f'''\
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:podcast="https://podcastindex.org/namespace/1.0">
              <channel>
                <title>Nağmedar Podcast</title>
                <link>https://example.org/nagmedar</link>
                <language>tr</language>
                <description>Test yayını: bölüm listesi, bölümler (chapters), yeniden yükleme.</description>
                <itunes:author>Nağmedar</itunes:author>
                <itunes:image href="https://example.org/nagmedar/cover.png"/>
                <podcast:guid>6a1b6e2b-8f1c-5c4e-9f4a-6d2b1f0e5a11</podcast:guid>
                <item>
                  <title>Bölüm 1: Kayıpsız ses</title>
                  <guid isPermaLink="false">nagmedar-ep-1</guid>
                  <pubDate>Sat, 10 Oct 2026 09:00:00 +0300</pubDate>
                  <enclosure url="https://example.org/nagmedar/episode-01.mp3" length="{size}" type="audio/mpeg"/>
                  <itunes:duration>00:01:00</itunes:duration>
                  <podcast:chapters url="https://example.org/nagmedar/episode-01.chapters.json" type="application/json+chapters"/>
                </item>
              </channel>
            </rss>
            ''')
        with open(self.path('podcast/feed.xml'), 'w', encoding='utf-8') as fh:
            fh.write(feed)
        with open(self.path('podcast/episode-01.chapters.json'), 'w', encoding='utf-8') as fh:
            json.dump({'version': '1.2.0', 'chapters': [{'startTime': s / 1000, 'title': t} for _, s, _, t in chapters]},
                      fh, ensure_ascii=False, indent=1)

    def radio(self):
        with open(self.path('radio/stations.m3u8'), 'w', encoding='utf-8') as fh:
            fh.write('#EXTM3U\n#EXTINF:-1 tvg-logo="https://example.org/logo.png" group-title="Test",Nağmedar Test FM (AAC)\n'
                     'https://example.org/stream/aac\n#EXTINF:-1,Nağmedar Test FM (MP3)\nhttps://example.org/stream/mp3\n')
        with open(self.path('radio/station.pls'), 'w', encoding='utf-8') as fh:
            fh.write('[playlist]\nNumberOfEntries=1\nFile1=https://example.org/stream/aac\nTitle1=Nağmedar Test FM\n'
                     'Length1=-1\nVersion=2\n')


STEPS = {
    'flac': Builder.flac, 'codecs': Builder.lossy_and_lossless, 'surround': Builder.surround_codecs, 'dsd': Builder.dsd,
    'lyrics': Builder.lyrics, 'gapless': Builder.gapless, 'cue': Builder.cue, 'tracker': Builder.tracker_modules,
    'podcast': Builder.podcast, 'radio': Builder.radio,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--work', default=None)
    ap.add_argument('--only', default='')
    args = ap.parse_args()
    builder = Builder(os.path.abspath(args.out), os.path.abspath(args.work or os.path.join(args.out, '..', 'work')))
    only = [s for s in args.only.split(',') if s]
    failed = []
    for name, step in STEPS.items():
        if only and name not in only:
            continue
        log(f'== {name}')
        try:
            step(builder)
        except Exception as e:  # keep going: verify.py reports what is missing
            log(f'  ! {name} failed: {e}')
            failed.append(name)
            if os.environ.get('GITHUB_ACTIONS'):
                text = str(e).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print(f'::error title=Test medyası {name}::{text}', flush=True)
    if failed:
        log('failed steps: ' + ', '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    main()
