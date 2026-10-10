#!/usr/bin/env python3
"""Builds Nağmedar's test media set (S-1401) from scratch.

Everything is synthesised (test patterns, tones, our own captions and font),
so the set carries no third-party content and can be published with the
repository. Samples that cannot be synthesised (Dolby Vision profiles 5 and 7,
DTS-HD MA, TrueHD Atmos, VobSub) are listed in manifest.json as "manual".

Usage: generate.py <out-dir> [--only id,id] [--skip-heavy]

Needs ffmpeg/ffprobe (with libx265, libx264, libsvtav1, libmp3lame, libopus),
mkvmerge, dovi_tool, hdr10plus_tool on PATH, and Python with numpy, Pillow
and mutagen. Without mkvmerge the HEVC samples fall back to ffmpeg muxing,
which loses the Dolby Vision configuration record (verify.py reports it).
"""
import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import textwrap

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'tools'))

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import dsf  # noqa: E402
import pgs  # noqa: E402

REPO = os.path.dirname(HERE)
FONT_DIR = os.path.join(REPO, 'application', 'nagmedar-ui', 'src', 'main', 'res', 'font')
FONT = os.path.join(FONT_DIR, 'ibm_plex_sans_semibold.ttf')
FONT_REGULAR = os.path.join(FONT_DIR, 'ibm_plex_sans_regular.ttf')

FPS_2398 = '24000/1001'
MASTER_DISPLAY = 'G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1)'
HDR10_X265 = ('hdr10=1:hdr10-opt=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:'
              'colormatrix=bt2020nc:range=limited:master-display=' + MASTER_DISPLAY + ':max-cll=1000,400')

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


def has_encoder(name):
    out = subprocess.run(['ffmpeg', '-hide_banner', '-encoders'], capture_output=True, text=True).stdout
    return any(line.split()[1:2] == [name] for line in out.splitlines() if len(line.split()) > 1)


def ffmpeg_text(text):
    """Escapes text for the drawtext filter."""
    return text.replace('\\', '\\\\').replace(':', '\\:').replace("'", "\\'").replace('%', '\\%')


class Builder:
    def __init__(self, out, work):
        self.out = out
        self.work = work
        self.mkvmerge = shutil.which('mkvmerge')
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

    def pq_ramp(self, width, height):
        """16-bit grey ramp over the full 10-bit code range: shows where a display clips."""
        p = self.tmp(f'ramp_{width}x{height}.png')
        if not os.path.exists(p):
            ramp = np.linspace(0, 65535, width, dtype=np.float64)
            img = np.tile(ramp, (height, 1)).astype(np.uint16)
            Image.fromarray(img).save(p)  # uint16 -> 16-bit greyscale PNG
        return p

    def hevc_base(self, name, width, height, fps, frames, label, x265_params, bitrate=None, noise=False):
        """Annex-B HEVC Main 10 test pattern with a burnt-in label and a PQ ramp strip."""
        p = self.tmp(name + '.hevc')
        if os.path.exists(p):
            return p
        ramp_h = height // 6
        ramp = self.pq_ramp(width, ramp_h)
        size = min(width, height) // 20
        chain = (f'[0:v]format=yuv420p10le,drawtext=fontfile={FONT}:text=\'{ffmpeg_text(label)}\':'
                 f'fontsize={size}:fontcolor=white:box=1:boxcolor=black@0.6:boxborderw={size // 3}:x={size}:y={size},'
                 'drawtext=fontfile=' + FONT + ':text=\'%{pts\\:hms} %{n}\':'
                 f'fontsize={size}:fontcolor=white:box=1:boxcolor=black@0.6:x=w-tw-{size}:y={size}[top];'
                 f'[1:v]format=yuv420p10le[ramp];[top][ramp]vstack=inputs=2,format=yuv420p10le')
        if noise:
            # grain the encoder cannot predict, so the bitrate is really spent
            chain += ',format=yuv420p,noise=alls=24:allf=t,format=yuv420p10le'
        params = x265_params
        if bitrate:
            params += f':bitrate={bitrate}:vbv-maxrate={int(bitrate * 1.2)}:vbv-bufsize={int(bitrate * 1.2)}'
        else:
            params += ':crf=20'
        ffmpeg('-f', 'lavfi', '-i', f'testsrc2=size={width}x{height - ramp_h}:rate={fps}',
               '-loop', '1', '-framerate', fps, '-i', ramp,
               '-filter_complex', chain, '-frames:v', frames,
               '-c:v', 'libx265', '-preset', 'ultrafast' if bitrate else 'veryfast', '-profile:v', 'main10',
               '-x265-params', params + ':log-level=error', '-f', 'hevc', p)
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

    def mkv_from_hevc(self, out, hevc, fps, extra_inputs=(), transfer='pq'):
        """Muxes annex-B HEVC; [transfer] 'pq' adds the HDR10 colour and mastering elements, 'hlg' the HLG ones."""
        if self.mkvmerge:
            cmd = [self.mkvmerge, '-q', '-o', out, '--default-duration', f'0:{fps}p', '--language', '0:und']
            if transfer:
                cmd += ['--colour-matrix-coefficients', '0:9', '--colour-range', '0:1',
                        '--colour-transfer-characteristics', '0:' + ('16' if transfer == 'pq' else '18'),
                        '--colour-primaries', '0:9']
            if transfer == 'pq':
                cmd += ['--max-content-light', '0:1000', '--max-frame-light', '0:400',
                        '--chromaticity-coordinates', '0:0.68,0.32,0.265,0.69,0.15,0.06',
                        '--white-colour-coordinates', '0:0.3127,0.329', '--max-luminance', '0:1000',
                        '--min-luminance', '0:0.0001']
            cmd += [hevc]
            for extra in extra_inputs:
                cmd += list(extra)
            run(*cmd)
        else:
            log('  ! mkvmerge missing: muxing with ffmpeg (no Dolby Vision configuration record)')
            args = ['-fflags', '+genpts', '-r', fps, '-i', hevc]
            files = [e[-1] for e in extra_inputs if e[0] != '--chapters']
            for f in files:
                args += ['-i', f]
            args += ['-map', '0:v']
            for i in range(len(files)):
                args += ['-map', f'{i + 1}']
            ffmpeg(*args, '-c', 'copy', out)

    # ---------------------------------------------------------------- video

    def hdr10(self):
        frames = 240
        hevc = self.hevc_base('hdr10_4k', 3840, 2160, FPS_2398, frames, 'HDR10 · PQ · 1000 cd/m² · 23,976', HDR10_X265)
        audio = self.tmp('eac3_51.eac3')
        if not os.path.exists(audio):
            ffmpeg('-i', self.surround(seconds=10), '-c:a', 'eac3', '-b:a', '768k', audio)
        self.mkv_from_hevc(self.path('video/hdr10_2160p23976_hevc10_eac3.mkv'), hevc, FPS_2398,
                           extra_inputs=[('--language', '0:tur', '--track-name', '0:E-AC-3 5.1', audio)])

    def hdr10plus(self):
        frames = 240
        hevc = self.hevc_base('hdr10_4k', 3840, 2160, FPS_2398, frames, 'HDR10 · PQ · 1000 cd/m² · 23,976', HDR10_X265)
        meta = self.tmp('hdr10plus.json')
        scenes = [(0, 80, 900, 2000), (80, 80, 9000, 15000), (160, 80, 3000, 6000)]  # start, length, avg, maxscl
        info = []
        for scene_id, (start, length, avg, maxscl) in enumerate(scenes):
            for i in range(length):
                info.append({
                    'LuminanceParameters': {
                        'AverageRGB': avg,
                        'LuminanceDistributions': {
                            'DistributionIndex': [1, 5, 10, 25, 50, 75, 90, 95, 99],
                            'DistributionValues': [2, 3000, 40, avg // 8, avg // 3, avg, avg * 2, maxscl // 2, maxscl - 1],
                        },
                        'MaxScl': [maxscl, maxscl, maxscl],
                    },
                    'NumberOfWindows': 1,
                    'TargetedSystemDisplayMaximumLuminance': 0,
                    'SceneFrameIndex': i,
                    'SceneId': scene_id,
                    'SequenceFrameIndex': start + i,
                })
        with open(meta, 'w') as fh:
            json.dump({
                'JSONInfo': {'HDR10plusProfile': 'A', 'Version': '1.0'},
                'SceneInfo': info,
                'SceneInfoSummary': {'SceneFirstFrameIndex': [s[0] for s in scenes], 'SceneFrameNumbers': [s[1] for s in scenes]},
                'ToolInfo': {'Tool': 'nagmedar testmedia', 'Version': '1'},
            }, fh)
        out_hevc = self.tmp('hdr10plus_4k.hevc')
        run('hdr10plus_tool', 'inject', '-i', hevc, '-j', meta, '-o', out_hevc)
        self.mkv_from_hevc(self.path('video/hdr10plus_2160p23976_hevc10.mkv'), out_hevc, FPS_2398)

    def dolby_vision_p81(self):
        frames = 240
        hevc = self.hevc_base('hdr10_4k', 3840, 2160, FPS_2398, frames, 'HDR10 · PQ · 1000 cd/m² · 23,976', HDR10_X265)
        config = self.tmp('dv_p81.json')
        shots = [(0, 80, 0, 2081, 819), (80, 80, 0, 3079, 1580), (160, 80, 7, 2700, 1200)]
        with open(config, 'w') as fh:
            json.dump({
                'cm_version': 'V40',
                'length': frames,
                'level6': {'max_display_mastering_luminance': 1000, 'min_display_mastering_luminance': 1,
                           'max_content_light_level': 1000, 'max_frame_average_light_level': 400},
                'shots': [{'start': s, 'duration': d, 'metadata_blocks': [{'Level1': {'min_pq': mn, 'max_pq': mx, 'avg_pq': av}}]}
                          for s, d, mn, mx, av in shots],
            }, fh)
        rpu = self.tmp('dv_p81_rpu.bin')
        run('dovi_tool', 'generate', '-j', config, '-o', rpu)
        out_hevc = self.tmp('dv_p81_4k.hevc')
        run('dovi_tool', 'inject-rpu', '-i', hevc, '--rpu-in', rpu, '-o', out_hevc)
        self.mkv_from_hevc(self.path('video/dv_p8.1_2160p23976_hevc10.mkv'), out_hevc, FPS_2398)

    def dolby_vision_p5(self):
        """Profile 5 RPU (no reshaping) on the test pattern. A real profile 5 base layer is IPTPQc2, this one
        is not, so a correct player shows wrong colours here: the sample tests detection and the decoder path
        (and that SP-03 refuses to convert it), not the picture."""
        frames = 120
        hevc = self.hevc_base('dv_p5_base_4k', 3840, 2160, FPS_2398, frames, 'DV P5 · sentetik · renkler yanlış',
                              'range=full:repeat-headers=1')
        config = self.tmp('dv_p5.json')
        with open(config, 'w') as fh:
            json.dump({
                'cm_version': 'V40',
                'profile': '5',
                'length': frames,
                'level6': {'max_display_mastering_luminance': 1000, 'min_display_mastering_luminance': 1,
                           'max_content_light_level': 1000, 'max_frame_average_light_level': 400},
                'shots': [{'start': s, 'duration': d, 'metadata_blocks': [{'Level1': {'min_pq': 0, 'max_pq': mx, 'avg_pq': av}}]}
                          for s, d, mx, av in [(0, 60, 2081, 819), (60, 60, 3079, 1580)]],
            }, fh)
        rpu = self.tmp('dv_p5_rpu.bin')
        run('dovi_tool', 'generate', '-j', config, '-o', rpu)
        out_hevc = self.tmp('dv_p5_4k.hevc')
        run('dovi_tool', 'inject-rpu', '-i', hevc, '--rpu-in', rpu, '-o', out_hevc)
        self.mkv_from_hevc(self.path('video/dv_p5_2160p23976_hevc10.mkv'), out_hevc, FPS_2398, transfer=None)

    def dolby_vision_p7(self):
        """Profile 7 MEL (base and enhancement layer in one stream, as on UHD Blu-ray remuxes) from dovi_tool's
        test assets (MIT): 256x144, 259 frames. $DOVI_TOOL_ASSETS points at dovi_tool's assets/hevc_tests."""
        assets = os.environ.get('DOVI_TOOL_ASSETS', '')
        el = os.path.join(assets, 'regular_start_code_4_muxed_el.hevc')
        mel = os.path.join(assets, 'regular_rpu_mel.bin')
        if not (os.path.exists(el) and os.path.exists(mel)):
            raise RuntimeError('DOVI_TOOL_ASSETS must point at dovi_tool 2.3.4 assets/hevc_tests '
                               '(regular_start_code_4_muxed_el.hevc, regular_rpu_mel.bin)')
        hevc = self.tmp('dv_p7_mel.hevc')
        run('dovi_tool', 'inject-rpu', '-i', el, '--rpu-in', mel, '-o', hevc)
        audio = self.tmp('eac3_51.eac3')
        if not os.path.exists(audio):
            ffmpeg('-i', self.surround(seconds=10), '-c:a', 'eac3', '-b:a', '768k', audio)
        subs = self.tmp('dv_p7.srt')
        with open(subs, 'w', encoding='utf-8') as fh:
            fh.write('1\n00:00:01,000 --> 00:00:04,000\nDönüştürmeden sonra bu altyazı kalmalı.\n\n'
                     '2\n00:00:05,000 --> 00:00:09,000\nSes izi ve bölümler de korunmalı.\n')
        chapters = self.tmp('dv_p7_chapters.xml')
        with open(chapters, 'w', encoding='utf-8') as fh:
            fh.write('<?xml version="1.0" encoding="UTF-8"?>\n<Chapters><EditionEntry>'
                     '<ChapterAtom><ChapterTimeStart>00:00:00.000</ChapterTimeStart><ChapterDisplay><ChapterString>Bir</ChapterString></ChapterDisplay></ChapterAtom>'
                     '<ChapterAtom><ChapterTimeStart>00:00:05.000</ChapterTimeStart><ChapterDisplay><ChapterString>İki</ChapterString></ChapterDisplay></ChapterAtom>'
                     '</EditionEntry></Chapters>\n')
        self.mkv_from_hevc(self.path('video/dv_p7_mel_144p23976_hevc10.mkv'), hevc, FPS_2398, extra_inputs=[
            ('--language', '0:tur', '--track-name', '0:E-AC-3 5.1', audio),
            ('--language', '0:tur', '--sub-charset', '0:UTF-8', subs),
            ('--chapters', chapters),
        ])

    def hlg(self):
        hevc = self.hevc_base('hlg_1080p50', 1920, 1080, '50', 250, 'HLG · BT.2020 · 50p',
                              'colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc:range=limited:repeat-headers=1')
        self.mkv_from_hevc(self.path('video/hlg_1080p50_hevc10.mkv'), hevc, '50', transfer='hlg')

    def stress(self):
        hevc = self.hevc_base('stress_4k', 3840, 2160, FPS_2398, 360, 'Yük testi · 100 Mbit/s · 23,976', HDR10_X265,
                              bitrate=100000, noise=True)
        self.mkv_from_hevc(self.path('video/stress_2160p23976_hevc10_100mbps.mkv'), hevc, FPS_2398)

    def interlaced(self):
        ffmpeg('-f', 'lavfi', '-i', 'testsrc2=size=1920x1080:rate=50',
               '-f', 'lavfi', '-i', "aevalsrc='0.4*sin(2*PI*1000*t)|0.4*sin(2*PI*1000*t)':s=48000",
               '-vf', 'tinterlace=mode=interleave_top,fieldorder=tff,format=yuv420p', '-t', '10',
               '-c:v', 'libx264', '-preset', 'veryfast', '-flags', '+ilme+ildct', '-x264-params', 'tff=1', '-field_order', 'tt',
               '-c:a', 'ac3', '-b:a', '448k', self.path('video/interlaced_1080i25_h264_ac3.mkv'))

    def av1(self):
        out = self.path('video/av1_1080p24_10bit_opus.mkv')
        if has_encoder('libsvtav1'):
            venc = ['-c:v', 'libsvtav1', '-preset', '10', '-crf', '35']
        elif has_encoder('libaom-av1'):
            venc = ['-c:v', 'libaom-av1', '-cpu-used', '8', '-row-mt', '1', '-crf', '35', '-b:v', '0']
        else:
            log('  ! no AV1 encoder: skipped')
            return
        ffmpeg('-f', 'lavfi', '-i', 'testsrc2=size=1920x1080:rate=24',
               '-f', 'lavfi', '-i', "aevalsrc='0.4*sin(2*PI*440*t)|0.4*sin(2*PI*660*t)':s=48000",
               '-t', '10', '-pix_fmt', 'yuv420p10le', *venc, '-c:a', 'libopus', '-b:a', '128k', out)

    def subtitles(self):
        base = self.path('video/subtitles_1080p24_h264.mkv')
        av = self.tmp('subs_av.mkv')
        ffmpeg('-f', 'lavfi', '-i', 'testsrc2=size=1920x1080:rate=24',
               '-f', 'lavfi', '-i', "aevalsrc='0.3*sin(2*PI*440*t)|0.3*sin(2*PI*550*t)':s=48000",
               '-t', '30', '-c:v', 'libx264', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', av)
        tr_lines = [
            (1000, 4000, 'Gece yarısı Kuzey Işığı’nda buluşalım.'),
            (5000, 8500, 'Şu dağın ardında güneş doğmuyor mu?\nİğneyle kuyu kazar gibi çalıştık.'),
            (9500, 13000, 'Çığlık, ığdır, ışık, ılık — İstanbul’da “ş” ve “ğ” sorunsuz mu?'),
            (14000, 18000, 'İkinci satır: ÇĞİÖŞÜ çğıöşü'),
            (19000, 23000, 'Ses gecikmesi testi: bu satır tam 19. saniyede görünmeli.'),
            (24000, 29000, 'Son altyazı · son satır.'),
        ]
        en_lines = [(1000, 4000, 'Let us meet at midnight at the Northern Light.'),
                    (9500, 13000, 'PGS: palette, run-length coding and position are real Blu-ray data.'),
                    (24000, 29000, 'Last caption.')]
        signs = [(5000, 8500, '[SIGN: Kuzey Işığı Oteli]'), (19000, 23000, '[TEXT ON SCREEN: 19 s]')]

        def srt(lines):
            def ts(ms):
                return '%02d:%02d:%02d,%03d' % (ms // 3600000, ms // 60000 % 60, ms // 1000 % 60, ms % 1000)
            return '\n'.join(f'{i}\n{ts(a)} --> {ts(b)}\n{text}\n' for i, (a, b, text) in enumerate(lines, 1))

        tr_srt = self.tmp('tr.srt')
        with open(tr_srt, 'w', encoding='utf-8') as fh:
            fh.write(srt(tr_lines))
        # sidecars: the Turkish one in Windows-1254, as most Turkish subtitle sites serve them
        side_1254 = self.path('video/subtitles_1080p24_h264.tr.srt')
        with open(side_1254, 'w', encoding='cp1254', newline='\r\n') as fh:
            fh.write(srt(tr_lines).replace('’', "'").replace('“', '"').replace('”', '"').replace('—', '-').replace('·', '-'))
        with open(self.path('video/subtitles_1080p24_h264.en.srt'), 'w', encoding='utf-8-sig') as fh:
            fh.write(srt(en_lines))
        signs_srt = self.tmp('signs.srt')
        with open(signs_srt, 'w', encoding='utf-8') as fh:
            fh.write(srt(signs))
        ass = self.tmp('tr.ass')
        with open(ass, 'w', encoding='utf-8') as fh:
            fh.write(textwrap.dedent('''\
                [Script Info]
                Title: Nağmedar ASS testi
                ScriptType: v4.00+
                PlayResX: 1920
                PlayResY: 1080
                WrapStyle: 0
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: Default,IBM Plex Sans SemiBold,64,&H00FFFFFF,&H0000B8F2,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,3,1,2,80,80,60,1
                Style: Top,IBM Plex Sans SemiBold,52,&H006EFF7A,&H00FFFFFF,&H00000000,&H80000000,0,1,0,0,100,100,0,0,1,3,0,8,80,80,50,1
                Style: Karaoke,IBM Plex Sans SemiBold,60,&H00FFFFFF,&H0000B8F2,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,3,1,2,80,80,140,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Gece yarısı {\\i1}Kuzey Işığı{\\i0}’nda buluşalım.
                Dialogue: 0,0:00:01.00,0:00:08.00,Top,,0,0,0,,{\\fad(400,400)}Üst satır: aynı anda iki altyazı
                Dialogue: 0,0:00:05.00,0:00:08.50,Default,,0,0,0,,{\\pos(960,540)\\frz8\\c&H6EFF7A&}Ortada, döndürülmüş, yeşil
                Dialogue: 0,0:00:09.50,0:00:13.00,Karaoke,,0,0,0,,{\\k50}Şar{\\k50}kı {\\k60}söz{\\k60}le{\\k80}ri {\\k100}ka{\\k100}ra{\\k120}o{\\k120}ke
                Dialogue: 0,0:00:14.00,0:00:18.00,Default,,0,0,0,,{\\an7\\pos(80,80)\\fs40}Sol üst köşe · \\N{\\b1}kalın{\\b0} ve {\\u1}altı çizili{\\u0}
                Dialogue: 0,0:00:19.00,0:00:23.00,Default,,0,0,0,,{\\move(200,900,1720,900)}Kayan yazı
                Dialogue: 0,0:00:24.00,0:00:29.00,Default,,0,0,0,,Son altyazı · son satır.
                '''))
        shutil.copy(ass, self.path('video/subtitles_1080p24_h264.tr.ass'))
        sup = self.tmp('en.sup')
        pgs.write_sup(sup, en_lines, FONT)
        chapters = self.tmp('chapters.xml')
        with open(chapters, 'w', encoding='utf-8') as fh:
            fh.write(textwrap.dedent('''\
                <?xml version="1.0" encoding="UTF-8"?>
                <Chapters><EditionEntry>
                  <ChapterAtom><ChapterTimeStart>00:00:00.000</ChapterTimeStart><ChapterDisplay><ChapterString>Açılış</ChapterString><ChapterLanguage>tur</ChapterLanguage></ChapterDisplay></ChapterAtom>
                  <ChapterAtom><ChapterTimeStart>00:00:09.500</ChapterTimeStart><ChapterDisplay><ChapterString>Karaoke</ChapterString><ChapterLanguage>tur</ChapterLanguage></ChapterDisplay></ChapterAtom>
                  <ChapterAtom><ChapterTimeStart>00:00:19.000</ChapterTimeStart><ChapterDisplay><ChapterString>Senkron ölçümü</ChapterString><ChapterLanguage>tur</ChapterLanguage></ChapterDisplay></ChapterAtom>
                </EditionEntry></Chapters>
                '''))
        if self.mkvmerge:
            run(self.mkvmerge, '-q', '-o', base, av,
                '--language', '0:tur', '--track-name', '0:Türkçe', '--default-track-flag', '0:yes', tr_srt,
                '--language', '0:tur', '--track-name', '0:Türkçe (ASS, stilli)', '--default-track-flag', '0:no', ass,
                '--language', '0:eng', '--track-name', '0:English (PGS)', '--default-track-flag', '0:no', sup,
                '--language', '0:eng', '--track-name', '0:English signs (forced)', '--forced-display-flag', '0:yes',
                '--default-track-flag', '0:no', signs_srt,
                '--attachment-mime-type', 'font/ttf', '--attachment-name', 'IBMPlexSans-SemiBold.ttf', '--attach-file', FONT,
                '--chapters', chapters)
        else:
            log('  ! mkvmerge missing: subtitles muxed with ffmpeg, no chapters/flags')
            ffmpeg('-i', av, '-i', tr_srt, '-i', ass, '-i', sup, '-i', signs_srt,
                   '-map', '0', '-map', '1', '-map', '2', '-map', '3', '-map', '4', '-c', 'copy',
                   '-metadata:s:s:0', 'language=tur', '-metadata:s:s:1', 'language=tur',
                   '-metadata:s:s:2', 'language=eng', '-metadata:s:s:3', 'language=eng',
                   '-disposition:s:0', 'default', '-disposition:s:3', 'forced', base)

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
    'hdr10': Builder.hdr10, 'hdr10plus': Builder.hdr10plus, 'dv-p81': Builder.dolby_vision_p81,
    'dv-p5': Builder.dolby_vision_p5, 'dv-p7': Builder.dolby_vision_p7,
    'hlg': Builder.hlg, 'stress': Builder.stress, 'interlaced': Builder.interlaced, 'av1': Builder.av1,
    'subtitles': Builder.subtitles, 'flac': Builder.flac, 'codecs': Builder.lossy_and_lossless,
    'surround': Builder.surround_codecs, 'dsd': Builder.dsd, 'lyrics': Builder.lyrics, 'gapless': Builder.gapless,
    'cue': Builder.cue, 'podcast': Builder.podcast, 'radio': Builder.radio,
}
HEAVY = {'stress'}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--work', default=None)
    ap.add_argument('--only', default='')
    ap.add_argument('--skip-heavy', action='store_true')
    args = ap.parse_args()
    builder = Builder(os.path.abspath(args.out), os.path.abspath(args.work or os.path.join(args.out, '..', 'work')))
    only = [s for s in args.only.split(',') if s]
    failed = []
    for name, step in STEPS.items():
        if only and name not in only:
            continue
        if args.skip_heavy and name in HEAVY:
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
