#!/usr/bin/env python3
"""Summarise JUnit XML results as GitHub annotations.

Usage: junit_summary.py <results dir> <title>

Prints one notice with the totals and one error per failed test (the first ten, with the
message and the top of the stack trace), adds a table to $GITHUB_STEP_SUMMARY and exits 1
when a test failed or none ran.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

MAX_ERRORS = 10


def esc(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def main(results, title):
    total = failed = skipped = 0
    failures, per_class = [], {}
    for path in sorted(glob.glob(os.path.join(results, '**', 'TEST-*.xml'), recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            failures.append((os.path.basename(path), f'unreadable result file: {e}'))
            continue
        for case in root.iter('testcase'):
            cls = case.get('classname', '?').split('.')[-1]
            total += 1
            stats = per_class.setdefault(cls, [0, 0])
            stats[0] += 1
            if case.find('skipped') is not None:
                skipped += 1
                continue
            problem = case.find('failure')
            if problem is None:
                problem = case.find('error')
            if problem is not None:
                failed += 1
                stats[1] += 1
                text = (problem.get('message') or '') + '\n' + '\n'.join((problem.text or '').strip().splitlines()[:12])
                failures.append((f"{cls}.{case.get('name')}", text.strip()[:2500]))
    print(f'::notice title={title}::{total} test, {failed} başarısız, {skipped} atlandı')
    for name, text in failures[:MAX_ERRORS]:
        print(f'::error title={esc(name)}::{esc(text)}')
    if len(failures) > MAX_ERRORS:
        print(f'::error title={title}::{len(failures) - MAX_ERRORS} başarısız test daha: '
              + esc(', '.join(n for n, _ in failures[MAX_ERRORS:]))[:2500])
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as fh:
            fh.write(f'## {title}\n\n{total} test, {failed} başarısız, {skipped} atlandı\n\n| Sınıf | Test | Başarısız |\n| --- | --- | --- |\n')
            for cls, (n, bad) in sorted(per_class.items()):
                fh.write(f'| {cls} | {n} | {bad} |\n')
    return 1 if failed or total == 0 else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1], sys.argv[2]))
