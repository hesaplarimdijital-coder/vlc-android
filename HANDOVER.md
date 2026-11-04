# Nağmedar — orchestration kit (handover of the 1.0 → 1.0.2 session)

This orphan branch holds what the orchestrating session used while it built Nağmedar 1.0, 1.0.1 and 1.0.2
(14 Oct – 30 Oct 2026). It is not part of the app. Product code lives on `master`.

## What is here
- `orch/AGENT_BRIEF.md` — the brief every implementation agent read first (sections 000 and 00 are the user's
  1.0.x decisions and override the older sections). `orch/REVIEW_BRIEF.md` — the reviewers' brief.
- `orch/LOG.md` — the orchestration log, newest at the bottom: every PR landed, every release, every finding.
- `orch/notes/*.md` — per-track notes written by the agents ("notes for the next story"): kit-1.0.md and kit-port.md
  (Desktop Kit 1.0 port), nav-pages.md, polish-1.0.1.md, crash-sweep.md, big-player.md, sheet-drag.md, whisper-out.md,
  podcast.md, android-auto.md, release.md, video-1029.md (the user's 29 Oct usage video, frame by frame) and older ones.
- `orch/prompts/*.md` — the task prompts given to agents (a template for new ones).
- `orch/reports/*.md` — the independent reviews of 1.0 (auto, UI, playback, removal).
- `orch/plan.txt` — the original plan (backlog, ADRs).
- Scripts (POSIX sh; they read `SCRATCH`, `ORCH`, `REPO`, `WT` from the environment):
  - `ci.sh <sha>` — every check run of a commit and the annotations of finished jobs (compiler errors, failed tests,
    the emulator walk's verdicts).
  - `wait_ci.sh <sha>` — waits until no app job is queued or running.
  - `merge.sh <id> <sha>` / `land.sh <id> <sha>` — ff-merges `story/<id>` at `<sha>` into master after green CI
    (refuses if the branch is not based on master or the remote head differs), runs `dupstr.py` on the worktree,
    pushes master, removes the worktree and the local branch.
  - `dupstr.py <worktree>` — finds duplicated names and broken tags in the strings.xml files after a merge.
  - `release_chain.sh <id> <sha> <version>` — land, dispatch the personal release on master, wait for
    `ci/personal-release` to name master's sha, decrypt, check the APK.
  - `wait_runs.sh <branch> <sha-prefix>` — waits for the Emulator workflow runs of a commit.
  - `delivery/encrypt_apk.sh`, `delivery/decrypt.sh` — the delivery envelope (RSA-OAEP-SHA256 of a random password +
    the APK's SHA-256; the APK AES-256-CBC with PBKDF2 100k, split in 90 MB parts). The same scheme is in the repo
    as `buildsystem/release/encrypt_apk.sh` / `decrypt_apk.sh`.
  - `tools/apkcheck.py <apk>` — package, versions, debuggable, signing certificate SHA-256, baseline.prof, ABIs
    (`pip install --break-system-packages androguard`).

## Setting it up in a new session
```sh
export SCRATCH=<your scratchpad>  REPO=<your vlc-android clone>  WT=$HOME/wt  ORCH=$SCRATCH/orch
git -C "$REPO" fetch origin orch/handover
git -C "$REPO" archive origin/orch/handover orch tools | tar -x -C "$SCRATCH"
```

### The delivery key (do this once)
The private half of the delivery key stayed in the old session and is not here. Make a new pair in the
scratchpad, never in the repository:
```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$ORCH/delivery/delivery.key"
openssl pkey -in "$ORCH/delivery/delivery.key" -pubout -out "$ORCH/delivery/delivery.pub"
chmod 600 "$ORCH/delivery/delivery.key"
```
Then put the new public half into `buildsystem/release/delivery.pub` on master (a small PR, CI green, ff-merge).
From then on `ci.yml`'s personal release encrypts for the new key and `delivery/decrypt.sh` opens it. Never read,
print or copy `delivery.key` anywhere but `decrypt.sh`.

## Notes on the old paths
Paths in the notes and the log were the old session's: `$SCRATCH`, `$ORCH`, `$REPO`, `$WT`, `$KIT` (the desktop-kit
clone) stand for them. The stub Kotlin toolchain for local test compilation (`$SCRATCH/kt`) is not handed over; CI
compiles and runs every test.
