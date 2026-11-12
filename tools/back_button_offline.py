"""back_button_offline.py <emulator_smoke.py>: the walk's Back-button check (1.0.4) against a pretended device.

No emulator: adb is replaced by a device that keeps a stack of open pages, a navigation bar with a Back button and a
screen it can draw whole or shrunk. Each case says what the system and the app do and what the check must report."""
import argparse
import importlib.util
import io
import re
import sys
import tempfile
import time

from PIL import Image, ImageDraw

spec = importlib.util.spec_from_file_location("smoke", sys.argv[1])
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
time.sleep = lambda s: None

PKG = "app.nagmedar.debug"
W, H = 1080, 2400
BACK = (154, 2274, 444, 2400)


class Device:
    def __init__(self, shrinks, predictive=True, hold_commits=False, in_windows=True, button=BACK, tap_works=True, windows_flag=True):
        self.pages = ["podcast-page", "episode-detail"]
        self.shrinks, self.predictive, self.hold_commits = shrinks, predictive, hold_commits
        self.in_windows, self.button, self.tap_works, self.windows_flag = in_windows, button, tap_works, windows_flag
        self.held = None
        self.log = []
        self.mini = False
        self.lost_taps = 0

    def hit(self, x, y):
        b = self.button
        return b is not None and b[0] <= x <= b[2] and b[1] <= y <= b[3]

    def node(self, rid, bounds, pkg=PKG, text="", clickable="false"):
        return ('<node index="0" text="%s" resource-id="%s" class="android.view.View" package="%s" content-desc="" '
                'clickable="%s" scrollable="false" bounds="[%d,%d][%d,%d]" />' % ((text, rid, pkg, clickable) + bounds))

    def app_nodes(self):
        out = [self.node("", (0, 0, W, H)), self.node("", (0, 2200, 270, 2274), text="Music")]
        if self.pages:
            top = self.pages[-1]
            out.append(self.node("%s:id/%s" % (PKG, top), (0, 0, W, H) if top == "phone-player" else (0, 170, W, H)))
        if self.mini and "phone-player" not in self.pages:
            out.append(self.node("%s:id/mini-player" % PKG, (24, 1980, 1056, 2180)))
        return "".join(out)

    def app_xml(self):
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><hierarchy rotation="0">%s</hierarchy>' % self.app_nodes()

    def windows_xml(self):
        bar = self.node("com.android.systemui:id/back", self.button, pkg="com.android.systemui", clickable="true") if self.in_windows and self.button else ""
        home = self.node("com.android.systemui:id/home", (470, 2274, 610, 2400), pkg="com.android.systemui", clickable="true")
        if not self.windows_flag:
            return self.app_xml()
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><displays><display id="0">'
                '<window index="0" title="app"><hierarchy rotation="0">%s</hierarchy></window>'
                '<window index="1" title="NavigationBar0"><hierarchy rotation="0">%s%s</hierarchy></window></display></displays>'
                % (self.app_nodes(), bar, home))

    def screen(self, shrunk=False, pressed=False, tick=0):
        im = Image.new("RGB", (W, H), (250, 250, 252))
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, W, 60), fill=(240, 240, 244))
        if self.pages:
            page = Image.new("RGB", (W, H - 170), (250, 250, 252))
            pd = ImageDraw.Draw(page)
            for i in range(14):
                pd.rectangle((60, 120 + i * 150, 700 - (i % 3) * 90, 150 + i * 150), fill=(30, 30, 40))
            # a seek bar's thumb that moves by itself
            pd.ellipse((200 + tick * 12, 1900, 240 + tick * 12, 1940), fill=(70, 90, 160))
            if shrunk:
                d.rectangle((0, 170, W, H), fill=(70, 70, 76))
                small = page.resize((int(W * 0.9), int((H - 170) * 0.9)))
                im.paste(small, (int(W * 0.05) + 63, 170 + int((H - 170) * 0.05)))
            else:
                im.paste(page, (0, 170))
        d.rectangle((0, 2274, W, H), fill=(240, 240, 244))
        if pressed:
            d.ellipse((240, 2290, 360, 2390), fill=(200, 200, 210))
        return im

    def png(self, im):
        buf = io.BytesIO()
        im.save(buf, "PNG")
        return buf.getvalue()

    def adb(self, *args, timeout=60, binary=False):
        self.log.append(" ".join(args)[:110])
        if args[0] == "shell":
            cmd = args[1]
            if cmd.startswith("uiautomator dump --windows"):
                return 0, "UI hierchary dumped to: /sdcard/ui-windows.xml"
            if cmd.startswith("uiautomator dump"):
                return 0, "UI hierchary dumped to: /sdcard/ui.xml"
            m = re.search(r"input motionevent DOWN (\d+) (\d+)", cmd)
            if m:
                hit = self.hit(int(m.group(1)), int(m.group(2)))
                self.held = self.png(self.screen(shrunk=hit and self.predictive and self.shrinks, pressed=hit, tick=1))
                if hit and self.hold_commits and self.pages:
                    self.pages.pop()
                return 0, ""
            m = re.match(r"input swipe (\d+) (\d+) \d+ \d+ 100$", cmd)
            if m:
                if self.hit(int(m.group(1)), int(m.group(2))) and self.tap_works and self.pages:
                    self.pages.pop()
                return 0, ""
            m = re.match(r"input tap (\d+) (\d+)$", cmd)
            if m:
                x, y = int(m.group(1)), int(m.group(2))
                if self.mini and "phone-player" not in self.pages and 24 <= x <= 1056 and 1980 <= y <= 2180:
                    if self.lost_taps > 0:
                        self.lost_taps -= 1
                    else:
                        self.pages.append("phone-player")
                return 0, ""
            if cmd == "input keyevent 4":
                if self.pages:
                    self.pages.pop()
                return 0, ""
            if cmd.startswith("pidof"):
                return 0, "4321"
            return 0, ""
        if args[0] == "exec-out":
            if args[1] == "cat /sdcard/ui.xml":
                return 0, self.app_xml()
            if args[1] == "cat /sdcard/ui-windows.xml":
                return 0, self.windows_xml()
            if args[1] == "cat /sdcard/nag-held.png":
                return (0, self.held) if self.held else (1, b"")
            if args[1] == "screencap -p":
                return 0, self.png(self.screen(tick=2))
        return 0, (b"" if binary else "")


def run(name, expect, **kw):
    out = tempfile.mkdtemp(prefix="bb-")
    a = argparse.Namespace(apk="x.apk", package=PKG, out=out, res="/nonexistent", media="", music="", feed="", auto_apk="",
                           build="debug", passes="back-button", adb="adb", deadline_min=40, source="offline", branch="")
    s = smoke.Smoke(a)
    dev = Device(**kw)
    s.adb = dev.adb
    s.log = lambda msg: None
    s.cur_pass, s.lang = "phone-en", "en"
    s.strings = {"en": {"nag_nav_more": "More"}, "tr": {}}
    s.back_button_check("the episode's page", "episode-detail")
    checks = {c["name"]: c["ok"] for c in s.checks}
    press = s.back_presses[-1] if s.back_presses else None
    got = {"whole": checks.get("the episode's page stays whole while the system's Back button is held"),
           "closes": checks.get("the system's Back button closes the episode's page"),
           "pages": list(dev.pages), "warned": any("was not found" in w for w in s.warnings),
           "keys": sum(1 for l in dev.log if l == "shell input keyevent 4")}
    ok = all(got[k] == v for k, v in expect.items())
    print("%s %s\n     %s\n     change %s, button %s, let go %s, tap %s; animations left %s" % (
        "ok  " if ok else "FAIL", name, got, None if not press or press["change"] is None else "%.1f %%" % (press["change"] * 100),
        press and press["button"], press and press["let_go"], press and press["tap"],
        [l for l in dev.log if "animator_duration_scale" in l][-1][-1:]))
    if not ok:
        print("     expected", expect)
    return ok, s


results = []
results.append(run("Android 16+, 1.0.3: the page shrinks under the held button; a tap closes it",
                   {"whole": False, "closes": True, "pages": ["podcast-page"], "warned": False, "keys": 0}, shrinks=True)[0])
ok, s = run("Android 16+, 1.0.4: whole under the button; a tap closes it",
            {"whole": True, "closes": True, "pages": ["podcast-page"], "warned": False, "keys": 0}, shrinks=False)
results.append(ok)
results.append(run("Android 15: no predictive button; whole, a tap closes it",
                   {"whole": True, "closes": True, "pages": ["podcast-page"], "warned": False, "keys": 0}, shrinks=True, predictive=False)[0])
results.append(run("the hold's release is Back already (no tap needed)",
                   {"whole": True, "closes": True, "pages": ["podcast-page"], "warned": False, "keys": 0}, shrinks=False, hold_commits=True)[0])
results.append(run("no --windows hierarchy, the guessed place is the button: read as usual",
                   {"whole": False, "closes": True, "pages": ["podcast-page"], "warned": False, "keys": 0}, shrinks=True, windows_flag=False)[0])
results.append(run("no button anywhere (gesture navigation): a warning, Back by the key, no check",
                   {"whole": None, "closes": None, "pages": ["podcast-page"], "warned": True, "keys": 1}, shrinks=True, button=None)[0])
results.append(run("the button found but Back does nothing: the check fails, the walk goes on by the key",
                   {"whole": True, "closes": False, "pages": ["podcast-page"], "warned": False, "keys": 1}, shrinks=False, tap_works=False)[0])
# the full player: a tap on the mini player's words opens it (the first tap lost), the button takes it down
out = tempfile.mkdtemp(prefix="bb-")
a = argparse.Namespace(apk="x.apk", package=PKG, out=out, res="/nonexistent", media="", music="", feed="", auto_apk="",
                       build="debug", passes="back-button", adb="adb", deadline_min=40, source="offline", branch="")
p = smoke.Smoke(a)
dev = Device(shrinks=False)
dev.pages, dev.mini, dev.lost_taps = [], True, 1
p.adb, p.log = dev.adb, (lambda msg: None)
p.cur_pass, p.lang, p.strings = "phone-en", "en", {"en": {"nag_nav_more": "More"}, "tr": {}}
p.player_back_button()
names = [(c["name"], c["ok"]) for c in p.checks]
player_ok = names == [("a tap on the mini player opens the full player", True),
                      ("the full player stays whole while the system's Back button is held", True),
                      ("the system's Back button closes the full player", True)] and dev.pages == []
print("%s the full player: %s, steps %s" % ("ok  " if player_ok else "FAIL", names, [st["title"] for st in p.steps]))
results.append(player_ok)
# the report's section
s.cur_pass = "all"
s.finish()
text = open(s.out + "/report.md", encoding="utf-8").read()
section = text[text.index("## The system's Back button"):].split("\n\n")[0:2]
print("\n".join(section))
print("ALL OK" if all(results) else "SOME FAILED")
