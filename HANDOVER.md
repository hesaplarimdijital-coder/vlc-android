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

## The 1.0.3 session (4 – 12 Nov 2026)
`orch/LOG.md` has it day by day. New since the handover above: `orch/watch_sha.sh <sha> [minutes]` (waits for every CI
job and emulator walk of a commit, re-runs once the walk jobs that died in the runner's setup, prints the summaries and
the failures), `orch/prompts/` of the Desktop Kit 2.3.0 port's parts, `orch/notes/kit-2.3-map.md` (the 67 rows of the
port) and the parts' notes in `orch/notes/kit-port.md`. `AGENT_BRIEF.md` section 0000 holds this session's rules
(minSdk 34, glass B, the split walk, the attribution lines).

State on 12 Nov 01:10 (Istanbul): master is 7cfbc735 (the train, PRs #107–#112). The release is the draft PR #113,
branch `story/release-1.0.3` (version 4010003 / 14010009, CHANGELOG, guide): on top of the train it has the phone's
shelf fix, the profile generator's fix, the folders' single back arrow, the new Back (Desktop Kit 2.4.0 and the
kit's unmerged `fix/tab-back-button`, done by the orchestrator after the user's interrupt stopped its agent) and
`targetSdk 37` (the user's answer "37" at 00:55). `orch/release-1.0.3/` holds the PR's body, the device checks to put
at the top of the plan document's "1.0 cihaz testi" tab, and the stopped agent's partial diff; `orch/ux/montage.py`
makes the before/after sheets of the Back strips. Left when this was written: CI green on the branch's head (first
compile of the Back work pending), the screenshot references of the folders' arrow, the Baseline Profile regenerated
on the final code, the walks (debug and personal, API 35 and 37.0), then `release_chain.sh release-1.0.3 <sha> 1.0.3`
from outside the worktree, `tools/apkcheck.py` (now expecting targetSdk 37) and the APK sent to the user.

State on 12 Nov 03:05 (Istanbul): **1.0.3 is delivered.** master is 56366976 (PR #113 merged by fast-forward): CI
green (2290 tests, 281 screenshots the same, release build), the walks green on the final commit (debug API 35 and
37.0, personal API 35 and 37.0), the Baseline Profile regenerated (44195 rules, the phone's player in six rounds of
six). The personal APK (run 43121979708): `app.nagmedar.debug`, 14010009, minSdk 34, target 37, 97433156 bytes,
SHA-256 `31e780146059a20a504a0757dd3b89decdd562fda13993a65d1a6a09fc95e5f9`; it was sent to the user, the device
checks are at the top of the plan document's "1.0 cihaz testi" tab, the plan's "Sürüm" row says delivered. New here:
`orch/wait_debug.sh <sha>` (the app's debug job alone), `orch/wait_ids.sh <run ids…>`, and `watch_sha.sh`'s third
argument (the least number of runs to wait for: a run that has not started its jobs has no check runs yet). Two things
learned the hard way: a dispatch of `emulator.yml` with the same inputs on the same branch cancels the one in progress,
and `gh pr edit` needs GraphQL (refused here): `gh api -X PATCH repos/<owner>/<repo>/pulls/<n> -F body=@file`.
Left for a later release, told to the user: "Klasör seç" and "Kütüphaneye ekle" share one icon; the shortcut list's
rows are tight on a phone with a keyboard; the "Sıradaki" button stays as it is; the One UI 9 items (Now Bar, the lock
screen's player, DeX desktops) can only be tried on his devices; the old `story/*` branches on GitHub wait for his
word before they are deleted.

## 1.0.4 (12 Nov 2026, in progress)
The user on 1.0.3, 11:47 (Istanbul): "Podcast sayfasında podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi
onu da düzeltiver". The cause, read from Android's source (not yet seen on a device): since Android 16 the Back button
of the three-button navigation starts a predictive Back with no edge whose progress the system runs by itself while
the button is down; the app's three own handlers (a detail's on a phone, a detail's wider up, the phone's player)
followed it as a finger's and shrank the page into a rounded card. The fix and its tests are on `story/release-1.0.4`
(draft PR #114, version 4010004 / 14010010); `orch/release-1.0.4/` has the PR's body and the device checks.

State at 15:30: **nothing of 1.0.4 is compiled yet.** GitHub stopped running this fork's workflows ("Workflows are
not currently being run on this fork due to the scale of GitHub Actions usage", on the repository's Actions tab; a
dispatch answers 422). Only the owner's click on "Enable workflows" there turns them on; he was told at 12:35.
`orch/await_enabled.sh [minutes]` waits for it without starting anything. `orch/LOG.md`'s last entry is the plan of
runs for when it is on, kept lean so GitHub does not stop them again.

New: `story/back-button-walk` (pushed, not in the PR on purpose: a change of the walk's driver makes every push of a
pull request run two walks). It makes the emulator walk touch the system's own Back button, held and tapped, instead
of sending a key, which never showed the bug; `passes=back-button` with the workflow's `ref` input walks an older
app with it. `tools/back_button_offline.py <emulator_smoke.py>` checks that code against a pretended device, and
`tools/ktsyntax.py <worktree>` counts a branch's Kotlin syntax slips against master (tree-sitter).
