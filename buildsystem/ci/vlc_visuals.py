#!/usr/bin/env python3
"""Fail the build when VLC's own visuals come back where Nagmedar shows itself.

The classic interface is gone from phones, tablets and DeX (the leanback TV keeps it), but a
branch can bring one of VLC's pictures back without anyone seeing it before a device does. Two
checks, each a GitHub annotation per finding, exit status 1 when there is one:

- glyphs: Nagmedar's code (application/nagmedar-*, vlc-android, app, remote-access-server, tools)
  may not draw VLC's notification, widget and speed glyphs: R.drawable.ic_notif_*,
  ic_widget_* or ic_auto_speed* (@drawable/... in XML). Nagmedar's are ic_nagmedar_mark_mono,
  nagmedar_notif_*, nagmedar_pip_*.
- orange: the launcher's resources (the manifests' launcher icons, every res/xml/shortcuts.xml
  icon and each drawable these lead to) may not use VLC's orange (@color/orange*, its values).

Usage: vlc_visuals.py [repository root]
"""
import glob
import os
import re
import sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')

# Where Nagmedar's notifications, media session and windows are drawn (not the TV's module)
CODE = ['application/nagmedar-*/src/main', 'application/vlc-android/src', 'application/vlc-android/res',
        'application/app/src/main', 'application/remote-access-server/src/main', 'application/tools/src/main']
GLYPH = re.compile(r'(?:R\.drawable\.|@drawable/)(ic_notif_\w+|ic_widget_\w+|ic_auto_speed\w*)\b')

# Fixed by another branch of the same review: allowed until it lands, then removed from here (a
# pair that is no longer found is reported so).
PENDING: set = set()

MANIFESTS = ['application/app/src/main/AndroidManifest.xml', 'application/vlc-android/AndroidManifest.xml']
LAUNCHER_ICON = re.compile(r'<application\b[^>]*>', re.S)
SHORTCUTS = ['application/*/res/xml/shortcuts.xml', 'application/*/src/main/res/xml/shortcuts.xml',
             'application/*/flavors/*/res/xml/shortcuts.xml']
ICON = re.compile(r'android:(?:icon|roundIcon)="@(drawable|mipmap)/(\w+)"')
REFERENCE = re.compile(r'@(drawable|mipmap)/(\w+)')
# VLC's orange: its colour names and values (resources' colors.xml, the cone)
ORANGE = re.compile(r'@color/orange\w*|#(?:[0-9a-f]{2})?(?:ff8800|ff7d00|ff7200|ff610a|ff5014|ffa11a|f46e00)\b', re.I)
RES_DIRS = ['application/*/res', 'application/*/src/main/res', 'application/*/flavors/*/res']


def rel(path):
    return os.path.relpath(path, ROOT)


def files(patterns, suffixes):
    for pattern in patterns:
        for top in glob.glob(os.path.join(ROOT, pattern)):
            if '/television/' in top:
                continue
            for folder, _, names in os.walk(top):
                if '/build/' in folder + '/':
                    continue
                for name in names:
                    if name.endswith(suffixes):
                        yield os.path.join(folder, name)


def annotate(path, line, title, message):
    print('::error file=%s,line=%d,title=%s::%s' % (rel(path), line, title, message))


def glyphs():
    found, pending_seen = 0, set()
    for path in sorted(set(files(CODE, ('.kt', '.java', '.xml')))):
        with open(path, encoding='utf-8', errors='replace') as fh:
            for number, text in enumerate(fh, 1):
                for match in GLYPH.finditer(text):
                    key = (rel(path), match.group(1))
                    if key in PENDING:
                        pending_seen.add(key)
                        continue
                    annotate(path, number, 'VLC görseli',
                             "%s VLC'nin simgesi: Nağmedar'ınkini kullanın (ic_nagmedar_mark_mono, nagmedar_notif_*, nagmedar_pip_*)" % match.group(1))
                    found += 1
    for path, name in sorted(PENDING - pending_seen):
        print("::warning file=%s,title=VLC görseli::%s artık yok: buildsystem/ci/vlc_visuals.py'deki PENDING'den çıkarın" % (path, name))
    return found


def resource_files(kind, name):
    out = []
    for pattern in RES_DIRS:
        for res in glob.glob(os.path.join(ROOT, pattern)):
            if '/television/' in res or '/build/' in res:
                continue
            out += glob.glob(os.path.join(res, kind + '*', name + '.xml'))
    return out


def orange():
    found = 0
    todo = []
    for manifest in MANIFESTS:
        path = os.path.join(ROOT, manifest)
        if os.path.exists(path):
            with open(path, encoding='utf-8') as fh:
                for tag in LAUNCHER_ICON.findall(fh.read()):
                    todo += [(kind, name, rel(path)) for kind, name in ICON.findall(tag)]
    for pattern in SHORTCUTS:
        for path in glob.glob(os.path.join(ROOT, pattern)):
            with open(path, encoding='utf-8') as fh:
                todo += [(kind, name, rel(path)) for kind, name in ICON.findall(fh.read())]
    seen = set()
    while todo:
        kind, name, origin = todo.pop()
        if (kind, name) in seen:
            continue
        seen.add((kind, name))
        for path in resource_files(kind, name):
            with open(path, encoding='utf-8', errors='replace') as fh:
                lines = fh.read().splitlines()
            for number, text in enumerate(lines, 1):
                for match in ORANGE.finditer(text):
                    annotate(path, number, 'VLC turuncusu',
                             "%s başlatıcının simgesinde (%s): Nağmedar'ın renklerini kullanın" % (match.group(0), origin))
                    found += 1
                todo += [(k, n, rel(path)) for k, n in REFERENCE.findall(text)]
    return found


def main():
    found = glyphs() + orange()
    if found:
        print('%d VLC görseli bulundu' % found)
        return 1
    print("VLC görseli yok: bildirimler, oturum ve başlatıcı Nağmedar'ın")
    return 0


if __name__ == '__main__':
    sys.exit(main())
