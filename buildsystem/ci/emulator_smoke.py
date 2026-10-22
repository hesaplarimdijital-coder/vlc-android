#!/usr/bin/env python3
"""Emulator smoke test of the Nağmedar app (adb + uiautomator dump + input; no extra APKs).

Installs the debug APK on the running emulator, walks the app like a user would and, after
every step, saves a screenshot, the UI hierarchy and a verdict from logcat (crashes, ANRs,
StrictMode violations). The walk, in passes (a size and a language each):

  tablet-en  first run (every step), then each top-level destination, Settings and its pages
  phone-en   the same on a phone-sized screen with media granted and test files scanned, the
             first-run flow again, opening files for playback, the command palette (Ctrl+K),
             the shortcut list, rotation, background and foreground
  phone-tr   every destination and Settings in Turkish

Destinations are found by their labels: every nag_nav_* string of app/nagmedar-ui's
strings.xml (in the pass's language) that shows up in the UI hierarchy is visited, so a
destination added later is visited without a change here. The Nağmedar native libraries exist
for arm64 only: on this x86_64 emulator they are missing, and the app must say so and go on.

Exit status 1 when the app crashed, hung (ANR) or the driver itself failed; the report
(report.md, report.json), annotations.txt and the evidence go to the --out directory.
"""
import argparse
import collections
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

try:
    from PIL import Image
except Exception:  # Pillow is optional: screenshots are then kept at full size
    Image = None

PERMISSIONS_MEDIA = ["android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO",
                     "android.permission.READ_MEDIA_IMAGES"]
# nag_nav_* keys that are not destinations to tap in the loop
NAV_SKIP = {"nag_nav_settings", "nag_nav_more", "nag_nav_search"}
LOG_RE = re.compile(r"^(\S+ \S+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*: (.*)$")
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


class Node:
    def __init__(self, el):
        a = el.attrib
        self.text = a.get("text", "")
        self.desc = a.get("content-desc", "")
        self.cls = a.get("class", "")
        self.pkg = a.get("package", "")
        self.rid = a.get("resource-id", "")
        self.clickable = a.get("clickable") == "true"
        m = BOUNDS_RE.match(a.get("bounds", ""))
        self.b = tuple(int(g) for g in m.groups()) if m else (0, 0, 0, 0)

    @property
    def center(self):
        return (self.b[0] + self.b[2]) // 2, (self.b[1] + self.b[3]) // 2

    @property
    def area(self):
        return max(0, self.b[2] - self.b[0]) * max(0, self.b[3] - self.b[1])


def unescape(s):
    return (s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@")
            .replace("\\?", "?").replace("\\u2026", "…").replace("\\u2212", "−"))


def read_strings(res_dir, locale):
    """name -> text of one locale's strings.xml ('' = English)."""
    path = os.path.join(res_dir, "values-%s" % locale if locale else "values", "strings.xml")
    out = {}
    if not os.path.exists(path):
        return out
    for el in ET.parse(path).getroot().iter("string"):
        out[el.get("name")] = unescape("".join(el.itertext()))
    return out


def slug(s):
    return re.sub(r"[^A-Za-z0-9]+", "-", s).strip("-").lower()[:48] or "step"


class Smoke:
    def __init__(self, a):
        self.a = a
        self.pkg = a.package
        self.out = os.path.abspath(a.out)
        self.adb_bin = a.adb
        self.t0 = time.time()
        self.deadline = self.t0 + a.deadline_min * 60
        self.n = 0
        self.steps = []
        self.crashes = []
        self.errors = []
        self.warnings = []
        self.strict = collections.Counter()
        self.strict_total = 0
        self.app_errors = collections.Counter()
        self.native_warn = collections.OrderedDict()
        self.seen = set()
        self.pids = set()
        self.cur_pass = "setup"
        self.launcher = None
        self.last_sig = None
        self.relaunches = 0
        for d in ("shots", "dumps", "raw"):
            os.makedirs(os.path.join(self.out, d), exist_ok=True)
        self.logfile = open(os.path.join(self.out, "logcat-warn.txt"), "w", encoding="utf-8")
        self.logbytes = 0
        res = a.res
        self.strings = {"en": read_strings(res, ""), "tr": read_strings(res, "tr")}

    # ---------------------------------------------------------------- adb

    def adb(self, *args, timeout=60, binary=False):
        try:
            r = subprocess.run([self.adb_bin, *args], capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return 124, b"" if binary else ""
        out = r.stdout if binary else r.stdout.decode("utf-8", "replace")
        if not binary and r.stderr:
            out += r.stderr.decode("utf-8", "replace")
        return r.returncode, out

    def sh(self, cmd, timeout=60):
        return self.adb("shell", cmd, timeout=timeout)[1].strip()

    def log(self, msg):
        print("[%5.0fs] %s" % (time.time() - self.t0, msg), flush=True)

    def over_time(self):
        return time.time() > self.deadline

    # ---------------------------------------------------------------- device

    def wait_boot(self, first_sleep=0, timeout=420):
        if first_sleep:
            time.sleep(first_sleep)
        end = time.time() + timeout
        while time.time() < end:
            self.adb("wait-for-device", timeout=60)
            if self.sh("getprop sys.boot_completed", timeout=15) == "1" and "package:" in self.sh("pm path android", timeout=20):
                return True
            time.sleep(3)
        return False

    def prepare_device(self):
        for attempt in range(5):
            if self.wait_boot(timeout=300):
                break
            self.log("device not ready, attempt %d" % (attempt + 1))
        else:
            raise RuntimeError("the emulator did not finish booting")
        for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.sh("settings put global %s 0" % k)
        self.sh("settings put global stay_on_while_plugged_in 7")
        self.sh("svc power stayon true")
        self.sh("input keyevent 82")
        self.sh("settings put system accelerometer_rotation 0")
        self.sh("settings put system user_rotation 0")
        info = {k: self.sh("getprop " + p) for k, p in (("model", "ro.product.model"), ("sdk", "ro.build.version.sdk"),
                                                         ("abi", "ro.product.cpu.abi"), ("build", "ro.build.display.id"))}
        info["size"] = self.sh("wm size")
        info["density"] = self.sh("wm density")
        self.device = info
        self.log("device: %s" % info)

    def install(self):
        apk = self.a.apk
        if os.path.isdir(apk):
            apks = sorted(glob.glob(os.path.join(apk, "**", "*.apk"), recursive=True))
            pick = [p for p in apks if "x86_64" in p] or apks
            if not pick:
                raise RuntimeError("no APK under " + apk)
            apk = pick[0]
        self.log("installing %s (%.0f MB)" % (apk, os.path.getsize(apk) / 1e6))
        for attempt in range(3):
            rc, out = self.adb("install", "-r", "-t", apk, timeout=420)
            if rc == 0 and "Success" in out:
                break
            self.log("install failed (%s): %s" % (rc, out.strip()[-300:]))
            time.sleep(5)
        else:
            raise RuntimeError("adb install failed")
        self.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % self.pkg)
        rc, out = self.adb("shell", "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER %s" % self.pkg)
        lines = [l.strip() for l in out.splitlines() if "/" in l and not l.startswith("priority")]
        if not lines:
            raise RuntimeError("no launcher activity for %s: %s" % (self.pkg, out.strip()))
        self.launcher = lines[-1]
        self.log("launcher activity: %s" % self.launcher)

    def launch(self, force=True):
        if force:
            self.sh("am force-stop " + self.pkg)
            time.sleep(1)
        out = self.sh("am start -W -n %s" % self.launcher, timeout=90)
        if "Error" in out:
            self.warnings.append("am start: " + out[:300])
        time.sleep(2)

    def set_display(self, size=None, density=None):
        self.sh("wm size %s" % (size or "reset"))
        self.sh("wm density %s" % (density or "reset"))
        time.sleep(2)
        self.log("display: %s %s" % (self.sh("wm size").replace("\n", " "), self.sh("wm density").replace("\n", " ")))

    def rotate(self, rotation):
        self.sh("settings put system user_rotation %d" % rotation)
        time.sleep(2)

    def combo(self, *codes):
        rc, out = self.adb("shell", "input keycombination " + " ".join(str(c) for c in codes))
        if rc != 0 or "rror" in out or "nknown" in out:
            self.warnings.append("input keycombination %s failed: %s" % (codes, out.strip()[:200]))
            return False
        return True

    def key(self, code):
        self.sh("input keyevent %s" % code)

    def set_locale(self, tag):
        """tr-TR for the app (Android 13+ per-app language); the system's as a rooted fallback."""
        self.sh("cmd locale set-app-locales %s --locales %s" % (self.pkg, tag))
        time.sleep(1)
        self.launch()
        if self.ui_in_language("tr" if tag.startswith("tr") else "en"):
            return True
        self.log("per-app locale did not take; trying the system locale (adb root)")
        self.adb("root", timeout=60)
        time.sleep(3)
        self.sh("setprop persist.sys.locale %s" % tag)
        self.sh("setprop ctl.restart zygote")
        if not self.wait_boot(first_sleep=15):
            self.errors.append("the emulator did not come back after the system locale change")
            return False
        self.sh("input keyevent 82")
        self.launch()
        return self.ui_in_language("tr" if tag.startswith("tr") else "en")

    def ui_in_language(self, lang):
        labels = {v for k, v in self.strings[lang].items() if k.startswith("nag_nav_") and "%" not in v}
        other = "en" if lang == "tr" else "tr"
        mine_only = labels - {v for k, v in self.strings[other].items() if k.startswith("nag_nav_")}
        for _ in range(3):
            nodes = self.nodes(self.dump())
            seen = {n.text for n in nodes} | {n.desc for n in nodes}
            if seen & mine_only:
                return True
            time.sleep(2)
        return False

    # ---------------------------------------------------------------- ui

    def dump(self):
        for _ in range(3):
            rc, out = self.adb("shell", "uiautomator dump /sdcard/ui.xml", timeout=60)
            if rc == 0 and "dumped" in out:
                rc, xml = self.adb("exec-out", "cat /sdcard/ui.xml", timeout=30)
                if rc == 0 and xml.lstrip().startswith("<?xml"):
                    return xml
            time.sleep(1.5)
        return None

    @staticmethod
    def nodes(xml):
        if not xml:
            return []
        try:
            return [Node(e) for e in ET.fromstring(xml).iter("node")]
        except ET.ParseError:
            return []

    @staticmethod
    def signature(nodes):
        return hashlib.md5("|".join("%s~%s~%s" % (n.text, n.desc, n.b) for n in nodes).encode()).hexdigest()

    def settle(self, minimum=0.8):
        """Waits until two hierarchy dumps in a row agree (at most five tries); returns the last dump."""
        time.sleep(minimum)
        prev, xml = None, None
        for _ in range(5):
            xml = self.dump()
            sig = self.signature(self.nodes(xml)) if xml else None
            if sig and sig == prev:
                break
            prev = sig
            time.sleep(0.6)
        return xml

    def find(self, nodes, label):
        """Best node for a label: exact text or description, clickable and in the navigation first."""
        pat = re.compile(r"^%s(\s*[,·\n].*)?$" % re.escape(label), re.S | re.I)
        best, best_score = None, None
        w = max((n.b[2] for n in nodes), default=0)
        h = max((n.b[3] for n in nodes), default=0)
        for n in nodes:
            if n.pkg != self.pkg or n.area == 0:
                continue
            if not (pat.match(n.text.strip()) or pat.match(n.desc.strip())):
                continue
            cx, cy = n.center
            nav = (h and cy > h * 0.85) or (w and cx < w * 0.28)
            score = (n.clickable, bool(nav), -n.area)
            if best_score is None or score > best_score:
                best, best_score = n, score
        return best

    def tap(self, node):
        x, y = node.center
        self.sh("input tap %d %d" % (x, y))

    def screen_size(self, nodes):
        w = max((n.b[2] for n in nodes), default=1080)
        h = max((n.b[3] for n in nodes), default=1920)
        return w, h

    def swipe_up(self, nodes):
        w, h = self.screen_size(nodes)
        self.sh("input swipe %d %d %d %d 350" % (w // 2, int(h * 0.75), w // 2, int(h * 0.25)))

    def swipe_down(self, nodes):
        w, h = self.screen_size(nodes)
        self.sh("input swipe %d %d %d %d 250" % (w // 2, int(h * 0.25), w // 2, int(h * 0.80)))

    def system_dialogs(self, nodes):
        """Permission and error dialogs: the permission ones are allowed, the error ones are findings."""
        for n in nodes:
            if n.rid.endswith("permission_allow_button") or n.rid.endswith("permission_allow_foreground_only_button"):
                self.warnings.append("[%s] a permission dialog was shown and allowed" % self.cur_pass)
                self.tap(n)
                time.sleep(1)
                return True
        for n in nodes:
            if n.text == "Got it" and n.pkg in ("android", "com.android.systemui"):  # the full-screen notice of the player
                self.tap(n)
                time.sleep(1)
                return True
        text = " ".join(n.text for n in nodes if n.pkg in ("android", "com.android.systemui"))
        if re.search(r"isn.t responding|keeps stopping|has stopped", text):
            kind = "ANR dialog" if "responding" in text else "crash dialog"
            self.add_crash(kind, "The system showed: " + text[:300], "dialog")
            for n in nodes:
                if n.rid == "android:id/aerr_close":
                    self.tap(n)
            self.key(4)
            return True
        return False

    # ---------------------------------------------------------------- logcat

    def read_logs(self):
        """Logcat since the last call: new crashes, ANRs, StrictMode violations; then clears it."""
        pid_out = self.sh("pidof " + self.pkg, timeout=15)
        pids = set(pid_out.split())
        self.pids |= pids
        _, main = self.adb("logcat", "-d", "-v", "threadtime", timeout=90)
        _, crash = self.adb("logcat", "-b", "crash", "-d", "-v", "threadtime", timeout=60)
        self.adb("logcat", "-b", "all", "-c", timeout=30)
        found = []
        for text in (main, crash):
            found += self.scan(text.splitlines())
        return found, bool(pids)

    def scan(self, lines):
        parsed = [LOG_RE.match(l) for l in lines]
        recs = [(m.group(2), m.group(3), m.group(4), m.group(5).strip(), m.group(6)) for m in parsed if m]
        found = []
        pkg = self.pkg
        i = 0
        while i < len(recs):
            pid, tid, lvl, tag, msg = recs[i]
            if tag == "AndroidRuntime" and "FATAL EXCEPTION" in msg:
                block = [msg]
                j = i + 1
                while j < len(recs) and recs[j][0] == pid and recs[j][3] == "AndroidRuntime":
                    block.append(recs[j][4])
                    j += 1
                text = "\n".join(block)
                if "Process: %s" % pkg in text:
                    found.append(self.add_crash("FATAL EXCEPTION", text, "java"))
                i = j
                continue
            if tag in ("DEBUG", "crash_dump64", "crash_dump32") and (">>> %s" % pkg) in msg:
                block = [msg]
                j = i + 1
                while j < len(recs) and recs[j][0] == pid and recs[j][3] == tag and len(block) < 120:
                    block.append(recs[j][4])
                    j += 1
                found.append(self.add_crash("native crash", "\n".join(block), "native"))
                i = j
                continue
            if tag == "ActivityManager" and msg.startswith("ANR in %s" % pkg):
                block = [msg]
                j = i + 1
                while j < len(recs) and recs[j][3] == "ActivityManager" and len(block) < 25 and not recs[j][4].startswith("ANR in"):
                    block.append(recs[j][4])
                    j += 1
                found.append(self.add_crash("ANR", "\n".join(block), "anr"))
                i = j
                continue
            if tag == "StrictMode" and "StrictMode policy violation" in msg:
                self.strict_total += 1
                m = re.search(r"android\.os\.strictmode\.(\w+)", msg)
                cls, frame = (m.group(1) if m else "?"), "?"
                j = i + 1
                while j < len(recs) and recs[j][3] == "StrictMode" and j < i + 40:
                    m = re.search(r"android\.os\.strictmode\.(\w+)", recs[j][4])
                    if m and cls == "?":
                        cls = m.group(1)
                    m = re.search(r"at ((?:app\.nagmedar|org\.videolan)[\w.$<>]+)", recs[j][4])
                    if m and frame == "?":
                        frame = m.group(1)
                    j += 1
                self.strict[(cls, frame)] += 1
                i = j
                continue
            if lvl in "EF" and (pid in self.pids):
                self.app_errors["%s: %s" % (tag, msg[:140])] += 1
            if lvl in "WEF" and re.search(r"UnsatisfiedLinkError|dlopen failed|couldn't find \"lib|library \"lib\S+\" not found", msg) \
                    and (pid in self.pids or pkg in msg):
                self.native_warn.setdefault(msg[:220], 0)
                self.native_warn[msg[:220]] += 1
            if lvl in "WEF" and (pid in self.pids or pkg in msg) and self.logbytes < 6_000_000:
                line = "%s %s/%s: %s\n" % (pid, lvl, tag, msg)
                self.logfile.write(line)
                self.logbytes += len(line)
            i += 1
        return [f for f in found if f]

    def add_crash(self, kind, text, family):
        key = hashlib.md5((kind + text[:600]).encode()).hexdigest()
        if key in self.seen:
            return None
        self.seen.add(key)
        c = {"kind": kind, "text": text, "family": family, "step": self.n, "pass": self.cur_pass}
        self.crashes.append(c)
        return c

    # ---------------------------------------------------------------- steps

    def step(self, title, action=None, wait=0.8, shot=True):
        """Does the action, waits for the screen to settle, saves evidence and checks the logs."""
        self.n += 1
        n = self.n
        name = "%03d-%s-%s" % (n, self.cur_pass, slug(title))
        t = time.time()
        note = []
        try:
            if action:
                r = action()
                if isinstance(r, str):
                    note.append(r)
        except Exception as e:  # a failing action is a driver problem, not the app's
            note.append("action failed: %s" % e)
            self.errors.append("[%s] %s: %s" % (self.cur_pass, title, e))
        xml = self.settle(wait)
        nodes = self.nodes(xml)
        if self.system_dialogs(nodes):
            xml = self.settle(0.5)
            nodes = self.nodes(xml)
        sig = self.signature(nodes) if nodes else None
        if xml is None:
            note.append("no UI dump (the screen never went idle?)")
            self.warnings.append("[%s] step %d %s: no UI dump" % (self.cur_pass, n, title))
        else:
            with open(os.path.join(self.out, "dumps", name + ".xml"), "w", encoding="utf-8") as f:
                f.write(xml)
        shotfile = None
        if shot:
            shotfile = self.screenshot(name)
        crashes, alive = self.read_logs()
        status = "ok"
        if crashes:
            status = "FAIL"
        elif not alive:
            status = "FAIL"
            c = self.add_crash("process gone", "No process %s after step %d (%s) and no crash in the log; "
                               "it was killed or exited." % (self.pkg, n, title), "gone")
            if c:
                crashes = [c]
        if status == "FAIL" and self.relaunches < 6:
            self.relaunches += 1
            note.append("app relaunched after the failure")
            self.launch()
            self.settle(1.0)
        pk = sorted({x.pkg for x in nodes if x.pkg})
        if nodes and self.pkg not in pk and status == "ok":
            status = "warn"
            note.append("the app is not in the foreground (windows: %s)" % ", ".join(pk))
        changed = sig is not None and sig != self.last_sig
        self.last_sig = sig
        self.steps.append({"n": n, "pass": self.cur_pass, "title": title, "status": status, "shot": shotfile,
                           "dump": "dumps/%s.xml" % name if xml else None, "changed": changed,
                           "seconds": round(time.time() - t, 1), "note": "; ".join(note)})
        self.log("step %d [%s] %s -> %s %s" % (n, self.cur_pass, title, status, "; ".join(note)))
        if n % 10 == 0 or status == "FAIL":
            self.publish("step %d" % n)
        return nodes

    def screenshot(self, name):
        rc, data = self.adb("exec-out", "screencap -p", timeout=60, binary=True)
        if rc != 0 or len(data) < 1000:
            self.warnings.append("screenshot failed: " + name)
            return None
        rel = "shots/%s.png" % name
        path = os.path.join(self.out, rel)
        raw = os.path.join(self.out, "raw", name + ".png")
        with open(raw, "wb") as f:
            f.write(data)
        try:
            if Image is None:
                raise RuntimeError
            im = Image.open(raw)
            if im.width > 1280:
                im = im.resize((1280, int(im.height * 1280 / im.width)), Image.LANCZOS)
            im.convert("RGB").save(path, "PNG", optimize=True)
            os.remove(raw)
        except Exception:
            shutil.move(raw, path)
        return rel

    # ---------------------------------------------------------------- scenarios

    def nav_labels(self, lang):
        """label -> string name of every destination label of the language (long and short)."""
        s = self.strings[lang]
        return {v: k for k, v in s.items() if k.startswith("nag_nav_") and k not in NAV_SKIP and "%" not in v}

    def open_label(self, label, via_more=True):
        """Taps the control with the label; through More when it is not in sight. Returns True when tapped."""
        nodes = self.nodes(self.dump())
        n = self.find(nodes, label)
        if not n and via_more:
            more = self.find(nodes, self.strings[self.lang]["nag_nav_more"])
            if more:
                self.tap(more)
                time.sleep(1)
                nodes = self.nodes(self.settle(0.8))
                n = self.find(nodes, label)
        if n:
            self.tap(n)
            return True
        return False

    def visit_destinations(self, title_prefix="Open"):
        labels = self.nav_labels(self.lang)
        more_label = self.strings[self.lang]["nag_nav_more"]
        visited = set()
        nodes = self.nodes(self.dump())
        more_tried = False
        side_scrolls = 0
        for _ in range(60):
            if self.over_time():
                break
            todo = [l for l in labels if l not in visited]
            target = None
            for l in todo:
                n = self.find(nodes, l)
                if n:
                    target = (l, n)
                    break
            if target:
                label, node = target
                visited.add(label)
                nodes = self.step("%s %s" % (title_prefix, label), lambda nd=node: self.tap(nd))
                title_seen = any(x.text == label and not x.clickable for x in nodes)
                if not title_seen:
                    self.steps[-1]["note"] += ("; " if self.steps[-1]["note"] else "") + "page title not seen"
                more_tried = False
                continue
            more = self.find(nodes, more_label)
            w, h = self.screen_size(nodes)
            if w > 1500 and side_scrolls < 3 and any(l not in visited for l in labels):
                # a tablet's sidebar is a list that scrolls: more destinations below TV Lab
                side_scrolls += 1
                self.sh("input swipe %d %d %d %d 350" % (w // 10, int(h * 0.75), w // 10, int(h * 0.30)))
                nodes = self.nodes(self.settle(0.6))
                continue
            if more and not more_tried:
                more_tried = True
                nodes = self.step("Open %s (overflow)" % more_label, lambda nd=more: self.tap(nd))
                continue
            break
        missing = [l for l in labels if l not in visited]
        # destinations that are not on this size's screens (e.g. a phone's bottom bar has five) were
        # reached through More; the ones still missing are listed for the report
        if missing:
            self.warnings.append("[%s] labels never seen in the UI: %s" % (self.cur_pass, ", ".join(sorted(missing))))
        return visited

    def scroll_page(self, title, max_swipes=24):
        """Scrolls the current page to its end, one step each (stops when it stops moving)."""
        nodes = self.nodes(self.dump())
        prev = None
        for i in range(max_swipes):
            if self.over_time():
                break
            nodes = self.step("%s scroll %d" % (title, i + 1), lambda nd=nodes: self.swipe_up(nd), wait=0.6)
            sig = self.signature([x for x in nodes if x.pkg == self.pkg])
            if sig == prev:
                break
            prev = sig

    def scroll_to_top(self):
        nodes = self.nodes(self.dump())
        for _ in range(30):
            self.swipe_down(nodes)
        time.sleep(0.8)

    def settings_pass(self, full=True):
        s = self.strings[self.lang]
        if not self.open_label(s["nag_nav_settings"]):
            self.warnings.append("[%s] Settings not found in the UI" % self.cur_pass)
            return
        self.step("Settings")
        if full:
            self.scroll_page("Settings", 24 if self.cur_pass == "tablet-en" else 10)
        for key, title in (("nag_eq_open", "Equaliser"), ("nag_settings_about", "About"),
                           ("nag_settings_classic_settings", "Classic settings")):
            if not full or key not in s:
                continue
            if self.open_by_scrolling(s[key]):
                self.step("Open " + title)
                self.key(4)
                self.step("Back from " + title)
            else:
                self.warnings.append("[%s] no '%s' control found in Settings" % (self.cur_pass, s[key]))

    def open_by_scrolling(self, label):
        self.scroll_to_top()
        nodes = self.nodes(self.dump())
        for _ in range(30):
            n = self.find(nodes, label)
            if n:
                self.tap(n)
                return True
            self.swipe_up(nodes)
            time.sleep(0.5)
            nodes = self.nodes(self.dump())
        return False

    @staticmethod
    def fmt_regex(fmt):
        """A string resource with %1$d / %3$s placeholders as a regular expression."""
        return re.compile("^" + re.sub(r"%\d\\\$[ds]", ".+", re.escape(fmt)) + "$")

    def first_run_walk(self, title="First run"):
        """Presses Continue / Start (else Skip) through the first-run flow while its screens show."""
        s = self.strings[self.lang]
        markers = [self.fmt_regex(s["nag_fr_progress"]), self.fmt_regex(s["nag_fr_step"])]
        btn = [re.compile(r"^(%s|%s)\b" % (re.escape(s["nag_fr_continue"]), re.escape(s["nag_fr_finish"])), re.I),
               re.compile(r"^%s$" % re.escape(s["nag_fr_skip"]), re.I)]
        for i in range(10):
            nodes = self.nodes(self.dump())
            in_flow = any(m.match(x.text.strip()) or m.match(x.desc.strip()) for x in nodes for m in markers) or \
                any(x.text.strip() == s["nag_fr_skip"] for x in nodes)
            if not in_flow:
                if i == 0:
                    self.warnings.append("[%s] no first-run flow on screen" % self.cur_pass)
                break
            target = None
            for rx in btn:
                for x in nodes:
                    if x.pkg == self.pkg and x.area and (rx.match(x.text.strip()) or rx.match(x.desc.strip())):
                        target = x
                        break
                if target:
                    break
            if not target:
                self.warnings.append("[%s] first-run step %d has no Continue/Start/Skip button" % (self.cur_pass, i + 1))
                break
            self.step("%s step %d (%s)" % (title, i + 1, target.text.strip() or target.desc.strip()),
                      lambda t=target: self.tap(t), wait=1.0)

    # ---------------------------------------------------------------- passes

    def pass_tablet_en(self):
        self.cur_pass, self.lang = "tablet-en", "en"
        self.set_display(None, None)
        self.rotate(0)
        self.launch()
        self.step("Launch (first run)")
        self.first_run_walk()
        self.step("After the first run")
        self.visit_destinations()
        self.settings_pass(full=True)

    def pass_phone_en(self):
        self.cur_pass, self.lang = "phone-en", "en"
        s = self.strings["en"]
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2400", "420")
        self.prepare_media()
        self.launch()
        self.step("Launch (phone)")
        self.visit_destinations()
        self.settings_pass(full=True)
        self.open_label(s["nag_nav_settings"])
        # the first run again, with media access granted and test files to scan
        if self.open_by_scrolling(s["nag_settings_first_run_again"]):
            self.step("Show the first run again")
            self.first_run_walk("First run again")
            time.sleep(8)
            self.step("After the scan")
        else:
            self.warnings.append("[phone-en] 'first run again' not found in Settings")
        self.visit_destinations("Reopen")
        self.playback()
        self.palette()
        self.rotation_and_background()

    def pass_phone_tr(self):
        self.cur_pass, self.lang = "phone-tr", "tr"
        if not self.set_locale("tr-TR"):
            self.warnings.append("[phone-tr] the Turkish interface could not be set; the pass was skipped")
            return
        self.step("Launch (Turkish)")
        self.visit_destinations()
        self.settings_pass(full=True)
        self.sh("cmd locale set-app-locales %s --locales en-US" % self.pkg)

    def prepare_media(self):
        if not self.a.media or not os.path.isdir(self.a.media):
            self.warnings.append("no test media: the library stays empty")
            return
        for perm in PERMISSIONS_MEDIA:
            self.sh("pm grant %s %s" % (self.pkg, perm))
        for f, dest in (("tone.mp3", "/sdcard/Music/"), ("smoke.mp4", "/sdcard/Movies/")):
            src = os.path.join(self.a.media, f)
            if os.path.exists(src):
                self.sh("mkdir -p " + dest)
                self.adb("push", src, dest + f, timeout=120)
                self.sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://%s%s" % (dest, f))

    def playback(self):
        for f, mime, title in (("/sdcard/Movies/smoke.mp4", "video/mp4", "video"), ("/sdcard/Music/tone.mp3", "audio/mpeg", "audio")):
            if self.over_time():
                return
            if not self.sh("ls %s 2>/dev/null" % f):
                continue
            self.step("Open the %s file" % title,
                      lambda f=f, m=mime: self.sh("am start -a android.intent.action.VIEW -d file://%s -t %s -p %s" % (f, m, self.pkg), timeout=60),
                      wait=6)
            self.step("Playing the %s file" % title, None, wait=4)
            self.key(4)
            self.step("Back from the %s file" % title)
        self.key(3)
        self.step("Home during playback")
        self.launch(force=False)
        self.step("Back to the app after playback")

    def palette(self):
        s = self.strings["en"]
        before = self.nodes(self.dump())
        sig0 = self.signature(before)
        ok = self.combo(113, 39)  # Ctrl+K
        nodes = self.step("Command palette (Ctrl+K)")
        if ok and self.signature(nodes) == sig0:
            n = self.find(nodes, s["nag_search"]) or self.find(nodes, s["nag_nav_search"])
            self.warnings.append("[phone-en] Ctrl+K changed nothing on screen%s" % ("; the Search control was used instead" if n else ""))
            if n:
                nodes = self.step("Search (tapped)", lambda: self.tap(n))
        self.step("Palette query", lambda: self.sh("input text mus"))
        self.step("Palette: select the first result", lambda: self.key(66), wait=1.2)  # Enter
        self.key(111)  # Escape
        self.step("Escape")
        if self.combo(113, 76):  # Ctrl+/ the shortcut list
            self.step("Shortcut list (Ctrl+/)")
            self.key(111)
        if self.combo(113, 55):  # Ctrl+, settings
            self.step("Settings (Ctrl+,)")

    def rotation_and_background(self):
        self.rotate(1)
        self.step("Rotated to landscape")
        self.rotate(0)
        self.step("Rotated back")
        self.key(3)
        time.sleep(2)
        self.step("In the background (home screen)")
        self.launch(force=False)
        self.step("Foreground again")
        self.sh("am send-trim-memory %s RUNNING_CRITICAL" % self.pkg)
        self.step("After a memory-trim request")
        self.sh("am kill %s" % self.pkg)
        time.sleep(1)
        self.launch(force=False)
        self.step("Cold start after the process was killed")

    # ---------------------------------------------------------------- report

    def publish(self, why):
        """Force-pushes the evidence so far to the branch (env EMULATOR_PUSH_URL), so a hung or cancelled run leaves some."""
        url = os.environ.get("EMULATOR_PUSH_URL")
        if not url:
            return
        try:
            self.logfile.flush()
            self.write_progress()
            work = os.path.join(os.path.dirname(self.out), "emulator-push")
            shutil.rmtree(work, ignore_errors=True)
            shutil.copytree(self.out, work, ignore=shutil.ignore_patterns("raw", ".git"))
            env = dict(os.environ, GIT_AUTHOR_NAME="emulator", GIT_AUTHOR_EMAIL="ci@invalid",
                       GIT_COMMITTER_NAME="emulator", GIT_COMMITTER_EMAIL="ci@invalid")
            for cmd in (["git", "init", "-q"], ["git", "checkout", "-q", "--orphan", "ci/emulator"], ["git", "add", "-A"],
                        ["git", "commit", "-q", "-m", "Emulator smoke test, partial: " + why],
                        ["git", "push", "-q", "--force", url, "HEAD:refs/heads/ci/emulator"]):
                subprocess.run(cmd, cwd=work, env=env, capture_output=True, timeout=180)
        except Exception as e:
            self.log("publish failed: %r" % e)

    def write_progress(self):
        with open(os.path.join(self.out, "progress.txt"), "w", encoding="utf-8") as f:
            f.write("%d s, %d steps, pass %s, %d crashes\n" % (time.time() - self.t0, len(self.steps), self.cur_pass, len(self.crashes)))
            for st in self.steps[-8:]:
                f.write("%d %s %s %s\n" % (st["n"], st["pass"], st["title"], st["status"]))

    def finish(self):
        self.logfile.close()
        n_fail = sum(1 for s in self.steps if s["status"] == "FAIL")
        verdict = "FAILED" if (self.crashes or self.errors) else "passed"
        dur = int(time.time() - self.t0)
        md = []
        md.append("# Emulator smoke test: %s\n" % verdict)
        md.append("- app: `%s`, APK `%s`" % (self.pkg, os.path.basename(self.a.apk.rstrip("/"))))
        md.append("- device: %s" % ", ".join("%s=%s" % kv for kv in getattr(self, "device", {}).items()))
        md.append("- source: %s" % (self.a.source or "?"))
        md.append("- %d steps in %d min %d s; %d crash/ANR finding(s), %d driver error(s), %d StrictMode violation(s)\n"
                  % (len(self.steps), dur // 60, dur % 60, len(self.crashes), len(self.errors), self.strict_total))
        if self.crashes:
            md.append("## Crashes and ANRs\n")
            for i, c in enumerate(self.crashes, 1):
                md.append("### %d. %s (pass %s, step %d)\n" % (i, c["kind"], c["pass"], c["step"]))
                md.append("```\n%s\n```\n" % c["text"][:6000])
        if self.errors:
            md.append("## Driver errors\n")
            md += ["- %s" % e for e in self.errors]
            md.append("")
        md.append("## Steps\n")
        md.append("| # | pass | step | result | screenshot | note |")
        md.append("|---|------|------|--------|------------|------|")
        for s in self.steps:
            shot = "[%s](%s)" % (os.path.basename(s["shot"]), s["shot"]) if s["shot"] else ""
            md.append("| %d | %s | %s | %s | %s | %s |" % (s["n"], s["pass"], s["title"], s["status"], shot,
                                                         s["note"].replace("|", "/")))
        md.append("")
        md.append("## Warnings\n")
        if self.warnings:
            md += ["- %s" % w for w in self.warnings]
        else:
            md.append("None.")
        md.append("")
        md.append("### Missing native libraries (arm64-only libraries on an x86_64 emulator)\n")
        if self.native_warn:
            md += ["- %dx `%s`" % (v, k) for k, v in self.native_warn.items()]
        else:
            md.append("No link errors were logged.")
        md.append("")
        md.append("### StrictMode violations: %d\n" % self.strict_total)
        for (cls, frame), cnt in self.strict.most_common(25):
            md.append("- %dx %s at `%s`" % (cnt, cls, frame))
        md.append("")
        md.append("### Error-level log lines of the app (top 25)\n")
        for k, v in self.app_errors.most_common(25):
            md.append("- %dx `%s`" % (v, k))
        md.append("")
        with open(os.path.join(self.out, "report.md"), "w", encoding="utf-8") as f:
            f.write("\n".join(md))
        with open(os.path.join(self.out, "report.json"), "w", encoding="utf-8") as f:
            json.dump({"verdict": verdict, "steps": self.steps, "crashes": self.crashes, "errors": self.errors,
                       "warnings": self.warnings, "strictmode": self.strict_total, "seconds": dur}, f, indent=1)
        ann = []
        for c in self.crashes:
            head = c["text"].splitlines()
            body = " | ".join(head[:12])
            ann.append("::error title=Emulator %s (%s, step %d)::%s" % (c["kind"], c["pass"], c["step"], enc(body[:900])))
        for e in self.errors:
            ann.append("::error title=Emulator driver::%s" % enc(e[:600]))
        summary = "%s: %d steps, %d crash/ANR, %d StrictMode, %d warnings, %d min. Evidence: branch %s, report.md" % (
            verdict, len(self.steps), len(self.crashes), self.strict_total, len(self.warnings), dur // 60,
            self.a.branch or "ci/emulator")
        ann.append("::notice title=Emulator Ekran görüntüleri ve rapor::%s" % enc(summary))
        with open(os.path.join(self.out, "annotations.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(ann) + "\n")
        print("\n".join(md[:12]))
        return 1 if (self.crashes or self.errors) else 0


def enc(s):
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--apk", required=True, help="APK file or a directory with it")
    p.add_argument("--package", default="app.nagmedar.debug")
    p.add_argument("--out", default="emulator-out")
    p.add_argument("--res", default="application/nagmedar-ui/src/main/res")
    p.add_argument("--media", default="", help="directory with tone.mp3 and smoke.mp4 to push to the device")
    p.add_argument("--passes", default="tablet-en,phone-en,phone-tr")
    p.add_argument("--adb", default="adb")
    p.add_argument("--deadline-min", type=int, default=40, help="stop starting new work after this many minutes")
    p.add_argument("--source", default="", help="what was tested (ref and commit), for the report")
    p.add_argument("--branch", default="", help="branch the evidence is published to, for the annotation")
    a = p.parse_args()
    s = Smoke(a)
    try:
        s.write_progress()
        s.publish("start")
        s.prepare_device()
        s.log("device ready")
        s.publish("device ready")
        s.install()
        for name in a.passes.split(","):
            if s.over_time():
                s.warnings.append("time is up before pass %s" % name)
                break
            try:
                getattr(s, "pass_" + name.strip().replace("-", "_"))()
            except Exception as e:
                s.errors.append("pass %s: %s" % (name, e))
                s.log("pass %s failed: %r" % (name, e))
    except Exception as e:
        s.errors.append("setup: %s" % e)
    shutil.rmtree(os.path.join(s.out, "raw"), ignore_errors=True)
    return s.finish()


if __name__ == "__main__":
    sys.exit(main())
