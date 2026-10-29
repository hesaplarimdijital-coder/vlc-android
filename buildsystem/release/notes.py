#!/usr/bin/env python3
"""S-1503: a release's version and notes.

    notes.py --version        the app's version, as build.gradle names it for libVLC 4 (1.0.0)
    notes.py [VERSION]        that version's section of CHANGELOG.md (the app's version by default),
                              without its heading, as the GitHub release's notes

Exits 1 when the version or its section is missing, so that a release never goes out without notes.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]


def version() -> str:
    gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
    # versionName = vlcMajorVersion == 3 ? '1.0.0-vlc3' : '1.0.0'
    match = re.search(r"versionName\s*=.*:\s*'([^']+)'", gradle)
    if not match:
        sys.exit("versionName not found in build.gradle")
    return match.group(1)


def notes(wanted: str) -> str:
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    sections = re.split(r"^## ", text, flags=re.M)[1:]
    for section in sections:
        heading, _, body = section.partition("\n")
        if heading.split()[0] == wanted:
            return body.strip() + "\n"
    sys.exit(f"CHANGELOG.md has no section for {wanted}")


def main() -> None:
    if sys.argv[1:] == ["--version"]:
        print(version())
        return
    print(notes(sys.argv[1] if len(sys.argv) > 1 else version()), end="")


if __name__ == "__main__":
    main()
