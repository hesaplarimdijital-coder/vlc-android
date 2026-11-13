#!/usr/bin/env python3
"""An offline model of the palette's list for the walk's row reader (emulator_smoke.py's palette_all_rows): the app's
stops and items (headers, hits, "show all" lines), the viewport following the selection as CommandPalette.kt does.
usage: palette_rows_model.py <emulator_smoke.py>   -> how many random lists had a row the reader missed"""
import importlib.util, random, sys
spec = importlib.util.spec_from_file_location("smoke", sys.argv[1])
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

class Model(m.Smoke):
    def __init__(self, groups, visible, cut_last):
        self.items, self.stops = [], []
        for g, (hits, more) in enumerate(groups):
            self.items.append(("header", "H%d" % g))
            for h in range(hits):
                self.stops.append(len(self.items)); self.items.append(("hit", "g%d-r%d" % (g, h)))
            if more:
                self.stops.append(len(self.items)); self.items.append(("more", "more%d" % g))
        self.visible, self.cut_last, self.first_item, self.selected, self.keys, self.reads = visible, cut_last, 0, 0, 0, 0
        self.warnings = []
    def over_time(self): return False
    cur_pass = "model"
    def step(self, title, wait=0): return self.read()
    def settle(self, t): return "x"
    def nodes(self, xml): return self.read()
    def read(self):
        self.reads += 1
        return [("node",)]
    def palette_rows(self, nodes):
        last = min(len(self.items), self.first_item + self.visible)
        part = self.items[self.first_item:last]
        # the last item in sight may be cut by the list's edge: its detail is not there, so it does not read as a row
        if self.cut_last and last - self.first_item == self.visible:
            part = part[:-1]
        return [(lab, "d", None) for kind, lab in part if kind == "hit"]
    def key(self, code, record=True):
        self.sh("input keyevent %d" % code)
    def sh(self, cmd):
        for code in cmd.split()[2:]:
            if not self.stops: return
            self.selected = (self.selected + (1 if code == "20" else -1)) % len(self.stops)
            row = self.stops[self.selected]
            first, last = self.first_item, min(len(self.items), self.first_item + self.visible) - 1
            if row <= first: self.first_item = max(0, row - 1)
            elif row >= last: self.first_item = max(0, row - (last - first) + 1)
            self.keys += 1

random.seed(7)
bad = cases = 0
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
    mdl = Model(groups, random.randint(4, 22), random.random() < 0.5)
    order, _ = mdl.palette_all_rows("t")
    want = [lab for kind, lab in mdl.items if kind == "hit"]
    cases += 1
    worst = max(worst, mdl.reads)
    if sorted(l for l, d in order) != sorted(want):
        bad += 1
        if bad <= 8: print("MISSED", groups, "visible", mdl.visible, "cut", mdl.cut_last, "read", len(order), "of", len(want), "keys", mdl.keys)
print("lists: %d; with a row missed: %d; most readings for one list: %d" % (cases, bad, worst))
