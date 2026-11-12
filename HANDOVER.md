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

## 1.0.4 (12 Nov 2026) — delivered
Two reports of the user on 1.0.3, both fixed, both in `CHANGELOG.md`:
1. 11:47 (Istanbul): "Podcast sayfasında podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi onu da
   düzeltiver". Since Android 16 the Back button of the three-button navigation starts a predictive Back whose progress
   the system runs by itself while the button is down. The app's three own handlers (`DepthBackHandler`: a detail on a
   phone; `DetailBackHandler`: a detail wider up, Up next, the window player; `ExpandablePlayer`: the phone's player)
   followed it as a finger's and shrank the page into a rounded card. The rule now: `BackSource.finger(swipeEdge,
   edgeSwipes)`, a predictive Back is a finger's only when it names an edge AND the system keeps the window's side
   edges for the Back swipe (`rememberBackSwipes`: the system gesture insets). With three buttons nothing is a finger's,
   whatever edge a maker's button names.
2. 15:02: "Bir de podcast çaldığında canlı yayın diyor müzik barı. Canlı yayın olmamalı". An episode streamed from its
   enclosure is, to the player, a stream without a length; 1.0.2's bar rule drew "● Canlı yayın" for every such
   stream. `PlayingLength` (the rule in one place) and `EpisodeAddresses` (the addresses given to the player as
   episodes, told by `EpisodeMedia.of` before the episode plays; Room is asked once for a list restored at start).

State on 12 Nov 17:50 (Istanbul): **1.0.4 is delivered.** master is 23a19b22 (PR #114 merged by fast-forward): CI
green (2314 tests, 281 screenshots the same, release build); walks green on that commit (debug API 35 tablet and
phone, personal API 37.0 phone; the personal API 37.0 tablet walk stands from 01607e78, the same app code). The
personal APK (run 43238370290): `app.nagmedar.debug`, 14010010, minSdk 34, target 37, 97433156 bytes, SHA-256
`f263b72ac07fbd0a6c2e6cc13c6d1ba0624accd465a2906358f2310404402159`; sent to the user with a side-by-side picture of
1.0.3 and 1.0.4 under the held Back button. The device checks are at the top of the plan document's "1.0 cihaz
testi" tab, the plan tab has a 1.0.4 paragraph. The Baseline Profile is 1.0.3's (not regenerated: the release was
kept lean after GitHub had stopped the fork's workflows for some hours that day).

What was seen where, said to the user the same way:
- The Back button's effect is real and was seen on the Android 17 emulator: with 1.0.3 the system's own Back button,
  held for a second, changes 21.7 % of the episode's page, 19.8 % of the podcast's page and 57.1 % of the full player
  (shrunk rounded cards); with 1.0.4 at most 0.1 %. There the button's predictive Back starts with a long press; a
  Samsung may start it at once, which cannot be seen from here. The rule does not depend on it.
- "Canlı yayın" was NOT reproduced on the emulator: the walk's local feed server lets libVLC know the length at once,
  1.0.3 reads the same seek row there. The fix rests on the code and `PlayingLengthTest`; his phone is the test.

New in the kit:
- The walk touches the system's own Back button (`back_button_check` in `buildsystem/ci/emulator_smoke.py`: the
  button is found in the windows' hierarchy, held, the screen taken a tap's length and a second into the press, then
  let go; whole = at most 2 % of the page differs) on the full player, an episode's page and a podcast's page of
  `phone-en`, and ends by playing an episode and reading the bar (`episode_plays`). `passes=back-button` runs these
  alone; with the workflow's `ref` input it walks an older app with today's driver (that is how 1.0.3 was shown).
  The driver and the workflow are in master now: every push of a pull request that touches them runs two walks.
- `tools/back_button_offline.py <emulator_smoke.py>` checks that code against a pretended device (11 cases);
  `tools/ktsyntax.py <worktree> [base]` counts a branch's Kotlin syntax slips against its base (tree-sitter; known
  false positives: DetailTransition.kt 3, MotionStripsTest.kt 3). Gradle does not run in the session: CI compiles.
- `orch/await_enabled.sh [minutes]` waits for GitHub to run the fork's workflows again without starting anything.
  GitHub stopped them on 12 Nov ("not currently being run on this fork due to the scale of GitHub Actions usage");
  only the owner's click on the Actions tab turned them on. Four runs of that day are still "queued" there; a
  session may not cancel a run.
- Every Compose test class needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (`ComposeSandboxTest` fails otherwise).

Left, told to the user: the offer to make CI lighter (fewer walks per push) so GitHub does not stop the fork again;
the merged `story/release-1.0.4` and `story/back-button-walk` branches wait, with the older `story/*`, for his word
before they are deleted. Seen on the way, not touched: on Android 15 (not his systems) the emulator's keyboard stays
up after the "Add by RSS address" dialog and takes the next Back.

## 1.0.5 (12 Nov 2026) — delivered
The user's recording of 1.0.4 (19:04 Istanbul): "Podcastte sayfası böyle oluyor. İlk yğklediğimde açılmıştı o zaman da
aşağı scroll yapılmıyordu. / Alt navigasyon da yukarı kayıyor dikkat edersen arkası gözüküyor". Three causes, all in
`CHANGELOG.md`. The first two shipped in 1.0.3 AND 1.0.4: the walks opened pages with the system's animations off and
the JVM tests began with the page already open.
1. A show's page opened from its row went blank when its way ended. Upstream Compose: a `SeekableTransitionState` at
   rest keeps its animations as the last way left them and its fraction at 0; when the transition's total duration
   changes afterwards (`onTotalDurationChanged` → `seekToFraction` → `Transition.seekAnimations(0)`) its own animations
   jump back to the START of the way and `Transition.isRunning` stays true. The duration changed when the shared
   elements' side transitions left after the way. Now `rememberDepth` makes the three values under
   `key(seekable.currentState)` (fresh, settled animations at every rest), `detailLayer` is the identity when the depth
   is open and at rest, and `moving()` does not read `transition.isRunning`.
2. A page that was there would not scroll. Upstream Compose: `LazyListState.hasLookaheadOccurred` is never reset;
   after one lookahead pass every measure without one is taken for the approach pass and the scroll is not consumed.
   1.0.3's `DepthHost` moved the content into a `SharedTransitionLayout` for a way and back into a plain Box. Now a
   depth with shared elements keeps its content in the layout for good (`SharedHome`); only the sides' scopes come and
   go with `moving`. COST: the Podcasts list and a show's page are measured twice a frame while scrolling on a phone.
3. A line under the bottom glass where the page behind showed. Haze 2.0.1 records the blur's input into
   `(size * 0.790569).roundToIntSize()` and draws it back at exactly 1/0.790569 (423 px → 334 → 422.5 drawn).
   `glass()` paints the material's solid colour under the blur.
Found in the walk of the fix, not reported by the user: after Back from a scrolled page a list too short to scroll had
no bottom bar (Desktop Kit 0.2.0's rule: only a scroll up brings the bars back). `ShellScrollState.showBars()` and
`LocalShellScroll`: a detail opened, closed or changed on a phone shows the bars again.

State on 12 Nov 23:45 (Istanbul): **1.0.5 is delivered.** master is fab4ce73 (PR #115 merged by fast-forward; it was
a draft, REST cannot mark one ready and the push closes it as merged all the same). CI green (2327 tests, 13 of them
new; 281 screenshots the same; release build), the four personal walks green on that commit (API 35 and 37.0: phone
0/71, tablet 0/66), `page-opens` green on the branch and on master (0/37 both). The personal APK (run 43298418416):
`app.nagmedar.debug`, 14010011, minSdk 34, target 37, 97449540 bytes, SHA-256
`c5a473ff06c1cb54be7df8ec92d210d138aa478a8d76c859cadbf507179e6d96`; sent to the user with a five-panel picture of the
page on 1.0.4 and on 1.0.5. The device checks are at the top of the plan document's "1.0 cihaz testi" tab (rev 10),
the plan tab has a 1.0.5 paragraph (rev 36). The Baseline Profile is still 1.0.3's. `orch/release-1.0.5/` holds the
PR's body and the device checks. Next version: 1.0.6 = 4010006 / arm64 14010012.

What was seen where, said to the user the same way:
- The blank page and the page that stands still are real and were seen on the Android 17 emulator at his phone's
  size (1080x2340, 480 dpi, three buttons, dark) with the animations on. On 1.0.4 (run 43277971874) the page of a
  tapped row is drawn 0.3 s after the tap (14.2 % of its place), gone at 0.8 s (0.0 %) and not in the UI's tree,
  twice; the page the app opens after following and a tile's page stay but move 75–82 px under an 842 px swipe.
  On 1.0.5 (runs 43289445307 and 43298541656) all seven openings stay and move 616–1514 px; the bottom bar is there
  on every page and after every Back.
- In the JVM: the tests alone on 1.0.4's code were red first (run 43275028253, 7 red: the page at
  `Rect(1080, 168, 2160, 1809)`, "moving true, running true"); the first fix alone left the scroll test red (run
  43277885554), which is how the second cause was found.
- The line under the bottom glass was NOT reproduced on the emulator (it never drew the sliver). The fix rests on
  the arithmetic; his phone is the test.
- NOT established: which of the things that fly with the page outlasts the way on the owner's row. A first guess (the
  cover flying more than ~500 px; in the first commits' messages and in a mid-way message to the user) is wrong: the
  walk's top row has its cover ~120 px from the page's and lost its page all the same. The fix does not depend on it.

New in the kit:
- `passes=page-opens` (`buildsystem/ci/emulator_smoke.py`, permanent, in the release line of
  `buildsystem/release/README.md`): the owner's geometry, the system's animations ON, a fresh app, `feed-long.xml`
  (80 episodes, served by the workflow). `opening_check` takes the raw screen 0.3 / 0.8 / 2.5 s after the tap (the
  share of the page's place that is drawn, `PAGE_INK` 1 %), reads the UI's tree and the bottom bar; `scrolls_check`
  asks that an episode in sight move up by `PAGE_FOLLOWS` = 40 % of the swipe; `podcasts_list` reads the list and the
  bar after every Back. A pass outside the phone/tablet groups gets its own job and its own evidence branch
  `ci/emulator-api37.0-personal-<pass>`; two dispatches of the same pass share that branch, so run them one after
  another and fetch in between. With the workflow's `ref` input the pass walks an older app (the control on 1.0.4).
  OPEN: on an app with the bug the page opened after following can itself be blank (run 43294020905); the pass then
  reports "the feed is followed: FAILED" and stops instead of pressing Back and going on. A reader of fab4ce73's
  check runs sees that control as the one failed `page-opens` job.
- `tools/sheet.py <dir> <outdir> <per-row> <width>` makes contact sheets of a walk's screenshots (PIL). LOOK at them:
  the first scroll check ("at least 1 % of the page's place changed") passed 1.0.4's stuck page, and only the
  pictures showed it. A check is trusted once it has been seen to fail on the broken app.
- `tools/ktsyntax.py` takes the identifier `open` at a statement's start for a slip: use another name in new code.
- Compose semantics: what `clearAndSetSemantics {}` covers stays in the UNMERGED tree; "is it covered" is asked of the
  merged tree.
- The causes were read in the libraries' sources: other public GitHub repositories can be cloned read-only after
  `add_repo` (androidx: `compose/animation/animation-core`, `compose/animation/animation`, `compose/foundation`;
  chrisbanes/haze 2.0.1). Maven Central and dl.google.com are refused by the session's proxy.

Left, told to the user: his phone decides the line under the glass and whether the two lists still scroll smoothly;
the merged `story/release-1.0.5`, `story/release-1.0.4`, `story/back-button-walk`, the experiments' `exp/page-blank`
(never merged), the evidence branches `ci/emulator-api37.0-personal-page-blank` and `…-page-blank-2` and the older
`story/*` wait for his word before they are deleted; four runs of 11 Nov and 14 Oct are still "queued" on GitHub (a
session may not cancel a run); the offer to make CI lighter stands.
