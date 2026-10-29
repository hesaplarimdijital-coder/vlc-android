#!/usr/bin/env python3
"""Render the built remote access web app against a mock of the app's server (S-1304).

Serves <dist> over HTTPS (and plain HTTP, for the "connection is not secure" page) and answers the
API the app's server does with sample media, in the language of the app's own strings: the
translation the web app loads is made from the same string resources TranslationMapping.kt names.
Headless Chromium then walks the pages: the security page, the one-time code (wrong, then right),
the library, the player bar, About. It checks, and fails on:

  * a script error on a page,
  * "VLC" in the title or in the text of any page other than the attribution in About,
  * a missing Nağmedar mark, manifest or favicon,

and writes a screenshot of each step to <out>.

Usage: preview.py --dist <remoteaccess/dist> --repo <repo root> --out <dir> [--channel chrome]
"""
import argparse
import hashlib
import http.server
import json
import mimetypes
import os
import re
import socketserver
import ssl
import subprocess
import sys
import tempfile
import threading
import urllib.parse
import xml.etree.ElementTree as ET

CODE = '123456'
CHALLENGE = 'nagmedar-challenge'

mimetypes.add_type('font/woff2', '.woff2')
mimetypes.add_type('application/manifest+json', '.webmanifest')

STATE = {'lang': 'tr', 'authed': False, 'polled': False, 'translation': {}, 'dist': '', 'repo': ''}


def android_text(raw):
    """An Android string resource's text as the app shows it."""
    text = raw.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n').replace('\\@', '@').replace('\\?', '?')
    return text.replace('\\\\', '\\')


def read_strings(path):
    if not os.path.exists(path):
        return {}
    out = {}
    for element in ET.parse(path).getroot().iter('string'):
        out[element.get('name')] = android_text(''.join(element.itertext()))
    return out


def translation(repo, lang):
    """TranslationMapping.generateTranslations for [lang]: the mapped string resources, %s as {msg}."""
    res = os.path.join(repo, 'application/resources/src/main/res')
    server_res = os.path.join(repo, 'application/remote-access-server/src/main/res')
    strings = read_strings(os.path.join(res, 'values/strings.xml'))
    strings.update(read_strings(os.path.join(server_res, 'values/strings.xml')))
    if lang != 'en':
        strings.update(read_strings(os.path.join(res, 'values-%s/strings.xml' % lang)))
        strings.update(read_strings(os.path.join(server_res, 'values-%s/strings.xml' % lang)))
    source = open(os.path.join(repo, 'application/remote-access-server/src/main/java/org/videolan/vlc/remoteaccessserver/TranslationMapping.kt'),
                  encoding='utf-8').read()
    out = {}
    for key, resource in re.findall(r'^\s+([A-Z0-9_]+)\(R\.string\.(\w+)\)', source, re.M):
        out[key] = strings.get(resource, '').replace('%s', '{msg}')
    out['REMOTE_ACCESS_HASH'] = 'bb1f07b'
    out['REMOTE_ACCESS_VERSION'] = '0.1.0'
    out['PORT'] = 'android'
    out['DEVICE_NAME'] = 'Samsung - SM-X920'
    out['APP_VERSION'] = '0.4.0'
    out['LANG'] = lang
    return out


def vector_to_svg(repo, name, width):
    """A simple VectorDrawable (paths only) as an SVG, black unless the fill is a literal colour."""
    path = os.path.join(repo, 'application/resources/src/main/res/drawable/%s.xml' % name)
    if not os.path.exists(path):
        return None
    root = ET.parse(path).getroot()
    a = '{http://schemas.android.com/apk/res/android}'
    vw, vh = root.get(a + 'viewportWidth', '24'), root.get(a + 'viewportHeight', '24')
    paths = []
    for p in root.iter('path'):
        fill = p.get(a + 'fillColor', '#000000')
        if not fill.startswith('#'):
            fill = '#000000'
        elif len(fill) == 9:
            fill = '#' + fill[3:]
        paths.append('<path fill="%s" d="%s"/>' % (fill, p.get(a + 'pathData', '')))
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s">%s</svg>'
            % (width, width, vw, vh, ''.join(paths)))


def item(i, title, artist='', duration=0, folder=False, playing=False, progress=0, resolution='', kind=''):
    return {'id': i, 'title': title, 'artist': artist, 'duration': duration, 'artworkURL': 'artwork-%d' % i, 'playing': playing,
            'resolution': resolution, 'path': '/storage/emulated/0/%s' % title, 'isFolder': folder, 'progress': progress,
            'played': False, 'fileType': kind, 'favorite': i % 4 == 0}


ARTISTS = [item(10, 'Aylin Kaya', '3 albüm'), item(11, 'The Quiet Hours', '2 albums'), item(12, 'Mert Demir', '1 albüm'),
           item(13, 'Lumen', '4 albums'), item(14, 'Selin Aras', '2 albüm'), item(15, 'North Wind', '5 albums')]
TRACKS = [item(20, 'Gece Yarısı', 'Aylin Kaya', 213_000), item(21, 'Slow Harbour', 'The Quiet Hours', 245_000),
          item(22, 'Dalga Sesi', 'Mert Demir', 187_000), item(23, 'Afterglow', 'Lumen', 301_000)]
NOW = {'type': 'now-playing', 'title': 'Gece Yarısı', 'artist': 'Aylin Kaya', 'playing': True, 'isVideoPlaying': False, 'progress': 92_000,
       'duration': 213_000, 'id': 20, 'artworkURL': 'artwork-20', 'uri': 'file:///x', 'volume': 60, 'speed': 1.0, 'sleepTimer': 0,
       'waitForMediaEnd': False, 'resetOnInteraction': False, 'shuffle': False, 'repeat': 0, 'shouldShow': True, 'bookmarks': [],
       'chapters': [], 'audioTracks': [], 'currentAudioTrack': '-1', 'subtitleTracks': [], 'currentSubtitleTrack': '-1'}


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = 'NagmedarMock'

    def log_message(self, *args):
        pass

    def send(self, code, body=b'', ctype='application/json', headers=None):
        if isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def json(self, value):
        self.send(200, json.dumps(value))

    def logged_in(self):
        return STATE['authed'] and 'nagmedar-session=ok' in self.headers.get('Cookie', '')

    def do_HEAD(self):
        self.do_GET()

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        length = int(self.headers.get('Content-Length') or 0)
        form = urllib.parse.parse_qs(self.rfile.read(length).decode('utf-8'))
        if path == '/code':
            self.send(200, CHALLENGE, 'text/plain')
        elif path == '/verify-code':
            expected = hashlib.sha256((CODE + CHALLENGE).encode()).hexdigest()
            if form.get('code', [''])[0] == expected:
                STATE['authed'] = True
                STATE['polled'] = False
                self.send(302, b'', 'text/plain', {'Location': '/', 'Set-Cookie': 'nagmedar-session=ok; Path=/'})
            else:
                self.send(302, b'', 'text/plain', {'Location': '/index.html#/login/error'})
        else:
            self.send(200, b'', 'text/plain')

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        path, query = url.path, urllib.parse.parse_qs(url.query)
        if path == '/translation':
            return self.json(STATE['translation'])
        if path == '/icon':
            name = query.get('id', [''])[0]
            logo = os.path.join(STATE['dist'], 'logo.svg')
            if name == 'ic_icon' and os.path.exists(logo):
                return self.send(200, open(logo, 'rb').read(), 'image/svg+xml')
            svg = vector_to_svg(STATE['repo'], name, query.get('width', ['24'])[0])
            return self.send(200, svg, 'image/svg+xml') if svg else self.send(404)
        static = os.path.join(STATE['dist'], 'index.html' if path == '/' else path.lstrip('/'))
        if os.path.isfile(static) and os.path.realpath(static).startswith(os.path.realpath(STATE['dist'])):
            ctype = mimetypes.guess_type(static)[0] or 'application/octet-stream'
            return self.send(200, open(static, 'rb').read(), ctype)
        if not self.logged_in():
            return self.send(401)
        if path == '/wsticket':
            return self.send(404)
        if path == '/longpolling':
            if STATE['polled']:
                return self.json([])
            STATE['polled'] = True
            return self.json([{'type': 'player-status', 'playing': True}, NOW,
                              {'type': 'play-queue', 'medias': [dict(TRACKS[0], playing=True)] + TRACKS[1:]}])
        if path == '/artwork':
            title = query.get('artwork', ['x'])[0]
            hue = sum(map(ord, title)) % 360
            return self.send(200, '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10" preserveAspectRatio="none"><rect width="10" height="10" '
                                  'fill="hsl(%d,28%%,34%%)"/></svg>' % hue, 'image/svg+xml')
        if path == '/artist-list':
            return self.json(ARTISTS)
        if path in ('/track-list', '/album-list', '/genre-list', '/playlist-list'):
            return self.json(TRACKS)
        return self.json([])


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def serve(port, cert=None):
    server = Server(('127.0.0.1', port), Handler)
    if cert:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(*cert)
        server.socket = context.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


class Failures(list):
    def check(self, ok, message):
        if not ok:
            self.append(message)
            print('::error title=Uzaktan erişim web arayüzü::' + message)


def visible_text(page):
    return page.evaluate('document.body.innerText')


def wait_text(page, text, timeout=15000):
    try:
        page.wait_for_function('t => document.body.innerText.includes(t)', arg=text, timeout=timeout)
    except Exception:
        page.screenshot(path=os.path.join(STATE['out'], 'timeout.png'))
        raise AssertionError('"%s" never showed on %s (timeout.png)' % (text, page.url))


def walk(args, https, http_port, failures):
    from playwright.sync_api import sync_playwright
    out = args.out
    os.makedirs(out, exist_ok=True)
    plain = 'http://127.0.0.1:%d/' % http_port
    secure = 'https://127.0.0.1:%d/' % https
    with sync_playwright() as p:
        browser = p.chromium.launch(channel=args.channel) if args.channel else p.chromium.launch()

        def context(lang, scheme, width, height, mobile=False):
            STATE['lang'] = lang
            STATE['translation'] = translation(args.repo, lang)
            ctx = browser.new_context(locale='tr-TR' if lang == 'tr' else 'en-GB', color_scheme=scheme, ignore_https_errors=True,
                                      viewport={'width': width, 'height': height}, is_mobile=mobile, has_touch=mobile,
                                      device_scale_factor=2 if mobile else 1)
            page = ctx.new_page()
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            return ctx, page, errors

        def shot(page, name):
            page.wait_for_timeout(700)
            page.screenshot(path=os.path.join(out, name + '.png'))

        def no_vlc(page, label):
            text = visible_text(page)
            failures.check(not re.search(r'\bVLC\b', text), '%s: "VLC" in the page\'s text: %s' % (label, text[:200].replace('\n', ' ')))
            failures.check(not re.search(r'VLC', page.title()), '%s: "VLC" in the title: %s' % (label, page.title()))

        # 1. the connection is not secure
        STATE['authed'] = False
        ctx, page, errors = context('tr', 'light', 1280, 760)
        page.goto(plain)
        wait_text(page, STATE['translation']['SSL_EXPLANATION_TITLE'])
        no_vlc(page, 'ssl')
        shot(page, 'ssl-tr-light')
        failures.check(not errors, 'ssl: ' + '; '.join(errors))
        ctx.close()

        # 2. the one-time code: wrong, then right
        STATE['authed'] = False
        ctx, page, errors = context('en', 'dark', 1280, 760)
        page.goto(secure)
        page.wait_for_selector('#digit0', timeout=15000)
        failures.check('Nağmedar' in page.title(), 'login: the title is "%s"' % page.title())
        no_vlc(page, 'login')
        shot(page, 'login-en-dark')
        for wrong in (True, False):
            page.click('#digit0')
            page.keyboard.type('000000' if wrong else CODE)
            page.click('button.action-btn.btn-lg')
            if wrong:
                wait_text(page, STATE['translation']['INVALID_OTP'])
                failures.check(not STATE['authed'], 'a wrong code let the browser in')
                shot(page, 'login-error-en-dark')
        # the page opens on the music (Nağmedar plays sound only)
        wait_text(page, 'Aylin Kaya')
        page.wait_for_selector('.media-list, .card', timeout=15000)
        failures.check(STATE['authed'], 'the right code did not let the browser in')
        failures.check(STATE['translation']['VIDEO'] not in visible_text(page), 'the header still offers the videos')
        no_vlc(page, 'library')
        shot(page, 'library-en-dark')
        failures.check(not errors, 'login: ' + '; '.join(errors))
        # the page's own identity
        failures.check(page.evaluate('document.documentElement.lang') == 'en', 'the page language is not the app\'s')
        failures.check('Nağmedar' in page.title(), 'library: the title is "%s"' % page.title())
        manifest = page.evaluate('fetch("/site.webmanifest").then(r => r.json())')
        failures.check(manifest.get('name') == 'Nağmedar', 'the manifest is not Nağmedar\'s: %s' % manifest.get('name'))
        failures.check(page.evaluate('!!document.querySelector("#logo")'), 'the header has no mark')
        logo = page.evaluate('document.querySelector("#logo").naturalWidth')
        failures.check(logo > 0, 'the header mark did not load')
        # the player bar
        wait_text(page, 'Gece Yarısı')
        no_vlc(page, 'player')
        shot(page, 'player-en-dark')
        # About: the attribution is there and is the only place VLC is named
        page.goto(secure + '#/about')
        wait_text(page, STATE['translation']['LIBRARIES'])
        text = visible_text(page)
        failures.check('VideoLAN' in text and 'General Public License' in text, 'About does not say it is based on VLC under the GPL')
        failures.check(page.evaluate('!!document.querySelector("#attribution")'), 'About has no #attribution')
        outside = page.evaluate('(() => { const a = document.querySelector("#attribution"); '
                                'const c = document.body.cloneNode(true); const x = c.querySelector("#attribution"); if (x) x.remove(); '
                                'return c.innerText; })()')
        failures.check(not re.search(r'\bVLC\b', outside), 'About: "VLC" outside the attribution: %s' % outside[:200].replace('\n', ' '))
        shot(page, 'about-en-dark')
        ctx.close()

        # 3. Turkish, light, on a desktop and a phone
        STATE['authed'] = True
        STATE['polled'] = False
        for name, width, height, mobile, scheme in (('desktop', 1280, 760, False, 'light'), ('phone', 390, 800, True, 'light')):
            STATE['polled'] = False
            ctx, page, errors = context('tr', scheme, width, height, mobile)
            ctx.add_cookies([{'name': 'nagmedar-session', 'value': 'ok', 'url': secure}])
            page.goto(secure)
            wait_text(page, 'Aylin Kaya')
            if mobile:  # the player is a button on a phone; it opens over the page
                page.click('.mini-player-fab')
            wait_text(page, 'Gece Yarısı')
            no_vlc(page, 'library-tr-' + name)
            shot(page, 'library-tr-%s-%s' % (name, scheme))
            failures.check(page.evaluate('document.documentElement.lang') == 'tr', 'the page language is not Turkish')
            failures.check(not errors, 'library-tr: ' + '; '.join(errors))
            page.goto(secure + '#/audio/artists')
            if mobile:  # the first navigation only closes the player
                page.goto(secure + '#/audio/artists')
            wait_text(page, 'Aylin Kaya')
            shot(page, 'artists-tr-%s-%s' % (name, scheme))
            ctx.close()
        browser.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dist', required=True)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--channel', default='')
    args = parser.parse_args()
    STATE['dist'] = os.path.abspath(args.dist)
    STATE['repo'] = os.path.abspath(args.repo)
    STATE['out'] = os.path.abspath(args.out)
    os.makedirs(args.out, exist_ok=True)
    cert_dir = tempfile.mkdtemp()
    key, crt = os.path.join(cert_dir, 'key.pem'), os.path.join(cert_dir, 'cert.pem')
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', key, '-out', crt, '-days', '2',
                    '-subj', '/CN=localhost'], check=True, capture_output=True)
    secure = serve(0, (crt, key))
    plain = serve(0)
    failures = Failures()
    try:
        walk(args, secure.server_address[1], plain.server_address[1], failures)
    except Exception as error:  # a page that never shows is a failure too
        failures.check(False, 'the walk stopped: %s' % error)
    if failures:
        sys.exit(1)
    print('Uzaktan erişim web arayüzü: %s' % ', '.join(sorted(f for f in os.listdir(args.out) if f.endswith('.png'))))


if __name__ == '__main__':
    main()
