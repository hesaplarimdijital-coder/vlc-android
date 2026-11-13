#!/usr/bin/env python3
"""An offline model, by pixels, of the palette's list for the walk's row reader (emulator_smoke.py's palette_all_rows):
the app's lines (titles, hits, "show all" lines) with their heights, the list's padding, and the list following the
selection as 1.0.7's CommandPalette.kt does (showWhole: as little as shows the selected line whole, its group's title
with a group's first hit). A row reads when something of its label and of its detail shows.
usage: palette_rows_model2.py <emulator_smoke.py>   -> how many random lists had a row the reader missed, and whether
the selected line was whole after every key"""
import importlib.util, random, sys
spec = importlib.util.spec_from_file_location("smoke", sys.argv[1])
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
PAD = 6

class Model(m.Smoke):
    def __init__(self, groups, viewport, heights):
        self.items, self.stops = [], []
        for g, (hits, more) in enumerate(groups):
            self.items.append(("header", "H%d" % g))
            for h in range(hits):
                self.stops.append(len(self.items)); self.items.append(("hit", "g%d-r%d" % (g, h)))
            if more:
                self.stops.append(len(self.items)); self.items.append(("more", "more%d" % g))
        self.h = [heights[kind] for kind, _ in self.items]
        self.y = [sum(self.h[:i]) for i in range(len(self.items))]
        self.total = sum(self.h)
        self.v = viewport
        self.max = max(0, self.total - (viewport - 2 * PAD))
        self.s, self.selected, self.keys, self.reads, self.cut = 0, 0, 0, 0, 0
        self.warnings = []
    def over_time(self): return False
    cur_pass = "model"
    def step(self, title, wait=0): return self.read()
    def settle(self, t): return "x"
    def nodes(self, xml): return self.read()
    def read(self):
        self.reads += 1
        return [("node",)]
    def shown(self, i):
        """Whether something of the line shows: the list draws from its padding's top to its padding's bottom."""
        return self.y[i] + self.h[i] > self.s - PAD and self.y[i] < self.s - PAD + self.v
    def palette_rows(self, nodes):
        rows = []
        lo, hi = self.s - PAD, self.s - PAD + self.v
        for i, (kind, lab) in enumerate(self.items):
            if kind != "hit": continue
            y, h = self.y[i], self.h[i]
            label, detail = (y + h * 0.18, y + h * 0.55), (y + h * 0.55, y + h * 0.84)
            if label[1] > lo and label[0] < hi and detail[1] > lo and detail[0] < hi:
                rows.append((lab, "d", None))
        return rows
    def key(self, code, record=True):
        self.sh("input keyevent %d" % code)
    def follow(self):
        row = self.stops[self.selected]
        top = row - 1 if row > 0 and self.items[row - 1][0] == "header" else row
        lines = [i for i in range(len(self.items)) if self.shown(i)]
        if (self.y[top] - self.s < 0) if top in lines else (top < lines[0]):
            self.s = min(self.max, self.y[top])
        else:
            below = row not in lines
            if below: self.s = min(self.max, self.y[row])
            over = self.y[row] - self.s + self.h[row] - (self.v - 2 * PAD)
            if over > 0 or (below and over < 0):
                self.s = max(0, min(self.max, self.s + over))
        # the selected line whole, between the list's paddings
        if not (self.y[row] >= self.s and self.y[row] + self.h[row] <= self.s + self.v - 2 * PAD): self.cut += 1
        if top != row and not self.y[top] >= self.s: self.cut += 1
    def sh(self, cmd):
        for code in cmd.split()[2:]:
            if not self.stops: return
            self.selected = (self.selected + (1 if code == "20" else -1)) % len(self.stops)
            self.follow()
            self.keys += 1

random.seed(7)
bad = cases = cut = 0
worst = 0
for trial in range(6000):
    groups = []
    for g in range(4):
        if g < 3:
            hits = random.choice([0, 0, 1, 2, 3, 6, 6])
        else:
            hits = random.choice([0, 1, 2, 5, 9, 13, 40, 73, 200])
        if hits:
            groups.append((hits, g < 3 and hits == 6 and random.random() < 0.6))
    if not groups: continue
    scale = random.choice([1, 2, 2, 3])
    heights = {"header": 31 * scale, "hit": random.choice([44, 52, 56]) * scale, "more": random.choice([32, 52, 56]) * scale}
    mdl = Model(groups, random.randint(200, 900) * scale, heights)
    order, _ = mdl.palette_all_rows("t")
    want = [lab for kind, lab in mdl.items if kind == "hit"]
    cases += 1
    worst = max(worst, mdl.reads)
    cut += mdl.cut
    if sorted(l for l, d in order) != sorted(want):
        bad += 1
        if bad <= 8: print("MISSED", groups, "viewport", mdl.v, heights, "read", len(order), "of", len(want), "keys", mdl.keys)
print("lists: %d; with a row missed: %d; keys after which the selected line was not whole: %d; most readings for one list: %d" % (cases, bad, cut, worst))
