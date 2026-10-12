#!/usr/bin/env python3
"""S-1404: Nağmedar's macrobenchmark results against thresholds.json.

    check.py RESULTS_DIR [--thresholds thresholds.json]

RESULTS_DIR is what run.sh gathered: the macrobenchmarks' *benchmarkData.json (anywhere under
it), perf.log (the app's NagmedarPerf lines: one a playback) and instrument.txt (the run's own
output, for its failed tests). Prints a table and writes it to RESULTS_DIR/summary.md; exits 1
when a threshold is passed or a required benchmark is missing, 0 otherwise.
"""

import argparse
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent


def benchmarks(results: pathlib.Path) -> dict:
    """ClassName.method -> its metrics and sampled metrics, from every benchmarkData.json found."""
    found = {}
    for path in sorted(results.rglob("*benchmarkData.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for bench in data.get("benchmarks", []):
            name = bench.get("className", "").rsplit(".", 1)[-1] + "." + bench.get("name", "")
            values = {}
            for group in ("metrics", "sampledMetrics"):
                for metric, stats in (bench.get(group) or {}).items():
                    values[metric] = stats
            found[name] = values
    return found


def playbacks(results: pathlib.Path) -> list:
    """The NagmedarPerf playback lines, as dicts of their fields."""
    log = results / "perf.log"
    if not log.exists():
        return []
    lines = []
    for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
        if "playback file=" not in line:
            continue
        fields = dict(re.findall(r'(\w+)=("[^"]*"|\S+)', line.split("playback ", 1)[1]))
        fields["file"] = fields.get("file", "").strip('"')
        lines.append(fields)
    return lines


def failed_tests(results: pathlib.Path) -> list:
    """The tests am instrument reported failed, by their class and method."""
    text = results / "instrument.txt"
    if not text.exists():
        return []
    failed, current = [], {}
    for line in text.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"INSTRUMENTATION_STATUS: (class|test)=(.*)", line)
        if match:
            current[match.group(1)] = match.group(2).strip()
        match = re.match(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        if match:
            # -2: failed, -4: an assumption that did not hold (skipped)
            if match.group(1) == "-2" and current:
                failed.append(current.get("class", "").rsplit(".", 1)[-1] + "." + current.get("test", ""))
            current = {}
    return sorted(set(failed))


def number(value: float) -> str:
    return f"{value:.1f}".rstrip("0").rstrip(".") if value != int(value) else str(int(value))


def check(results: pathlib.Path, thresholds: dict) -> tuple:
    rows, ok = [], True
    found = benchmarks(results)
    for rule in thresholds["benchmarks"]:
        stats = found.get(rule["test"], {}).get(rule["metric"])
        value = None if stats is None else stats.get(rule["stat"])
        label = f'{rule["test"]} · {rule["metric"]} {rule["stat"]}'
        if value is None:
            missing = rule.get("required", True)
            ok = ok and not missing
            rows.append((label, "yok", f'≤ {number(rule["max"])}', "EKSİK" if missing else "atlandı", rule["why"]))
            continue
        passed = value <= rule["max"]
        ok = ok and passed
        rows.append((label, number(value), f'≤ {number(rule["max"])}', "geçti" if passed else "KALDI", rule["why"]))
    play = thresholds["playback"]
    seen = playbacks(results)
    for name in play["files"]:
        lines = [p for p in seen if p["file"] == name]
        label = f"Oynatma · {name} · kayıp kare %"
        if not lines:
            rows.append((label, "yok", f'≤ {number(play["dropped_pct_max"])}', "atlandı", play["why"]))
            continue
        worst = max(float(p.get("dropped_pct", "100")) for p in lines)
        passed = worst <= play["dropped_pct_max"]
        ok = ok and passed
        rows.append((label, f"{worst:.3f}", f'≤ {number(play["dropped_pct_max"])}', "geçti" if passed else "KALDI", play["why"]))
    for test in failed_tests(results):
        ok = False
        rows.append((test, "—", "—", "HATA", "Test başarısız (instrument.txt)"))
    return rows, ok


def table(rows: list) -> str:
    head = ("Ölçüm", "Değer", "Eşik", "Sonuç", "Neden")
    out = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    out += ["| " + " | ".join(row) + " |" for row in rows]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("results", type=pathlib.Path)
    parser.add_argument("--thresholds", type=pathlib.Path, default=HERE / "thresholds.json")
    args = parser.parse_args()
    thresholds = json.loads(args.thresholds.read_text(encoding="utf-8"))
    rows, ok = check(args.results, thresholds)
    text = table(rows) + "\n\n" + ("Tüm eşikler sağlandı." if ok else "Eşiklerin bazıları sağlanmadı.") + "\n"
    (args.results / "summary.md").write_text(text, encoding="utf-8")
    print(text)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
