"""Offline checks of the walk's raw-touch notice pass (no device): python3 notice_swipe_offline.py <emulator_smoke.py> <res dir> <tmp dir>"""
import importlib.util, os, struct, sys, types
from PIL import Image, ImageDraw

spec = importlib.util.spec_from_file_location("smoke", sys.argv[1])
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
out = sys.argv[3]
a = types.SimpleNamespace(package="app.nagmedar.debug", out=out, adb="adb", deadline_min=40, res=sys.argv[2], feed="", media="", music="",
                          auto_apk="", build="debug", passes="notice-swipe", source="", branch="")
s = smoke.Smoke(a)

GETEVENT = """add device 1: /dev/input/event3
  name:     "virtio_input_multi_touch_2"
  events:
    KEY (0001): BTN_TOUCH
    ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                ABS_MT_TOUCH_MAJOR    : value 0, min 0, max 2147483647, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_X     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_Y     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
                ABS_MT_PRESSURE       : value 0, min 0, max 1023, fuzz 0, flat 0, resolution 0
  input props:
    INPUT_PROP_DIRECT
add device 2: /dev/input/event2
  name:     "virtio_input_multi_touch_1"
  events:
    KEY (0001): BTN_TOUCH
    ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                ABS_MT_TOUCH_MAJOR    : value 0, min 0, max 2147483647, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_X     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                ABS_MT_POSITION_Y     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                ABS_MT_TRACKING_ID    : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
                ABS_MT_PRESSURE       : value 0, min 0, max 1023, fuzz 0, flat 0, resolution 0
  input props:
    INPUT_PROP_DIRECT
add device 3: /dev/input/event0
  name:     "gpio-keys"
  events:
    KEY (0001): KEY_POWER
"""
DUMPSYS = """Input Reader State (Nums of device: 3):
  Device 2: virtio_input_multi_touch_1
    EventHub Devices: [ 2 ]
    Touch Input Mapper (mode - DIRECT):
      Raw Touch Axes:
        X: min=0, max=32767, flat=0, fuzz=0, resolution=0
      Viewport INTERNAL: displayId=0, uniqueId=local:4619827259835644672, port=0, orientation=0, logicalFrame=[0, 0, 1080, 2340], physicalFrame=[911, 0, 1649, 1600], deviceSize=[2560, 1600], isActive=[1]
  Device 3: virtio_input_multi_touch_2
    EventHub Devices: [ 3 ]
RecentQueue: length=4
      MotionEvent(deviceId=2, eventTime=1000000000ns, source=TOUCHSCREEN, displayId=0, action=DOWN, pointers=[0: (1132.4, 1418.8)]), policyFlags=0x62000000, age=30ms
      MotionEvent(deviceId=2, eventTime=1150000000ns, source=TOUCHSCREEN, displayId=0, action=MOVE, pointers=[0: (1437.2, 1418.8)]), policyFlags=0x62000000, age=30ms
      MotionEvent(deviceId=2, eventTime=1166000000ns, source=TOUCHSCREEN, displayId=0, action=MOVE, pointers=[0: (1471.3, 1418.8)]), policyFlags=0x62000000, age=30ms
      MotionEvent(deviceId=2, eventTime=1166000000ns, source=TOUCHSCREEN, displayId=0, action=UP, pointers=[0: (1471.3, 1418.8)]), policyFlags=0x62000000, age=30ms
"""
calls = []
def fake_sh(cmd, timeout=60):
    calls.append(cmd)
    if cmd == "id -u": return "0"
    if cmd == "getevent -lp": return GETEVENT
    if cmd == "dumpsys input": return DUMPSYS
    if cmd == "wm size": return "Physical size: 2560x1600\nOverride size: 1080x2340"
    if cmd == "wm density": return "Physical density: 320\nOverride density: 480"
    return ""
pushed = []
def fake_adb(*args, timeout=60, binary=False):
    if args and args[0] == "push": pushed.append(args)
    return 0, (b"" if binary else "")
s.sh, s.adb = fake_sh, fake_adb
smoke.time.sleep = lambda x: None

t = s.raw_touch()
assert t is not None, s.raw_touch_why
assert t["name"] == "virtio_input_multi_touch_1" and t["node"] == "/dev/input/event2", t
assert t["map"] == {"logical": [0, 0, 1080, 2340], "physical": [911, 0, 1649, 1600], "device": [2560, 1600]}, t["map"]
assert t["slots"] and t["btn"]
# the screen's corners and its middle in raw axes
x, y = s.raw_point(t, 0, 0); assert abs(x - 911 * 32768 / 2560) <= 1 and y == 0, (x, y)
x, y = s.raw_point(t, 1080, 2340); assert abs(x - 1649 * 32768 / 2560) <= 1 and abs(y - 32768) <= 1, (x, y)
x, y = s.raw_point(t, 540, 1170); assert abs(x - 1280 * 32768 / 2560) <= 1 and abs(y - 16384) <= 1, (x, y)
# and back, as the input reader does it
def back(rx, ry):
    px, py = rx * 2560 / 32768.0, ry * 1600 / 32768.0
    return (px - 911) * 1080 / 738.0, py * 2340 / 1600.0
bx, by = back(*s.raw_point(t, 324, 2075)); assert abs(bx - 324) < 1.5 and abs(by - 2075) < 1.5, (bx, by)

# without the reader's account: from wm size
DUMPSYS_KEEP = DUMPSYS
DUMPSYS = "nothing here"
def fake_sh2(cmd, timeout=60):
    if cmd == "dumpsys input": return "nothing"
    return fake_sh(cmd, timeout)
s.sh = fake_sh2
t2 = s.raw_touch()
assert t2 is not None and t2["name"] == "virtio_input_multi_touch_1", t2
assert t2["map"]["logical"] == [0, 0, 1080, 2340] and t2["map"]["device"] == [2560, 1600], t2["map"]
assert abs(t2["map"]["physical"][0] - 911) <= 1 and abs(t2["map"]["physical"][2] - 1649) <= 1, t2["map"]
s.sh = fake_sh

# frames
def events(data):
    assert len(data) % 24 == 0
    return [struct.unpack("<qqHHi", data[i:i + 24])[2:] for i in range(0, len(data), 24)]
d = events(s.raw_frame(t, "down", (100, 200), track=7))
assert d[0] == (3, 0x2f, 0) and d[1] == (3, 0x39, 7) and (3, 0x35, 100) in d and (3, 0x36, 200) in d and (1, 0x14a, 1) in d and d[-1] == (0, 0, 0), d
assert (3, 0x3a, 255) in d and (3, 0x30, 8) in d, d
m = events(s.raw_frame(t, "move", (110, 200)))
assert m == [(3, 0x35, 110), (3, 0x36, 200), (0, 0, 0)], m
u = events(s.raw_frame(t, "up"))
assert u == [(3, 0x39, -1), (1, 0x14a, 0), (0, 0, 0)], u

# the script: in motion (the last move and the lift one write), at rest (the lift after a pause)
pts = [(324 + 50 * i, 2075) for i in range(11)]
script, raw = s.raw_swipe_script(t, pts, 0.008, True, 0.25)
files = sorted(os.listdir(os.path.join(out, "raw", "swipe")))
assert files == ["f%02d.bin" % i for i in range(11)], files
last = events(open(os.path.join(out, "raw", "swipe", "f10.bin"), "rb").read())
assert last[0][1] == 0x35 and last[-3] == (3, 0x39, -1) and last[-1] == (0, 0, 0) and len(last) == 6, last
assert script.count("cat ") == 11 and script.count("sleep 0.008") == 9 and "sleep 0.25" not in script, script
assert script.endswith("cat /data/local/tmp/nagraw/f10.bin > /dev/input/event2"), script
script, raw = s.raw_swipe_script(t, pts, 0.008, False, 0.25)
files = sorted(os.listdir(os.path.join(out, "raw", "swipe")))
assert files == ["f%02d.bin" % i for i in range(12)], files
assert script.count("cat ") == 12 and script.endswith("sleep 0.25 ; cat /data/local/tmp/nagraw/f11.bin > /dev/input/event2"), script
assert events(open(os.path.join(out, "raw", "swipe", "f11.bin"), "rb").read()) == [(3, 0x39, -1), (1, 0x14a, 0), (0, 0, 0)]
assert pushed and pushed[-1][2] == "/data/local/tmp/nagraw", pushed[-1]

# the dispatcher's last events
DUMPSYS = DUMPSYS_KEEP
seq = s.recent_touch()
assert [e[1] for e in seq] == ["DOWN", "MOVE", "MOVE", "UP"] and seq[-2][2:] == (1471, 1418) and seq[-1][0] - seq[-2][0] == 0, seq
end = s.panel_point(t, 820, 2075); assert abs(end[0] - 1471.3) < 1 and abs(end[1] - 1418.8) < 1, end
assert abs(seq[1][0] - seq[0][0] - 150.0) < 0.01, seq

# the bar's edge, read against the page without it
os.makedirs(os.path.join(out, "shots"), exist_ok=True)
def page(name, bar=None, alpha=1.0):
    im = Image.new("RGB", (1080, 2340), (17, 20, 24))
    dr = ImageDraw.Draw(im)
    for i in range(40):  # the page's own light text under the band
        dr.rectangle((96 + i * 22, 2030, 96 + i * 22 + 14, 2060), fill=(230, 232, 236))
    if bar is not None:
        c = tuple(int(17 + (232 - 17) * alpha) for _ in range(3))
        dr.rounded_rectangle((bar, 2003, min(bar + 984, 1300), 2147), 27, fill=c)
        dr.rectangle((bar + 42, 2040, bar + 600, 2060), fill=(17, 20, 24))
    im.save(os.path.join(out, "shots", name))
    return "shots/" + name
ref = page("ref.png")
band = (2020, 2120)
assert s.notice_edge(page("rest.png", 48), ref, band, 1080) in (48, 49, 50, 51), s.notice_edge("shots/rest.png", ref, band, 1080)
e = s.notice_edge(page("aside.png", 48 + 472, 0.52), ref, band, 1080); assert abs(e - 520) <= 4, e
assert s.notice_edge(page("gone.png"), ref, band, 1080) is None
e = s.notice_edge(page("faint.png", 48 + 800, 0.19), ref, band, 1080); assert e is not None and abs(e - 848) <= 4, e

# 1.0.6, after the review: the verdict of one swipe, end to end, with what the device would have given
rest, aside, gone, back = page("v-rest.png", 48), page("v-aside.png", 48 + 472, 0.52), page("v-gone.png"), page("v-back.png", 50)
def verdict(shots5, together_ms, lift_with_last, leaves, stay=True):
    s.notice_swipes, got = [], []
    s.notices_stay = stay
    s.strings.setdefault("en", {})["nag_lib_rescan_started"] = "Scanning the library again; the list stays as it is meanwhile"
    s.lang = "en"
    s.raw_shots = lambda cmd, count, name: list(shots5)
    s.raw_swipe_script = lambda touch, points, gap, with_last, rest_s: ("true", [b"", b""])
    def recent():
        return [(1000.0, "DOWN", 0, 0), (1150.0, "MOVE", int(end_x[0]), int(end_x[1])), (1150.0 + together_ms, "UP", int(end_x[0]), int(end_x[1]))]
    s.recent_touch = recent
    s.step = lambda title, action=None, wait=0.8, shot=True, read=True: []
    s.app_nodes = lambda nodes: []
    s.notice_gone = lambda text, seconds: True
    s.check = lambda name, ok, seen="": (got.append((name, bool(ok), seen)), bool(ok))[1]
    s.log = lambda *a: None
    return got
place = (1080, 2340, band, 2075)
def run(shots5, together_ms, lift_with_last, leaves, far, stay=True):
    global end_x
    length = int(1080 * 0.46) if far else 90
    end_x = s.panel_point(t, int(1080 * 0.30) + length, 2075)
    got = verdict(shots5, together_ms, lift_with_last, leaves, stay)
    s.notice_swipe(t, "x", (540, 1200), place, far, lift_with_last, leaves)
    return [(n.split(": ")[-1] if "reached" in n else n.split(" x ")[-1], ok) for n, ok, seen in got], s.notice_swipes[-1]
# flung with the last move: it leaves
r, rec = run([rest, gone, gone, gone, ref], 0.0, True, True, True); assert r == [("the raw swipe reached it", True), ("leaves", True)] and rec["ok"], (r, rec)
# 1.0.5: it stays where it was let go
r, rec = run([rest, aside, aside, aside, ref], 0.0, True, True, True); assert r == [("the raw swipe reached it", True), ("leaves", False)] and not rec["ok"], r
# the lift did not come with the last move: not the swipe meant, no verdict on the notice
r, rec = run([rest, gone, gone, gone, ref], 50.0, True, True, True); assert r == [("the raw swipe reached it", False)] and not rec["ok"] and not rec["as_meant"], r
# lifted at rest: well after the last move
r, rec = run([rest, gone, gone, gone, ref], 300.0, False, True, True); assert r == [("the raw swipe reached it", True), ("leaves", True)], r
r, rec = run([rest, gone, gone, gone, ref], 20.0, False, True, True); assert r == [("the raw swipe reached it", False)], r
# a picture that did not come is not "no bar"
r, rec = run([rest, gone, None, gone, ref], 0.0, True, True, True); assert r == [("the raw swipe reached it", True), ("leaves", False)], r
# where notices do not stay, "no bar" does not say that it left
r, rec = run([rest, gone, gone, gone, ref], 0.0, True, True, True, stay=False); assert r == [("the raw swipe reached it", True), ("leaves", False)], r
# swiped a little: back at its place, or still aside
r, rec = run([rest, back, back, back, ref], 0.0, True, False, False); assert r == [("the raw swipe reached it", True), ("comes back to its place", True)], r
r, rec = run([rest, aside, aside, aside, ref], 0.0, True, False, False); assert r == [("the raw swipe reached it", True), ("comes back to its place", False)], r
r, rec = run([rest, back, None, back, ref], 0.0, True, False, False); assert r == [("the raw swipe reached it", True), ("comes back to its place", False)], r
print("offline: ok")
