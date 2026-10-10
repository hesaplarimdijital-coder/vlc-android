#!/usr/bin/env python3
"""Replace the VLC product name with Nağmedar in the English and Turkish UI strings.

Attribution strings (copyright line, libVLC engine options, donation texts that
describe supporting the VLC project) are kept on purpose: VideoLAN is credited
in the About screen.

Usage: rebrand_strings.py <res_dir>
"""
import re
import sys

KEEP = {'vlc_authors', 'custom_libvlc_options', 'custom_libvlc_options_invalid',
        'donate', 'donate_desc', 'donation_description', 'tip_jar', 'thank_you_desc'}

OVERRIDES = {
    'values': {
        'app_name_full': 'Nağmedar',
        'about_vlc_text': 'Nağmedar is a media player based on VLC for Android by VideoLAN, the popular open source media player. It can read all files and network streams.',
        'ml_database_warning': 'Your database contains the list of all of your media. It will be shared with the developers',
        'logs_warning': 'May contain info about your played media. It will be shared with the developers',
        'widget_default_text': 'Nağmedar',
    },
    'values-tr': {
        'app_name_full': 'Nağmedar',
        'about_vlc_text': "Nağmedar, VideoLAN\\'ın açık kaynaklı VLC for Android uygulamasına dayanan bir ortam oynatıcısıdır. Tüm dosya ve ağ akışlarını okuyabilir.",
        'ml_database_warning': 'Veri tabanınızda, tüm ortam dosyalarınızın listesi bulunur. Bu liste geliştiricilerle paylaşılacak.',
        'logs_warning': 'Oynattığınız ortam dosyaları ile ilgili bilgiler içerebilir. Bu bilgiler geliştiricilerle paylaşılacak.',
    },
}

# Turkish case suffixes after "VLC" (read "ve-le-ce") -> after "Nağmedar" (back vowel, final r)
TR_SUFFIXES = [("VLC\\'ye", "Nağmedar\\'a"), ("VLC\\'yi", "Nağmedar\\'ı"), ("VLC\\'nin", "Nağmedar\\'ın"),
               ("VLC\\'de", "Nağmedar\\'da"), ("VLC\\'den", "Nağmedar\\'dan"),
               ('Android™ için VLC', 'Nağmedar'), ('Android için VLC', 'Nağmedar')]
EN_PHRASES = [('VLC for Android™', 'Nağmedar'), ('VLC for Android', 'Nağmedar'), ('VLC media player', 'Nağmedar')]
WORD = re.compile(r'(?<![A-Za-z])VLC(?![A-Za-z])')
STRING = re.compile(r'(<string name="([^"]+)"[^>]*>)(.*?)(</string>)', re.S)


def rebrand(text, folder):
    phrases = TR_SUFFIXES if folder == 'values-tr' else EN_PHRASES
    overrides = OVERRIDES.get(folder, {})
    changed = []

    def repl(m):
        head, name, body, tail = m.groups()
        if name in overrides:
            new = overrides[name]
        elif name in KEEP or 'VLC' not in body:
            return m.group(0)
        else:
            new = body
            for old, rep in phrases:
                new = new.replace(old, rep)
            new = WORD.sub('Nağmedar', new)
        if new != body:
            changed.append(name)
        return head + new + tail

    return STRING.sub(repl, text), changed


def main(res):
    for folder in ('values', 'values-tr'):
        path = '%s/%s/strings.xml' % (res, folder)
        with open(path, encoding='utf-8') as fh:
            text = fh.read()
        text, changed = rebrand(text, folder)
        text = text.replace('<string name="app_name" translatable="false">VLC</string>',
                            '<string name="app_name" translatable="false">Nağmedar</string>')
        with open(path, 'w', encoding='utf-8') as fh:
            fh.write(text)
        print(folder, len(changed), 'strings')


if __name__ == '__main__':
    main(sys.argv[1])
