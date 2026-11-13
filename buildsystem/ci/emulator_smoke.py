#!/usr/bin/env python3
"""Emulator smoke test of the Nağmedar app (adb + uiautomator dump + input; no extra APKs).

Installs the APK on the running emulator, walks the app like a user would and, after every step,
saves a screenshot, the UI hierarchy and a verdict from logcat (crashes, ANRs, StrictMode
violations). The walk, in passes (a size and a language each):

  tablet-en    first run (every step), then each top-level destination, Settings and its pages
  phone-en     the same on a phone-sized screen with media granted and test files scanned, the
               first-run flow again, then 1.0's phone: the bottom bar (Music · Podcast · Radio · ⋯
               and nothing else), 1.0.1's pages (⋯ opens the "More" page, Settings is a page of its
               own without the bottom bar and with a back arrow, Back returns to "More", the top
               bar's colour the same at rest, during and after a scroll), a track's ⋯ sheet, a
               track played, the mini player (1.0.2: the tablet's bar, two rows) dragged up into the
               full player and down again, a sideways swipe on its first row to the next track and
               one along its seek bar that must not change it, a podcast followed (--feed) and an
               episode's page whose play button must not move after it opened, the Radio; then a
               music file opened from outside, the command palette (Ctrl+K), the shortcut list,
               rotation, background and foreground; 1.0.3, with --auto-apk (the personal build):
               Android Auto's tree read by a MediaBrowserCompat (application/benchmark's
               AutoBrowseTest): the four tabs, the walk's music and podcast, the radio's lists
  workstation  the tablet's Music: the inspector shown, Ctrl+I hides and shows it, the table's
               column chooser (a title's long press) opens and Escape closes it; 1.0.2: with a file
               of Bu cihaz playing, the transport bar's "open the player" opens the big player over
               the window and Back closes it
  phone-tr     every destination and Settings in Turkish, the bottom bar and the ⋯ pages
  sheet-trials (on demand) the phone's track sheet dragged down again and again by `input swipe`, by
               drag() and by a stalled swipe, with the moves the system delivered (sheet-trials.md)
  glass        (on demand, debug build) the user's glass decision: the phone in three-button
               navigation, dark and light, the Music list and the Playlists' grid scrolled so rows and
               covers pass under the bars, the mini player showing, for the chrome's three modes (A
               solid, B the bottom on one glass, C the kit's: and the top bar fading to glass), the
               app relaunched in each by the debug-only extra app.nagmedar.debug.CHROME_GLASS
               (glass/README.md names each screenshot)
  glass-b      (on demand, either build) the product's glass B (the user's decision of 6 Nov): a cold
               start into each phone page with a list under the bottom chrome, its first frames read,
               then each page with the mini player and its list under the bars, Sıradaki and the full
               player, dark and light; the bar's and the card's glass must blur wherever they show
               (glass-b/README.md names each screenshot)
  back-button  (on demand, either build; an older app by the workflow's ref input) 1.0.4: the system's
               Back button of the three-button navigation touched where the owner touches it, on what
               the phone opens over a list: an episode's page, the podcast's page under it, the full
               player; each at rest, with the button held and after a tap of it (back_button_check);
               and the bar read while an episode plays: a seek bar, never "live" (episode_plays)
  page-opens   (on demand, either build; an older app by the workflow's ref input) 1.0.5: what the phone
               opens over a list, opened with the system's animations on and nothing in the player, on a
               screen of the owner's phone's size, each read by its pixels: a show's page once its feed is
               followed, from its row (twice), an episode over it and back, from its tile (twice); the
               page must be on screen, in the UI's tree (opening_check) and scroll: its episodes follow
               a swipe by most of its length (scrolls_check)
  notice-swipe (on demand, either build; an older app by the workflow's ref input) 1.0.6: a notice
               swiped as a finger swipes it, by raw touch events written to the emulator's touch screen
               (adb as root), on a screen of the owner's phone's size with the animations on: swiped far
               and lifted at rest it leaves; flung and lifted in motion (the last move and the lift
               together) it leaves; swiped a little and lifted in motion it comes back. Read by the
               bar's place in the pixels 0.3 s, 0.9 s and 1.6 s after the lift (notice_swipe)
  search       (on demand, either build; an older app by the workflow's ref input) 1.0.7: the search with
               the walk's library in the device's Music (64 tracks, eight albums, four artists, and the
               tone). On the tablet's screen the palette (Ctrl+K) is asked for an artist's name, an
               album's, a word of every title and a title with its artist: every row it lists is read
               (the selection moved down through the list) and compared with what the library holds by
               the search's own rule, every word in the title, the artist or the album (search_expected);
               an artist and an album found are opened in the Music view; a kind that shows its best
               only says how many it has and Enter on that line lists them. On the phone's screen the
               search page is asked for the artist: its artists, albums and tracks, and an album opened.
               The report's "The search" lists every query with what was listed and what was missing
  auto-shots   (on demand; the workflow boots an Android Automotive image for it) 1.0.6: the tree
               Android Auto browses, drawn by a car's own media app bound to the playback service:
               its tabs, the music's albums, an album, a track played and the car's player, the
               podcasts' directory, the radio's countries and a station, what was played, the car's
               search. Android Auto itself needs a phone signed in to the Play Store and cannot run
               here; these are the car system's drawings of the same tree

1.0.3: the phone job (phone-en, glass) runs in three-button navigation, as the user's phone: the
bottom bar's colour must continue behind the Back, Home and Recents buttons (the shell's band).
1.0.4: and Back is pressed as he presses it. The walk's Back is a key (`input keyevent 4`), and a
key is not his button: since Android 16 a touch on the system's Back button starts a predictive
Back whose progress the system runs by itself while the button is down. 1.0.3's detail pages and
its player followed that progress as a finger's and shrank into a rounded card before they left
(the owner, 12 Nov: "podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi"); no key shows
it. phone-en now leaves the full player, the episode's page and the podcast's by the button itself,
found in the windows' hierarchy: held, the page must stay whole; tapped, it must close (the report's
"The system's Back button"). 1.0.4 also (the owner: "podcast çaldığında canlı yayın diyor müzik
barı"): phone-en ends by playing an episode of the walk's feed from its page, a stream as his are,
and reads the bar: a seek bar with its times, never a station's "live" line (episode_plays).
1.0.5 (the owner, 12 Nov, with a recording: "Podcastte sayfası böyle oluyor"): his show's page slid in
and was gone, every pixel of it, in the frame its opening ended, in 1.0.3 and 1.0.4. The walk opens
its pages with the system's animations off (a transition then is a snap) and mostly over a playing
track, and never saw it; a release is walked through page-opens too, where they are on.
The product draws the phone's bottom chrome on one glass
(glass B); with the system's animations removed, as the walk runs, the app draws it opaque, so
phone-en's glass_b_check turns them on to see it blur: switched on at run time over the search
page's covers, moved, and from a cold start (the walk's folder has a cover.jpg for that).

1.0.3: the workflow runs the passes in two jobs (tablet-en, workstation and phone-tr; phone-en), so
each pass brings what it needs: phone-en goes through a fresh app's first run, workstation and
phone-tr push the walk's media and show the first run again when no phone-en came before them
(ensure_library). The system's own ANRs and crashes (the emulator's System UI, launcher, setup
apps) are recorded in the report, waited out (closed when the same process hangs again) and the
step done once more when the dialog took its tap; the app's (any of its processes) fail the run.

1.0.1 (the user's crash in Bu cihaz): phone-en, phone-tr and workstation also walk This device /
Bu cihaz: the internal storage, its Music folder (the walk's files), a file played from it, back up
to the drive and to the places, the sort menu's orders and hidden files, Home and back with the
folder open, rotation.

Every dump of the app is also searched for the words of what 1.0 deleted (films, series, videos,
the Home page, TV Lab). Around a long list's scroll and the player's drag the frames are counted
(dumpsys gfxinfo: janky frames and the 50/90/95/99th percentiles, informational on an emulator).
Each 1.0 check is a row of the report's "1.0 checks"; a failed one fails the run, as do a crash,
an ANR and, for the debug build (--build debug), a StrictMode violation of the app.

Destinations are found by their labels: every nag_nav_* string of app/nagmedar-ui's
strings.xml (in the pass's language) that shows up in the UI hierarchy is visited, so a
destination added later is visited without a change here. The Nağmedar native libraries exist
for arm64 only: on this x86_64 emulator they are missing, and the app must say so and go on.

Exit status 1 when the app crashed, hung (ANR), a check failed or the driver itself failed; the
report (report.md, report.json), annotations.txt and the evidence go to the --out directory.

The offline checks of the dialog handling and of step()'s retry (no device) are in the 1.0.3 walk
notes; drags whose distance matters use drag(), never `input swipe` (see drag()).
"""
import argparse
import collections
import glob
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

try:
    from PIL import Image, ImageChops
except Exception:  # Pillow is optional: screenshots are then kept at full size
    Image = None

PERMISSIONS_MEDIA = ["android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO",
                     "android.permission.READ_MEDIA_IMAGES"]
# nag_nav_* keys that are not destinations to tap in the loop
NAV_SKIP = {"nag_nav_settings", "nag_nav_more", "nag_nav_search", "nag_nav_back"}
LOG_RE = re.compile(r"^(\S+ \S+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*: (.*)$")
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
# what 1.0 deleted (films, series, videos, the Home page, TV Lab) and 1.0.2 (Whisper's transcripts, the Lab's SP-05)
# must not be named on the app's screens
GONE_RE = re.compile(r"(?<!\w)(Filmler|Diziler|Videolar|Ana sayfa|TV Lab|Films|Movies|TV shows|Videos|Whisper|SP-05)(?!\w)")
# 1.0.3: the system's error dialogs, "<who> isn't responding" (an ANR) and "<who> keeps stopping" / "<who> has stopped"
# (a crash), in English or, when the system's language is Turkish, in Turkish; <who> is an app's label
DIALOG_RE = re.compile(r"^(.+?)\s+(isn.t responding|keeps stopping|has stopped|yanıt vermiyor|durmaya devam ediyor|durdu)\b", re.S)
# a hierarchy that may hold one of the dialogs system_dialogs() deals with (a cheap test before parsing it)
DIALOG_HINT = re.compile(r"aerr_|permission_allow|isn.t responding|keeps stopping|has stopped|yanıt vermiyor|durmaya devam|durdu|Got it")
# the app's label, as the system's dialogs name it
APP_LABELS = ("Nağmedar", "Nagmedar")
TIME_RE = re.compile(r"\d:\d\d")
# 1.0.3: the system's three-button navigation (an overlay of the emulator's System UI) and the gesture one it replaces
THREE_BUTTON_OVERLAY = "com.android.internal.systemui.navbar.threebutton"
GESTURAL_OVERLAY = "com.android.internal.systemui.navbar.gestural"
# 1.0.3: the debug-only launch extra that draws the phone's chrome on glass (shell/ChromeGlass.kt)
CHROME_GLASS_EXTRA = "app.nagmedar.debug.CHROME_GLASS"
# 1.0.4: the system's Back button in the windows' hierarchy (System UI's navigation bar, the launcher's taskbar)
BACK_ID_RE = re.compile(r":id/back$")
# 1.0.4: a page counts as whole under the held Back button while no more than this share of it differs from the page
# at rest (a seek bar's thumb, a time that changed); shrunk towards nine tenths, a fifth of it and more does
BACK_HELD_WHOLE = 0.02
# 1.0.5: a page is on screen when more than this share of its place (between the bars) is not its ground colour (an
# empty page: 0.0 %; the owner's show's page: some 11 to 14 %)
PAGE_INK = 0.01
# 1.0.5: a page scrolls when an episode in sight moves up by this share of a swipe's length at least (a show's page
# under the walk's 842 px swipe: 565 to 825 px; 1.0.4's page, which no longer took the scroll: 75 to 82 px)
PAGE_FOLLOWS = 0.4
# 1.0.6: a swiped notice is back at its place, or still aside, by this many px of its left edge
NOTICE_STILL = 12
# 1.0.6: "the lift with the last move" is a lift the dispatcher sent this soon after it (a frame is 16.7 ms; the runs so
# far: 0.0 to 1 ms), "lifted at rest" one that came at least this long after (the runs: 289 and 327 ms)
NOTICE_TOGETHER_MS, NOTICE_AT_REST_MS = 4, 100
# 1.0.6: Android Automotive's media app, which draws a media service's tree as Android Auto does (pass_auto_shots)
CAR_MEDIA = "com.android.car.media"
# 1.0.6: the Linux input events of a touch screen (multi-touch protocol B, with slots; A without)
EV_SYN, EV_KEY, EV_ABS = 0, 1, 3
SYN_REPORT, SYN_MT_REPORT, BTN_TOUCH = 0, 2, 0x14a
ABS_MT = {"SLOT": 0x2f, "TOUCH_MAJOR": 0x30, "POSITION_X": 0x35, "POSITION_Y": 0x36, "TRACKING_ID": 0x39, "PRESSURE": 0x3a}


class Node:
    def __init__(self, el):
        a = el.attrib
        self.text = a.get("text", "")
        self.desc = a.get("content-desc", "")
        self.cls = a.get("class", "")
        self.pkg = a.get("package", "")
        self.rid = a.get("resource-id", "")
        self.clickable = a.get("clickable") == "true"
        self.scrollable = a.get("scrollable") == "true"
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
        self.strict_blocks = collections.OrderedDict()  # (class, frame) -> the first whole stack seen
        self.strict_dropbox_count = collections.Counter()  # from the dropbox, with the stack
        self.app_errors = collections.Counter()
        self.native_warn = collections.OrderedDict()
        self.seen = set()
        self.pids = set()
        self.cur_pass = "setup"
        self.launcher = None
        self.last_sig = None
        self.relaunches = 0
        self.checks = []  # 1.0's checks: pass, name, ok, detail, step
        self.jank = []  # dumpsys gfxinfo around a gesture
        self.gone = collections.OrderedDict()  # a deleted feature's word -> where it was first seen
        # 1.0.3: the system's own ANRs and crashes (System UI, the launcher: the emulator's), recorded, not failures
        self.system_events = []
        self.dialogs_seen = []  # what dump() dealt with: "permission", "notice", "app" or "system", in order
        self.step_mark = 0  # len(dialogs_seen) when the last step began
        self.system_waited = set()  # the system processes whose "not responding" was waited for once
        self.last_input = None  # (function, argument, len(dialogs_seen), the step before it): remember_input()
        self.last_app_sig = None  # the app's part of the screen at the end of the last step
        self.library_ready = False  # the walk's media pushed and granted, and scanned (the first run with them)
        self.sheet_trials = []  # the sheet-drag pass's trials
        self.back_presses = []  # 1.0.4: what the system's Back button did to each page (back_button_check)
        self.back_system_info = None  # 1.0.4: the system's navigation mode and predictive Back flags (back_system)
        self.page_openings = []  # 1.0.5: the pages opened with the animations on (opening_check)
        self.notice_swipes = []  # 1.0.6: a notice swiped with a finger's raw events (notice_swipe)
        self.raw_touch_why = ""  # 1.0.6: why the touch screen takes no raw events here (raw_touch)
        self.notices_stay = False  # 1.0.6: the system's longer time for notices is in force (pass_notice_swipe)
        self.searches = []  # 1.0.7: what each query of the search pass listed, beside what the library holds (pass_search)
        self.other_app_expected = False  # 1.0.6: the steps are of another app's screen on purpose (pass_auto_shots)
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
        self.unlock()
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
        # Android 17: a build that targets 37 reaches the walk's feed (10.0.2.2, the local network) and the Network page's
        # servers only with the local network permission; elsewhere the grant is refused and changes nothing
        self.sh("pm grant %s android.permission.ACCESS_LOCAL_NETWORK" % self.pkg)
        # what was installed: a release-type build (personal) is neither debuggable nor profileable
        dump = self.sh("dumpsys package %s" % self.pkg, timeout=60)
        keep = [l.strip() for l in dump.splitlines() if re.match(r"\s*(versionCode|versionName|pkgFlags)=", l)]
        self.app_info = "; ".join(dict.fromkeys(keep))
        self.log("installed: %s" % self.app_info)
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
        for attempt in range(3):
            out = self.sh("am start -W -n %s" % self.launcher, timeout=90)
            if "Error" in out:
                self.warnings.append("am start: " + out[:300])
            time.sleep(2)
            # the app's activity resumed on top; else the system was busy: it is waited for and the
            # app started again
            top = [l.strip() for l in self.sh("dumpsys activity activities", timeout=60).splitlines()
                   if re.search(r"(topResumedActivity|mResumedActivity|ResumedActivity:)", l)][:2]
            if any(self.pkg + "/" in l for l in top):
                return
            self.warnings.append("[%s] the app was not in front after it was started (attempt %d): am start said %s; resumed: %s"
                                 % (self.cur_pass, attempt + 1, " ".join(out.split())[:300], " / ".join(top)[:300]))
            self.wait_boot(timeout=180)
            self.unlock()
            # 1.0.3: a system dialog in front (the launcher's ANR) is dealt with by reading the screen
            self.dump()

    def unlock(self):
        """The screen on and the keyguard away. 1.0.3: without the menu key the walk used to send for it, which an
        emulator's focused app without a window (the SDK setup app) never handled: its ANR came back all walk long."""
        self.sh("input keyevent KEYCODE_WAKEUP")
        self.sh("wm dismiss-keyguard")

    def set_display(self, size=None, density=None):
        self.sh("wm size %s" % (size or "reset"))
        self.sh("wm density %s" % (density or "reset"))
        time.sleep(2)
        self.log("display: %s %s" % (self.sh("wm size").replace("\n", " "), self.sh("wm density").replace("\n", " ")))

    def three_button_navigation(self):
        """1.0.3: the system's three-button navigation (Back, Home, Recents), as on the user's phone; True when it is on.
        The emulator's System UI swaps its navigation bar by an overlay; the bar's height is read back from the window."""
        out = self.sh("cmd overlay enable-exclusive --category %s" % THREE_BUTTON_OVERLAY, timeout=60)
        if "rror" in out or "nknown" in out:
            out = self.sh("cmd overlay enable %s" % THREE_BUTTON_OVERLAY, timeout=60)
        time.sleep(3)
        listed = self.sh("cmd overlay list android", timeout=60)
        on = any(THREE_BUTTON_OVERLAY in l and l.strip().startswith("[x]") for l in listed.splitlines())
        self.log("three-button navigation: %s (%s)" % ("on" if on else "NOT on", " ".join(out.split())[:200]))
        if not on:
            self.warnings.append("[%s] three-button navigation could not be turned on: %s" % (self.cur_pass, " ".join(out.split())[:200]))
        return on

    def band_check(self, nodes, what):
        """1.0.3: in three-button navigation the bottom bar's colour continues behind the buttons (the shell's band): the
        pixel under the bar (at a quarter of the width, clear of the buttons' icons) is the bar's own colour, read from
        the last step's screenshot."""
        w, h = self.screen_size(nodes)
        more = [n for n in self.app_nodes(nodes) if n.text.strip() == self.strings[self.lang]["nag_nav_more"] and n.center[1] > h * 0.8]
        if Image is None or not more or not self.steps or not self.steps[-1]["shot"]:
            self.warnings.append("[%s] the band behind the buttons was not read (%s)" % (self.cur_pass, what))
            return
        rel = self.steps[-1]["shot"]
        # the bar's own colour between its first two tabs' labels, and behind the buttons near the window's bottom
        bar = self.pixel(rel, w // 4, more[0].center[1], w)
        band = self.pixel(rel, w // 8, h - 6, w)
        self.check("the bottom bar's colour continues behind the three buttons (%s)" % what, bar is not None and bar == band,
                   "bar %s, behind the buttons %s" % (bar, band))

    def rotate(self, rotation):
        self.sh("settings put system user_rotation %d" % rotation)
        time.sleep(2)

    def combo(self, *codes):
        rc, out = self.adb("shell", "input keycombination " + " ".join(str(c) for c in codes))
        if rc != 0 or "rror" in out or "nknown" in out:
            self.warnings.append("input keycombination %s failed: %s" % (codes, out.strip()[:200]))
            return False
        self.remember_input(lambda c: self.combo(*c), codes)
        return True

    def key(self, code, record=True):
        self.sh("input keyevent %s" % code)
        if record:
            self.remember_input(self.key, code)

    def remember_input(self, fn, arg):
        """1.0.3: the walk's last tap or key, made for the next step: step() sends it once more when a system dialog came
        after it and the app's screen stayed as it was (the dialog took it)."""
        self.last_input = (fn, arg, len(self.dialogs_seen), self.n)

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
        self.unlock()
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
        """The UI hierarchy as XML (None when it could not be read). 1.0.3: a system error or permission dialog in it is
        dealt with first (system_dialogs) and the screen read again, so every reader of the screen sees the app."""
        for _ in range(3):
            xml = self.raw_dump()
            if xml is None or not DIALOG_HINT.search(xml):
                return xml
            what = self.system_dialogs(self.nodes(xml))
            if not what:
                return xml
            self.dialogs_seen.append(what)
        return self.raw_dump()

    def raw_dump(self):
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

    def app_signature(self, nodes):
        """The app's part of the screen, without the times that change by themselves (a player's 1:23)."""
        return self.signature([n for n in nodes if n.pkg == self.pkg and not TIME_RE.search(n.text)])

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

    def tap(self, node, record=True):
        x, y = node.center
        self.sh("input tap %d %d" % (x, y))
        if record:
            self.remember_input(self.tap, node)

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
        """Permission and error dialogs: the permission ones are allowed; an error dialog of the app (an ANR or a crash of
        any of its processes) is a finding that fails the run, one of the system's (System UI, the launcher: the emulator's
        own) is recorded and waited out ("Wait"), and the step it came in is tried once more (step()). Returns what it
        dealt with ("permission", "notice", "app", "system") or None."""
        for n in nodes:
            if n.rid.endswith("permission_allow_button") or n.rid.endswith("permission_allow_foreground_only_button"):
                self.warnings.append("[%s] a permission dialog was shown and allowed" % self.cur_pass)
                self.tap(n, record=False)
                time.sleep(1)
                return "permission"
        for n in nodes:
            if n.text == "Got it" and n.pkg in ("android", "com.android.systemui"):  # the full-screen notice of the player
                self.tap(n, record=False)
                time.sleep(1)
                return "notice"
        sysnodes = [n for n in nodes if n.pkg in ("android", "com.android.systemui")]
        texts = [n.text.strip() for n in sysnodes if n.text.strip()]
        hit = next((m for m in (DIALOG_RE.match(t) for t in texts) if m), None)
        if not hit and not any(n.rid.startswith("android:id/aerr_") for n in sysnodes):
            return None
        who = hit.group(1).strip() if hit else "?"
        anr = (not hit) or ("respond" in hit.group(2) or "yanıt" in hit.group(2))
        text = " ".join(texts)[:300]
        ids = {n.rid: n for n in sysnodes}
        if self.dialog_is_app(who):
            kind = "ANR dialog" if anr else "crash dialog"
            self.add_crash(kind, "The system showed: " + text, "dialog")
            if "android:id/aerr_close" in ids:
                self.tap(ids["android:id/aerr_close"], record=False)
            else:
                self.key(4, record=False)
            time.sleep(1)
            return "app"
        # the system's own: recorded; an ANR is waited out the first time (closing System UI or the launcher restarts it),
        # closed when the same process is not responding again (1.0.3's phone job: the emulator's SDK setup app, without
        # a window, said so every few seconds however often it was waited for); a crash is closed
        again = who in self.system_waited
        wait = ids.get("android:id/aerr_wait") if anr and not again else None
        button = wait or ids.get("android:id/aerr_close")
        how = "Wait" if wait else ("Close app" + (" (not responding again)" if anr else "")) if button else "Back"
        self.system_events.append({"pass": self.cur_pass, "step": self.n + 1, "kind": "ANR" if anr else "crash",
                                   "who": who, "seen": text, "how": "dialog, " + how})
        self.log("system %s dialog (%s): %s; %s" % ("ANR" if anr else "crash", who, text, how))
        if wait:
            self.system_waited.add(who)
        if button:
            self.tap(button, record=False)
        else:
            self.key(4, record=False)
        time.sleep(1.5)
        return "system"

    def dialog_is_app(self, who):
        """Whether an error dialog's [who] names the app (its label, its package or one of its processes)."""
        w = who.strip().strip("\"'“”").lower()
        if not w or w == "?":
            return True  # unnamed: taken as the app's, to be safe
        # the label, the package or one of its processes, also as "Process app.nagmedar.debug:logger" (a process
        # without a label of its own)
        return any(l.lower() in w for l in APP_LABELS) or self.pkg.lower() in w

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
                else:
                    m = re.search(r"Process: (\S+?),", text)
                    self.system_event("crash", m.group(1) if m else "?", " ".join(block[1:3]) or text[:200])
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
            if tag == "ActivityManager" and msg.startswith("ANR in "):
                block = [msg]
                j = i + 1
                while j < len(recs) and recs[j][3] == "ActivityManager" and len(block) < 25 and not recs[j][4].startswith("ANR in"):
                    block.append(recs[j][4])
                    j += 1
                if msg.startswith("ANR in %s" % pkg):
                    found.append(self.add_crash("ANR", "\n".join(block), "anr"))
                else:
                    # 1.0.3: another process's (System UI, the launcher): the emulator's, recorded only
                    reason = next((b for b in block if b.startswith("Reason:")), "")
                    self.system_event("ANR", msg[len("ANR in "):].split()[0] if len(msg) > 7 else "?", reason[:200])
                i = j
                continue
            if tag == "StrictMode" and "StrictMode policy violation" in msg:
                if pid not in self.pids:
                    i += 1
                    continue
                self.strict_total += 1
                m = re.search(r"android\.os\.strictmode\.(\w+)", msg)
                cls, frame, other = (m.group(1) if m else "?"), "?", "?"
                block = [msg]
                j = i + 1
                # the whole stack: the frames of the app's own code come after the framework's, often deep
                while j < len(recs) and recs[j][0] == pid and recs[j][3] == "StrictMode" and len(block) < 160 \
                        and "StrictMode policy violation" not in recs[j][4]:
                    line = recs[j][4]
                    block.append(line)
                    m = re.search(r"android\.os\.strictmode\.(\w+)", line)
                    if m and cls == "?":
                        cls = m.group(1)
                    m = re.search(r"at ((?:app\.nagmedar|org\.videolan)[\w.$<>]+)", line)
                    if m and frame == "?":
                        frame = m.group(1)
                    m = re.search(r"at ((?!android\.|androidx\.|java\.|kotlin\.|kotlinx\.|dalvik\.|com\.android\.|libcore\.|sun\.|okhttp3\.|okio\.)[\w.$<>]+)", line)
                    if m and other == "?":
                        other = m.group(1)
                    j += 1
                if frame == "?":
                    frame = other
                self.strict[(cls, frame)] += 1
                key = (cls, frame)
                if key not in self.strict_blocks:
                    self.strict_blocks[key] = "[%s] %s\n%s" % (self.cur_pass, self.steps[-1]["title"] if self.steps else "setup", "\n".join(block))
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

    def system_event(self, kind, who, detail):
        """1.0.3: an ANR or crash of another process than the app's (the emulator's System UI, launcher …): recorded."""
        self.system_events.append({"pass": self.cur_pass, "step": self.n, "kind": kind, "who": who,
                                   "seen": " ".join(detail.split())[:200], "how": "log"})
        self.log("system %s in the log (%s): %s" % (kind, who, detail[:160]))

    def add_crash(self, kind, text, family):
        key = hashlib.md5((kind + text[:600]).encode()).hexdigest()
        if key in self.seen:
            return None
        self.seen.add(key)
        c = {"kind": kind, "text": text, "family": family, "step": self.n, "pass": self.cur_pass}
        self.crashes.append(c)
        return c

    # ---------------------------------------------------------------- steps

    def step(self, title, action=None, wait=0.8, shot=True, read=True):
        """Does the action, waits for the screen to settle, saves evidence and checks the logs. 1.0.6, read=False: a
        screen that never rests (a car's player while it plays) has no tree to wait for; its picture only."""
        self.n += 1
        n = self.n
        name = "%03d-%s-%s" % (n, self.cur_pass, slug(title))
        t = time.time()
        note = []
        before = self.last_app_sig
        # the tap or key the walk made for this step before calling it (open_label() then step("Music")), if any
        pending = self.last_input if self.last_input and self.last_input[3] == n - 1 else None
        mark = self.step_mark = min(len(self.dialogs_seen), pending[2]) if pending else len(self.dialogs_seen)
        for attempt in (1, 2):
            try:
                if action:
                    r = action()
                    if isinstance(r, str):
                        note.append(r)
            except Exception as e:  # a failing action is a driver problem, not the app's
                note.append("action failed: %s" % e)
                self.errors.append("[%s] %s: %s" % (self.cur_pass, title, e))
            if read:
                # dump() deals with the system's dialogs on the way (system_dialogs)
                xml = self.settle(wait)
            else:
                time.sleep(wait)
                xml = None
            nodes = self.nodes(xml)
            came = [d for d in self.dialogs_seen[mark:] if d == "system"]
            if not came or attempt == 2:
                break
            # 1.0.3: a dialog of the system's own (the emulator's) came during the step and was dealt with: the step once
            # more when it left the app's screen as it was (the dialog took the step's tap, or the one made for it)
            mark = len(self.dialogs_seen)
            redo = action if action is not None else (pending and (lambda p=pending: p[0](p[1])))
            if not redo or self.app_signature(nodes) != before:
                note.append("a system dialog came and was dealt with")
                break
            note.append("a system dialog came and was dealt with: the step once more")
            if action is None:
                redo()
        self.last_input = None
        sig = self.signature(nodes) if nodes else None
        self.last_app_sig = self.app_signature(nodes) if nodes else None
        self.gone_words(nodes, name)
        if xml is None and not read:
            note.append("the picture only: this screen does not rest, so it was not read")
        elif xml is None:
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
        if nodes and self.pkg not in pk and status == "ok" and not self.other_app_expected:
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
        # a car has more than one display, and screencap then says so in front of the picture
        at = data.find(b"\x89PNG\r\n\x1a\n") if rc == 0 else -1
        if at < 0 or len(data) - at < 1000:
            self.warnings.append("screenshot failed: " + name)
            return None
        data = data[at:]
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
                # a tablet's sidebar is a list that scrolls: more destinations below the library's
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
        """Scrolls the page down from its top until [label] shows and taps it. 1.0.3: False once two swipes in a row left
        the page as it was (its end): a control this build does not have (the equaliser, without the engine on an
        x86_64 emulator) cost 30 swipes, three minutes of every Settings pass."""
        self.scroll_to_top()
        nodes = self.nodes(self.dump())
        prev, still = None, 0
        for _ in range(30):
            n = self.find(nodes, label)
            if n:
                self.tap(n)
                return True
            sig = self.app_signature(nodes)
            still = still + 1 if sig == prev else 0
            if still >= 2:
                return False
            prev = sig
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

    # ---------------------------------------------------------------- 1.0 checks

    def check(self, name, ok, detail=""):
        """One of 1.0's checks, with the step it was read at; a failed one fails the run."""
        if not ok and "system" in self.dialogs_seen[self.step_mark:]:
            detail = (detail + "; " if detail else "") + "a system dialog came meanwhile"
        self.checks.append({"pass": self.cur_pass, "name": name, "ok": bool(ok), "detail": detail, "step": self.n})
        self.log("check [%s] %s: %s %s" % (self.cur_pass, name, "ok" if ok else "FAILED", detail))
        if self.steps and not ok:
            st = self.steps[-1]
            st["note"] += ("; " if st["note"] else "") + "check failed: " + name
        return ok

    def gone_words(self, nodes, where):
        # the names of files and folders (Bu cihaz's rows and trail: Android's own "Movies") are the user's, not the app's words
        names = [n.b for n in nodes if n.rid.endswith("device-table") or n.rid.endswith("device-trail")]
        for n in nodes:
            if n.pkg != self.pkg or any(self.inside(n, b) for b in names):
                continue
            for text in (n.text, n.desc):
                m = GONE_RE.search(text)
                if m and m.group(1) not in self.gone:
                    self.gone[m.group(1)] = '%s: "%s"' % (where, text.replace("\n", " ")[:120])

    def app_nodes(self, nodes):
        return [n for n in nodes if n.pkg == self.pkg and n.area]

    def by_id(self, nodes, rid):
        """The app's nodes whose test tag (a resource id, testTagsAsResourceId) is [rid]."""
        return [n for n in self.app_nodes(nodes) if n.rid == rid or n.rid.endswith(":id/" + rid)]

    def texts(self, nodes):
        return {n.text.strip() for n in self.app_nodes(nodes)} | {n.desc.strip() for n in self.app_nodes(nodes)}

    def wait_for(self, find, seconds=10.0):
        """The first dump's nodes for which find(nodes) is true within [seconds]; (nodes, result)."""
        end = time.time() + seconds
        nodes, found = [], None
        while True:
            nodes = self.nodes(self.dump())
            found = find(nodes)
            if found or time.time() > end:
                return nodes, found
            time.sleep(0.7)

    def bottom_bar(self, nodes):
        """The labels of the phone's bottom bar, left to right (the row of the lowest Music label)."""
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        music = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_nav_music"] and n.center[1] > h * 0.8]
        if not music:
            return []
        y = max(n.center[1] for n in music)
        row = [n for n in self.app_nodes(nodes) if n.text.strip() and abs(n.center[1] - y) <= 16]
        return [n.text.strip() for n in sorted(row, key=lambda n: n.b[0])]

    def has_bottom_bar(self, nodes):
        """The phone's bottom bar is on screen: its ⋯ tab's label at the screen's foot (the label is a child of the tab)."""
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        return any(n.text.strip() == s["nag_nav_more"] and n.center[1] > h * 0.85 for n in self.app_nodes(nodes))

    def check_bottom_bar(self, nodes):
        s = self.strings[self.lang]
        want = [s["nag_nav_music"], s["nag_nav_podcasts_short"], s["nag_nav_radio"], s["nag_nav_more"]]
        got = self.bottom_bar(nodes)
        return self.check("bottom bar is %s" % " · ".join(want), got == want, "seen: %s" % (" · ".join(got) or "no bar"))

    def animations(self, on):
        """The system's animation scales: on (1) around the frame counts, as on a device; off (0) elsewhere."""
        for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.sh("settings put global %s %s" % (k, "1" if on else "0"))

    def frames_reset(self):
        self.sh("dumpsys gfxinfo %s reset" % self.pkg, timeout=60)

    def frames_read(self, what):
        """dumpsys gfxinfo's summary since the last reset: frames, janky %, 50/90/95/99th percentiles."""
        out = self.sh("dumpsys gfxinfo %s" % self.pkg, timeout=60)
        r = {"pass": self.cur_pass, "what": what, "step": self.n}
        for key, rx in (("frames", r"Total frames rendered:\s*(\d+)"), ("janky", r"Janky frames:\s*\d+\s*\(([\d.]+)%\)"),
                        ("janky_n", r"Janky frames:\s*(\d+)"), ("p50", r"50th percentile:\s*(\d+)ms"),
                        ("p90", r"90th percentile:\s*(\d+)ms"), ("p95", r"95th percentile:\s*(\d+)ms"),
                        ("p99", r"99th percentile:\s*(\d+)ms")):
            m = re.search(rx, out)
            r[key] = float(m.group(1)) if m else None
        self.jank.append(r)
        self.log("frames [%s] %s: %s" % (self.cur_pass, what, r))
        if r["janky"] is not None and r["janky"] > 40:
            self.warnings.append("[%s] %s: %.1f %% janky frames (over 40 %%; informational on an emulator)" % (self.cur_pass, what, r["janky"]))
        return r

    def back_to_tabs(self):
        """Back while a page of its own (Settings) hides the phone's bottom bar; only where its back arrow shows, never out of the app."""
        nodes = self.nodes(self.dump())
        # a phone's screen only: a tablet has no bottom bar, and Back would leave the app
        if self.screen_size(nodes)[0] >= 1600:
            return
        for _ in range(4):
            if self.has_bottom_bar(nodes) or not self.back_arrow(nodes):
                return
            self.key(4)
            nodes = self.nodes(self.settle(0.8))

    def open_music(self):
        s = self.strings[self.lang]
        self.back_to_tabs()
        if not self.open_label(s["nag_nav_music"], via_more=False):
            self.warnings.append("[%s] Music not found in the UI" % self.cur_pass)
        return self.step("Music")

    def page_title(self, nodes, label):
        """The top bar's title [label]: text that is not a control, in the top tenth of the screen."""
        w, h = self.screen_size(nodes)
        return [n for n in self.app_nodes(nodes) if n.text.strip() == label and not n.clickable and n.center[1] < h * 0.12]

    def back_arrow(self, nodes):
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        return [n for n in self.app_nodes(nodes) if n.desc.strip() == s["nag_nav_back"] and n.center[1] < h * 0.12]

    def phone_pages(self):
        """1.0.1: ⋯ opens the "More" page; Settings is a page of its own; Back returns to "More"; the top bar keeps its colour."""
        s = self.strings[self.lang]
        nodes = self.open_music()
        self.check_bottom_bar(nodes)
        self.top_bar_colour_check(nodes)
        nodes = self.open_music()
        w, h = self.screen_size(nodes)
        more = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_nav_more"] and n.center[1] > h * 0.85]
        if not self.check("⋯ in the bottom bar", bool(more)):
            return
        rows = (s["nag_nav_downloads"], s["nag_nav_settings"], s["nag_nav_playlists"])
        nodes = self.step("Bottom bar ⋯", lambda: self.tap(more[-1]))
        seen = {t.split("\n")[0].strip() for t in self.texts(nodes)}
        self.check("⋯ opens the More page", all(x in seen for x in rows) and bool(self.page_title(nodes, s["nag_nav_more"]))
                   and self.has_bottom_bar(nodes), "seen: %s" % ", ".join(x for x in rows if x in seen))
        settings = self.find(nodes, s["nag_nav_settings"])
        if not self.check("Settings on the More page", settings is not None):
            return
        nodes = self.step("Settings from More", lambda: self.tap(settings), wait=1.2)
        self.check("Settings is a page of its own: no bottom bar", not self.has_bottom_bar(nodes) and bool(self.page_title(nodes, s["nag_nav_settings"])),
                   "bottom bar: %s" % ("shown" if self.has_bottom_bar(nodes) else "none"))
        self.check("Settings has a back arrow", bool(self.back_arrow(nodes)))
        self.key(4)
        nodes = self.step("Back from Settings", wait=1.2)
        self.check("Back from Settings shows More", bool(self.page_title(nodes, s["nag_nav_more"])) and self.has_bottom_bar(nodes),
                   "title: %s" % ", ".join(n.text for n in self.app_nodes(nodes) if n.center[1] < self.screen_size(nodes)[1] * 0.12 and n.text))
        # the arrow does what Back does
        settings = self.find(nodes, s["nag_nav_settings"])
        if settings is not None:
            nodes = self.step("Settings from More again", lambda: self.tap(settings), wait=1.2)
            arrow = self.back_arrow(nodes)
            if arrow:
                nodes = self.step("The back arrow", lambda: self.tap(arrow[0]), wait=1.2)
                self.check("the back arrow returns to More", bool(self.page_title(nodes, s["nag_nav_more"])))

    def pixel(self, rel, x, y, width):
        """The colour at ([x], [y]) of a screen [width] px wide, read from the screenshot [rel]; None without one."""
        if Image is None or not rel:
            return None
        try:
            im = Image.open(os.path.join(self.out, rel)).convert("RGB")
        except Exception:
            return None
        k = im.width / float(width)
        return im.getpixel((min(int(x * k), im.width - 1), min(int(y * k), im.height - 1)))

    def top_bar_colour_check(self, nodes):
        """The top bar's colour (beside the title) at rest, while a finger drags the list and after it: one colour."""
        s = self.strings[self.lang]
        titles = self.page_title(nodes, s["nag_nav_music"])
        w, h = self.screen_size(nodes)
        if Image is None or not titles:
            self.warnings.append("[%s] top bar colour not read (%s)" % (self.cur_pass, "no Pillow" if Image is None else "no title"))
            return
        t = titles[0]
        x, y = t.b[2] - 24, t.center[1]
        rest = self.pixel(self.steps[-1]["shot"], x, y, w)
        # a slow drag, the screen taken while the finger is still on it
        mid_name = "%03d-%s-%s" % (self.n + 1, self.cur_pass, "top-bar-mid-drag")
        self.sh("(input swipe %d %d %d %d 2500 &) ; sleep 1.3 ; screencap -p /sdcard/nag-mid.png" % (w // 2, int(h * 0.75), w // 2, int(h * 0.35)), timeout=60)
        rc, data = self.adb("exec-out", "cat /sdcard/nag-mid.png", timeout=60, binary=True)
        mid = None
        if rc == 0 and len(data) > 1000:
            rel = "shots/%s.png" % mid_name
            with open(os.path.join(self.out, rel), "wb") as f:
                f.write(data)
            mid = self.pixel(rel, x, y, w)
        time.sleep(1.5)
        self.step("Music scrolled (top bar)")
        after = self.pixel(self.steps[-1]["shot"], x, y, w)
        self.check("the top bar keeps its colour while the list scrolls", rest is not None and rest == after and (mid is None or mid == rest),
                   "at rest %s, while dragged %s, after %s" % (rest, mid, after))
        for _ in range(3):
            self.swipe_down(nodes)

    def item_sheet(self, nodes):
        """A track's ⋯ opens its sheet (Add to queue …), Back closes it."""
        s = self.strings[self.lang]
        rx = self.fmt_regex(s["nag_more_actions_for"])
        w, h = self.screen_size(nodes)
        buttons = [n for n in self.app_nodes(nodes) if rx.match(n.desc.strip()) and h * 0.2 < n.center[1] < h * 0.75]
        if not self.check("a track's ⋯ button", bool(buttons)):
            return
        b = buttons[0]
        nodes = self.step("A track's ⋯ (%s)" % b.desc.strip()[:40], lambda: self.tap(b))
        opened = s["nag_add_to_queue"] in self.texts(nodes)
        self.check("a track's ⋯ opens its sheet", opened)
        if opened:
            self.key(4)
            nodes = self.step("Back from the track's sheet")
            self.check("Back closes the track's sheet", s["nag_add_to_queue"] not in self.texts(nodes))
            self.sheet_drag(b)

    def sheet_drag(self, button):
        """1.0.2: the track's sheet again, dragged down from its middle (over its rows, not its handle): it goes.
        1.0.3: dragged once it stands still, by drag() (every point a move of its own), and read until it has gone."""
        s = self.strings[self.lang]
        nodes = self.step("A track's ⋯ again", lambda: self.tap(button))
        rows = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_add_to_queue"]]
        if not self.check("the track's sheet opens again", bool(rows)):
            return
        w, h = self.screen_size(nodes)
        box = self.sheet_still(nodes) or (0, rows[0].b[1], w, h)
        x, y = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
        how = {}
        nodes = self.step("Drag the sheet down from its middle", lambda: how.update(self.drag(x, y, x, int(h * 0.97))), wait=0.5)
        gone = lambda ns: s["nag_add_to_queue"] not in self.texts(ns) and not self.by_id(ns, "sheet")
        if not gone(nodes):
            nodes, _ = self.wait_for(gone, 4)
        self.check("a drag down from the sheet's middle closes it", gone(nodes),
                   "from y=%d of %d, %d moves in %d ms" % (y, h, how.get("moves", 0), how.get("ms", 0)))

    def sheet_still(self, nodes, seconds=3.0):
        """The sheet's bounds once two readings agree (it has finished sliding up), or None without a sheet."""
        prev = self.by_id(nodes, "sheet")
        end = time.time() + seconds
        while prev and time.time() < end:
            cur = self.by_id(self.nodes(self.dump()), "sheet")
            if cur and cur[0].b == prev[0].b:
                return cur[0].b
            prev = cur
        return prev[0].b if prev else None

    def drag(self, x0, y0, x1, y1, moves=12):
        """1.0.3: a finger's drag from (x0, y0) to (x1, y1): down, [moves] moves, up where the last move ended.
        `input swipe x0 y0 x1 y1 300` waits for each move to be handled and sends no more once its 300 ms are up, then
        lifts the finger at (x1, y1): when the app or the emulator stalls on an early move, the finger jumps from there
        to the end as it lifts. That jump is not part of a drag (Compose drags by the moves; the lift is no move), and
        the pause before it leaves no fling: 1.0.2's sheet sprang back and stayed (the evidence: the sheet-trials pass,
        "stalled", 0 of 10 closed on API 37 with the app of master and with a changed one alike; drag 10 of 10). Here
        every point is a move of its own and the lift comes where the last move ended. Returns {"moves", "ms"}."""
        pts = [(x0 + (x1 - x0) * i // moves, y0 + (y1 - y0) * i // moves) for i in range(1, moves + 1)]
        cmds = ["input motionevent DOWN %d %d" % (x0, y0)] + ["input motionevent MOVE %d %d" % p for p in pts] \
            + ["input motionevent UP %d %d" % (x1, y1)]
        t = time.time()
        self.sh(" ; ".join(cmds), timeout=90)
        return {"moves": moves, "ms": int((time.time() - t) * 1000)}

    def music_list(self, nodes):
        """The Music's scrolling list (its biggest scrollable view) or None."""
        lists = [n for n in self.app_nodes(nodes) if n.scrollable]
        return max(lists, key=lambda n: n.area) if lists else None

    def scroll_frames(self, what, nodes):
        """Flings the biggest list down and back and counts the frames."""
        lst = self.music_list(nodes)
        w, h = self.screen_size(nodes)
        if lst is None or lst.b[3] - lst.b[1] < h // 3:
            self.warnings.append("[%s] no long scrolling list for '%s': the middle of the screen is swiped" % (self.cur_pass, what))
            box = (w // 3, int(h * 0.3), w * 2 // 3, int(h * 0.75))
        else:
            box = lst.b
        x = (box[0] + box[2]) // 2
        top, bottom = box[1] + (box[3] - box[1]) // 5, box[3] - (box[3] - box[1]) // 5
        self.animations(True)
        self.frames_reset()
        for direction in (1, 1, 1, -1, -1, -1):
            a, b = (bottom, top) if direction > 0 else (top, bottom)
            self.sh("input swipe %d %d %d %d 150" % (x, a, x, b))
            time.sleep(1.2)
        self.frames_read(what)
        self.animations(False)
        self.step(what + " (after the scroll)")

    def play_track(self, nodes):
        """Plays the first of the walk's tracks (--music, "Profile track …") from the Music's list."""
        # a row of the table, not a card of the "recently played" shelf above it
        shelf = [n.b for n in self.by_id(nodes, "recent-shelf")]
        inside = lambda n: any(b[0] <= n.center[0] <= b[2] and b[1] <= n.center[1] <= b[3] for b in shelf)
        rows = [n for n in self.app_nodes(nodes) if n.text.startswith("Profile track") and not inside(n)]
        if not rows:
            rows = [n for n in self.app_nodes(nodes) if n.text.strip() == "Smoke tone"]
        if not self.check("a track in the Music's list", bool(rows)):
            return []
        row = rows[0]
        self.step("Play %s" % row.text.strip(), lambda: self.tap(row), wait=2)
        nodes, mini = self.wait_for(lambda ns: self.by_id(ns, "mini-player"), 10)
        self.check("the mini player shows the track", bool(mini))
        return nodes

    def mini_title(self, nodes):
        mini = self.by_id(nodes, "mini-player")
        if not mini:
            return None
        b = mini[0].b
        # 1.0.2: the words only, not the times either side of the bar's seek bar (they change every second)
        seek = [n.b for n in self.by_id(nodes, "mini-player-seek")]
        inside = [n for n in self.app_nodes(nodes) if n.text.strip() and b[0] <= n.center[0] <= b[2] and b[1] <= n.center[1] <= b[3]
                  and not any(self.inside(n, sb) for sb in seek)]
        return " / ".join(n.text.strip() for n in inside)

    @staticmethod
    def first_row(bounds):
        """1.0.2: the y of the phone's bar's first row (the artwork, the words, the transport); its seek bar is the second."""
        return bounds[1] + int((bounds[3] - bounds[1]) * 0.28)

    def player_drag(self):
        """The mini player dragged up into the full player and down again; then swiped sideways."""
        nodes = self.nodes(self.dump())
        mini = self.by_id(nodes, "mini-player")
        if not self.check("a mini player to drag", bool(mini)):
            return
        w, h = self.screen_size(nodes)
        # from the words of its first row: the seek bar under them takes a drag of its own
        cx, cy = mini[0].center[0], self.first_row(mini[0].b)
        # each drag's frames alone: counted from just before the finger goes down to a moment after it settles
        self.animations(True)

        def drag(what, x0, y0, x1, y1):
            self.frames_reset()
            self.sh("input swipe %d %d %d %d 450" % (x0, y0, x1, y1))
            time.sleep(1.5)
            self.frames_read(what)

        nodes = self.step("Drag the mini player up", lambda: drag("player drag up", cx, cy, cx, int(h * 0.12)), wait=0.5)
        nodes, up = self.wait_for(lambda ns: self.by_id(ns, "phone-player"), 4)
        tries = 1
        if not up:
            # 1.0.2: once more, should a slow emulator (API 37's frames are mostly late) have lost the first drag
            tries = 2
            nodes = self.step("Drag the mini player up again", lambda: drag("player drag up (again)", cx, cy, cx, int(h * 0.12)), wait=0.5)
            nodes, up = self.wait_for(lambda ns: self.by_id(ns, "phone-player"), 4)
        self.check("dragged up, the full player shows", bool(up), "drags: %d" % tries)
        nodes = self.step("Drag the full player down", lambda: drag("player drag down", w // 2, int(h * 0.3), w // 2, int(h * 0.95)), wait=0.5)
        self.animations(False)
        self.check("dragged down, the mini player again", not self.by_id(nodes, "phone-player") and bool(self.by_id(nodes, "mini-player")))
        before = self.mini_title(nodes)
        mini = self.by_id(nodes, "mini-player")
        if not mini:
            return
        b = mini[0].b
        y = self.first_row(b)
        # from the title's middle towards the start edge (past the 30 % threshold), clear of the buttons
        x0, x1 = b[0] + int((b[2] - b[0]) * 0.5), b[0] + int((b[2] - b[0]) * 0.03)
        changed = lambda ns: (lambda t: t if t and t != before else None)(self.mini_title(ns))
        self.step("Swipe the mini player sideways", lambda: self.sh("input swipe %d %d %d %d 300" % (x0, y, x1, y)), wait=2)
        nodes, after = self.wait_for(changed, 6)
        tries = 1
        if not after:
            # once more, should the first swipe have come while the strip was still settling; a slow emulator can
            # deliver the swipe as a tap, which opens the full player: it is closed first (1.0.1: the walk went on
            # inside the full player and found no Podcasts, Radio or Bu cihaz)
            tries = 2
            if self.by_id(nodes, "phone-player"):
                self.key(4)
                nodes = self.step("Back: the swipe came as a tap and opened the full player")
            self.step("Swipe the mini player sideways again", lambda: self.sh("input swipe %d %d %d %d 300" % (x0, y, x1, y)), wait=2)
            nodes, after = self.wait_for(changed, 6)
        self.check("a sideways swipe changes the track", bool(after), "%s → %s (swipes: %d)" % (before, after or self.mini_title(nodes), tries))
        if self.by_id(nodes, "phone-player"):
            # the walk goes on from the mini player
            self.key(4)
            nodes = self.step("Back: the full player closed")
        # 1.0.2: along the bar's seek bar the same swipe seeks, the track stays
        seek = self.by_id(nodes, "mini-player-seek")
        if self.check("the bar's seek bar", bool(seek)):
            time.sleep(1.5)
            nodes = self.nodes(self.dump())
            before = self.mini_title(nodes)
            sb = self.by_id(nodes, "mini-player-seek")[0].b if self.by_id(nodes, "mini-player-seek") else seek[0].b
            sy = (sb[1] + sb[3]) // 2
            sx0, sx1 = sb[0] + int((sb[2] - sb[0]) * 0.8), sb[0] + int((sb[2] - sb[0]) * 0.2)
            nodes = self.step("Swipe along the bar's seek bar", lambda: self.sh("input swipe %d %d %d %d 300" % (sx0, sy, sx1, sy)), wait=2)
            after = self.mini_title(nodes)
            self.check("a swipe along the seek bar does not change the track", after == before and not self.by_id(nodes, "phone-player"),
                       "%s → %s" % (before, after))
            if self.by_id(nodes, "phone-player"):
                self.key(4)
                self.step("Back: the full player closed")

    # ---------------------------------------------------------------- 1.0.4: the system's Back button

    def system_back_button(self, nodes):
        """1.0.4: the system's Back button of the three-button navigation: the x and y of its centre, the top of its bar
        and how it was found. The app's own dump has the app's window alone, so the hierarchy of every window is read
        (System UI's navigation bar, or the launcher's taskbar); failing that, where AOSP's bar has the button."""
        w, h = self.screen_size(nodes)
        rc, out = self.adb("shell", "uiautomator dump --windows /sdcard/ui-windows.xml", timeout=60)
        if rc == 0 and "dumped" in out:
            rc, xml = self.adb("exec-out", "cat /sdcard/ui-windows.xml", timeout=30)
            backs = [n for n in (self.nodes(xml) if rc == 0 else [])
                     if n.pkg != self.pkg and n.area and BACK_ID_RE.search(n.rid) and n.center[1] > h * 0.9]
            if backs:
                b = max(backs, key=lambda n: n.area)
                return b.center[0], b.center[1], b.b[1], "%s of %s" % (b.rid.split("/")[-1], b.pkg)
        # AOSP's bar, 48 dp high: Back in the middle of the last two thirds of the space before Home
        bar = int(round(48 * w / 411.4))
        return int(w * 0.277), h - bar // 2, h - bar, "a guess (not in the windows' hierarchy)"

    def back_button_held(self, x, y, name):
        """The screen while a finger holds the Back button at ([x], [y]), twice: a moment into the press (a tap's length)
        and a second later (a long press: Android 16's notes have the button's predictive Back start with one); the
        finger is lifted after them. The screenshots [name]-tap and [name]-long, None for one that could not be taken."""
        self.sh("rm -f /sdcard/nag-held-1.png /sdcard/nag-held-2.png ; input motionevent DOWN %d %d ; sleep 0.05 ; "
                "screencap -p /sdcard/nag-held-1.png ; sleep 0.9 ; screencap -p /sdcard/nag-held-2.png ; "
                "input motionevent UP %d %d" % (x, y, x, y), timeout=90)
        shots = []
        for i, moment in ((1, "tap"), (2, "long")):
            rc, data = self.adb("exec-out", "cat /sdcard/nag-held-%d.png" % i, timeout=60, binary=True)
            rel = None
            if rc == 0 and len(data) > 1000:
                rel = "shots/%s-%s.png" % (name, moment)
                with open(os.path.join(self.out, rel), "wb") as f:
                    f.write(data)
            shots.append(rel)
        return shots

    def back_system(self):
        """What the system says of its Back button, for the report: the navigation mode and the flags that name the
        predictive Back (aflags, where the build has it)."""
        mode = self.sh("settings get secure navigation_mode", timeout=30)
        flags = self.sh("aflags list 2>/dev/null | grep -i -E 'predictive_back|three_button|back_anim' | head -20", timeout=60)
        flags = "; ".join(" ".join(l.split()) for l in flags.splitlines() if l.strip())
        return "navigation_mode=%s; flags: %s" % (mode or "?", flags or "none listed")

    def page_change(self, rel_a, rel_b, box, width):
        """How much of [box] (x0, y0, x1, y1 on a screen [width] px wide) differs between the screenshots [rel_a] and
        [rel_b]: the share of its pixels, 0 to 1, with a colour channel more than 16 apart; None without Pillow or
        either screenshot."""
        if Image is None or not rel_a or not rel_b:
            return None
        try:
            a = Image.open(os.path.join(self.out, rel_a)).convert("RGB")
            b = Image.open(os.path.join(self.out, rel_b)).convert("RGB")
        except Exception:
            return None
        if b.size != a.size:
            b = b.resize(a.size)
        k = a.width / float(width)
        x0, y0, x1, y1 = [int(round(v * k)) for v in box]
        crop = (x0, y0, max(x1, x0 + 1), max(y1, y0 + 1))
        red, green, blue = ImageChops.difference(a.crop(crop), b.crop(crop)).split()
        hist = ImageChops.lighter(ImageChops.lighter(red, green), blue).histogram()
        return sum(hist[17:]) / float(sum(hist) or 1)

    def back_button_check(self, what, tag):
        """1.0.4 (the owner's report from his phone, three buttons, Android 17: Back from an episode's page played "the
        old animation"): Back from the page [tag] by the system's own Back button, touched where he touches it. Since
        Android 16 a touch on that button starts a predictive Back whose progress the system runs by itself while the
        button is down; 1.0.3's detail pages and its player followed it as a finger's and shrank into a rounded card.
        So first the button held: the page must stay whole (on an older system the held button changes nothing, and the
        check is met without telling much); then a tap of it, which must close the page. The system's animations are on
        meanwhile: without them the app moves nothing with a Back gesture either. Returns the nodes after."""
        nodes = self.nodes(self.dump())
        page = self.by_id(nodes, tag)
        if not page:
            self.warnings.append("[%s] %s is not open: Back by the key" % (self.cur_pass, what))
            self.key(4)
            return self.step("Back from %s" % what)
        w, h = self.screen_size(nodes)
        x, y, bar_top, how = self.system_back_button(nodes)
        if self.back_system_info is None:
            self.back_system_info = self.back_system()
            self.log("back button: the system: %s" % self.back_system_info)
        b = page[0].b
        # the page without the system's bar under it (the pressed button lights up there)
        box = (b[0], b[1], b[2], min(b[3], bar_top))
        closed = lambda ns: bool(self.app_nodes(ns)) and not self.by_id(ns, tag)
        self.animations(True)
        try:
            time.sleep(1.0)
            self.step("Before the Back button: %s" % what)
            rest = self.steps[-1]["shot"]
            held = self.back_button_held(x, y, "%03d-%s-%s" % (self.n + 1, self.cur_pass, slug("back button held on " + what)))
            time.sleep(1.5)
            nodes = self.step("The Back button held on %s, then let go" % what)
            # a tap's length into the press, and a long press's
            changes = [self.page_change(rest, shot, box, w) for shot in held]
            read = [c for c in changes if c is not None]
            change = max(read) if read else None
            gone = closed(nodes)
            press = {"pass": self.cur_pass, "page": what, "button": "%s at %d, %d" % (how, x, y), "rest": rest, "held": held,
                     "changes": changes, "change": change, "let_go": "closed" if gone else "open", "tap": ""}
            self.back_presses.append(press)
            if not gone:
                # a tap as a finger's: down for a tenth of a second (`input tap` lifts at once; a long press is no Back)
                self.step("The Back button tapped on %s" % what, lambda: self.sh("input swipe %d %d %d %d 100" % (x, y, x, y)), wait=1.2)
                nodes, gone = self.wait_for(closed, 5)
                press["tap"] = "closed" if gone else "open"
            self.log("back button [%s] %s: %s" % (self.cur_pass, what, press))
            if not gone and how.startswith("a guess"):
                # nothing came of a touch at a guessed place: the button is not there, and nothing was read of it
                self.warnings.append("[%s] the system's Back button was not found (%s): %s was not left by it"
                                     % (self.cur_pass, press["button"], what))
            else:
                if change is None:
                    self.warnings.append("[%s] %s under the held Back button was not read (no screenshot of it, or no Pillow)"
                                         % (self.cur_pass, what))
                else:
                    self.check("%s stays whole while the system's Back button is held" % what, change <= BACK_HELD_WHOLE,
                               "of the page differs from the page at rest: %s a tap's length into the press, %s a second on (the button: %s)"
                               % (tuple("?" if c is None else "%.1f %%" % (c * 100) for c in changes) + (press["button"],)))
                self.check("the system's Back button closes %s" % what, bool(gone),
                           "held and let go: %s; tapped: %s" % (press["let_go"], press["tap"] or "not needed"))
            if not gone:
                # the walk goes on from the page under
                self.key(4)
                nodes = self.step("Back from %s (the key)" % what)
        finally:
            self.animations(False)
        return nodes

    def player_back_button(self):
        """1.0.4: the full player, opened by a tap on the mini player's words over a track that plays, left by the
        system's own Back button (back_button_check): held, the player stays the page from edge to edge; tapped, it
        goes down to the mini player."""
        nodes = self.nodes(self.dump())
        mini = self.by_id(nodes, "mini-player")
        if not mini:
            self.warnings.append("[%s] no mini player: the full player's Back button was not walked" % self.cur_pass)
            return nodes
        b = mini[0].b
        x, y = (b[0] + b[2]) // 2, self.first_row(b)
        shown = None
        for attempt in ("", " (again)"):
            # once more, should a slow emulator have lost the first tap
            self.step("A tap on the mini player" + attempt, lambda: self.sh("input tap %d %d" % (x, y)), wait=1.5)
            nodes, shown = self.wait_for(lambda ns: self.by_id(ns, "phone-player"), 6)
            if shown:
                break
        if not self.check("a tap on the mini player opens the full player", bool(shown)):
            return nodes
        return self.back_button_check("the full player", "phone-player")

    def follow_podcast(self, nodes):
        """Follows the walk's feed (--feed) by the Podcasts' "Add by address" when no podcast is followed."""
        s = self.strings[self.lang]
        add = self.by_id(nodes, "podcast-add")
        if not add:
            self.warnings.append("[%s] no 'podcast-add' on the Podcasts" % self.cur_pass)
            return nodes
        nodes = self.step("Add a podcast by its address", lambda: self.tap(add[0]))
        field = [n for n in self.app_nodes(nodes) if n.rid.endswith("podcast-add-field") or n.cls == "android.widget.EditText"]
        if not self.check("the podcast address field", bool(field)):
            self.key(4)
            return self.step("Back from the address dialog")
        self.tap(field[0])
        time.sleep(0.5)
        self.sh("input text '%s'" % self.a.feed)
        nodes = self.step("The feed's address typed")
        submit = [n for n in self.app_nodes(nodes) if n.rid.endswith("podcast-add-submit") or n.text.strip() == s["nag_pod_subscribe"]]
        self.step("Subscribe", (lambda: self.tap(submit[-1])) if submit else (lambda: self.key(66)), wait=2)
        nodes, done = self.wait_for(lambda ns: self.by_id(ns, "podcast-page") or self.by_id(ns, "podcast-episode") or self.by_id(ns, "podcast-row"), 30)
        self.check("the feed is followed", bool(done))
        return self.step("Followed")

    def podcast_page(self):
        """The Podcasts, then a followed podcast's page (the walk's feed, --feed, followed first when none is): the nodes
        with its episodes; None without a feed or when the page did not open. From wherever the walk is: a page of its
        own (Settings) hides the phone's bottom bar, and is left first."""
        s = self.strings[self.lang]
        self.back_to_tabs()
        self.open_label(s["nag_nav_podcasts_short"]) or self.open_label(s["nag_nav_podcasts"])
        nodes = self.step("Podcasts")
        self.check("the Podcasts open", bool(self.by_id(nodes, "podcast-add") or self.by_id(nodes, "podcast-list")
                                             or self.by_id(nodes, "podcast-grid") or self.by_id(nodes, "podcast-row")))
        if not self.a.feed:
            return None
        if not (self.by_id(nodes, "podcast-row") or self.by_id(nodes, "podcast-tile")):
            nodes = self.follow_podcast(nodes)
        if not self.by_id(nodes, "podcast-episode"):
            show = self.by_id(nodes, "podcast-row") or self.by_id(nodes, "podcast-tile")
            if not self.check("a followed podcast", bool(show)):
                return None
            self.step("The podcast's page", lambda: self.tap(show[0]))
            nodes, _ = self.wait_for(lambda ns: self.by_id(ns, "podcast-episode"), 15)
        return nodes

    def episode_plays(self):
        """1.0.4 (the owner, 12 Nov: "podcast çaldığında canlı yayın diyor müzik barı. Canlı yayın olmamalı"): an episode
        of the walk's feed played from its page, streamed from its enclosure as his are: the bar must show a seek bar
        with its times, never a station's "live" line. An episode that does not start in time is a warning (the
        emulator's network), a bar that says live a failed check. Paused after, and back to the Podcasts."""
        s = self.strings[self.lang]
        # phone-en ends on Settings, restored by a start after the process was killed: Back leaves it (podcast_page);
        # should that have left the app, or the bar still be away, the app is started afresh on its first page
        self.back_to_tabs()
        if not self.has_bottom_bar(self.nodes(self.dump())):
            self.launch()
            self.step("The app started again (for the episode)", wait=1.5)
        nodes = self.podcast_page()
        episodes = self.by_id(nodes, "podcast-episode") if nodes else []
        if not episodes:
            self.warnings.append("[%s] no episode to play: the bar was not read while one plays" % self.cur_pass)
            return
        self.step("An episode's page (to play it)", lambda: self.tap(episodes[0]), wait=1.5)
        nodes, play = self.wait_for(lambda ns: self.by_id(ns, "episode-play"), 12)
        if play:
            self.step("Play the episode", lambda: self.tap(play[0]), wait=2.0)
            # the walk's episodes are "Episode 1" … "Episode 3": the bar names the one that plays
            nodes, named = self.wait_for(lambda ns: "Episode" in (self.mini_title(ns) or ""), 25)
            if not named:
                self.warnings.append("[%s] the episode did not start within 25 s: the bar was not read while one plays (the bar: %s)"
                                     % (self.cur_pass, self.mini_title(nodes)))
            else:
                time.sleep(2.0)
                nodes = self.step("The episode plays")
                seek = self.by_id(nodes, "mini-player-seek")
                line = seek + [n for n in self.app_nodes(nodes) if seek and self.inside(n, seek[0].b)]
                words = " ".join(w for n in line for w in (n.text.strip(), n.desc.strip()) if w)
                live = bool(self.by_id(nodes, "bar-live")) or s["nag_radio_live"] in words
                self.check("an episode that plays is not on the air in the bar: a seek bar with its times",
                           bool(seek) and not live and bool(TIME_RE.search(words)),
                           "the bar's line: %s" % (words[:160] or "nothing read"))
                again = self.by_id(nodes, "episode-play")
                if again:
                    self.step("Pause the episode", lambda: self.tap(again[0]), wait=1.5)
        else:
            self.warnings.append("[%s] no play button on the episode's page: the bar was not read while one plays" % self.cur_pass)
        for what in ("the episode", "the podcast"):
            self.key(4)
            self.step("Back from %s" % what)

    def episode_page(self):
        """A followed podcast's page, then an episode's: its play button stays where it first showed. 1.0.4: Back from
        both by the system's own Back button, held and tapped (back_button_check)."""
        nodes = self.podcast_page()
        if nodes is None:
            return
        episodes = self.by_id(nodes, "podcast-episode")
        if not self.check("an episode on the podcast's page", bool(episodes)):
            return
        # the play button's bounds in the first dump that has it and in one at least 1.5 s later
        self.tap(episodes[0])
        t_tap = time.time()
        first, t_first = None, None
        while time.time() - t_tap < 12:
            nd = self.nodes(self.dump())
            play = self.by_id(nd, "episode-play")
            if play:
                first, t_first = play[0].b, time.time()
                break
        if not self.check("the episode's play button", first is not None):
            self.key(4)
            return
        time.sleep(1.6)
        later = []
        for _ in range(2):
            nd = self.nodes(self.dump())
            play = self.by_id(nd, "episode-play")
            later.append((round(time.time() - t_first, 1), play[0].b if play else None))
            time.sleep(1.0)
        self.step("Episode page (the play button watched)")
        moved = [(dt, b) for dt, b in later if b != first]
        self.check("the episode's play button does not move", not moved,
                   "first %s at %.1f s; then %s" % (first, t_first - t_tap, "; ".join("%s at +%.1f s" % (b, dt) for dt, b in later)))
        # 1.0.4: Back as the owner presses it (the key the walk pressed here never showed what his button does)
        self.back_button_check("the episode's page", "episode-detail")
        self.back_button_check("the podcast's page", "podcast-page")

    def radio_opens(self):
        s = self.strings[self.lang]
        self.open_label(s["nag_nav_radio"])
        nodes = self.step("Radio")
        ids = {"radio-tabs", "radio-list", "radio-offline", "radio-none", "radio-retry"}
        self.check("the Radio opens", any(self.by_id(nodes, i) for i in ids))

    def phone_player(self):
        """1.0's phone: sheets, the pages, a track played, the list's frames, the player's drag, 1.0.4's Back button on the
        full player, the episode page (and the button again), the Radio."""
        nodes = self.open_music()
        self.item_sheet(nodes)
        self.phone_pages()
        nodes = self.open_music()
        self.scroll_frames("Music list scroll", nodes)
        for _ in range(4):
            self.swipe_down(nodes)
        nodes = self.nodes(self.settle(0.8))
        self.play_track(nodes)
        self.player_drag()
        self.player_back_button()
        self.episode_page()
        self.radio_opens()

    def column_header(self, nodes):
        """The Music table's column titles: the row where at least three of them stand side by side."""
        s = self.strings[self.lang]
        names = {s[k] for k in ("nag_column_title", "nag_column_artist", "nag_column_album", "nag_column_duration",
                                "nag_column_year", "nag_column_track_number") if k in s}
        hits = [n for n in self.app_nodes(nodes) if n.text.strip() in names]
        rows = collections.defaultdict(list)
        for n in hits:
            rows[n.center[1] // 12].append(n)
        best = max(rows.values(), key=len, default=[])
        return best if len(best) >= 3 else []

    def pass_workstation(self):
        """The tablet's (and DeX's) Music: the inspector, Ctrl+I, the column chooser, the table's frames."""
        self.cur_pass, self.lang = "workstation", "en"
        s = self.strings["en"]
        self.set_display(None, None)
        self.rotate(0)
        self.ensure_library()
        self.launch()
        nodes = self.open_music()
        nodes, _ = self.wait_for(lambda ns: self.by_id(ns, "inspector"), 6)
        self.check("the Music shows the inspector", bool(self.by_id(nodes, "inspector")))
        self.combo(113, 37)  # Ctrl+I
        nodes = self.step("Ctrl+I (hide the inspector)", wait=1.2)
        self.check("Ctrl+I hides the inspector", not self.by_id(nodes, "inspector"))
        self.combo(113, 37)
        nodes = self.step("Ctrl+I (show the inspector)", wait=1.2)
        self.check("Ctrl+I shows the inspector again", bool(self.by_id(nodes, "inspector")))
        # the table settled after the inspector came back; then a long press on the second title
        nodes = self.nodes(self.settle(1.0))
        header = self.column_header(nodes)
        if self.check("the table's column titles", bool(header), ", ".join(n.text for n in header)):
            # the chooser lists every column with its check, those the table does not show among them
            # (its last rows, "Reset columns" with them, may be below the screen's edge)
            hidden = (s["nag_column_album_artist"], s["nag_column_composer"])
            opened, other = False, []
            for attempt in range(2):
                cell = sorted(header, key=lambda n: n.b[0])[1]
                x, y = cell.center
                nodes = self.step("Long press on the column title %s" % cell.text, lambda: self.sh("input swipe %d %d %d %d 1200" % (x, y, x, y)), wait=1.2)
                opened = all(h in self.texts(nodes) for h in hidden)
                if opened:
                    break
                # what opened instead (a row's menu?) is closed, and the titles read again
                instead = "a track's menu" if s["nag_add_to_queue"] in self.texts(nodes) else "no menu"
                other.append("try %d at (%d, %d): %s" % (attempt + 1, x, y, instead))
                self.key(111)
                nodes = self.nodes(self.settle(1.0))
                header = self.column_header(nodes) or header
            self.check("the column chooser opens", opened, "; ".join(other))
            if opened:
                self.key(111)  # Escape
                nodes = self.step("Escape closes the column chooser")
                self.check("Escape closes the column chooser", not any(h in self.texts(nodes) for h in hidden))
        self.scroll_frames("Music table scroll (tablet)", self.nodes(self.dump()))
        self.device_walk(wide=True)

    # ---------------------------------------------------------------- 1.0.1: Bu cihaz

    @staticmethod
    def inside(node, box):
        x, y = node.center
        return box[0] <= x <= box[2] and box[1] <= y <= box[3]

    def in_tag(self, nodes, tag, text):
        """The app's nodes reading [text] inside the node tagged [tag] (a row of the device table, a place)."""
        boxes = [n.b for n in self.by_id(nodes, tag)]
        return [n for n in self.app_nodes(nodes) if n.text.strip() == text and any(self.inside(n, b) for b in boxes)]

    def open_device_page(self):
        """This device / Bu cihaz: on a phone through ⋯ (a sheet or a page: found by its text, scrolled to), on a tablet in the sidebar."""
        s = self.strings[self.lang]
        label = s["nag_nav_device"]
        self.back_to_tabs()
        nodes = self.nodes(self.dump())
        node = self.find(nodes, label)
        w, h = self.screen_size(nodes)
        if not node and w > 1500:
            # a tablet's sidebar scrolls: the sources are below the library
            for dy in (1, 1, 1, -1, -1, -1, -1, -1, -1):
                a, b = (int(h * 0.75), int(h * 0.30)) if dy > 0 else (int(h * 0.30), int(h * 0.75))
                self.sh("input swipe %d %d %d %d 350" % (w // 10, a, w // 10, b))
                nodes = self.nodes(self.settle(0.6))
                node = self.find(nodes, label)
                if node:
                    break
        if not node:
            more = self.find(nodes, s["nag_nav_more"])
            if more:
                self.tap(more)
                nodes = self.nodes(self.settle(0.8))
                for _ in range(4):
                    node = self.find(nodes, label)
                    if node:
                        break
                    self.swipe_up(nodes)
                    nodes = self.nodes(self.settle(0.6))
        if not self.check("%s is found (⋯ or the sidebar)" % label, bool(node)):
            return None
        nodes = self.step("Open %s" % label, lambda: self.tap(node))
        nodes, places = self.wait_for(lambda ns: self.in_tag(ns, "device-places", s["nag_device_internal"]), 8)
        self.check("%s lists the places, the internal storage first" % label, bool(places))
        return nodes

    def device_playing(self, nodes):
        """A file of the folder plays: the phone's mini player, or the app's media session playing."""
        if self.by_id(nodes, "mini-player"):
            return "mini player"
        # a wide window's transport bar, titled with the file (its tag "tone", or its name)
        bar = [n.b for n in self.by_id(nodes, "transport-bar")]
        title = [n.text.strip() for n in self.app_nodes(nodes) if n.text.strip() in ("tone", "Smoke tone") and any(self.inside(n, b) for b in bar)]
        if title:
            return "transport bar: %s" % title[0]
        if self.session_playing():
            return "media session playing"
        return None

    def session_playing(self):
        """A media session of the app says it plays: "state=3" or, as newer systems write it, "state=PLAYING(3)" (1.0.6:
        that second form was never matched, a word boundary was asked for after its bracket; and any of the app's records
        counts, the first one need not be the one in use)."""
        out = self.sh("dumpsys media_session", timeout=30)
        return any(block.split(None, 1)[:1] == [self.pkg] and re.search(r"state=PlaybackState \{state=(PLAYING\(3\)|3\b)", block[:3000])
                   for block in re.split(r"(?m)^\s*package=", out)[1:])

    def big_player(self):
        """1.0.2: the transport bar's "open the player" opens the big player over the window (not the Music page);
        Back closes it and the bar is there again."""
        nodes = self.nodes(self.dump())
        # the sidebar's transport bar has the button; a rail's mini player opens it from its words
        button = self.by_id(nodes, "transport-open-player") or self.by_id(nodes, "mini-player")
        if not self.check("the transport bar's open button", bool(button)):
            return nodes
        nodes, shown = self.tap_until("The bar's open button", button[0], lambda ns: self.by_id(ns, "phone-player"))
        self.check("the bar's open button opens the big player", bool(shown))
        if not shown:
            return nodes
        player = self.by_id(nodes, "phone-player")[0].b
        w, h = self.screen_size(nodes)
        self.check("the big player covers the window", player[2] - player[0] >= w * 0.9 and player[3] - player[1] >= h * 0.8,
                   "player %s in %dx%d" % (player, w, h))
        self.check("the bars stand aside under it", not self.by_id(nodes, "transport-bar"))
        nodes = self.step("The big player (screenshot)", wait=0.5)
        if not self.by_id(nodes, "phone-player"):
            # the walk's tone (five minutes) ended meanwhile and the player went down with it, as it does when music stops:
            # Back now would leave the page under it (1.0.2's personal walk on API 35 did)
            self.check("the big player goes down when the music stops", not self.device_playing(nodes), "the tone ended")
            return nodes
        self.key(4)
        nodes = self.step("Back: the big player closes", wait=1.2)
        nodes, closed = self.wait_for(lambda ns: not self.by_id(ns, "phone-player"), 6)
        self.check("Back closes the big player", bool(closed))
        # the bar is back while something plays (the walk's tone may have ended by now on a slow emulator)
        if closed and not self.by_id(nodes, "transport-bar") and self.device_playing(nodes):
            self.check("the bar is back after the big player", False, "something plays and no transport bar shows")
        return nodes

    def tap_until(self, title, node, find, seconds=8.0):
        """Taps [node] (once more if nothing came of it: a first touch may only switch the input mode) until find(nodes)."""
        nodes = self.step(title, lambda: self.tap(node), wait=1.0)
        nodes, found = self.wait_for(find, seconds)
        if not found:
            nodes = self.step(title + " (again)", lambda: self.tap(node), wait=1.0)
            nodes, found = self.wait_for(find, seconds)
        return nodes, found

    def device_walk(self, wide):
        """1.0.1 (the user's crash of 29 Oct): the internal storage, its Music folder, a file played from it, back up to
        the drive and the places, the sort menu, hidden files, Home and back with the folder open, rotation."""
        s = self.strings[self.lang]
        internal, folder, tone = s["nag_device_internal"], "Music", "tone.mp3"
        nodes = self.open_device_page()
        if nodes is None:
            return
        drive = self.in_tag(nodes, "device-places", internal)
        if not drive:
            return
        nodes, rows = self.tap_until("Open %s" % internal, drive[0], lambda ns: self.in_tag(ns, "device-table", folder))
        boxes = [n.b for n in self.by_id(nodes, "device-table")]
        listed = sorted({n.text.strip() for n in self.app_nodes(nodes) if n.text.strip() and any(self.inside(n, b) for b in boxes)})
        if not self.check("%s opens and lists its folders (Music among them)" % internal, bool(rows), "rows: %s" % (", ".join(listed[:12]) or "none")):
            return
        nodes, files = self.tap_until("Open its Music folder", rows[0], lambda ns: self.in_tag(ns, "device-table", tone))
        if not self.check("the Music folder lists the walk's files (%s)" % tone, bool(files)):
            return
        nodes, playing = self.tap_until("Play %s from the folder" % tone, files[0], self.device_playing, 10)
        self.check("a file of the folder plays", bool(playing), playing or "no mini player, no session playing")
        if wide and playing:
            nodes = self.big_player()
        if playing:
            # pause the tone (five minutes long): while something plays each dump waits about ten seconds longer for
            # the window to go still (its time ticks), and the folder's next checks read the screen within seconds;
            # the bar or the mini player stays, paused. Keep it the media key, this long after the tone started: it is
            # what found a pause's save reading the disk on the main thread (1.0.3; the StrictMode check at the end)
            self.key(127)
            time.sleep(0.5)
        # back to the drive: Back on a phone, the trail's first folder beside the places
        if wide:
            crumb = self.in_tag(nodes, "device-trail", internal)
            if not self.check("the trail names the drive", bool(crumb)):
                return
            nodes = self.step("The trail back to %s" % internal, lambda: self.tap(crumb[0]))
        else:
            self.key(4)
            nodes = self.step("Back (up to %s)" % internal)
        nodes, up = self.wait_for(lambda ns: self.in_tag(ns, "device-table", folder) and not self.in_tag(ns, "device-table", tone), 6)
        self.check("back up at the drive, its folders listed", bool(up))
        # the order and the hidden files, from the header's menu
        for entry in ("nag_device_sort_size", "nag_device_sort_name", "nag_device_show_hidden", "nag_device_show_hidden"):
            menu = self.by_id(self.nodes(self.dump()), "device-view")
            if not self.check("the sort menu's button", bool(menu)):
                break
            nodes = self.step("Sort menu", lambda: self.tap(menu[0]))
            item = [n for n in self.app_nodes(nodes) if n.text.strip() == s[entry]]
            listed = all(any(n.text.strip() == s[k] for n in self.app_nodes(nodes)) for k in ("nag_device_sort_name", "nag_device_sort_size", "nag_device_sort_date"))
            if not self.check("the sort menu lists the orders and the hidden files", listed and bool(item)):
                self.key(4)
                break
            nodes = self.step("Sort menu: %s" % s[entry], lambda: self.tap(item[0]))
            # the rows may still be moving to their new places when the screen is read: read again for a moment
            nodes, listed = self.wait_for(lambda ns: self.in_tag(ns, "device-table", folder), 10)
            self.check("after '%s' the drive still lists its folders" % s[entry], bool(listed))
        if not wide:
            self.key(4)
            nodes = self.step("Back (to the places)")
            nodes, places = self.wait_for(lambda ns: self.by_id(ns, "device-places") and not self.by_id(ns, "device-table"), 6)
            self.check("Back goes on to the places", bool(places))
            drive = self.in_tag(nodes, "device-places", internal)
            if not drive:
                return
            nodes, rows = self.tap_until("Open %s again" % internal, drive[0], lambda ns: self.in_tag(ns, "device-table", folder))
        else:
            self.check("the places stay beside the folder", bool(self.in_tag(nodes, "device-places", internal)))
            rows = self.in_tag(nodes, "device-table", folder)
        if not rows:
            return
        nodes, files = self.tap_until("Open the Music folder again", rows[0], lambda ns: self.in_tag(ns, "device-table", tone))
        if not files:
            return
        # the folder open while the app goes to the background and comes back, then turned. The folder is open when
        # its file is in the table, or the trail ends in it: a phone turned sideways is a short window whose floating
        # player (the tone still plays) lies over the table's last rows
        still_open = lambda ns: self.in_tag(ns, "device-table", tone) or self.in_tag(ns, "device-trail", folder)
        self.key(3)
        time.sleep(2)
        self.step("Home with the folder open")
        self.launch(force=False)
        nodes, kept = self.wait_for(still_open, 8)
        self.step("Back in the app with the folder open")
        self.check("after Home the folder is still open", bool(kept))
        self.rotate(1)
        nodes = self.step("Rotated with the folder open", wait=1.5)
        nodes, kept = self.wait_for(still_open, 6)
        self.check("rotated, the folder is still open", bool(kept))
        self.rotate(0)
        nodes = self.step("Rotated back with the folder open", wait=1.5)
        nodes, kept = self.wait_for(still_open, 6)
        self.check("rotated back, the folder is still open", bool(kept))
        if not wide:
            self.key(4)
            self.step("Back (up to the drive)")
            self.key(4)
            self.step("Back (to the places)")

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
        # 1.0.3: the user's phone has three-button navigation: the band and Back are walked as he uses them
        self.three_button_navigation()
        self.prepare_media()
        self.launch()
        nodes = self.step("Launch (phone)")
        if any(x.text.strip() == s["nag_fr_skip"] for x in self.app_nodes(nodes)):
            # 1.0.3: the phone's job of the split walk starts on a fresh app: its first run, with the media granted
            self.first_run_walk()
            time.sleep(8)
            nodes = self.step("After the first run (phone)")
        self.check_bottom_bar(nodes)
        self.band_check(nodes, "at the first launch")
        self.visit_destinations()
        self.settings_pass(full=True)
        self.open_label(s["nag_nav_settings"])
        # the first run again, with media access granted and test files to scan
        if self.open_by_scrolling(s["nag_settings_first_run_again"]):
            self.step("Show the first run again")
            self.first_run_walk("First run again")
            time.sleep(8)
            self.step("After the scan")
            self.music_after_restart()
        else:
            self.warnings.append("[phone-en] 'first run again' not found in Settings")
        self.library_ready = True
        self.visit_destinations("Reopen")
        self.phone_player()
        self.glass_b_check()
        self.auto_tree()
        self.device_walk(wide=False)
        self.playback()
        self.palette()
        self.rotation_and_background()
        # 1.0.4, last: nothing else of the pass has an episode in the player
        self.episode_plays()

    def ensure_library(self):
        """1.0.3: a pass that needs the walk's media (workstation, phone-tr) and runs without phone-en before it (the walk
        is split in two jobs) pushes and grants it and goes through the first run with it, as phone-en does."""
        if self.library_ready:
            return
        self.library_ready = True
        self.prepare_media()
        self.launch()
        nodes = self.step("Launch (the walk's media pushed)")
        s = self.strings[self.lang]
        if any(x.text.strip() == s["nag_fr_skip"] for x in nodes):
            self.first_run_walk("First run with the media")
        elif self.open_label(s["nag_nav_settings"]) and self.open_by_scrolling(s["nag_settings_first_run_again"]):
            self.step("Show the first run again")
            self.first_run_walk("First run again")
        else:
            self.warnings.append("[%s] the first run could not be shown again: the scan is left to the app" % self.cur_pass)
        time.sleep(8)
        self.step("After the scan")
        self.launch()

    def pass_phone_tr(self):
        # the media first (in English: the app is not Turkish yet), should no pass before have brought it
        self.cur_pass, self.lang = "phone-tr", "en"
        self.set_display("1080x2400", "420")
        self.ensure_library()
        self.lang = "tr"
        if not self.set_locale("tr-TR"):
            self.warnings.append("[phone-tr] the Turkish interface could not be set; the pass was skipped")
            return
        self.step("Launch (Turkish)")
        self.phone_pages()
        self.device_walk(wide=False)
        self.visit_destinations()
        self.settings_pass(full=True)
        self.sh("cmd locale set-app-locales %s --locales en-US" % self.pkg)

    def pass_glass(self):
        """1.0.3, on demand (passes=glass, the debug build): screenshots for the user's glass decision. The phone in
        three-button navigation, dark then light, the walk's albums in folders of their own with covers and a playlist
        each; in each mode of the chrome (A solid, B the bottom glass, C the kit's), set by the debug-only extra on an
        intent to the running app (playback goes on, the mini player stays): the Music list at its top and scrolled so its
        rows pass under the bars, then the Playlists' grid likewise with its covers. The system's animations are on for
        the pass: with "Remove animations" the app draws glass solid. glass/README.md names each screenshot."""
        self.cur_pass, self.lang = "glass", "en"
        s = self.strings["en"]
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2400", "420")
        self.three_button_navigation()
        self.glass_media()
        self.launch()
        nodes = self.step("Launch (glass)")
        if any(x.text.strip() == s["nag_fr_skip"] for x in self.app_nodes(nodes)):
            self.first_run_walk("First run (glass)")
            time.sleep(10)
            self.step("After the scan (glass)")
        self.animations(True)
        os.makedirs(os.path.join(self.out, "glass"), exist_ok=True)
        shots = []
        nodes = self.open_music()
        # the scan may still be going: the walk's tracks are waited for
        nodes, _ = self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.startswith("Profile track")], 40)
        self.play_track(nodes)
        time.sleep(2)
        # paused: the strip and the lists stand still for the pictures
        self.key(85)
        activity = "%s/app.nagmedar.ui.NagmedarActivity" % self.pkg
        for theme in ("dark", "light"):
            self.sh("cmd uimode night %s" % ("yes" if theme == "dark" else "no"))
            time.sleep(2)
            for mode, letter in (("solid", "A"), ("bottom", "B"), ("kit", "C")):
                if self.over_time():
                    break
                self.sh("am start -n %s --activity-single-top --activity-clear-top --es %s %s" % (activity, CHROME_GLASS_EXTRA, mode), timeout=60)
                time.sleep(2)
                # a page change once the mode changed: the glass switched on at run time over a still page draws only after
                # the page is drawn anew (Haze 2.0.1: the content records itself after the bar's last draw)
                self.open_label(s["nag_nav_podcasts_short"], via_more=False)
                time.sleep(1.5)
                nodes = self.open_music()
                self.glass_top(nodes)
                nodes = self.step("Music (%s, %s)" % (letter, theme), wait=1.0)
                self.check("the mini player shows (%s, %s)" % (letter, theme), bool(self.by_id(nodes, "mini-player")))
                if letter == "A":
                    self.band_check(nodes, "%s theme" % theme)
                shots += self.glass_shots(nodes, letter, theme, "music")
                if self.open_label(s["nag_nav_playlists"]):
                    time.sleep(1.5)
                    nodes = self.nodes(self.settle(1.0))
                    self.glass_top(nodes)
                    nodes = self.step("Playlists (%s, %s)" % (letter, theme), wait=1.0)
                    shots += self.glass_shots(nodes, letter, theme, "playlists")
                # the search page's results, the phone's list that scrolls beneath the top bar as well (C's top bar)
                nodes = self.open_music()
                search = [n for n in self.app_nodes(nodes) if n.desc.strip() == s["nag_search"]]
                if search:
                    self.tap(search[0])
                    time.sleep(1.5)
                    # the page keeps its words: cleared first, then one word every walk track has
                    clear = [n for n in self.app_nodes(self.nodes(self.dump())) if n.desc.strip() == s["nag_search_clear"]]
                    if clear:
                        self.tap(clear[0], record=False)
                        time.sleep(0.8)
                    self.sh("input text track")
                    time.sleep(2.0)
                    # the on-screen keyboard away (the bars come back); a Back without one would leave the page
                    if self.keyboard_shown():
                        self.key(4, record=False)
                        time.sleep(1.0)
                    nodes = self.step("Search (%s, %s)" % (letter, theme), wait=1.0)
                    if self.by_id(nodes, "search"):
                        shots += self.glass_shots(nodes, letter, theme, "search")
                    self.key(4, record=False)
                    self.step("Back from the search page")
                self.back_to_tabs()
        self.sh("cmd uimode night no")
        self.animations(False)
        with open(os.path.join(self.out, "glass", "README.md"), "w", encoding="utf-8") as f:
            f.write("# Glass decision: the phone's chrome in three modes\n\n"
                    "Real emulator screenshots (API %s, %s, 1080x2400 at 420 dpi, three-button navigation, the debug build), "
                    "switched with the debug-only extra `%s`. A = all opaque (the product today); B = the mini player, the "
                    "bottom bar and the band behind the buttons on one glass, the top bar opaque and never changing colour; "
                    "C = the kit's: B and a top bar that fades to glass while content passes under it. The track is paused. "
                    "On the Music and Playlists pages the page's own header stays under the top bar (only the search page's "
                    "list scrolls beneath it on a phone), so C's top bar shows its glass over that header there.\n\n"
                    "| file | mode | theme | what |\n|---|---|---|---|\n"
                    % (getattr(self, "device", {}).get("sdk", "?"), getattr(self, "device", {}).get("model", "?"), CHROME_GLASS_EXTRA))
            for name, letter, theme, what in shots:
                f.write("| %s | %s | %s | %s |\n" % (name, letter, theme, what))
        self.check("the glass screenshots were taken (A, B, C in dark and light)", len(shots) >= 12, "%d screenshots" % len(shots))

    def more_page(self):
        """The phone's More page (its tab, and again when the tab showed a page opened from it: the tab keeps its pages,
        and chosen again it goes to its first page); the nodes, or None."""
        s = self.strings[self.lang]
        for _ in range(2):
            if not self.open_label(s["nag_nav_more"], via_more=False):
                return None
            time.sleep(1.2)
            nodes = self.nodes(self.settle(0.8))
            if self.page_title(nodes, s["nag_nav_more"]):
                return nodes
        return None

    def cold_start(self, route=None):
        """1.0.3: the app ended and started again (into the page of [route], by the launcher shortcuts' extra), the screen
        read as soon as its window has come in (within its first second); the path of that screenshot, or None."""
        self.sh("am force-stop " + self.pkg)
        time.sleep(1)
        # a page: the app's activity itself with the extra (the launcher's activity hands no extras on)
        target = "%s/app.nagmedar.ui.NagmedarActivity --es nagmedar_destination %s" % (self.pkg, route) if route else self.launcher
        # the window drawn, its opening animation (on in the glass checks) over and the list's first rows arrived in turn
        # (ListAppear, about 0.3 s): the app's first second
        self.sh("am start -W -n %s ; sleep 1.0 ; screencap -p /sdcard/nag-cold.png" % target, timeout=90)
        rc, data = self.adb("exec-out", "cat /sdcard/nag-cold.png", timeout=60, binary=True)
        if rc != 0 or len(data) < 1000:
            return None
        rel = "shots/%03d-%s-first-frames-%s.png" % (self.n + 1, self.cur_pass, route or "start")
        with open(os.path.join(self.out, rel), "wb") as f:
            f.write(data)
        return rel

    def pass_glass_b(self):
        """1.0.3, on demand (passes=glass-b, either build): the product's glass B, the phone in three-button navigation,
        dark then light, the system's animations on (the glass drawn), the walk's albums with covers (glass_media). First
        a cold start into each page with a list under the bottom chrome (Music, Podcasts, Radio, Playlists, the search
        page, Settings and its About page, the More page; the launcher shortcuts' extra), its first frames read as soon as
        the window has drawn: the bar's glass must blur the rows beneath it (no rows sharp under it). Then, a track played
        and paused so the mini player shows, each page again with its list moved under the bars, Sıradaki (a page over the
        window: the glass off under it) and the full player: the card's and the bar's glass blur wherever they show.
        glass-b/README.md lists the screenshots."""
        self.cur_pass, self.lang = "glass-b", "en"
        s = self.strings["en"]
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2400", "420")
        self.three_button_navigation()
        self.glass_media()
        self.launch()
        nodes = self.step("Launch (glass B)")
        if any(x.text.strip() == s["nag_fr_skip"] for x in self.app_nodes(nodes)):
            self.first_run_walk("First run (glass B)")
            time.sleep(10)
            self.step("After the scan (glass B)")
        self.animations(True)
        folder = os.path.join(self.out, "glass-b")
        os.makedirs(folder, exist_ok=True)
        shots = []

        def keep(rel, name, what):
            if rel:
                shutil.copyfile(os.path.join(self.out, rel), os.path.join(folder, name))
                shots.append((name, what))

        def glass_ok(nodes, what, rel=None):
            """The bar's and the card's glass, where they show, blur what passes beneath (from [rel] or the last shot)."""
            w, h = self.screen_size(nodes)
            boxes = self.glass_boxes(nodes)
            shot = rel or (self.steps[-1]["shot"] if self.steps else None)
            got = {k: self.region(shot, b, w) for k, b in boxes.items() if b}
            self.log("glass [glass-b] %s: %s" % (what, got))
            if got:
                self.check("glass B: %s: the glass blurs, nothing sharp beneath it" % what, all(self.blurred(v) for v in got.values()),
                           "; ".join("%s %s" % (k, v) for k, v in got.items()))

        def moved(nodes):
            """The page's list down (the bars slide away), then a little back up: the bars return over it."""
            w, h = self.screen_size(nodes)
            for _ in range(2):
                self.glass_drag(w // 2, int(h * 0.66), int(h * 0.40))
                time.sleep(0.4)
            self.glass_drag(w // 2, int(h * 0.45), int(h * 0.53))
            time.sleep(1.2)

        def words(word="track"):
            """The search page's words typed, the keyboard away so the bars show."""
            clear = [n for n in self.app_nodes(self.nodes(self.dump())) if n.desc.strip() == s["nag_search_clear"]]
            if clear:
                self.tap(clear[0], record=False)
                time.sleep(0.8)
            self.sh("input text %s" % word)
            time.sleep(2.0)
            if self.keyboard_shown():
                self.key(4, record=False)
                time.sleep(1.0)

        routes = ["music", "podcasts", "radio", "playlists", "search", "settings", "about", "more"]
        for theme in ("dark", "light"):
            if self.over_time():
                break
            self.sh("cmd uimode night %s" % ("yes" if theme == "dark" else "no"))
            time.sleep(2)
            # a cold start into each page: its first frames, then the page settled
            for route in routes:
                if self.over_time():
                    break
                first = self.cold_start(route)
                nodes = self.step("Glass B cold start: %s (%s)" % (route, theme), wait=1.0)
                if route == "search":
                    words()
                    nodes = self.step("Glass B cold start: search words (%s)" % theme, wait=1.0)
                glass_ok(nodes, "%s, cold start's first frames (%s)" % (route, theme), rel=first)
                glass_ok(nodes, "%s, cold start (%s)" % (route, theme))
                keep(first, "%s-%s-0-first-frames.png" % (theme, route), "%s, the first frames of a cold start into it" % route)
                keep(self.steps[-1]["shot"], "%s-%s-1-cold-start.png" % (theme, route), "%s, a cold start into it, settled" % route)
            # a track played and paused: the mini player's card on the glass
            nodes = self.open_music()
            nodes, _ = self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.startswith("Profile track")], 20)
            self.play_track(nodes)
            time.sleep(2)
            self.key(85)
            for route, label in (("music", s["nag_nav_music"]), ("podcasts", s["nag_nav_podcasts_short"]), ("radio", s["nag_nav_radio"]),
                                 ("playlists", s["nag_nav_playlists"]), ("search", None), ("settings", s["nag_nav_settings"]),
                                 ("more", s["nag_nav_more"])):
                if self.over_time():
                    break
                self.back_to_tabs()
                if route == "search":
                    nodes = self.search_results()
                    if nodes is None:
                        continue
                elif route == "music":
                    nodes = self.open_music()
                else:
                    # Playlists and Settings from the More page itself (the More tab may still show a page of its own)
                    via = self.more_page() if route in ("playlists", "settings", "more") else self.nodes(self.dump())
                    target = self.find(via, label) if via is not None and route != "more" else None
                    if route == "more" and via is not None:
                        nodes = self.step("Glass B: %s (%s)" % (route, theme), wait=0.5)
                    elif target is not None:
                        nodes = self.step("Glass B: %s (%s)" % (route, theme), lambda t=target: self.tap(t), wait=1.2)
                    else:
                        self.warnings.append("[glass-b] %s not found" % route)
                        continue
                glass_ok(nodes, "%s with the mini player (%s)" % (route, theme))
                keep(self.steps[-1]["shot"], "%s-%s-2-player.png" % (theme, route), "%s, the mini player showing" % route)
                moved(nodes)
                nodes = self.step("Glass B: %s, its list under the bars (%s)" % (route, theme), wait=0.8)
                glass_ok(nodes, "%s, its list under the bars (%s)" % (route, theme))
                keep(self.steps[-1]["shot"], "%s-%s-3-moved.png" % (theme, route), "%s, its list moved under the bars" % route)
                if route == "search":
                    self.key(4, record=False)
                    self.step("Back from the search page")
            # Sıradaki, a page over the whole window (from More), and the full player: the glass off under their colour
            self.back_to_tabs()
            more = self.more_page()
            if more is not None:
                queue = self.find(more, s["nag_queue_title"])
                if queue:
                    nodes = self.step("Glass B: Sıradaki (%s)" % theme, lambda: self.tap(queue), wait=1.5)
                    self.check("glass B: Sıradaki opens over the window (%s)" % theme, bool(self.by_id(nodes, "queue-page")))
                    keep(self.steps[-1]["shot"], "%s-queue.png" % theme, "Sıradaki, a page over the window (no glass under it)")
                    self.key(4)
                    self.step("Back from Sıradaki")
            nodes = self.open_music()
            mini = self.by_id(nodes, "mini-player")
            if mini:
                b = mini[0].b
                nodes = self.step("Glass B: the full player (%s)" % theme, lambda: self.sh("input tap %d %d" % ((b[0] + b[2]) // 2, self.first_row(b))), wait=2.0)
                if self.by_id(nodes, "phone-player"):
                    keep(self.steps[-1]["shot"], "%s-full-player.png" % theme, "the full player (no glass drawn under its colour)")
                    self.key(4)
                    self.step("Back from the full player")
        self.sh("cmd uimode night no")
        self.animations(False)
        with open(os.path.join(folder, "README.md"), "w", encoding="utf-8") as f:
            f.write("# Glass B, the product: the phone's bottom chrome on one glass\n\n"
                    "Real emulator screenshots (API %s, %s, 1080x2400 at 420 dpi, three-button navigation, the %s build), the system's "
                    "animations on (with them removed the app draws the glass opaque). \"0-first-frames\": read as soon as the window "
                    "of a cold start into the page had drawn; \"1-cold-start\": that page settled; \"2-player\": the page again with a "
                    "track paused in the mini player; \"3-moved\": its list moved under the bars. The walk read the bar's and the "
                    "card's glass on each (the run's checks): it blurs, nothing sharp beneath it.\n\n| file | what |\n|---|---|\n"
                    % (getattr(self, "device", {}).get("sdk", "?"), getattr(self, "device", {}).get("model", "?"), self.a.build))
            for name, what in shots:
                f.write("| %s | %s |\n" % (name, what))
        self.check("the glass B screenshots were taken", len(shots) >= 16, "%d screenshots" % len(shots))

    def glass_media(self):
        """The walk's tracks in a folder per album, each with a cover of its own (cover.jpg) and a playlist of the album
        (an M3U the library reads as a playlist): covers for the lists and the Playlists' grid to pass under the bars."""
        self.library_ready = True
        for perm in PERMISSIONS_MEDIA:
            self.sh("pm grant %s %s" % (self.pkg, perm))
        tracks = sorted(glob.glob(os.path.join(self.a.music or "", "*.mp3")))
        if not tracks:
            self.warnings.append("[glass] no walk tracks: the lists stay empty")
            return
        work = os.path.join(self.out, "raw", "glass-media")
        os.makedirs(work, exist_ok=True)
        palette = [((231, 76, 60), (241, 196, 15)), ((46, 134, 193), (26, 188, 156)), ((142, 68, 173), (236, 112, 99)),
                   ((39, 174, 96), (244, 208, 63)), ((211, 84, 0), (52, 73, 94)), ((22, 160, 133), (155, 89, 182)),
                   ((192, 57, 43), (93, 173, 226)), ((241, 196, 15), (44, 62, 80))]
        for album in range(1, 9):
            mine = [t for t in tracks if os.path.basename(t).startswith("album%d-" % album)]
            if not mine:
                continue
            folder = "/sdcard/Music/nagmedar-walk/album%d" % album
            self.sh("mkdir -p %s" % folder)
            self.adb("push", *mine, folder + "/", timeout=300)
            local = os.path.join(work, "album%d" % album)
            os.makedirs(local, exist_ok=True)
            if self.cover_image(os.path.join(local, "cover.jpg"), *palette[album - 1]):
                self.adb("push", os.path.join(local, "cover.jpg"), folder + "/cover.jpg", timeout=60)
            with open(os.path.join(local, "list.m3u"), "w", encoding="utf-8") as f:
                f.write("#EXTM3U\n" + "".join(os.path.basename(t) + "\n" for t in mine))
            self.adb("push", os.path.join(local, "list.m3u"), "%s/Profile album %d.m3u" % (folder, album), timeout=60)

    @staticmethod
    def cover_image(path, a, b):
        """A bright album cover (stripes of [a] and [b], a white disc) saved as a JPEG at [path]; False without Pillow."""
        if Image is None:
            return False
        from PIL import ImageDraw
        im = Image.new("RGB", (600, 600), a)
        d = ImageDraw.Draw(im)
        for i in range(0, 1200, 120):
            d.polygon([(i, 0), (i + 60, 0), (i - 540, 600), (i - 600, 600)], fill=b)
        d.ellipse((180, 180, 420, 420), fill=(250, 250, 250))
        d.ellipse((270, 270, 330, 330), fill=a)
        im.save(path, "JPEG", quality=90)
        return True

    def region(self, rel, box, width):
        """The colours inside [box] (x0, y0, x1, y1 on a screen [width] px wide) of the screenshot [rel]: their lowest,
        highest and mean values per channel and their spread (the largest high - low); None without Pillow or the shot."""
        if Image is None or not rel:
            return None
        try:
            im = Image.open(os.path.join(self.out, rel)).convert("RGB")
        except Exception:
            return None
        k = im.width / float(width)
        x0, y0, x1, y1 = [int(round(v * k)) for v in box]
        px = list(im.crop((x0, y0, max(x1, x0 + 1), max(y1, y0 + 1))).getdata())
        if not px:
            return None
        lo = tuple(min(q[i] for q in px) for i in range(3))
        hi = tuple(max(q[i] for q in px) for i in range(3))
        mean = tuple(round(sum(q[i] for q in px) / float(len(px)), 1) for i in range(3))
        return {"lo": lo, "hi": hi, "mean": mean, "spread": max(hi[i] - lo[i] for i in range(3))}

    def glass_boxes(self, nodes):
        """1.0.3 (glass B): where the phone's bottom chrome shows its glass and draws nothing of its own: down the mini
        player's card just inside its start edge (beside the artwork, clear of the times), down the bottom bar between
        its first two tabs (a quarter of the width, from above the icons to the labels' foot) and the band behind the
        system's buttons left of Back (where a list's covers pass beneath it). {"card", "bar", "band"}: a box each, None
        when not on screen."""
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        boxes = {"card": None, "bar": None, "band": None}
        mini = self.by_id(nodes, "mini-player")
        if mini:
            b = mini[0].b
            boxes["card"] = (b[0] + 4, b[1] + (b[3] - b[1]) // 4, b[0] + 18, b[3] - (b[3] - b[1]) // 4)
        music = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_nav_music"] and n.center[1] > h * 0.85]
        if music:
            label = max(music, key=lambda n: n.center[1])
            # the tab around the label (its icon and the selection pill above it): the bar's height
            tabs = [n for n in self.app_nodes(nodes) if n.clickable and self.inside(label, n.b) and n.b[1] > h * 0.75]
            b = min(tabs, key=lambda n: n.area).b if tabs else (label.b[0], label.b[1] - (label.b[3] - label.b[1]) * 2, label.b[2], label.b[3])
            boxes["bar"] = (w // 4 - 2, b[1] + 6, w // 4 + 3, b[3] - 6)
            # three-button navigation: the band from the bar's foot to the window's, Back's icon at a fifth of the width
            if h - b[3] > 40:
                boxes["band"] = (int(w * 0.06), b[3] + 8, int(w * 0.15), h - 8)
        return boxes

    def glass_read(self, nodes, what):
        """The colours of the bottom chrome's glass (glass_boxes) in the last step's screenshot, logged: {"card", "bar"}."""
        w, h = self.screen_size(nodes)
        rel = self.steps[-1]["shot"] if self.steps else None
        got = {k: (self.region(rel, b, w) if b else None) for k, b in self.glass_boxes(nodes).items()}
        self.log("glass [%s] %s: %s" % (self.cur_pass, what, got))
        return got

    @staticmethod
    def blurred(stats):
        """The glass is drawn and blurs: what passes beneath shows only through its tint (at most 12 % of it in the app's
        thick glass), so no sharp row or cover edge spans the region's colours (spread up to 40 of 255)."""
        return stats is not None and stats["spread"] <= 40

    @staticmethod
    def differs(a, b, by=1.0):
        """Two regions' mean colours differ (by [by] or more in a channel)."""
        return a is not None and b is not None and max(abs(a["mean"][i] - b["mean"][i]) for i in range(3)) >= by

    def search_results(self, word="track"):
        """The search page (from the top bar's search button) with [word]'s results, the on-screen keyboard away so the
        bars show; the nodes, or None when the page or its results did not come."""
        s = self.strings[self.lang]
        nodes = self.open_music()
        search = [n for n in self.app_nodes(nodes) if n.desc.strip() == s["nag_search"]]
        if not search:
            return None
        self.tap(search[0])
        time.sleep(1.5)
        # the page keeps its words: cleared first
        clear = [n for n in self.app_nodes(self.nodes(self.dump())) if n.desc.strip() == s["nag_search_clear"]]
        if clear:
            self.tap(clear[0], record=False)
            time.sleep(0.8)
        self.sh("input text %s" % word)
        time.sleep(2.0)
        # the keyboard away (the bars come back); a Back without one would leave the page
        if self.keyboard_shown():
            self.key(4, record=False)
            time.sleep(1.0)
        nodes = self.step("Search: %s" % word, wait=1.0)
        return nodes if self.by_id(nodes, "search") else None

    def glass_b_check(self):
        """1.0.3, the user's glass B in the product (the debug build's default and the personal build): the phone's bottom
        chrome is one glass. The walk runs with the system's animations removed, where the app draws it opaque (the
        system's fallback): first that, the card and the bar one colour; then with animations on, switched at run time
        over the still search page with the walk's covers under the mini player, after a short drag, and from a cold
        start on the Music: the card's glass is not the opaque colour, changes as the list moves, and nothing beneath
        shows sharp through the card or the bar (the glass blurs from its first frames, no rows sharp under the card)."""
        nodes = self.search_results()
        if nodes is None or not self.by_id(nodes, "mini-player"):
            self.warnings.append("[%s] glass B: no search results with the mini player: not checked" % self.cur_pass)
            self.back_to_tabs()
            return
        w, h = self.screen_size(nodes)
        # down the results (the bars slide away), then a little back up: the bars return over the covers
        for _ in range(2):
            self.glass_drag(w // 2, int(h * 0.66), int(h * 0.40))
            time.sleep(0.4)
        self.glass_drag(w // 2, int(h * 0.45), int(h * 0.53))
        time.sleep(1.2)
        nodes = self.step("Glass B: animations removed (opaque)", wait=0.8)
        solid = self.glass_read(nodes, "opaque")
        opaque = [v for v in solid.values() if v is not None]
        self.check("glass B: with animations removed the bottom chrome is opaque, one colour (card, bar, band)",
                   solid["card"] is not None and solid["bar"] is not None and all(v["spread"] <= 2 for v in opaque)
                   and not any(self.differs(v, solid["card"], by=2.0) for v in opaque),
                   "; ".join("%s %s" % (k, v) for k, v in solid.items()))
        self.animations(True)
        try:
            time.sleep(2.0)
            nodes = self.step("Glass B: animations on over the still page", wait=0.8)
            on = self.glass_read(nodes, "switched on")
            shown = [v for v in on.values() if v is not None]
            self.check("glass B: switched on at run time, the glass blurs what passes beneath it (card, bar, band)",
                       on["card"] is not None and all(self.blurred(v) for v in shown) and self.differs(on["card"], solid["card"]),
                       "; ".join("%s %s" % (k, v) for k, v in on.items()) + "; opaque %s" % (solid["card"] and solid["card"]["mean"],))
            self.glass_drag(w // 2, int(h * 0.45), int(h * 0.50))
            time.sleep(1.2)
            nodes = self.step("Glass B: the results moved under the bars", wait=0.8)
            moved = self.glass_read(nodes, "moved")
            # the covers pass right under the band; the card's edge is a little beside them, its glow fainter
            self.check("glass B: the glass changes as the list moves under it",
                       all(self.blurred(v) for v in moved.values() if v is not None)
                       and any(self.differs(moved[k], on[k], by=0.5) for k in ("band", "card")),
                       "band %s -> %s; card %s -> %s" % (on["band"] and on["band"]["mean"], moved["band"] and moved["band"]["mean"],
                                                         on["card"] and on["card"]["mean"], moved["card"] and moved["card"]["mean"]))
            # a cold start with the glass on: its first frames, read as soon as the window has drawn
            rel = self.cold_start()
            nodes = self.step("Glass B: a cold start", wait=1.0)
            # 1.0.3: the tracks played last are kept with the list: the phone's shelf is over the rows from the start
            # (the history, read after the kept list was drawn, used to leave it out of sight above the column titles)
            shelf = self.by_id(nodes, "recent-shelf")
            rows = [n for n in self.app_nodes(nodes) if n.text.startswith("Profile track")
                    and not any(self.inside(n, x.b) for x in shelf)]
            self.check("a cold start shows the shelf of the tracks played last over the Music's rows",
                       bool(shelf) and bool(rows) and shelf[0].b[3] <= min(n.b[1] for n in rows),
                       "shelf %s, first row %s" % (shelf[0].b if shelf else None, min((n.b[1] for n in rows), default=None)))
            bar = self.glass_boxes(nodes)["bar"]
            first = self.region(rel, bar, w) if bar and rel else None
            cold = self.glass_read(nodes, "cold start")
            self.check("glass B: from a cold start the bar's glass blurs the rows beneath it (no sharp rows)",
                       self.blurred(cold["bar"]) and (first is None or self.blurred(first)) and self.differs(cold["bar"], solid["card"]),
                       "first frames %s, settled %s; opaque %s" % (first, cold["bar"], solid["card"] and solid["card"]["mean"]))
        finally:
            self.animations(False)

    def keyboard_shown(self):
        """The on-screen keyboard is up (the input method service says so)."""
        return "mInputShown=true" in self.sh("dumpsys input_method", timeout=60)

    def glass_top(self, nodes):
        """The page's list back at its top, the bars shown."""
        w, h = self.screen_size(nodes)
        for _ in range(3):
            self.drag(w // 2, int(h * 0.30), w // 2, int(h * 0.75), moves=6)
        time.sleep(0.8)

    def glass_drag(self, x, y0, y1, moves=16):
        """A drag that stops before it lifts: the list moves by the drag alone, no fling after it (so the three modes'
        screenshots show the list at the same place)."""
        pts = [(x, y0 + (y1 - y0) * i // moves) for i in range(1, moves + 1)]
        self.sh(" ; ".join(["input motionevent DOWN %d %d" % (x, y0)] + ["input motionevent MOVE %d %d" % p for p in pts]
                           + ["sleep 0.4", "input motionevent UP %d %d" % (x, y1)]), timeout=90)

    def glass_shots(self, nodes, letter, theme, what):
        """The page as it is (its top) and scrolled so its content lies under the bars, the bars shown again over it; each
        screenshot copied to glass/<mode>-<theme>-<what>-<n>-<state>.png."""
        out = []
        w, h = self.screen_size(nodes)
        for i, label in ((1, "top"), (2, "scrolled")):
            if i == 2:
                # down the list (the bars slide away), then a little back up: they return over the rows beneath them
                for _ in range(2):
                    self.glass_drag(w // 2, int(h * 0.66), int(h * 0.38))
                    time.sleep(0.5)
                self.glass_drag(w // 2, int(h * 0.45), int(h * 0.53))
                time.sleep(1.5)
            self.step("Glass %s %s %s (%s)" % (letter, theme, what, label), wait=0.8)
            shot = self.steps[-1]["shot"]
            if shot:
                name = "%s-%s-%s-%d-%s.png" % (letter, theme, what, i, label)
                shutil.copyfile(os.path.join(self.out, shot), os.path.join(self.out, "glass", name))
                out.append((name, letter, theme, "%s, %s" % (what, "at its top" if i == 1 else "scrolled: content under the bars")))
        return out

    def prepare_media(self):
        if not self.a.media or not os.path.isdir(self.a.media):
            self.warnings.append("no test media: the library stays empty")
            return
        for perm in PERMISSIONS_MEDIA:
            self.sh("pm grant %s %s" % (self.pkg, perm))
        for f, dest in (("tone.mp3", "/sdcard/Music/"),):
            src = os.path.join(self.a.media, f)
            if os.path.exists(src):
                self.sh("mkdir -p " + dest)
                self.adb("push", src, dest + f, timeout=120)
                self.sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://%s%s" % (dest, f))
        # the walk's tracks (a long list to scroll, a queue to swipe through): a folder of their own; 1.0.3: with a
        # bright cover (cover.jpg) for the rows and the mini player, to pass under the phone's glass (glass_b_check)
        if self.a.music and os.path.isdir(self.a.music):
            self.sh("mkdir -p /sdcard/Music/nagmedar-walk")
            rc, out = self.adb("push", *sorted(glob.glob(os.path.join(self.a.music, "*.mp3"))), "/sdcard/Music/nagmedar-walk/", timeout=300)
            if rc != 0:
                self.warnings.append("the walk's tracks were not pushed: " + out.strip()[-200:])
            cover = os.path.join(self.out, "raw", "walk-cover.jpg")
            if self.cover_image(cover, (231, 76, 60), (241, 196, 15)):
                self.adb("push", cover, "/sdcard/Music/nagmedar-walk/cover.jpg", timeout=60)

    def playback(self):
        # a music file opened from another app (1.0 plays no video: it offers itself for audio only)
        for f, mime, title in (("/sdcard/Music/tone.mp3", "audio/mpeg", "audio"),):
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

    def music_after_restart(self):
        """1.0.3: the library is scanned once. After the first run's scan the app is ended and started again from the
        launcher: the Music lists the walk's tracks at once (the app's own log line "NagmedarPerf: music_list ms=…" says
        when the list first stood on screen, from the process's start) and neither the loading state nor a scan strip
        ("Scanning the library", "Looking for media folders") shows in front of the known list. One short step."""
        s = self.strings[self.lang]
        busy = {s[k] for k in ("nag_state_scanning", "nag_state_discovering", "nag_library_loading") if k in s}
        is_row = lambda n: n.text.startswith("Profile track") or n.text.strip() == "Smoke tone"
        self.sh("am force-stop " + self.pkg)
        self.adb("logcat", "-c", timeout=30)
        time.sleep(1)
        started = time.time()
        out = self.sh("am start -W -n %s" % self.launcher, timeout=90)
        frame = re.search(r"TotalTime:\s*(\d+)", out)
        seen_busy, rows = set(), []
        # the screen is read as often as uiautomator allows (about a dump a second) until the tracks show
        while time.time() - started < 15:
            nodes = self.nodes(self.dump())
            seen_busy |= busy & self.texts(nodes)
            rows = [n for n in self.app_nodes(nodes) if is_row(n)]
            if rows:
                break
        took = time.time() - started
        time.sleep(1)
        _, log = self.adb("logcat", "-d", "-s", "NagmedarPerf:I", timeout=60)
        listed = re.search(r"music_list ms=(\d+) tracks=(\d+) kept=(\w+)", log)
        cold = re.search(r"startup cold_ms=(\d+)", log)
        detail = "%d rows seen %.1f s after am start%s%s%s" % (
            len(rows), took,
            "; list on screen %s ms after the process started (%s tracks, %s)" % (
                listed.group(1), listed.group(2), "kept list" if listed.group(3) == "true" else "read") if listed else "",
            "; first frame %s ms (am start)%s" % (frame.group(1) if frame else "?", ", %s ms (the system's record)" % cold.group(1) if cold else ""),
            "; seen: " + ", ".join(sorted(seen_busy)) if seen_busy else "")
        self.step("Cold start: the Music's list", lambda: detail, wait=0.3)
        self.check("after a restart the Music lists its tracks at once, without a scanning state",
                   bool(rows) and not seen_busy and (listed is None or int(listed.group(1)) <= 8000), detail)

    def auto_tree(self):
        """1.0.3: Android Auto's tree as a car reads it, by application/benchmark's AutoBrowseTest (--auto-apk, the
        personal build's): a MediaBrowserCompat bound to the app's PlaybackService finds the four tabs, the walk's
        tracks under Albums, its feed under the shows and an answer in every list (the radio's: stations or one line)."""
        apk = self.a.auto_apk
        if not apk or self.over_time():
            return
        if os.path.isdir(apk):
            apk = next(iter(sorted(glob.glob(os.path.join(apk, "**", "*.apk"), recursive=True))), None)
        if not apk or not os.path.isfile(apk):
            self.check("Android Auto's tree (AutoBrowseTest)", False, "no test APK under %s" % self.a.auto_apk)
            return
        rc, out = self.adb("install", "-r", "-t", apk, timeout=180)
        if rc != 0 or "Success" not in out:
            self.check("Android Auto's tree (AutoBrowseTest)", False, "install: " + out.strip()[-300:])
            return
        tracks = len(glob.glob(os.path.join(self.a.music, "*.mp3"))) if self.a.music and os.path.isdir(self.a.music) else 0
        args = ["-e class app.nagmedar.macrobenchmark.AutoBrowseTest", "-e nagmedar.package %s" % self.pkg]
        if tracks:
            args.append("-e nagmedar.tracks %d" % tracks)
        if self.a.feed:
            args.append("-e nagmedar.feed %s" % self.a.feed)
        result = {}

        def run():
            _, result["out"] = self.adb("shell", "am instrument -w %s app.nagmedar.macrobenchmark/androidx.test.runner.AndroidJUnitRunner"
                                        % " ".join(args), timeout=240)
            return "AutoBrowseTest: " + " ".join(result["out"].split())[-160:]

        self.step("Android Auto's tree read by a MediaBrowserCompat", run, wait=0.5)
        out = result.get("out", "").replace("\r", "")
        with open(os.path.join(self.out, "auto-browse.txt"), "w", encoding="utf-8") as f:
            f.write(out)
        why = re.search(r"^\d+\) .*?(?=^\s+at |\Z)", out, re.M | re.S)
        self.check("Android Auto: the four tabs, the walk's %d tracks under Albums, its podcast, every list answers" % tracks,
                   "OK (1 test)" in out, (why.group(0) if why else out[-400:]).strip())

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

    def pass_back_button(self):
        """1.0.4, on demand (passes=back-button, either build; the workflow's ref input walks an older app with this
        walk): the system's Back button of the three-button navigation, touched where the owner touches it
        (back_button_check), on what the phone opens over a list: an episode's page (his report of 12 Nov), the
        podcast's page under it, then the full player over a track that plays. Each page at rest, while the button is
        held and after a tap of it; the report's "The system's Back button" lists the screenshots and how much of each
        page changed under the held button. In the light theme: around a shrunk page the list shows dark under its
        scrim."""
        self.cur_pass, self.lang = "back-button", "en"
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2400", "420")
        self.three_button_navigation()
        self.sh("cmd uimode night no")
        self.ensure_library()
        self.step("Launch (the Back button)")
        self.episode_page()
        self.episode_plays()
        self.play_track(self.open_music())
        self.player_back_button()
        pages = [p["page"] for p in self.back_presses if p["pass"] == self.cur_pass]
        self.check("the system's Back button was pressed on the episode's page, the podcast's and the full player",
                   len(pages) >= 3, "pressed on: %s" % (", ".join(pages) or "nothing"))

    # ---------------------------------------------------------------- 1.0.5: pages opened with the animations on

    def ink(self, rel, box, width):
        """The share of [box]'s pixels (x0, y0, x1, y1 on a screen [width] px wide) in the screenshot [rel] that are not
        its most common colour (a channel more than 24 apart): next to nothing for an empty page. None without Pillow
        or the screenshot."""
        if Image is None or not rel:
            return None
        try:
            a = Image.open(os.path.join(self.out, rel)).convert("RGB")
        except Exception:
            return None
        k = a.width / float(width)
        x0, y0, x1, y1 = [int(round(v * k)) for v in box]
        crop = a.crop((x0, y0, max(x1, x0 + 1), max(y1, y0 + 1)))
        small = crop.resize((max(1, crop.width // 4), max(1, crop.height // 4)), Image.NEAREST)
        colours = small.getcolors(maxcolors=small.width * small.height + 1) or []
        if not colours:
            return None
        base = max(colours)[1]
        red, green, blue = ImageChops.difference(crop, Image.new("RGB", crop.size, base)).split()
        hist = ImageChops.lighter(ImageChops.lighter(red, green), blue).histogram()
        return sum(hist[25:]) / float(sum(hist) or 1)

    def raw_shots(self, cmd, count, name):
        """Runs [cmd] on the device (it writes /sdcard/nag-b1.png … nag-b<count>.png) and brings the screenshots:
        shots/<name>-<i>.png, None for one that is not there. Nothing is asked of the UI meanwhile."""
        self.sh("rm -f /sdcard/nag-b*.png ; " + cmd, timeout=180)
        shots = []
        for i in range(1, count + 1):
            rc, data = self.adb("exec-out", "cat /sdcard/nag-b%d.png" % i, timeout=60, binary=True)
            rel = None
            if rc == 0 and len(data) > 1000:
                rel = "shots/%s-%d.png" % (name, i)
                with open(os.path.join(self.out, rel), "wb") as f:
                    f.write(data)
            shots.append(rel)
        return shots

    def opening_check(self, what, nodes, at=None, tags=("podcast-page", "podcast-episode"), swipe=True):
        """1.0.5: one page opened with the system's animations on, as on a person's phone. [at] (x, y) is tapped (None:
        the page is the one the app has just opened itself), and the screen is read with nothing asked of the UI
        meanwhile: 0.3 s, 0.8 s and 2.5 s after the tap. Only then the UI's own tree is read: the page must be in it
        ([tags]) and drawn in its place 2.5 s after it opened (more than PAGE_INK of the place between the bars is not
        the page's ground colour: an empty page has none). The owner's show's page (12 Nov) slid in and was gone,
        every pixel of it, in the frame its opening ended; the walk's own steps open pages with the animations off and
        never saw it. With [swipe] the page must then scroll (scrolls_check). Returns the nodes."""
        w, h = self.screen_size(nodes)
        # between the top bar and the bottom bar (the mini player's card, when there is one, is above this foot)
        box = (0, int(h * 0.12), w, int(h * 0.74))
        name = "%03d-%s-%s" % (self.n + 1, self.cur_pass, slug("opened " + what))
        cmd = ("input tap %d %d ; " % at if at else "") + \
            "sleep 0.3 ; screencap -p /sdcard/nag-b1.png ; sleep 0.4 ; screencap -p /sdcard/nag-b2.png ; sleep 1.5 ; screencap -p /sdcard/nag-b3.png"
        shots = self.raw_shots(cmd, 3, name)
        inks = [self.ink(r, box, w) for r in shots]
        nodes = self.step("%s: the UI asked" % what, wait=1.0)
        in_tree = any(self.by_id(nodes, t) for t in tags)
        pct = lambda v: "?" if v is None else "%.1f %%" % (v * 100)
        drawn = inks[2] is not None and inks[2] >= PAGE_INK
        r = {"pass": self.cur_pass, "what": what, "tapped": bool(at), "shots": shots, "inks": inks, "in_tree": in_tree,
             "swiped": bool(swipe), "moved": None, "scrolls": None, "ok": bool(drawn and in_tree)}
        self.page_openings.append(r)
        self.check("%s: the page is on screen 2.5 s after it opened (the animations on)" % what, drawn and in_tree,
                   "drawn in the page's place: %s at 0.3 s, %s at 0.8 s, %s at 2.5 s; the page in the UI's tree: %s"
                   % (pct(inks[0]), pct(inks[1]), pct(inks[2]), "yes" if in_tree else "NO"))
        # a page opened or closed is a new screen: the bars a scroll slid away are shown again
        self.check("%s: the bottom bar is shown" % what, self.bar_shown(nodes), self.bar_seen(nodes))
        if swipe:
            nodes, r["moved"], r["scrolls"] = self.scrolls_check(what, nodes)
            r["ok"] = bool(r["ok"] and r["scrolls"])
        self.log("page opening [%s]: %s" % (what, r))
        return nodes

    def episode_rows(self, nodes):
        """The episodes in sight on a show's page, top to bottom: each one's title (its row's first line) and where that
        line starts (px from the screen's top)."""
        out = []
        for row in sorted(self.by_id(nodes, "podcast-episode"), key=lambda n: n.b[1]):
            lines = sorted((n for n in self.app_nodes(nodes) if n is not row and n.text.strip() and self.inside(n, row.b)),
                           key=lambda n: n.b[1])
            if lines:
                out.append((lines[0].text.strip(), lines[0].b[1]))
        return out

    def scrolls_check(self, what, nodes):
        """1.0.5: a show's page scrolls: where its episodes are, a swipe up the page, where they are then. A list that
        scrolls follows the finger: an episode still in sight has moved up by most of the swipe (PAGE_FOLLOWS of it at
        least), or none of them is in sight any more. One that no longer takes the scroll (the owner's first opening,
        "açılmıştı, o zaman da aşağı scroll yapılmıyordu": the page was there and stood still) gives some 25 dp, what
        its rows in sight have left, and no more; a share of changed pixels does not tell the two apart (1.0.4's page
        changed 17 % of its place so). Returns the nodes, the px the page moved (None: all its episodes are others)
        and whether it scrolled."""
        before = self.episode_rows(nodes)
        w, h = self.screen_size(nodes)
        y0, y1 = int(h * 0.66), int(h * 0.30)
        self.sh("input swipe %d %d %d %d 250" % (w // 2, y0, w // 2, y1))
        nodes = self.step("%s: swiped up" % what, wait=1.5)
        after = self.episode_rows(nodes)
        once = lambda rows: {t: y for t, y in rows if [x for x, _ in rows].count(t) == 1}
        was, now = once(before), once(after)
        same = [t for t, _ in before if t in was and t in now]
        moved = (was[same[0]] - now[same[0]]) if same else None
        need = int((y0 - y1) * PAGE_FOLLOWS)
        ok = bool(before) and bool(after) and (moved is None or moved >= need)
        if not before or not after:
            seen = "episodes in sight before the swipe: %d, after it: %d" % (len(before), len(after))
        elif moved is None:
            seen = "none of the %d episodes in sight before the swipe is in sight after it" % len(before)
        else:
            seen = "\"%s\" moved up by %d px under a swipe of %d px (a page that follows the finger: %d px at least)" \
                % (same[0][:16], moved, y0 - y1, need)
        self.check("%s: the page scrolls under a swipe" % what, ok, seen)
        return nodes, moved, ok

    def bar_shown(self, nodes):
        """1.0.5: the phone's bottom bar is in its place: its ⋯ tab's label whole, above the system's buttons. Slid away
        by a scroll the label is a sliver at the window's foot, which has_bottom_bar() still takes for the bar."""
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        return any(n.text.strip() == s["nag_nav_more"] and n.center[1] > h * 0.8 and n.b[3] - n.b[1] >= 30 and n.b[3] <= h - 40
                   for n in self.app_nodes(nodes))

    def bar_seen(self, nodes):
        """Where the bottom bar's ⋯ label is, for a check's detail."""
        s = self.strings[self.lang]
        w, h = self.screen_size(nodes)
        more = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_nav_more"] and n.center[1] > h * 0.8]
        return "its ⋯ label at y %d to %d of %d" % (more[0].b[1], more[0].b[3], h) if more else "no ⋯ label at the screen's foot"

    def podcasts_list(self, what):
        """Back from a show's page to the Podcasts' list, the animations on: the nodes, and the checks that it is there
        and that the bottom bar is shown again (1.0.5: the page's scroll slid it away, and one show's list cannot
        scroll to call it back)."""
        self.key(4)
        nodes, rows = self.wait_for(lambda ns: self.by_id(ns, "podcast-row") or self.by_id(ns, "podcast-tile"), 8)
        nodes = self.step("Back on the Podcasts (%s)" % what, wait=1.0)
        rows = self.by_id(nodes, "podcast-row") or self.by_id(nodes, "podcast-tile")
        self.check("Back from the show's page shows the Podcasts again (%s)" % what, bool(rows) and not self.by_id(nodes, "podcast-page"),
                   "%d show(s) listed" % len(rows))
        self.check("Back on the Podcasts the bottom bar is shown (%s)" % what, self.bar_shown(nodes), self.bar_seen(nodes))
        return nodes

    def pass_page_opens(self):
        """1.0.5, on demand (passes=page-opens, either build; the workflow's ref input walks an older app with this
        walk): what the phone opens over a list, opened with the system's animations on and nothing in the player,
        on a screen of the owner's phone's size (1080 x 2340 at 480 dpi, dark, three buttons), each read by its
        pixels (opening_check) and then scrolled (scrolls_check). His report of 12 Nov, with a recording: his show's
        page slid in and was gone in the frame its opening ended, in 1.0.3 and 1.0.4; "the first time it opened, and
        would not scroll then".
        From a fresh app: the long feed (80 episodes, a cover, a long description) followed by its address, the
        page the app opens then; back, the show's row tapped, twice (the owner's tap; a second opening starts where
        the first left the transition); an episode opened over the show's page and left, the show's page still
        there; then the shows as covers and the tile tapped (it grows into the page's header: the other way in).
        The report's "Pages opened with the animations on" lists every opening with its screenshots."""
        self.cur_pass, self.lang = "page-opens", "en"
        s = self.strings["en"]
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2340", "480")
        self.three_button_navigation()
        self.sh("cmd uimode night yes")
        self.ensure_library()
        # the workflow's long feed: a cover, a link, a long description and 80 episodes, as the owner's show has
        if self.a.feed:
            self.a.feed = self.a.feed.rsplit("/", 1)[0] + "/feed-long.xml"
        self.animations(True)
        try:
            self.launch()
            self.back_to_tabs()
            self.open_label(s["nag_nav_podcasts_short"]) or self.open_label(s["nag_nav_podcasts"])
            nodes = self.step("Podcasts (the animations on)", wait=1.2)
            if not self.a.feed:
                self.check("a feed to follow (--feed)", False, "the walk was started without one")
                return
            self.page_opens_walk(nodes)
        finally:
            self.animations(False)
            self.sh("cmd uimode night no")

    def page_opens_walk(self, nodes):
        s = self.strings[self.lang]
        shows = lambda ns: self.by_id(ns, "podcast-row") or self.by_id(ns, "podcast-tile")
        if not shows(nodes):
            # nothing followed yet, the owner's first day: the "add" window closes onto the show's page
            nodes = self.follow_podcast(nodes)
            time.sleep(1.5)
            if self.keyboard_shown():
                # (a Back while it is up sends it away; without one it would leave the page)
                self.warnings.append("[%s] the keyboard was still up 1.5 s after the 'add' window closed onto the show's page" % self.cur_pass)
                self.key(4, record=False)
                time.sleep(1.0)
            nodes = self.step("After following: the page the app opened", wait=1.0)
            if self.by_id(nodes, "podcast-page") or self.by_id(nodes, "podcast-episode"):
                nodes = self.opening_check("the page the app opens once the show is followed", nodes)
                nodes = self.podcasts_list("after following")
            else:
                self.warnings.append("[%s] following the feed did not open its page: the page is opened from its row alone" % self.cur_pass)
        if not self.check("a followed show on the Podcasts", bool(shows(nodes)), "%d listed" % len(shows(nodes))):
            return
        # the owner's tap, and once more: a second opening starts where the first left the transition
        for turn in ("its row tapped", "its row tapped again"):
            nodes = self.opening_check("the show's page, %s" % turn, nodes, at=shows(nodes)[0].center)
            if turn.endswith("again"):
                break
            nodes = self.podcasts_list(turn)
            if not shows(nodes):
                return
        # the show's page is open: an episode's page over it, and back
        w, h = self.screen_size(nodes)
        episodes = [n for n in self.by_id(nodes, "podcast-episode") if h * 0.15 < n.center[1] < h * 0.72]
        if self.check("an episode on the show's page to open", bool(episodes), "%d in sight" % len(episodes)):
            x, y = episodes[0].center
            # (the row's title, clear of the buttons at its end)
            nodes = self.opening_check("an episode's page over the show's", nodes, at=(min(x, int(w * 0.4)), y),
                                       tags=("episode-detail",), swipe=False)
            self.key(4)
            nodes = self.opening_check("the show's page again, Back from the episode", nodes)
        nodes = self.podcasts_list("after the episode")
        if not shows(nodes):
            return
        # the other way in: the shows as covers, the tile grows into the page's header
        more = self.by_id(nodes, "podcast-more")
        if more:
            nodes = self.step("The Podcasts' menu", lambda: self.tap(more[0]), wait=1.0)
            covers = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_pod_view_grid"]]
            if covers:
                nodes = self.step("Show as covers", lambda: self.tap(covers[0]), wait=1.2)
            else:
                self.key(4)
                nodes = self.step("The menu closed (no 'Show as covers' in it)", wait=1.0)
        tiles = self.by_id(nodes, "podcast-tile")
        if self.check("the shows as covers", bool(tiles), "%d tile(s)" % len(tiles)):
            nodes = self.opening_check("the show's page, its tile tapped", nodes, at=tiles[0].center)
            nodes = self.podcasts_list("after the tile")
            tiles = self.by_id(nodes, "podcast-tile")
            if tiles:
                nodes = self.opening_check("the show's page, its tile tapped again", nodes, at=tiles[0].center)
                nodes = self.podcasts_list("after the tile, again")
        opened = [r for r in self.page_openings if r["pass"] == self.cur_pass]
        self.check("the pages were opened with the animations on: by following, from the row twice, an episode, from the tile twice",
                   len(opened) >= 6, "%d opening(s) read: %s" % (len(opened), "; ".join(r["what"] for r in opened)))

    # ---------------------------------------------------------------- 1.0.6: Android Auto's tree on a car's screen

    def car_screen(self):
        """The car's screen, and where its media app's small player begins when that is up: what lies under it is in
        the tree all the same, and a tap there opens the player."""
        nodes = self.nodes(self.dump())
        bar = [n.b[1] for n in nodes if n.area and n.rid.endswith(":id/minimized_playback_controls")]
        return nodes, min(bar) if bar else max((n.b[3] for n in nodes), default=768)

    def car_page_down(self, nodes, floor, by_button=True):
        """The list on: a page by its own button (a car's list has one), or without one (and when the button did not
        help) by a swipe up over what shows of the list."""
        button = [n for n in nodes if n.area and n.rid.endswith(":id/car_ui_scrollbar_page_down")]
        if button and by_button:
            self.sh("input tap %d %d" % button[0].center)
        else:
            w = max((n.b[2] for n in nodes), default=1024)
            self.sh("input swipe %d %d %d %d 400" % (w // 2, floor - 60, w // 2, max(floor - 300, 180)))
        time.sleep(1.2)

    def car_tap(self, labels, title, wait=3.0, below=0.0):
        """Taps what the car's media app says with one of [labels] (the app's own window says some of the same words:
        it does not count) and takes the step [title]; True when it was there, else a warning. [below]: only what is
        lower than this share of the screen (the lists, not the tabs). What the small player covers is paged up
        first."""
        for attempt in range(5):
            nodes, floor = self.car_screen()
            h = max((n.b[3] for n in nodes), default=768)
            said = [n for n in nodes if n.area and n.pkg == CAR_MEDIA and n.center[1] >= h * below
                    and (n.text.strip() in labels or n.desc.strip() in labels)]
            hit = [n for n in said if n.b[3] <= floor]
            if hit:
                x, y = hit[0].center
                self.step(title, lambda: self.sh("input tap %d %d" % (x, y)), wait=wait)
                return True
            self.car_page_down(nodes, floor, by_button=attempt < 2)
        self.warnings.append("[%s] \"%s\" is not on the car's screen (%s)" % (self.cur_pass, labels[0], title))
        return False

    def car_first_row(self, title, starts=None, wait=3.0, read=True):
        """Taps the first row of the list on the car's screen (the first text below the bar; with [starts], the first
        that begins so) and takes the step [title]; what the row says, or None with a warning."""
        nodes, floor = self.car_screen()
        h = max((n.b[3] for n in nodes), default=768)
        rows = sorted((n for n in nodes if n.area and n.text.strip() and n.center[1] > h * 0.22 and n.b[3] <= floor
                       and n.pkg == CAR_MEDIA and (starts is None or n.text.strip().startswith(starts))),
                      key=lambda n: (n.center[1], n.center[0]))
        if not rows:
            self.warnings.append("[%s] no row to open on the car's screen (%s)" % (self.cur_pass, title))
            return None
        x, y = rows[0].center
        said = rows[0].text.strip()
        self.step("%s (\"%s\")" % (title, said[:30]), lambda: self.sh("input tap %d %d" % (x, y)), wait=wait, read=read)
        return said

    def car_back(self):
        self.key(4, record=False)
        time.sleep(1.2)

    def car_loaded(self, what, unreached, seconds=20.0):
        """A list that is asked over the network: waits until the car no longer says that it is loading ([seconds] at
        most). True when it came; when it still loads, or the app says one of [unreached] (its lines for a directory
        it cannot reach), a warning and a picture of what the car says instead, and False: a runner's emulator does
        not always reach those servers, and "the first row" would be that line."""
        end = time.time() + seconds
        while True:
            nodes = self.nodes(self.dump())
            said = {n.text.strip() for n in nodes if n.pkg == CAR_MEDIA and n.text.strip()}
            loading = any(x.startswith("Loading content") for x in said)
            if not loading or time.time() >= end:
                break
            time.sleep(2.0)
        if not loading and not any(x in said for x in unreached):
            return True
        self.warnings.append("[%s] %s did not come here (the car says: %s)" % (self.cur_pass, what, "; ".join(sorted(said))[:160]))
        self.step("Not reached from here: %s" % what, wait=0.5)
        return False

    def car_tabs(self, labels, start):
        """Back until the car's media app shows the app's tabs again (one of [labels], said by the car's media app:
        the app's own window has tabs of the same names); [start] opens it once more when Back has left it. False when
        they do not come."""
        for _ in range(5):
            nodes = self.nodes(self.dump())
            said = {n.text.strip() for n in nodes if n.pkg == CAR_MEDIA} | {n.desc.strip() for n in nodes if n.pkg == CAR_MEDIA}
            if any(label in said for label in labels):
                return True
            if nodes and not any(n.pkg == CAR_MEDIA for n in nodes):
                start()
                time.sleep(4.0)
            else:
                self.car_back()
        self.warnings.append("[%s] the car's media app does not show the app's tabs again" % self.cur_pass)
        return False

    def car_pause(self):
        """Pauses what plays with the media key, as a steering wheel's button does; True once the app's session no
        longer says it plays."""
        for cmd in ("input keyevent 127", "cmd media_session dispatch pause"):
            self.sh(cmd)
            for _ in range(4):
                time.sleep(0.7)
                if not self.session_playing():
                    return True
        return False

    def car_player(self, title, starts=None, wait=4.0, named=True):
        """Taps the first row, a thing that plays: the car's player comes up over the list. A player that plays never
        rests (its time runs), so it is photographed as it is and read only once paused; Back then takes it down.
        [named]: the paused player must say what the row said and its clock must have run (not asked of a station: a
        stream names itself and has no clock). True when a row was tapped."""
        said = self.car_first_row(title + ": the car's player", starts=starts, wait=wait, read=False)
        if not said:
            return False
        playing = self.session_playing()
        for _ in range(0 if named else 3):
            # a stream may still be opening: a few seconds more before it is said not to have played
            if playing:
                break
            time.sleep(2.0)
            playing = self.session_playing()
        paused = self.car_pause()
        nodes = self.step("The car's player, paused" if paused else "The car's player (it would not pause)", wait=1.5, read=paused)
        if paused and named:
            on = [n.text.strip() for n in nodes if n.pkg == CAR_MEDIA and n.text.strip()]
            self.check("%s: the car's player names it" % title.lower(), said in on, "tapped \"%s\"; the player says: %s" % (said, ", ".join(on[:10])))
            # the player's own clock (its id), not the first thing that looks like a time: the length does too
            now = [n.text.strip() for n in nodes if n.pkg == CAR_MEDIA and n.rid.endswith(":id/current_time")]
            self.check("%s: it played (the car's player's clock ran)" % title.lower(), bool(now) and now[0] not in ("", "0:00"),
                       "the clock: %s; the app's session said it played: %s" % (now[0] if now else "not found", "yes" if playing else "no"))
        elif not playing:
            self.warnings.append("[%s] %s (\"%s\"): the app's session never said it played" % (self.cur_pass, title, said))
        else:
            self.log("%s: the app's session said it played" % title)
        self.car_back()
        return True

    def pass_auto_shots(self):
        """1.0.6, on demand (passes=auto-shots; the workflow boots an Android Automotive emulator for it): the owner
        asked for Android Auto's screens (13 Nov). Android Auto itself cannot run here: its head unit needs a phone
        whose Play Store is signed in. The tree Android Auto browses is opened where a car's own system draws it
        instead: Android Automotive's media app, bound to the app's PlaybackService as Android Auto is. The tabs, the
        lists and what plays are the app's; the drawing is the car's, not Android Auto's. In Turkish, as his car has
        it. Every step is a screenshot of the report. adb is root from this pass on (a car's media is pushed to its
        driver's storage): it is walked in a job of its own."""
        self.cur_pass, self.lang = "auto-shots", "tr"
        s = self.strings["tr"]
        user = self.sh("am get-current-user", timeout=20).strip()
        user = user if user.isdigit() else "0"
        rc, out = self.adb("root", timeout=60)
        time.sleep(3)
        self.adb("wait-for-device", timeout=60)
        self.log("the car's user: %s; adb: uid %s" % (user, self.sh("id -u", timeout=20)))
        # a car's system user has no storage of its own and no screen: the walk's media and the app's permissions go
        # to the driver's
        for perm in PERMISSIONS_MEDIA + ["android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_LOCAL_NETWORK"]:
            self.sh("pm grant --user %s %s %s" % (user, self.pkg, perm))
        base = "/data/media/%s/Music" % user
        self.sh("mkdir -p %s/nagmedar-walk" % base)
        files = []
        if self.a.media and os.path.exists(os.path.join(self.a.media, "tone.mp3")):
            self.adb("push", os.path.join(self.a.media, "tone.mp3"), base + "/tone.mp3", timeout=120)
        if self.a.music and os.path.isdir(self.a.music):
            files = sorted(glob.glob(os.path.join(self.a.music, "*.mp3")))
            if files:
                self.adb("push", *files, base + "/nagmedar-walk/", timeout=300)
            cover = os.path.join(self.out, "raw", "walk-cover.jpg")
            if self.cover_image(cover, (231, 76, 60), (241, 196, 15)):
                self.adb("push", cover, base + "/nagmedar-walk/cover.jpg", timeout=60)
        self.sh("chown -R media_rw:media_rw %s ; chmod -R 775 %s ; restorecon -R %s" % (base, base, base))
        scan = self.sh("content call --user %s --uri content://media --method scan_volume --arg external_primary" % user, timeout=180)
        seen = self.sh("content query --user %s --uri content://media/external/audio/media --projection _data | head -3" % user, timeout=60)
        self.log("the car's media: %d tracks pushed; scan: %s; the media store: %s" % (len(files), " ".join(scan.split())[:160], " ".join(seen.split())[:300]))
        self.sh("cmd locale set-app-locales %s --user %s --locales tr-TR" % (self.pkg, user))
        self.launch()
        nodes = self.step("The app on the car's screen", wait=2.5)
        if any(x.text.strip() == s["nag_fr_skip"] for x in nodes):
            self.first_run_walk("First run")
        time.sleep(12)
        self.step("The app after its first run", wait=1.0)
        # the car's media app with the app's service as its source, as Android Auto binds it
        component = "%s/org.videolan.vlc.PlaybackService" % self.pkg

        def start():
            return self.sh("am start --user %s -a android.car.intent.action.MEDIA_TEMPLATE --es android.car.intent.extra.MEDIA_COMPONENT %s"
                           % (user, component), timeout=60)

        self.log("the car's media app: %s" % " ".join(start().split())[:300])
        # from here on the steps are of the car's media app, not of the app
        self.other_app_expected = True
        nodes = self.step("The car's media app: Nağmedar's first tab", wait=6.0)
        # said by the car's media app: the app's own window has tabs of the same names
        said = {n.text.strip() for n in nodes if n.pkg == CAR_MEDIA} | {n.desc.strip() for n in nodes if n.pkg == CAR_MEDIA}
        tabs = {"music": [s["nag_nav_music"]], "podcasts": [s["nag_nav_podcasts"], s["nag_nav_podcasts_short"]],
                "radio": [s["nag_nav_radio"]], "recent": [s["nag_music_view_recent"]]}
        shown = [k for k, labels in tabs.items() if any(l in said for l in labels)]
        self.check("the car's media app shows the app's tabs", len(shown) >= 3,
                   "%s of Music, Podcasts, Radio, Recently played; the car's media app says: %s"
                   % (", ".join(shown) or "none", ", ".join(sorted(x for x in said if x)[:24]) or "nothing (it is not in front)"))
        if len(shown) < 3:
            return
        every = [label for labels in tabs.values() for label in labels]
        # Music: the albums, an album, a track played
        self.car_tap(tabs["music"], "Music")
        if self.car_tap([s["nag_music_view_albums"]], "Music: the albums", below=0.15):
            if self.car_first_row("An album", starts="Profile album"):
                self.car_player("A track tapped", starts="Profile track")
        # Podcasts: the lists, Discover, the popular shows of the directory, one of them
        if self.car_tabs(every, start) and self.car_tap(tabs["podcasts"], "Podcasts"):
            if self.car_tap([s["nag_auto_discover"]], "Podcasts: Discover", below=0.15):
                if self.car_tap([s["nag_auto_popular_shows"]], "Discover: the popular shows", wait=8.0, below=0.15):
                    # Apple's chart
                    if self.car_loaded("the podcast directory's popular shows", [s["nag_auto_podcasts_offline"]]):
                        self.car_first_row("A show of the directory", wait=8.0)
        # Radio: the lists, the countries, a country's stations, one of them played
        if self.car_tabs(every, start) and self.car_tap(tabs["radio"], "Radio"):
            radio_unreached = [s["nag_auto_radio_offline"], s["nag_auto_radio_busy"]]
            if self.car_tap([s["nag_auto_countries"]], "Radio: the countries", wait=8.0, below=0.15):
                if self.car_loaded("the radio directory's countries", radio_unreached) and self.car_first_row("A country's stations", wait=8.0):
                    if self.car_loaded("the country's stations", radio_unreached):
                        self.car_player("A station tapped", wait=9.0, named=False)
        if self.car_tabs(every, start):
            # the music that was played: the track
            self.car_tap(tabs["recent"], "Recently played")
            # the car's search, over music, shows and stations at once
            if self.car_tap(["Search"], "The car's search", wait=2.0):
                self.sh("input text Profile")
                self.step("Search: \"Profile\" typed", wait=5.0)
                self.car_back()
                self.step("Search: after Back (the keyboard down)", wait=1.5)

    # ---------------------------------------------------------------- 1.0.6: a notice swiped as a finger swipes it

    def raw_touch(self):
        """1.0.6: the emulator's touch screen as a device that takes a finger's events the way the kernel gives them
        (adb as root), and what turns a point of the screen into its raw axes: {"node", "name", "axes", "slots", "btn",
        "map"}; None with the reason in raw_touch_why. `input` and UiAutomator hand each event to the app and wait
        until it has handled it: a lift then never arrives with a move still waiting for its frame, which is what a
        person's finger does whenever it is lifted in motion (the system hands the waiting moves over just before the
        lift, and nothing of the app runs between the two)."""
        self.raw_touch_why = ""
        rc, out = self.adb("root", timeout=60)
        time.sleep(3)
        self.adb("wait-for-device", timeout=60)
        uid = self.sh("id -u", timeout=20)
        if uid != "0":
            self.raw_touch_why = "adb is not root here (id -u: %s; adb root: %s)" % (uid, " ".join(out.split())[:120])
            return None
        listed = self.sh("getevent -lp", timeout=30)
        dump = self.sh("dumpsys input", timeout=60)
        with open(os.path.join(self.out, "raw-input.txt"), "w", encoding="utf-8") as f:
            f.write("==== wm size / wm density\n%s\n%s\n\n==== getevent -lp\n%s\n\n==== dumpsys input\n%s\n"
                    % (self.sh("wm size"), self.sh("wm density"), listed, dump))
        devices = []
        for block in re.split(r"(?m)^add device \d+: ", listed)[1:]:
            name = re.search(r'name:\s+"([^"]*)"', block)
            axes = {m.group(1): (int(m.group(2)), int(m.group(3)))
                    for m in re.finditer(r"ABS_MT_(\w+)\s*: value -?\d+, min (-?\d+), max (-?\d+)", block)}
            if "POSITION_X" in axes and "POSITION_Y" in axes:
                devices.append({"node": block.split()[0], "name": name.group(1) if name else "", "axes": axes,
                                "slots": "SLOT" in axes, "btn": "BTN_TOUCH" in block})
        if not devices:
            self.raw_touch_why = "getevent lists no multi-touch screen"
            return None
        # (the emulator numbers its touch screens, one for each display it may have: the first one first)
        devices.sort(key=lambda d: [int(x) for x in re.findall(r"\d+", d["name"])] or [0])
        # the reader's own account of each device: the display it is on, its place there
        sections = {}
        for part in re.split(r"(?m)^\s*Device -?\d+: ", dump)[1:]:
            sections.setdefault(part.split("\n", 1)[0].strip(), part)
        viewport = re.compile(r"Viewport[^\n]*?displayId=(\d+)[^\n]*?orientation=(\d+)[^\n]*?logicalFrame=\[(-?\d+), (-?\d+), (-?\d+), (-?\d+)\]"
                              r"[^\n]*?physicalFrame=\[(-?\d+), (-?\d+), (-?\d+), (-?\d+)\][^\n]*?deviceSize=\[(\d+), (\d+)\]")
        pick, where = None, None
        for d in devices:
            m = viewport.search(sections.get(d["name"], ""))
            if m and m.group(1) == "0":
                pick, where = d, [int(v) for v in m.groups()[1:]]
                break
        if pick is None:
            # no account of it: the first screen (the emulator numbers them), the forced size fitted into the panel
            pick = devices[0]
            size = self.sh("wm size")
            phys = re.search(r"Physical size: (\d+)x(\d+)", size)
            over = re.search(r"Override size: (\d+)x(\d+)", size) or phys
            if not phys:
                self.raw_touch_why = "neither the input reader nor wm size says where the screen is"
                return None
            pw, ph, lw, lh = int(phys.group(1)), int(phys.group(2)), int(over.group(1)), int(over.group(2))
            k = min(pw / float(lw), ph / float(lh))
            fw, fh = int(round(lw * k)), int(round(lh * k))
            where = [0, 0, 0, lw, lh, (pw - fw) // 2, (ph - fh) // 2, (pw - fw) // 2 + fw, (ph - fh) // 2 + fh, pw, ph]
            pick["map_from"] = "wm size (the input reader's account was not read)"
        else:
            pick["map_from"] = "the input reader's viewport"
        if where[0] != 0:
            self.raw_touch_why = "the screen is turned (orientation %d): its raw axes are not worked out here" % where[0]
            return None
        pick["map"] = {"logical": where[1:5], "physical": where[5:9], "device": where[9:11]}
        self.log("raw touch: %s" % pick)
        return pick

    @staticmethod
    def panel_point(touch, x, y):
        """A point of the screen (the UI's px) on the panel itself, in the panel's own px: where a forced size (wm size)
        is drawn smaller in the panel's middle the two differ. The input dispatcher counts in these."""
        m = touch["map"]
        lx0, ly0, lx1, ly1 = m["logical"]
        px0, py0, px1, py1 = m["physical"]
        return (px0 + (x - lx0) * (px1 - px0) / float(lx1 - lx0), py0 + (y - ly0) * (py1 - py0) / float(ly1 - ly0))

    @staticmethod
    def raw_point(touch, x, y):
        """A point of the screen (the UI's px) in the touch screen's raw axes."""
        px, py = Smoke.panel_point(touch, x, y)
        dw, dh = touch["map"]["device"]
        (xmin, xmax), (ymin, ymax) = touch["axes"]["POSITION_X"], touch["axes"]["POSITION_Y"]
        return (int(round(xmin + px * (xmax - xmin + 1) / float(dw))), int(round(ymin + py * (ymax - ymin + 1) / float(dh))))

    @staticmethod
    def raw_frame(touch, kind, at=None, track=0):
        """One frame of a single finger for the touch screen's node, as bytes: "down" and "move" at the raw point
        [at], "up". Protocol B (slots) where the screen has them, A otherwise."""
        ev = lambda t, c, v: struct.pack("<qqHHi", 0, 0, t, c, v)
        axes = touch["axes"]
        out = b""
        if touch["slots"]:
            if kind == "down":
                out += ev(EV_ABS, ABS_MT["SLOT"], 0) + ev(EV_ABS, ABS_MT["TRACKING_ID"], track)
            if kind in ("down", "move"):
                out += ev(EV_ABS, ABS_MT["POSITION_X"], at[0]) + ev(EV_ABS, ABS_MT["POSITION_Y"], at[1])
            if kind == "down":
                # a finger with a size and a weight: without them some screens' touches hover
                if "TOUCH_MAJOR" in axes:
                    out += ev(EV_ABS, ABS_MT["TOUCH_MAJOR"], max(1, min(axes["TOUCH_MAJOR"][1], 8)))
                if "PRESSURE" in axes:
                    out += ev(EV_ABS, ABS_MT["PRESSURE"], max(1, axes["PRESSURE"][1] // 4))
                if touch["btn"]:
                    out += ev(EV_KEY, BTN_TOUCH, 1)
            if kind == "up":
                out += ev(EV_ABS, ABS_MT["TRACKING_ID"], -1)
                if touch["btn"]:
                    out += ev(EV_KEY, BTN_TOUCH, 0)
        else:
            if kind in ("down", "move"):
                out += ev(EV_ABS, ABS_MT["POSITION_X"], at[0]) + ev(EV_ABS, ABS_MT["POSITION_Y"], at[1])
                if "TOUCH_MAJOR" in axes:
                    out += ev(EV_ABS, ABS_MT["TOUCH_MAJOR"], max(1, min(axes["TOUCH_MAJOR"][1], 8)))
                if "PRESSURE" in axes:
                    out += ev(EV_ABS, ABS_MT["PRESSURE"], max(1, axes["PRESSURE"][1] // 4))
            out += ev(EV_SYN, SYN_MT_REPORT, 0)
            if touch["btn"] and kind in ("down", "up"):
                out += ev(EV_KEY, BTN_TOUCH, 1 if kind == "down" else 0)
        return out + ev(EV_SYN, SYN_REPORT, 0)

    def raw_swipe_script(self, touch, points, gap, lift_with_last, rest):
        """The device's own script of one finger's swipe along [points] (the screen's px): the down, a move every
        [gap] s and the lift, each frame written to the touch screen's node as the kernel would give it. With
        [lift_with_last] the last move and the lift are one write: they reach the app together, as when a finger is
        lifted in motion; otherwise the finger stays [rest] s where it stopped before it lifts. The frames are files
        pushed to the device first, so that nothing but the writes takes time."""
        local = os.path.join(self.out, "raw", "swipe")
        shutil.rmtree(local, ignore_errors=True)
        os.makedirs(local)
        raw = [self.raw_point(touch, x, y) for x, y in points]
        frames = [self.raw_frame(touch, "down", raw[0], track=100 + len(self.notice_swipes))]
        frames += [self.raw_frame(touch, "move", at) for at in raw[1:]]
        up = self.raw_frame(touch, "up")
        if lift_with_last:
            frames[-1] += up
        else:
            frames.append(up)
        for i, data in enumerate(frames):
            with open(os.path.join(local, "f%02d.bin" % i), "wb") as f:
                f.write(data)
        self.sh("rm -rf /data/local/tmp/nagraw")
        self.adb("push", local, "/data/local/tmp/nagraw", timeout=60)
        node = touch["node"]
        cmds = ["cat /data/local/tmp/nagraw/f00.bin > %s" % node, "sleep 0.03"]
        last = len(frames) - 1
        for i in range(1, len(frames)):
            if i == last and not lift_with_last:
                cmds.append("sleep %s" % rest)
            cmds.append("cat /data/local/tmp/nagraw/f%02d.bin > %s" % (i, node))
            if i < last:
                cmds.append("sleep %s" % gap)
        return " ; ".join(cmds), raw

    def recent_touch(self):
        """The last touch events the input dispatcher sent (dumpsys input's recent events), oldest first: (ms, action,
        x, y), the place in the panel's own px (panel_point); its queue keeps the last ten or so, so a long gesture's
        start is not among them. The times are nanoseconds, with or without the unit (Android 17 and 15)."""
        out = self.sh("dumpsys input", timeout=60)
        lines = [l.strip() for l in out.splitlines() if "MotionEvent" in l and ("DOWN" in l or "MOVE" in l or "UP" in l)]
        with open(os.path.join(self.out, "input-trials.txt"), "a", encoding="utf-8") as f:
            f.write("==== after the raw swipe at step %d\n%s\n" % (self.n, "\n".join(lines[-40:])))
        seq = []
        for l in lines:
            t = re.search(r"eventTime=(\d+)(ns)?", l)
            a = re.search(r"action=(?:AMOTION_EVENT_ACTION_|ACTION_)?(DOWN|MOVE|UP)\b", l)
            at = re.search(r"pointers=\[0: \((-?[\d.]+), (-?[\d.]+)\)", l)
            if t and a and at:
                v = int(t.group(1))
                ms = v / 1e6 if (t.group(2) or v > 10 ** 11) else float(v)
                seq.append((ms, a.group(1), int(float(at.group(1))), int(float(at.group(2)))))
        return seq

    def notice_edge(self, rel, ref, band, width):
        """Where a notice's bar begins in the screenshot [rel]: the first x (the screen's px) from which the band
        [band] (y0, y1), column by column, differs from the same page without the notice ([ref], taken before it was
        shown) for 24 px on end; None when nothing in the band does (no bar). The bar is the page's inverse, light on
        the dark page, also part faded; what the page itself has in the band is the same in both and cancels out."""
        if Image is None or not rel or not ref:
            return None
        try:
            a = Image.open(os.path.join(self.out, rel)).convert("L")
            b = Image.open(os.path.join(self.out, ref)).convert("L")
        except Exception:
            return None
        if a.size != b.size:
            return None
        k = a.width / float(width)
        y0, y1 = int(band[0] * k), max(int(band[1] * k), int(band[0] * k) + 1)
        row = list(ImageChops.difference(a, b).crop((0, y0, a.width, y1)).resize((a.width, 1), Image.BOX).getdata())
        run = max(4, int(24 * k))
        lit = 0
        for x in range(len(row)):
            lit = lit + 1 if row[x] > 18 else 0
            if lit >= run:
                return int(round((x - run + 1) / k))
        return None

    def rescan_button(self):
        """The library's "Rescan" button on the Settings page, scrolled to the page's middle, clear of where a notice
        shows: (x, y) or None. Its row's title says the same words above it: the lower of the two is the button."""
        label = self.strings[self.lang]["nag_lib_rescan"]
        self.scroll_to_top()
        prev, still = None, 0
        for _ in range(30):
            nodes = self.nodes(self.dump())
            w, h = self.screen_size(nodes)
            said = sorted((n for n in self.app_nodes(nodes) if n.text.strip() == label or n.desc.strip() == label), key=lambda n: n.center[1])
            if len(said) >= 2 and said[-1].center[1] < h * 0.62:
                return said[-1].center
            sig = self.app_signature(nodes)
            still = still + 1 if sig == prev else 0
            if still >= 2:
                return said[-1].center if len(said) >= 2 and said[-1].center[1] < h * 0.78 else None
            prev = sig
            # a third of the page at a time: the button must not pass the middle unseen
            self.sh("input swipe %d %d %d %d 400" % (w // 2, int(h * 0.62), w // 2, int(h * 0.34)))
            time.sleep(0.8)
        return None

    def notice_gone(self, text, seconds):
        """The app's screen no longer says [text] (within [seconds]); a tree that could not be read says nothing."""
        nodes, gone = self.wait_for(lambda ns: bool(self.app_nodes(ns)) and not [n for n in self.app_nodes(ns) if n.text.strip() == text], seconds)
        return bool(gone)

    def notice_swipe(self, touch, way, button, place, far, lift_with_last, leaves):
        """1.0.6: one notice shown (the library's "Rescan"), swiped sideways by raw touch events and read by its
        pixels: before the swipe and about 0.3 s, 0.9 s and 1.6 s after the lift, nothing asked of the UI meanwhile
        (a notice stays four seconds). [far]: across nearly half the screen and fast, else a little and slowly;
        [lift_with_last]: the last move and the lift written together. [leaves]: what it must do, leave (no bar in
        the band 0.9 s after the lift) or come back to its place. Up to 1.0.5 a notice let go in motion did neither:
        it stayed where the finger left it, part faded, until its time was up."""
        s = self.strings[self.lang]
        w, h, band, y = place
        start = int(w * 0.30)
        if far:
            length, moves, gap = int(w * 0.46), 10, 0.008
        else:
            length, moves, gap = 90, 9, 0.03
        points = [(start + length * i // moves, y) for i in range(moves + 1)]
        script, raw = self.raw_swipe_script(touch, points, gap, lift_with_last, 0.25)
        name = "%03d-%s-%s" % (self.n + 1, self.cur_pass, slug("notice " + way))
        # the page without the notice first: the bar is read against it
        cmd = ("screencap -p /sdcard/nag-b5.png ; input tap %d %d ; sleep 0.8 ; screencap -p /sdcard/nag-b1.png ; %s ; sleep 0.3 ; "
               "screencap -p /sdcard/nag-b2.png ; sleep 0.4 ; screencap -p /sdcard/nag-b3.png ; sleep 0.5 ; screencap -p /sdcard/nag-b4.png"
               % (button[0], button[1], script))
        shots = self.raw_shots(cmd, 5, name)
        ground, shots = shots[4], shots[:4]
        # a picture that did not come is not "no bar"
        missing = [i + 1 for i, r in enumerate(shots) if r is None] + ([5] if ground is None else [])
        edges = [self.notice_edge(r, ground, band, w) for r in shots]
        sent = self.recent_touch()
        nodes = self.step("A notice %s: the UI asked" % way, wait=0.6)
        said = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_lib_rescan_started"]]
        moves_seen = [e for e in sent if e[1] == "MOVE"]
        ups = [e for e in sent if e[1] == "UP"]
        last_move = moves_seen[-1] if moves_seen else None
        together = (ups[-1][0] - last_move[0]) if ups and last_move else None
        # the finger was on the notice: the last move the dispatcher sent is where the swipe ended, on the bar's line
        # (the dispatcher counts in the panel's own px)
        end = self.panel_point(touch, points[-1][0], y)
        reached = last_move is not None and abs(last_move[2] - end[0]) <= 6 and abs(last_move[3] - end[1]) <= 6
        # the lift came as it was meant to: with the last move (the thing under test), or well after it
        if lift_with_last:
            as_meant = together is not None and together <= NOTICE_TOGETHER_MS
        else:
            as_meant = together is not None and together >= NOTICE_AT_REST_MS
        rest_edge = edges[0]
        aside = lambda e: e is not None and rest_edge is not None and abs(e - rest_edge) > NOTICE_STILL
        if leaves:
            # only where notices stay for the system's longer time: elsewhere a stuck one goes by itself after its
            # four seconds, and "no bar" would not tell the two apart
            ok = rest_edge is not None and not missing and self.notices_stay and edges[2] is None and edges[3] is None
        else:
            # (by the last screenshot a notice that stays its four seconds only may have gone at its place)
            ok = rest_edge is not None and not missing and edges[2] is not None and not aside(edges[2]) and not aside(edges[3]) \
                and (edges[3] is not None or not self.notices_stay)
        px = lambda e: "no bar" if e is None else "%d px" % e
        r = {"pass": self.cur_pass, "way": way, "far": far, "lift_with_last": lift_with_last, "leaves": leaves, "shots": shots,
             "edges": edges, "reached": reached, "as_meant": as_meant, "together_ms": together, "sent": sent[-6:], "raw": [raw[0], raw[-1]],
             "in_tree": bool(said), "tree_left": said[0].b[0] if said else None, "ok": bool(ok and reached and as_meant)}
        self.notice_swipes.append(r)
        self.check("a notice %s: the raw swipe reached it" % way, reached and as_meant and rest_edge is not None,
                   "the notice's bar before the swipe: %s; the last move the dispatcher sent: %s of the panel, the swipe's end "
                   "there: (%d, %d); the lift %s after it (asked: %s)"
                   % (px(rest_edge), "(%d, %d)" % (last_move[2], last_move[3]) if last_move else "none read", end[0], end[1],
                      "at a time not read" if together is None else "%.1f ms" % together,
                      "%d ms at the most" % NOTICE_TOGETHER_MS if lift_with_last else "%d ms at the least" % NOTICE_AT_REST_MS))
        if reached and as_meant and rest_edge is not None:
            self.check("a notice %s %s" % (way, "leaves" if leaves else "comes back to its place"), ok,
                       "its bar's left edge: %s before the swipe; %s, %s and %s about 0.3 s, 0.9 s and 1.6 s after the lift; "
                       "in the UI's tree afterwards: %s; notices stay for the system's longer time here: %s%s"
                       % (px(edges[0]), px(edges[1]), px(edges[2]), px(edges[3]), "at x %d" % said[0].b[0] if said else "no",
                          "yes" if self.notices_stay else "no", "; screenshots that did not come: %s" % missing if missing else ""))
        self.log("notice swipe [%s]: %s" % (way, r))
        # the next one starts with no notice on screen
        if not self.notice_gone(s["nag_lib_rescan_started"], 24):
            self.warnings.append("[%s] the notice was still shown 24 s after it was %s" % (self.cur_pass, way))

    def pass_notice_swipe(self):
        """1.0.6, on demand (passes=notice-swipe, either build; the workflow's ref input walks an older app with this
        walk): the owner's notice of 13 Nov ("Şu alttaki uyarı türü kaydırılınca böyle kalıyor": the library's "Rescan"
        notice a third of the way across the screen, part faded, and staying). On a screen of his phone's size (1080 x
        2340 at 480 dpi, dark, three buttons), the animations on: the Settings page's "Rescan the library" shows the
        notice, and a finger swipes it by the touch screen's raw events (raw_touch): far and lifted at rest, far and
        lifted in motion, a little and lifted in motion. The first two must take it away, the third leaves it at its
        place. The report's "A notice swiped by raw touch events" lists each with its screenshots. adb is root from
        this pass on: it is walked in a job of its own."""
        self.cur_pass, self.lang = "notice-swipe", "en"
        s = self.strings["en"]
        self.sh("am force-stop " + self.pkg)
        self.set_display("1080x2340", "480")
        self.three_button_navigation()
        self.sh("cmd uimode night yes")
        self.ensure_library()
        touch = self.raw_touch()
        if not self.check("the emulator's touch screen takes a finger's raw events", touch is not None,
                          self.raw_touch_why or "%s (%s), its place from %s: %s" % (touch["name"], touch["node"], touch["map_from"], touch["map"])):
            self.sh("cmd uimode night no")
            return
        # a notice stays four seconds; the system's "time to take action" keeps it for the walk to read where it is
        timeouts = ("accessibility_non_interactive_ui_timeout_ms", "accessibility_interactive_ui_timeout_ms")
        before = {k: self.sh("settings get secure " + k) for k in timeouts}
        for k in timeouts:
            self.sh("settings put secure %s 20000" % k)
        self.notices_stay = False
        self.animations(True)
        try:
            self.launch()
            self.back_to_tabs()
            if not self.check("Settings opens", bool(self.open_label(s["nag_nav_settings"])), ""):
                return
            self.step("Settings (the animations on)", wait=1.2)
            button = self.rescan_button()
            nodes = self.step("Settings at the library's Rescan button", wait=0.8)
            if not self.check("Settings has the library's Rescan button", button is not None,
                              "at %s" % (button,) if button else "its words were not found twice on the page"):
                return
            # one notice to learn where it shows: its words' line is where the finger goes, their height the band read
            self.sh("input tap %d %d" % button)
            nodes, said = self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.strip() == s["nag_lib_rescan_started"]], 6)
            nodes = self.step("The Rescan notice", wait=0.3)
            said = [n for n in self.app_nodes(nodes) if n.text.strip() == s["nag_lib_rescan_started"]] or said
            if not self.check("the Rescan button shows its notice", bool(said), "\"%s\"" % s["nag_lib_rescan_started"]):
                return
            w, h = self.screen_size(nodes)
            x0, y0, x1, y1 = said[0].b
            place = (w, h, (y0 + 4, y1 - 4), (y0 + y1) // 2)
            self.log("the notice's words: %s; the finger's line y %d" % (said[0].b, place[3]))
            # still there after its own four seconds: the system's longer time is in force
            time.sleep(6)
            self.notices_stay = not self.notice_gone(s["nag_lib_rescan_started"], 0)
            self.log("notices stay for the system's longer time: %s" % self.notices_stay)
            self.notice_gone(s["nag_lib_rescan_started"], 24)
            for way, far, with_last, leaves in (("swiped far, the finger lifted at rest", True, False, True),
                                                ("flung, the finger lifted in motion", True, True, True),
                                                ("swiped a little, the finger lifted in motion", False, True, False)):
                if self.over_time():
                    break
                self.notice_swipe(touch, way, button, place, far, with_last, leaves)
        finally:
            self.animations(False)
            self.sh("cmd uimode night no")
            for k in timeouts:
                old = before[k].strip()
                self.sh("settings delete secure %s" % k if old in ("", "null") else "settings put secure %s %s" % (k, old))

    # ---------------------------------------------------------------- 1.0.7: the search

    @staticmethod
    def search_library():
        """The walk's tracks as the workflow tags them: (title, artist, album) of the 64, eight an album, two albums an
        artist. (The tone beside them, "Smoke tone" of "Nagmedar QA", has no word of the pass's queries.)"""
        return [("Profile track %d.%d" % (a, t), "Nagmedar Artist %d" % ((a + 1) // 2), "Profile album %d" % a)
                for a in range(1, 9) for t in range(1, 9)]

    @staticmethod
    def search_expected(tracks, query):
        """What a search for [query] must list of [tracks] (title, artist, album), by the rule the app's search states
        (S-311): every word of the query in the title, the artist or the album, in any order, whatever the case. An artist
        is found by the name, an album by its name or its artist's, a track by its title, its artist's or its album's.
        {"artists", "albums", "tracks"}: the names of each."""
        words = query.lower().split()
        has = lambda *texts: all(any(w in t.lower() for t in texts) for w in words)
        return {"artists": {a for _, a, _ in tracks if has(a)},
                "albums": {al for _, a, al in tracks if has(al, a)},
                "tracks": {t for t, a, al in tracks if has(t, a, al)}}

    def type_text(self, text):
        """Types [text] into the focused field (`input text`, where a space is written %s)."""
        self.sh("input text '%s'" % text.replace(" ", "%s"))

    def palette_rows(self, nodes):
        """The palette's rows on screen, top to bottom: (label, detail, the label's node). A row's detail stands right
        under its label, at the same left edge. The selected hit shown again beside the list (a wide window) is no row:
        its detail begins with its kind's name."""
        s = self.strings[self.lang]
        kinds = tuple(s[k] + " · " for k in ("nag_palette_commands", "nag_palette_screens", "nag_palette_settings", "nag_palette_media"))
        texts = sorted((n for n in self.app_nodes(nodes) if n.text.strip()), key=lambda n: (n.b[1], n.b[0]))
        rows = []
        for a in texts:
            under = [b for b in texts if b is not a and abs(b.b[0] - a.b[0]) <= 2 and abs(b.b[1] - a.b[3]) <= 3]
            if under and not under[0].text.startswith(kinds):
                rows.append((a.text.strip(), under[0].text.strip(), a))
        return rows

    def palette_read(self):
        """The palette's rows on screen now, [(label, detail)]; the screen read again when a reading has no tree. (rows, nodes)."""
        nodes = []
        for _ in range(3):
            nodes = self.nodes(self.settle(0.4))
            if nodes:
                break
        return [(label, detail) for label, detail, _ in self.palette_rows(nodes)], nodes

    def palette_all_rows(self, title):
        """Every row of the palette's list for what is typed, [(label, detail)] in the order first seen, and the last
        nodes read. The list shows what fits; the selection moves through it by the arrows and the list follows it.
        Read at its top; then at its end (Up from the first row goes round to the last); when the top's first row is
        still in sight there, everything is. Else from the top again, the selection moved down by fewer rows than a
        reading shows, each reading sharing a row with the one before (when one does not, the step is taken back and
        halved: no row passes unread), until a reading shares a row with the end's."""
        nodes = self.step(title, wait=1.0)
        top = [(label, detail) for label, detail, _ in self.palette_rows(nodes)]
        if not top:
            return [], nodes
        order = list(top)

        def add(rows):
            order.extend(r for r in rows if r not in order)

        self.key(19, record=False)  # Up: round to the last row
        end, nodes = self.palette_read()
        add(end)
        if top[0] in end:
            self.key(20, record=False)
            return order, nodes
        self.key(20, record=False)  # Down: round to the first row again
        before, nodes = self.palette_read()
        add(before)
        step = max(1, min(8, len(before) - 2))
        for _ in range(400):
            if set(before) & set(end) or self.over_time():
                break
            self.sh("input keyevent " + " ".join(["20"] * step))
            rows, nodes = self.palette_read()
            if rows and not set(rows) & set(before) and step > 1:
                # rows passed between the two readings (the titles of other kinds take room too): back, and by half
                self.sh("input keyevent " + " ".join(["19"] * step))
                step = max(1, step // 2)
                before, nodes = self.palette_read()
                add(before)
                continue
            add(rows)
            if rows:
                before = rows
                step = max(1, min(8, len(rows) - 2, step * 2))
        else:
            self.warnings.append("[%s] %s: the palette's list was not read to its end (%d rows read)" % (self.cur_pass, title, len(order)))
        return order, nodes

    def search_kinds(self):
        """The first word of a media row's detail in the palette, by what the row is."""
        s = self.strings[self.lang]
        return {"artists": s["nag_column_artist"], "albums": s["nag_column_album"], "tracks": s["nag_palette_track"]}

    def search_record(self, where, query, what, want, found):
        """One query's row of the report and its checks: what was listed of each kind, beside what the library holds.
        [found]: a set of names for each kind, or a count where only the count is read (the search page's headings)."""
        missing, extra, ok = [], [], True
        counts = {}
        for kind in ("artists", "albums", "tracks"):
            got = found.get(kind)
            if isinstance(got, set):
                lost, more = sorted(want[kind] - got), sorted(got - want[kind])
                missing += lost
                extra += more
                counts[kind] = len(got)
                good = not lost and not more
                detail = "%d listed%s%s" % (len(got), "; missing: %s%s" % (", ".join(lost[:6]), " …" if len(lost) > 6 else "") if lost else "",
                                            "; not in the library's answer: %s" % ", ".join(more[:6]) if more else "")
            else:
                counts[kind] = got
                good = got == len(want[kind])
                detail = "%s listed" % ("no heading read:" if got is None else got)
            ok = ok and good
            self.check("%s \"%s\" (%s): the %d %s that match are all listed, and nothing else" % (where, query, what, len(want[kind]), kind), good, detail)
        self.searches.append({"pass": self.cur_pass, "where": where, "query": query, "what": what, "found": counts,
                              "want": {k: len(v) for k, v in want.items()}, "missing": missing, "extra": extra, "ok": ok})
        return ok

    def palette_open(self, title, query):
        """Ctrl+K, and [query] typed once the palette's field is there; the on-screen keyboard sent away; the library's
        media waited for (the palette says while it reads them; an older app says nothing: a few seconds). False when
        no palette opened."""
        s = self.strings[self.lang]
        field = s["nag_palette_placeholder"]
        self.combo(113, 39)  # Ctrl+K
        nodes = self.step(title, wait=1.0)
        if not self.check("Ctrl+K opens the palette (%s)" % title, any(field in (n.desc.strip(), n.text.strip()) for n in self.app_nodes(nodes))):
            return False
        self.type_text(query)
        time.sleep(1.5)
        if self.keyboard_shown():
            # the owner's tablet has its keyboard: no on-screen one over half the palette (the emulator shows its own
            # whatever its settings say). A Back while it is up sends it away and leaves the palette open
            self.key(4, record=False)
            time.sleep(0.8)
        reading = s.get("nag_palette_reading")
        if reading:
            self.wait_for(lambda ns: ns and not any(n.text.strip() == reading for n in self.app_nodes(ns)), 30)
        else:
            time.sleep(6)
        return True

    def palette_close(self):
        """Escape closes the palette; once more when it is still there (an on-screen keyboard that was up takes the first)."""
        self.key(111)  # Escape
        nodes = self.step("The palette closed", wait=0.8)
        field = self.strings[self.lang]["nag_palette_placeholder"]
        if any(field in (n.desc.strip(), n.text.strip()) for n in self.app_nodes(nodes)):
            self.key(111)
            self.step("The palette closed (Escape again)", wait=0.8)

    def search_palette_query(self, query, what, library):
        """The palette asked for [query]: every media row it lists, beside what the library holds."""
        if not self.palette_open("Ctrl+K: %s" % what, query):
            return
        rows, _ = self.palette_all_rows("Palette: %s" % query)
        found = {kind: {label for label, detail in rows if detail == word or detail.startswith(word + " · ")}
                 for kind, word in self.search_kinds().items()}
        self.search_record("Ctrl+K", query, what, self.search_expected(library, query), found)
        self.palette_close()

    def search_palette_opens(self, query, name, tracks_of, what):
        """[query]'s first hit must be [name] (an artist, an album); Enter on it: the palette closes and the Music view shows
        its tracks, those whose titles begin as one of [tracks_of] does, and no other of the walk's."""
        if not self.palette_open("Ctrl+K: %s" % what, query):
            return
        nodes = self.step("Palette: %s" % query, wait=1.0)
        rows = self.palette_rows(nodes)
        kinds = self.search_kinds()
        first = rows[0] if rows else ("", "", None)
        word = kinds["artists"] if what.startswith("an artist") else kinds["albums"]
        if not self.check("Ctrl+K \"%s\": the first hit is %s" % (query, what), first[0] == name and first[1].startswith(word + " · "),
                          "the first row: %s (%s)" % (first[0] or "none", first[1] or "nothing under it")):
            self.palette_close()
            return
        self.key(66)  # Enter
        nodes = self.step("Enter: %s opens in the Music" % name, wait=1.5)
        nodes, _ = self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.startswith("Profile track")], 8)
        shown = sorted({n.text.strip() for n in self.app_nodes(nodes) if n.text.startswith("Profile track")})
        titled = any(n.text.strip() == name for n in self.app_nodes(nodes))
        self.check("Enter on %s (%s) opens its tracks in the Music view" % (name, what),
                   titled and bool(shown) and all(t.startswith(tracks_of) for t in shown),
                   "%s; %d of the walk's tracks in sight: %s" % ("its name shows" if titled else "its name does not show", len(shown), ", ".join(shown[:4])))

    def search_palette_more(self):
        """A kind that shows its best only: under the commands' six a line says how many there are; the arrows stop at it
        and Enter on it lists every one of them."""
        s = self.strings[self.lang]
        if not self.palette_open("Ctrl+K: a letter many commands have", "s"):
            return
        nodes = self.step("Palette: s", wait=1.0)
        line = self.fmt_regex(s["nag_palette_show_all"]) if "nag_palette_show_all" in s else None
        title = s["nag_palette_commands"].upper()
        heads = [n for n in self.app_nodes(nodes) if n.text.strip() == title]
        # the heading's count stands at its right, on its line
        count = next((int(n.text.strip()) for h in heads for n in self.app_nodes(nodes)
                      if n.text.strip().isdigit() and abs(n.b[1] - h.b[1]) <= 3 and n.b[0] >= h.b[2] - 3), None)
        shown = [n for n in self.app_nodes(nodes) if line and line.match(n.text.strip())]
        said = int(re.sub(r"\D", "", shown[0].text)) if shown and re.sub(r"\D", "", shown[0].text) else None
        if not self.check("the commands show their best and a line that says how many there are", count is not None and count > 6 and said == count,
                          "the heading counts %s; %s" % (count, "the line: %s" % shown[0].text.strip() if shown else "no such line")):
            self.palette_close()
            return
        # six hits down from the first is the line; Enter on it
        self.sh("input keyevent 20 20 20 20 20 20")
        self.key(66)
        rows, nodes = self.palette_all_rows("Enter on the line: every command")
        left = [n for n in self.app_nodes(nodes) if line.match(n.text.strip())]
        others = [n.text.strip() for n in self.app_nodes(nodes)
                  if n.text.strip() in (s["nag_palette_screens"].upper(), s["nag_palette_settings"].upper(), s["nag_palette_media"].upper())]
        self.check("Enter on that line lists every one of the commands, and the commands alone", len(rows) == count and not left and not others,
                   "%d rows read of %d%s%s" % (len(rows), count, "; the line is still there" if left else "", "; other kinds: %s" % ", ".join(others) if others else ""))
        self.palette_close()

    def search_page(self, library):
        """The phone's search page asked for an artist: its headings count the artists, the albums and the tracks found; an
        album of them opens in the Music view."""
        s = self.strings[self.lang]
        query, what = "nagmedar artist 3", "an artist's name"
        nodes = self.open_music()
        search = [n for n in self.app_nodes(nodes) if n.desc.strip() == s["nag_search"]]
        if not self.check("the phone's top bar has the search", bool(search)):
            return
        nodes = self.step("The search page", lambda: self.tap(search[0]), wait=1.2)
        self.type_text(query)
        time.sleep(2.5)
        if self.keyboard_shown():
            # (a Back while it is up sends it away; without one it would leave the page)
            self.key(4, record=False)
            time.sleep(1.0)
        nodes = self.step("Search page: %s" % query, wait=1.0)
        # the headings: "ARTISTS · 1", "ALBUMS · 3", "TRACKS · 29" (an older app: "MUSIC · 29", its tracks alone)
        names = {"artists": [s["nag_music_view_artists"]], "albums": [s["nag_music_view_albums"]],
                 "tracks": [s["nag_music_view_tracks"]] + ([s["nag_search_music"]] if "nag_search_music" in s else [])}
        found = {k: None for k in names}
        album, swiped = None, False
        for swipe in range(6):
            for n in self.app_nodes(nodes):
                m = re.match(r"^(.+) · (\d+)$", n.text.strip())
                for kind, titles in names.items():
                    if m and m.group(1) in [t.upper() for t in titles]:
                        found[kind] = int(m.group(2))
            album = album or next((n for n in self.app_nodes(nodes) if n.text.strip() == "Profile album 5"), None)
            # an older app has no artists and albums to wait for: its tracks' heading is the first and the last
            if all(v is not None for v in found.values()) or (found["tracks"] is not None and swipe >= 2):
                break
            self.swipe_up(nodes)
            swiped = True
            nodes = self.nodes(self.settle(0.6))
        want = self.search_expected(library, query)
        self.search_record("the search page", query, what, want, {k: (0 if v is None and found["tracks"] is not None else v) for k, v in found.items()})
        if not self.check("the search page lists the album \"Profile album 5\" among what it found", album is not None):
            return
        if swiped:
            self.scroll_to_top()
        nodes = self.nodes(self.settle(0.6))
        album = next((n for n in self.app_nodes(nodes) if n.text.strip() == "Profile album 5"), None)
        if album is None:
            self.check("the album's row is back in sight at the page's top", False)
            return
        nodes = self.step("The album tapped on the search page", lambda: self.tap(album), wait=1.5)
        nodes, _ = self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.startswith("Profile track")], 8)
        shelf = [n.b for n in self.by_id(nodes, "recent-shelf")]
        shown = sorted({n.text.strip() for n in self.app_nodes(nodes) if n.text.startswith("Profile track") and not any(self.inside(n, b) for b in shelf)})
        self.check("the album tapped on the search page opens its tracks in the Music view",
                   bool(shown) and all(t.startswith("Profile track 5.") for t in shown) and any(n.text.strip() == "Profile album 5" for n in self.app_nodes(nodes)),
                   "%d of the walk's tracks in sight: %s" % (len(shown), ", ".join(shown[:4])))

    def pass_search(self):
        """1.0.7, on demand (passes=search, either build; the workflow's ref input walks an older app with this walk): the
        search with the walk's library in the device's Music, whose every tag the walk knows (64 tracks "Profile track
        a.t" of eight albums "Profile album a", two albums each of "Nagmedar Artist 1" to "4", and the tone). The
        owner's report of 13 Nov: "ctrl k arama sayfası düzgün çalışmıyor. Her sonucu vermiyor". In 1.0.6 the palette
        looked for a track in its title alone (an artist's or an album's name found nothing), showed six of however many
        it found (fifty of the media asked for) and offered neither artists nor albums.
        On the tablet's screen, with a keyboard's Ctrl+K: four queries, each row of each read and compared with what the
        library holds (search_palette_query); an artist and an album opened from the palette (search_palette_opens); the
        line under a kind that shows its best only (search_palette_more). Then the phone's screen: the search page
        (search_page). The report's "The search" has every query."""
        self.cur_pass, self.lang = "search", "en"
        self.sh("am force-stop " + self.pkg)
        self.set_display(None, None)
        self.rotate(0)
        self.ensure_library()
        try:
            self.launch()
            nodes = self.open_music()
            # the scan may still be going: the walk's tracks are waited for, then the Music's count of them
            library = self.search_library()
            counted = re.compile(r"^(\d+) tracks?$")
            count_of = lambda ns: next((int(counted.match(n.text.strip()).group(1)) for n in self.app_nodes(ns) if counted.match(n.text.strip())), None)
            self.wait_for(lambda ns: [n for n in self.app_nodes(ns) if n.text.startswith("Profile track")], 40)
            nodes, _ = self.wait_for(lambda ns: (count_of(ns) or 0) >= len(library), 60)
            if not self.check("the Music holds the walk's %d tracks, which the search is compared with" % len(library),
                              (count_of(nodes) or 0) >= len(library), "the Music counts %s" % count_of(nodes)):
                return
            for query, what in (("profile", "a word of every title and album"), ("nagmedar artist 3", "an artist's name"),
                                ("album 6", "an album's name"), ("track 2.5 artist 1", "a title with its artist")):
                if self.over_time():
                    return
                self.search_palette_query(query, what, library)
            self.search_palette_opens("nagmedar artist 3", "Nagmedar Artist 3", ("Profile track 5.", "Profile track 6."), "an artist, by the name")
            self.search_palette_opens("profile album 7", "Profile album 7", ("Profile track 7.",), "an album, by its name")
            self.search_palette_more()
            # the phone's search page, on a screen of the owner's phone's size
            self.sh("am force-stop " + self.pkg)
            self.set_display("1080x2340", "480")
            self.launch()
            self.search_page(library)
        finally:
            self.sh("am force-stop " + self.pkg)
            self.set_display(None, None)

    def pass_sheet_trials(self):
        """1.0.3, on demand (passes=sheet-trials): the phone's track sheet dragged down from its middle again and again, by
        1.0.2's `input swipe … 300` and by drag() in turn, each trial with the moves the system delivered (dumpsys
        input) and whether the sheet went: the evidence for the flaky check of 1.0.2's personal walk on API 37."""
        self.cur_pass, self.lang = "sheet-trials", "en"
        s = self.strings["en"]
        self.set_display("1080x2400", "420")
        self.ensure_library()
        nodes = self.open_music()
        rx = self.fmt_regex(s["nag_more_actions_for"])
        kinds = ("swipe", "drag", "stalled")
        for i in range(int(os.environ.get("SHEET_TRIALS", "10")) * len(kinds)):
            if self.over_time():
                break
            how = kinds[i % len(kinds)]
            nodes = self.nodes(self.dump())
            w, h = self.screen_size(nodes)
            buttons = [n for n in self.app_nodes(nodes) if rx.match(n.desc.strip()) and h * 0.2 < n.center[1] < h * 0.75]
            if not buttons:
                nodes = self.open_music()
                continue
            nodes = self.step("Trial %d: a track's ⋯" % (i + 1), lambda: self.tap(buttons[0]))
            box = self.sheet_still(nodes)
            if not box:
                self.sheet_trials.append({"trial": i + 1, "how": how, "closed": None, "note": "no sheet"})
                continue
            x, y = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
            self.sh("dumpsys input > /dev/null")
            t = time.time()
            if how == "swipe":
                self.sh("input swipe %d %d %d %d 300" % (x, y, x, int(h * 0.97)))
            elif how == "drag":
                self.drag(x, y, x, int(h * 0.97))
            else:
                # what `input swipe` sends when the app stalls on an early move past the swipe's 300 ms: a few
                # short moves, then the lift at the far end (the lift's jump is no drag, and a pause makes no fling)
                self.sh(" ; ".join(["input motionevent DOWN %d %d" % (x, y)] + ["input motionevent MOVE %d %d" % (x, y + d)
                                                                               for d in (40, 80, 120)]
                                   + ["sleep 0.1", "input motionevent UP %d %d" % (x, int(h * 0.97))]), timeout=60)
            ms = int((time.time() - t) * 1000)
            moves = self.delivered_moves()
            time.sleep(1.5)
            nodes = self.step("Trial %d: dragged by %s" % (i + 1, how), wait=0.3)
            closed = not self.by_id(nodes, "sheet")
            self.sheet_trials.append({"trial": i + 1, "how": how, "closed": closed, "ms": ms, "from": y, "height": box[3] - box[1],
                                      "moves": moves})
            self.log("sheet trial %d (%s): %s in %d ms; moves %s" % (i + 1, how, "closed" if closed else "STAYED", ms, moves))
            if not closed:
                self.key(4)
                self.step("Trial %d: Back closes the sheet" % (i + 1))
        for how in kinds:
            done = [t for t in self.sheet_trials if t["how"] == how and t["closed"] is not None]
            self.warnings.append("[sheet-trials] %s: %d of %d closed the sheet" % (how, sum(1 for t in done if t["closed"]), len(done)))
        with open(os.path.join(self.out, "sheet-trials.md"), "w", encoding="utf-8") as f:
            f.write("| trial | how | closed | ms | from y | sheet height | events (action@y+ms, the display's own pixels) |\n"
                    "|---|---|---|---|---|---|---|\n")
            for t in self.sheet_trials:
                f.write("| %s | %s | %s | %s | %s | %s | %s |\n" % (t["trial"], t["how"], t["closed"], t.get("ms", ""), t.get("from", ""),
                                                                t.get("height", ""), t.get("moves", t.get("note", ""))))

    def delivered_moves(self):
        """The last touch gesture the input dispatcher sent (dumpsys input's recent events): each MotionEvent's action, y
        and time, so a trial shows how many moves arrived between the finger's down and its lift."""
        out = self.sh("dumpsys input", timeout=60)
        # the raw lines, whatever this system's format, for the evidence (input-trials.txt)
        lines = [l.strip() for l in out.splitlines() if "MotionEvent" in l and ("DOWN" in l or "MOVE" in l or "UP" in l)]
        with open(os.path.join(self.out, "input-trials.txt"), "a", encoding="utf-8") as f:
            f.write("==== trial at step %d\n%s\n" % (self.n, "\n".join(lines[-40:])))
        seq = []
        for l in lines:
            # API 35/37: "MotionEvent(…, eventTime=318172000000ns, …, action=MOVE, …, pointers=[0: (1280.0, 976.8)]), …"
            t = re.search(r"eventTime=(\d+)(ns)?", l)
            a = re.search(r"action=(?:AMOTION_EVENT_ACTION_|ACTION_)?(DOWN|MOVE|UP)\b", l)
            y = re.search(r"pointers=\[0: \(-?[\d.]+, (-?[\d.]+)\)", l) or re.search(r"(?:y\[0\]|\by)=(-?[\d.]+)", l)
            if t and a and y:
                ms = int(t.group(1)) // (1000000 if t.group(2) else 1)
                seq.append((ms, a.group(1), int(float(y.group(1)))))
        # the last gesture: from its last DOWN (the queue keeps the last few events only: a long gesture loses its start)
        downs = [i for i, e in enumerate(seq) if e[1] == "DOWN"]
        seq = seq[downs[-1]:] if downs else seq[-16:]
        if not seq:
            return "?"
        t0 = seq[0][0]
        return " ".join("%s@%d+%dms" % (a, y, t - t0) for t, a, y in seq[:20])

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
            branch = self.a.branch or "ci/emulator"
            for cmd in (["git", "init", "-q"], ["git", "checkout", "-q", "--orphan", branch], ["git", "add", "-A"],
                        ["git", "commit", "-q", "-m", "Emulator smoke test, partial: " + why],
                        ["git", "push", "-q", "--force", url, "HEAD:refs/heads/" + branch]):
                subprocess.run(cmd, cwd=work, env=env, capture_output=True, timeout=180)
        except Exception as e:
            self.log("publish failed: %r" % e)

    def write_progress(self):
        with open(os.path.join(self.out, "progress.txt"), "w", encoding="utf-8") as f:
            f.write("%d s, %d steps, pass %s, %d crashes\n" % (time.time() - self.t0, len(self.steps), self.cur_pass, len(self.crashes)))
            for st in self.steps[-8:]:
                f.write("%d %s %s %s\n" % (st["n"], st["pass"], st["title"], st["status"]))

    def strict_dropbox(self):
        """The app's StrictMode entries in the system's dropbox: a debuggable app runs under the system's
        policy, whose log line names only the violation; the stack is in data_app_strictmode."""
        _, dump = self.adb("shell", "dumpsys", "dropbox", "--print", "data_app_strictmode", timeout=180)
        entries = re.split(r"^=+\s*$", dump, flags=re.M)
        for e in entries:
            if ("Process: %s" % self.pkg) not in e:
                continue
            lines = e.strip().splitlines()
            start = next((k for k, l in enumerate(lines) if re.match(r"\s*android\.os\.strictmode\.\w+", l)), None)
            if start is None:
                continue
            stack = lines[start:start + 160]
            m = re.search(r"android\.os\.strictmode\.(\w+)", stack[0])
            cls = m.group(1) if m else "?"
            frame, other = "?", "?"
            for l in stack[1:]:
                m = re.search(r"at ((?:app\.nagmedar|org\.videolan)[\w.$<>]+)", l)
                if m and frame == "?":
                    frame = m.group(1)
                m = re.search(r"at ((?!android\.|androidx\.|java\.|kotlin\.|kotlinx\.|dalvik\.|com\.android\.|libcore\.|sun\.|okhttp3\.|okio\.)[\w.$<>]+)", l)
                if m and other == "?":
                    other = m.group(1)
            if frame == "?":
                frame = other
            key = (cls, frame)
            self.strict_dropbox_count[key] += 1
            if key not in self.strict_blocks:
                head = [l for l in lines[:start] if l.startswith(("Process:", "Duration-Millis:", "Loop-Violation-Number:"))]
                self.strict_blocks[key] = "\n".join(head + stack)

    def finish(self):
        self.logfile.close()
        try:
            self.strict_dropbox()
        except Exception as e:  # the report must still be written
            self.warnings.append("dropbox could not be read: %s" % e)
        # the words of what 1.0 deleted, over every dump of the walk
        self.cur_pass = "all"
        self.check("no film, series, video, Home, TV Lab or Whisper words on the app's screens", not self.gone,
                   "; ".join("%s (%s)" % kv for kv in self.gone.items()))
        if self.a.build == "debug":
            self.check("no StrictMode violation of the app (debug build)", self.strict_total == 0 and not self.strict_dropbox_count,
                       "%d in the log, %d in the dropbox" % (self.strict_total, sum(self.strict_dropbox_count.values())))
        failed = [c for c in self.checks if not c["ok"]]
        verdict = "FAILED" if (self.crashes or self.errors or failed) else "passed"
        dur = int(time.time() - self.t0)
        md = []
        md.append("# Emulator smoke test: %s\n" % verdict)
        md.append("- app: `%s`, APK `%s`" % (self.pkg, os.path.basename(self.a.apk.rstrip("/"))))
        md.append("- installed: %s" % (getattr(self, "app_info", "") or "?"))
        md.append("- device: %s" % ", ".join("%s=%s" % kv for kv in getattr(self, "device", {}).items()))
        md.append("- source: %s" % (self.a.source or "?"))
        md.append("- passes: %s" % self.a.passes)
        md.append("- %d steps in %d min %d s; %d crash/ANR finding(s), %d driver error(s), %d StrictMode violation(s), "
                  "%d of %d 1.0 checks failed; %d ANR/crash of the system (the emulator's, not failures)\n"
                  % (len(self.steps), dur // 60, dur % 60, len(self.crashes), len(self.errors), self.strict_total,
                     len(failed), len(self.checks), len(self.system_events)))
        md.append("## 1.0 checks\n")
        md.append("| pass | step | check | result | seen |")
        md.append("|------|------|-------|--------|------|")
        for c in self.checks:
            md.append("| %s | %d | %s | %s | %s |" % (c["pass"], c["step"], c["name"], "ok" if c["ok"] else "**FAILED**",
                                                    c["detail"].replace("|", "/").replace("\n", " ")[:300]))
        md.append("")
        md.append("## Frames (dumpsys gfxinfo; informational on an emulator, over 40 % janky is flagged)\n")
        md.append("| pass | step | gesture | frames | janky | 50th | 90th | 95th | 99th |")
        md.append("|------|------|---------|--------|-------|------|------|------|------|")
        for r in self.jank:
            pct = lambda k: "%d ms" % r[k] if r[k] is not None else "?"
            janky = "?" if r["janky"] is None else "%.1f %%%s" % (r["janky"], " **(over 40 %)**" if r["janky"] > 40 else "")
            md.append("| %s | %d | %s | %s | %s | %s | %s | %s | %s |" % (r["pass"], r["step"], r["what"],
                      "?" if r["frames"] is None else "%d" % r["frames"], janky, pct("p50"), pct("p90"), pct("p95"), pct("p99")))
        md.append("")
        if self.back_presses:
            md.append("## The system's Back button (1.0.4: touched as the owner touches it; whole: at most %.0f %% of the page differs)\n"
                      % (BACK_HELD_WHOLE * 100))
            md.append("The system: %s\n" % (self.back_system_info or "?"))
            md.append("| pass | page | the button | at rest | held, a tap's length | differs | held, a second on | differs | let go | tapped |")
            md.append("|------|------|------------|---------|----------------------|---------|-------------------|---------|--------|--------|")
            link = lambda rel: "[%s](%s)" % (os.path.basename(rel), rel) if rel else "none"
            pct = lambda c: "?" if c is None else "%.1f %%" % (c * 100)
            for p in self.back_presses:
                md.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
                    p["pass"], p["page"], p["button"], link(p["rest"]), link(p["held"][0]), pct(p["changes"][0]),
                    link(p["held"][1]), pct(p["changes"][1]), p["let_go"], p["tap"] or "not needed"))
            md.append("")
        if self.page_openings:
            md.append("## Pages opened with the animations on (1.0.5: on screen when more than %.0f %% of the page's place between the "
                      "bars is drawn, an empty page has 0.0 %%; it scrolls when it follows a swipe by %.0f %% of its length at least)\n"
                      % (PAGE_INK * 100, PAGE_FOLLOWS * 100))
            md.append("| pass | page | 0.3 s | 0.8 s | 2.5 s | in the UI's tree | moved under the swipe | result |")
            md.append("|------|------|-------|-------|-------|------------------|-----------------------|--------|")
            link = lambda rel, v: "none" if not rel else "[%s](%s)" % ("?" if v is None else "%.1f %%" % (v * 100), rel)
            for r in self.page_openings:
                cells = [link(rel, v) for rel, v in zip(r["shots"], r["inks"])]
                if not r["swiped"]:
                    moved = "not swiped"
                elif r["moved"] is None:
                    moved = "all its episodes are others" if r["scrolls"] else "**no episodes in sight**"
                else:
                    moved = "%d px" % r["moved"] if r["scrolls"] else "**%d px**" % r["moved"]
                md.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
                    r["pass"], r["what"], cells[0], cells[1], cells[2], "yes" if r["in_tree"] else "**no**", moved,
                    "ok" if r["ok"] else "**FAILED**"))
            md.append("")
        if self.searches:
            md.append("## The search (1.0.7: what each query listed, beside what the walk's library holds by the search's rule: "
                      "every word in the title, the artist or the album)\n")
            md.append("| pass | where | query | artists listed / in the library | albums | tracks | missing | listed but not in the library | result |")
            md.append("|------|-------|-------|---------------------------------|--------|--------|---------|-------------------------------|--------|")
            for r in self.searches:
                cut = lambda names: (", ".join(names[:6]) + (" … (%d)" % len(names) if len(names) > 6 else "")) or "none"
                md.append("| %s | %s | `%s` (%s) | %s | %s | %s | %s | %s | %s |" % (
                    r["pass"], r["where"], r["query"], r["what"],
                    *("%s / %d" % ("not read" if r["found"][k] is None else r["found"][k], r["want"][k]) for k in ("artists", "albums", "tracks")),
                    cut(r["missing"]), cut(r["extra"]), "ok" if r["ok"] else "**FAILED**"))
            md.append("")
        if self.notice_swipes:
            md.append("## A notice swiped by raw touch events (1.0.6: the bar's left edge in px, read from the screen; "
                      "\"no bar\": it has left)\n")
            md.append("| pass | the swipe | the lift after the last move | before | 0.3 s | 0.9 s | 1.6 s | in the UI's tree after | must | result |")
            md.append("|------|-----------|------------------------------|--------|-------|-------|-------|------------------------|------|--------|")
            cell = lambda rel, e: "none" if not rel else "[%s](%s)" % ("no bar" if e is None else "%d px" % e, rel)
            for r in self.notice_swipes:
                cells = [cell(rel, e) for rel, e in zip(r["shots"], r["edges"])]
                md.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
                    r["pass"], r["way"], "?" if r["together_ms"] is None else "%.1f ms" % r["together_ms"], cells[0], cells[1], cells[2],
                    cells[3], "at x %d" % r["tree_left"] if r["in_tree"] else "no", "leave" if r["leaves"] else "come back",
                    "ok" if r["ok"] else ("**FAILED**" if r["reached"] and r.get("as_meant", True) else "**not the swipe meant**")))
            md.append("")
        if self.crashes:
            md.append("## Crashes and ANRs\n")
            for i, c in enumerate(self.crashes, 1):
                md.append("### %d. %s (pass %s, step %d)\n" % (i, c["kind"], c["pass"], c["step"]))
                md.append("```\n%s\n```\n" % c["text"][:6000])
        if self.errors:
            md.append("## Driver errors\n")
            md += ["- %s" % e for e in self.errors]
            md.append("")
        md.append("## The system's ANRs and crashes (the emulator's own: recorded, the step tried once more)\n")
        if self.system_events:
            md.append("| pass | step | what | whose | seen | how |")
            md.append("|------|------|------|-------|------|-----|")
            for e in self.system_events:
                md.append("| %s | %d | %s | %s | %s | %s |" % (e["pass"], e["step"], e["kind"], e["who"].replace("|", "/"),
                                                        e["seen"].replace("|", "/")[:200], e["how"]))
        else:
            md.append("None.")
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
        md.append("### StrictMode violations: %d (the app's; by the system's policy for a debuggable app)\n" % self.strict_total)
        counts = self.strict_dropbox_count if self.strict_dropbox_count else self.strict
        for (cls, frame), cnt in counts.most_common(40):
            md.append("- %dx %s at `%s`" % (cnt, cls, frame))
        if self.strict_blocks:
            md.append("\nThe first whole stack of each is in strictmode.txt.")
        md.append("")
        with open(os.path.join(self.out, "strictmode.txt"), "w", encoding="utf-8") as f:
            for (cls, frame), block in self.strict_blocks.items():
                f.write("==== %s at %s (%dx)\n%s\n\n" % (cls, frame, counts[(cls, frame)], block))
        md.append("### Error-level log lines of the app (top 25)\n")
        for k, v in self.app_errors.most_common(25):
            md.append("- %dx `%s`" % (v, k))
        md.append("")
        with open(os.path.join(self.out, "report.md"), "w", encoding="utf-8") as f:
            f.write("\n".join(md))
        with open(os.path.join(self.out, "report.json"), "w", encoding="utf-8") as f:
            json.dump({"verdict": verdict, "steps": self.steps, "crashes": self.crashes, "errors": self.errors,
                       "warnings": self.warnings, "strictmode": self.strict_total, "checks": self.checks,
                       "frames": self.jank, "seconds": dur, "passes": self.a.passes, "source": self.a.source,
                       "system": self.system_events, "sheet_trials": self.sheet_trials, "back_presses": self.back_presses,
                       "page_openings": self.page_openings, "notice_swipes": self.notice_swipes, "searches": self.searches}, f, indent=1)
        ann = []
        for c in self.crashes:
            head = c["text"].splitlines()
            body = " | ".join(head[:12])
            ann.append("::error title=Emulator %s (%s, step %d)::%s" % (c["kind"], c["pass"], c["step"], enc(body[:900])))
        for e in self.errors:
            ann.append("::error title=Emulator driver::%s" % enc(e[:600]))
        for c in failed:
            ann.append("::error title=Emulator check (%s, step %d)::%s %s" % (c["pass"], c["step"], c["name"], enc(c["detail"][:600])))
        for e in self.system_events:
            ann.append("::warning title=Emulator: the system's %s (%s, step %d)::%s %s" % (e["kind"], e["pass"], e["step"], enc(e["who"]),
                                                                                       enc(e["seen"][:300])))
        summary = "%s: %d steps, %d crash/ANR, %d StrictMode, %d/%d checks failed, %d system ANR/crash, %d warnings, %d min. Evidence: branch %s, report.md" % (
            verdict, len(self.steps), len(self.crashes), self.strict_total, len(failed), len(self.checks), len(self.system_events),
            len(self.warnings), dur // 60, self.a.branch or "ci/emulator")
        ann.append("::notice title=Emulator Ekran görüntüleri ve rapor::%s" % enc(summary))
        with open(os.path.join(self.out, "annotations.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(ann) + "\n")
        print("\n".join(md[:12]))
        return 1 if (self.crashes or self.errors or failed) else 0


def enc(s):
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--apk", required=True, help="APK file or a directory with it")
    p.add_argument("--package", default="app.nagmedar.debug")
    p.add_argument("--out", default="emulator-out")
    p.add_argument("--res", default="application/nagmedar-ui/src/main/res")
    p.add_argument("--media", default="", help="directory with tone.mp3 to push to the device")
    p.add_argument("--music", default="", help="directory of tagged .mp3 tracks (\"Profile track …\") pushed to the device's Music")
    p.add_argument("--feed", default="", help="a podcast feed's address the walk follows (the emulator's view of it)")
    p.add_argument("--auto-apk", default="", help="application/benchmark's APK (file or directory) whose AutoBrowseTest reads Android Auto's tree")
    p.add_argument("--build", default="debug", help="the build walked (debug: StrictMode violations fail the run)")
    p.add_argument("--passes", default="tablet-en,phone-en,workstation,phone-tr")
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
                s.other_app_expected = False
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
