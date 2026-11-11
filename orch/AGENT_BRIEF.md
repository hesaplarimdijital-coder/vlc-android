# Nağmedar — implementation agent brief

You implement one or two backlog stories of **Nağmedar**, a fork of VLC for Android
(github.com/hesaplarimdijital-coder/vlc-android, branch `master`). Read this brief fully, then
your task prompt. The orchestrator (who launched you) merges your work; you never merge to
master yourself.

## 0000. THE 1.0.3 SESSION (from 5 Nov 2026) — overrides everything below where they differ

- **Attribution (replaces §2.5):** commits end with exactly these two lines (after a blank line):
  ```
  Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
  ```
  PR bodies end with:
  ```
  🤖 Generated with [Claude Code](https://claude.com/claude-code)

  https://claude.ai/code/session_01CCjCQf9dHq1sPdX5uoz8qa
  ```
- **Paths in this session:** `SCRATCH=/tmp/claude-0/-home-claude/ef20efb0-c7e1-5e3e-8eb6-1762a74320b1/scratchpad`,
  `ORCH=$SCRATCH/orch`, `REPO=/home/claude/vlc-android` (the orchestrator's clone: never check out, commit or push in it),
  `WT=/home/claude/wt` (your worktree is `$WT/<id>`, branch `story/<id>`), `KIT=/home/claude/hesaplarimdijital-coder/desktop-kit`
  (read only; 1.0.0 = `8ca85ad`, 2.3.0 = `bef6b9f` = `main`). `. $SCRATCH/env.sh` sets them in a shell. Your scratch:
  `$SCRATCH/agents/<id>/`, never inside the worktree.
- **No local Kotlin toolchain this time:** `$SCRATCH/kt` (stub compiler) and `$SCRATCH/i18nout` were not handed over, and no
  Maven/Google/Gradle host is reachable from the machine. CI is the only compiler and test runner: §4.2's local checks do
  not exist; read your diff twice before each push instead (imports, names, nullability, threads). `python3 $ORCH/dupstr.py
  <worktree>` still works (strings.xml duplicates and broken tags) and must pass before every push.
- **Master moved to 1.0.2** (0ed1076d): read orch/notes/{nav-pages,polish-1.0.1,crash-sweep,sheet-drag,big-player,whisper-out}.md
  for what the shell, sheets and players are now. The delivery key was replaced (PR #96); never touch `buildsystem/release/`.
- **The session's main work is porting Desktop Kit 2.0.0–2.3.0** (`$KIT/CHANGELOG.md`, `$KIT/MIGRATION.md`) into the app's own
  copies (theme/, shell/, components/…). The app does not depend on the kit. The map of what goes where:
  `$ORCH/notes/kit-2.3-map.md`. Never replace an app implementation with the kit's wholesale: take the kit's fix or behaviour
  into the app's file, keep the app's names and its 1.0.1–1.0.2 behaviour.
- **The user's standing decisions (1.0.1–1.0.2), kept by every story:** Settings and every page opened from a page are
  full-screen pages with a back arrow; the top bar never changes colour on touch or scroll; bars, menus, sheets and dialogs
  are opaque (GlassMode Solid) — EXCEPT, by the user's decision of 6 Nov 01:05 ("B olsun"), the phone's bottom chrome
  (mini player card, bottom bar, band behind the system buttons) on one glass (ChromeGlass.Bottom, story/glass-b); keycaps/shortcut hints only while a hardware keyboard is
  connected; the phone's Sıradaki is a full-window page; sheets close when dragged down anywhere; on the tablet "open
  player" opens the big artwork player; the phone's mini player is the tablet's bar (artwork-tinted seek bar).
- **Decided 5 Nov 08:33:** minSdk is 34 (the platform story sets it; new code needs no checks below API 34). The user is a
  pure-music-player user: no video features, ever. His priorities: the kit update, testing, smooth transitions.
- **Ask the orchestrator (in your final report, not by guessing) before:** raising targetSdk, bringing back any glass,
  removing a user-visible feature, adding a dependency.
- Tests are on again (`NAGMEDAR_RUN_TESTS: '1'`): §0's "no new tests" is void. New logic gets tests; changed screenshots are
  looked at one by one and committed in a separate "Screenshots: …" commit (§4.5). The emulator walk must stay green on
  phone and tablet; it is close to its 50 min limit, so new walk steps must be few and fast.
- **The walk since PR #98 (master 1e6a657):** each API level runs two jobs, "tablet" (tablet-en, workstation, phone-tr) and
  "phone" (phone-en), plus an "Emülatör · özet" summary job. Evidence branches are `ci/emulator[-api<level>][-personal]-<tablet|phone>`
  (e.g. `ci/emulator-personal-phone`, `ci/emulator-api37.0-tablet`); the old `ci/emulator*` names are no longer written.
  System ANRs are recorded and skipped; the app's fail the run. Sheets and drags use the walk's `drag()` (motion events),
  not `input swipe`. Notes: $ORCH/notes/release.md "## 1.0.3 walk".
- Do not edit CHANGELOG.md, the version or `buildsystem/release/README.md` (the orchestrator writes them).

## 000. NAĞMEDAR 1.0.2 (30 Oct 01:14, the user's decisions) — overrides everything below

- 1.0.1 shipped (master c2e49fe4): ⋯ is a "Daha fazla" page, pages pushed with back arrows (ShellNavigator selectTab/open/
  navigate/back, NavRules), Settings full screen on a phone, solid opaque bars (GlassMode Solid), the phone queue is a
  full-window WindowPage, keycaps only with a hardware keyboard (input/KeyboardHints.kt), DenseTable lazyKeys.
  Read orch/notes/{nav-pages,polish-1.0.1,crash-sweep}.md before touching those areas.
- **Whisper is removed completely** ("Whisper'ı komple kaldıralım", it slowed the tablet): podcast transcription with Whisper,
  its settings, models, workers, notifications, the lab's SP-05, whisper.cpp in native/lab, its CI and licence entries. The
  publisher's own transcripts (RSS podcast:transcript) stay.
- The tablet/DeX player bar (TransportBar) is liked. Its "open player" button must open a big-artwork player like the
  phone's (not the Music page). The phone's mini player becomes the same bar as the tablet's (artwork-tinted seek bar with
  times, the transport), adapted to the phone's width, keeping the phone's gestures.
- Sheets on the phone close by dragging down anywhere on them (as the full player does), not only by the handle.
- The deliverable stays one personal release APK (1.0.2) at the end; do not edit CHANGELOG.md or the version (the
  orchestrator does).

## 00. NAĞMEDAR 1.0 (28 Oct, the user's decisions) — overrides everything below where they differ

- **Pivot: Nağmedar 1.0 is a pure audio player — Music, Podcasts, Radio.** Every video feature is being
  deleted (films, series, videos, the video player and its panels, subtitles tools, film metadata,
  video conversion/HLS, video casting UI) and **TV Lab is deleted completely** (UI, workers, native remux /
  DV / bitrate / disk tools, its CI parts). The last video-capable version is kept on the branch
  `archive/0.5-video` (c7c4f898); nothing needs to be preserved "just in case" on master.
- **Never rewrite what exists and works** (the user's words: "Var olanları asla yeniden yazma"). Before adding
  anything, grep for an existing implementation (the music engine already has crossfade 0–12 s, album
  gapless, bit-perfect USB via AudioMixerAttributes, a direct USB DAC sink, AAudio exclusive; the UI already
  has SignalPathCard, artwork tint, LRCLIB and word-timed LRC, Last.fm + ListenBrainz, the ×/Esc dialog rule,
  the queue's no-empty-flash read, the episode page's reserved badge/timeline, the single copy notice, the
  "recently played" shelf from history). Extend those; never add a parallel copy.
- **Out of scope** (do not build): lyrics providers/engine (the user handles lyrics with Metadator — keep the
  existing lyrics features as they are), Discord, any bridge to Metadator / Walkmansync / Soulseek+, test APKs.
- Target devices: Galaxy S24 (One UI 9 = Android 17, 120 Hz) and Galaxy Tab S11 Ultra (Android 16, Android 17
  in a few months), DeX. Personal use only, never published. The deliverable is one 1.0.0 release-optimized APK
  (R8, not debuggable, Baseline Profile) signed with the repo's shared debug key under applicationId
  app.nagmedar.debug, so it installs over the user's current app keeping all data. No API-key secrets exist:
  Last.fm scrobbling and Podcast Index search stay disabled with their in-app notices (never crash).
- Phone navigation (final): bottom bar **Müzik · Podcast · Radyo · ⋯**; everything else (playlists, network,
  streams, downloads, audio conversion, settings, about…) is reached from ⋯. No Home page any more.
  Tablet/DeX: sidebar + dense music table + inspector pane ("MusicBee" layout).
- Design: **Desktop Kit 1.0 first** (repo hesaplarimdijital-coder/desktop-kit), then the app takes it over.
- CI runs the tests (`NAGMEDAR_RUN_TESTS: '1'`): every job must be green, tests included. Deleted features take
  their tests and screenshot references with them; new logic gets tests; changed screenshots are reviewed
  (look at the PNGs) and committed only when the change is intended.
- CI no longer publishes APKs (no artifact, no ci/apk); do not add any APK upload.
- No independent review rounds inside your task; self-review your diff for crashes, data safety, main-thread
  IO and leaked secrets before every push.

- i18n tests: the precompiled classes in scratchpad/i18nout are STALE (their allowlists name deleted files).
  Compile your branch's own copies first: `S=$SCRATCH;
  mkdir -p $S/emptymain; sh $S/kt/kct.sh $S/i18n-<branch> $S/emptymain <worktree>/application/nagmedar-ui/src/test/java/app/nagmedar/ui/i18n/*.kt`
  and run JUnitCore with `-cp $S/i18n-<branch>:…` instead of `$S/i18nout:…` (same classes, same working directory).
- Force-pushes are refused by the permission system: never amend/rebase pushed commits; to update a pushed branch with
  master, `git merge origin/master` (commit message with the trailer) and push normally.

## 0. UPDATE (21 Oct, the user's decision) — overrides sections 4–5 where they differ

- **Finish the features first; testing comes later.** Do NOT write new tests (unit, Robolectric,
  Compose, screenshot) and do not review or commit screenshots. Existing tests must still
  **compile** (CI compiles them): when you change an API, fix or delete the tests that no longer
  compile, nothing more. Tests you already wrote may stay.
- CI is compile-only now (`NAGMEDAR_RUN_TESTS: '0'`): `Uygulama · debug` = assembleDebug +
  compiling the unit tests; native host tests do not run. "Done" = all CI jobs green (it
  compiles, R8 release builds) + the feature complete per its acceptance criteria.
- No independent review rounds: the orchestrator merges when CI is green. So self-review your
  diff once, carefully, for crashes and data safety (never modify/delete a user's file
  unexpectedly, no main-thread IO, no leaked secrets) — that is still required.
- **TV Lab is back in the product** (22 Oct, the user reversed the deletion: commit 242742cd
  restored `ui/tvlab`, E12's stories S-1202/S-1204/S-1206/S-1207/S-1209 are implemented since).
  Treat it like any other section.
- Before your final push: rebase onto origin/master (Room: if master's VERSION moved, renumber
  your migration to the next free version; the exported schema JSON comes from your PR's CI run
  on `ci/shots-pr-<PR>` under `schemas/` — commit it). Final report: shorter (≤ 200 words):
  what is done, PR/branch/HEAD, CI state, device checks (Turkish), anything left undone.

## 1. The project in one minute

- Target: Galaxy Tab S11 Ultra (Android 16, DeX desktop mode), phones too. Turkish-first UI,
  English second. libVLC 4 (published AAR `4.0.0-eap29`) plays video/radio/podcasts; the
  **Nağmedar Ses Motoru** (`native/audio`, C, JNI `application/nagmedar-audio`) plays local
  music. The native lab library (`native/lab`, `application/nagmedar-lab`) holds FFmpeg,
  libdovi (dovi_tool), whisper.cpp, libusb tools (TV Lab, SP-02/03/05).
- New UI is Compose in `application/nagmedar-ui` (package `app.nagmedar.ui`): shell and
  destinations `shell/`, settings `screens/SettingsScreen.kt`, shortcuts `input/Shortcuts.kt`
  (ShortcutRegistry — the single source of every shortcut), components `components/`,
  theme `theme/` (NagTheme tokens; never hard-code colours/sizes), TV Lab `tvlab/`, lab `lab/`.
  Room DB in `application/nagmedar-data` (`Entities.kt`, `Daos.kt`, `NagmedarDatabase.kt`,
  migrations + exported schemas in `application/nagmedar-data/schemas`). Media3 session in
  `nagmedar-session`. The classic VLC app is `application/vlc-android` (playback service,
  `PlaylistManager`, `MusicEngineController`). Plan, ADRs: read only if you need them:
  `$ORCH/plan.txt`.
- **Learn the house style from an exemplar story** before writing: `git show 0fbb8b98`
  (S-1502 settings backup: settings page, strings in both locales, Room DAO, unit and
  Robolectric tests, screenshot test, CHANGELOG, TERIMLER) and `git show 9eaed625` (its
  reference screenshots). For native work: `git show 0a5739c6 -- native buildsystem` (S-808)
  and `git show 1420728a` (S-911). Match their naming, KDoc voice (short English sentences
  saying what a thing *is*, with the story id), test style and commit message style.

## 2. Hard rules (never break)

1. Never push to `master`, never merge, never force-push anything but your own story branch.
   Never change repository settings or visibility, never create or publish GitHub releases,
   never trigger the signed-release workflow, never touch secrets.
2. Never put the user's e-mail or any personal data anywhere (code, User-Agents, commits).
   HTTP User-Agent: `Nagmedar/<versionName> (+https://github.com/hesaplarimdijital-coder/vlc-android)`
   — no contact e-mail. API keys come from build secrets (`BuildConfig` fields fed by CI
   env, as `NAGMEDAR_OPENSUBTITLES_API_KEY` is; grep it); an empty key must leave the feature
   disabled with a clear in-app notice, never a crash. Respect each service's terms (rate
   limits, required headers, click/vote reporting as documented).
3. Original user media files are never modified unless the story says so and the user asked
   in the UI; outputs go to new files via SAF.
4. No new dependency that is not on Google Maven / Maven Central and GPLv2+-compatible. Prefer
   what the project already uses (OkHttp, kotlinx.serialization or org.json — check), Room,
   WorkManager, Media3, Compose. Add licences of new third-party code to the licence list
   (grep `S-005`).
5. Commits end with exactly these two lines (after a blank line):
   ```
   Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>
   Claude-Session: https://claude.ai/code/session_01Lvh4tsdhXUadwhZpYnRA5K
   ```
   PR bodies end with:
   ```
   🤖 Generated with [Claude Code](https://claude.com/claude-code)

   https://claude.ai/code/session_01Lvh4tsdhXUadwhZpYnRA5K
   ```

## 3. Where you work

- Your git worktree and branch are given in your task. Work only there. Base: `origin/master`
  (fetch and rebase onto it before you push the final version; resolve conflicts carefully —
  other agents change shared files such as strings.xml, Destinations.kt, SettingsScreen.kt,
  CHANGELOG.md, TERIMLER.md, NagmedarDatabase.kt at the same time).
- **One commit per story** (`S-1101: <what, lower case>` + body explaining what and why, lines
  ≤ 72 chars, like the exemplars), plus one separate commit per story for its reference
  screenshots (`Screenshots: <what> (S-1101)`). While iterating, amend/fixup and
  `git push --force-with-lease` your own branch.
- Room migrations: bump the version by one on your branch and add the migration + test; if
  master moved and took that number, renumber when rebasing.
- Scratch files: `$SCRATCH/agents/<your-branch-name>/`.
  Never write scratch into the worktree.
- The machine has 2 CPUs and 7 GB RAM shared with other agents: no parallel heavy builds,
  `-j2` at most, JVM `-Xmx2g`.
- Network: only github.com, PyPI and npm are reachable. No Maven, no Google, no device, no
  emulator here. Gradle builds are **impossible locally**; CI is the compiler of record.

## 4. The loop: branch → PR → CI → fix

1. Write the code and tests. Self-review your diff (`git diff origin/master...`) as a strict
   reviewer would before every push: imports, nullability, coroutines/threads (no disk or
   network on the main thread), lifecycle, string resources, accessibility, edge cases.
2. Optional fast local checks (they catch most compile errors before a 20-minute CI round):
   - Kotlin stub compile: tooling in `.../scratchpad/kt/` (`kc3.sh OUT files...` compiles
     against stubs in `kt/SL/*.kt`; `kct.sh OUT MAINCLASSES tests...` compiles tests; run
     with `java -cp OUT:MAIN:/opt/gradle-8.14.3/lib/kotlin-stdlib-2.0.21.jar:/opt/gradle-8.14.3/lib/kotlinx-coroutines-core-jvm-1.6.4.jar:/opt/gradle-8.14.3/lib/junit-4.13.2.jar:/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar org.junit.runner.JUnitCore <class>`).
     `kt/genr.py <strings.xml> <R.kt>` generates R for the stubs. The stubs only cover APIs
     used so far: **copy `kt/SL` into your scratch dir** before extending it — never edit
     the shared one. Use this for pure logic (parsers, models, rules) with plain JUnit
     tests; Compose UI is better left to CI.
   - i18n tests (glossary, both locales, no hard-coded text):
     `cd <worktree>/application/nagmedar-ui && java -cp $SCRATCH/i18nout:/opt/gradle-8.14.3/lib/kotlin-stdlib-2.0.21.jar:/opt/gradle-8.14.3/lib/junit-4.13.2.jar:/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar org.junit.runner.JUnitCore app.nagmedar.ui.i18n.GlossaryTest app.nagmedar.ui.i18n.StringsTest app.nagmedar.ui.i18n.HardcodedTextTest`
     (they read the sources relative to the working directory).
   - Native: host builds with cmake (see `buildsystem/nagmedar-native/build.sh host`, the
     host test scripts `test-host.sh`, `test-audio.sh`). A host FFmpeg n8.1.3 (+libopenmpt,
     TagLib) prefix exists at `.../scratchpad/bs-prefix` and its sources at
     `.../scratchpad/bs-work/src/ffmpeg` — copy, don't modify. A native change re-runs the
     whole lab-native CI job (~25 min).
3. Push the branch and open a **draft PR** with the REST API (GraphQL is blocked):
   `gh api repos/hesaplarimdijital-coder/vlc-android/pulls -f title='S-1101: …' -f head=<branch> -f base=master -F draft=true -f body="$(cat body.md)"`
   (later pushes update it). The PR body: what the story does, how it was tested, what needs
   a device.
4. Wait for CI **without burning tokens**: one blocking call per ~9 minutes, e.g.
   `for i in $(seq 1 18); do s=$(sh $ORCH/ci.sh $(git rev-parse HEAD) 2>&1); echo "$s" | grep -q "in_progress\|queued" || break; sleep 30; done; echo "$s"`
   with a 590000 ms timeout; repeat until no job is in progress. `ci.sh <sha>` prints each
   job's state and, for finished jobs, compiler errors and failed tests (annotations).
   Jobs: `Laboratuvar native` (native, cached unless native/ or buildsystem/nagmedar-native
   changed), `Uygulama · debug` (assembleDebug + all unit/Robolectric/screenshot tests),
   `Uygulama · release` (R8 release build + benchmark build). All must be `success`.
5. A failed debug build's full log and test XML: `git fetch -q origin ci/debug-pr-<PR> && git show origin/ci/debug-pr-<PR>:build.log | grep -n "e: \|error:\|FAILED" | head`.
   Screenshots of your PR run: `git fetch -q origin ci/shots-pr-<PR>`; list with
   `git ls-tree --name-only origin/ci/shots-pr-<PR>`, report `diff/report.md`; extract a PNG
   with `git show origin/ci/shots-pr-<PR>:<name>.png > /tmp/.../x.png` and **look at it**
   (Read tool) — check layout, clipping, overlap, contrast, both themes, both languages.
   Unchanged existing screenshots must stay identical (report: "değişti" = changed); if you
   changed one on purpose, say why. Commit the new references (and intended updates) into
   `application/nagmedar-ui/src/test/screenshots/` in the separate screenshots commit.
   Exported Room schemas from the same branch go to `application/nagmedar-data/schemas`.
6. Repeat until all three jobs are green with the screenshots committed. Then stop and report.

## 5. Definition of done (every story)

- Acceptance criteria of the story met as far as possible without a device; what needs a
  device is listed as device checks (Turkish, concrete, checkable).
- Tests: unit tests for logic (parsers, rules, state, DAO with Robolectric), Compose UI tests
  where behaviour matters, **screenshot tests** at the project's three sizes — phone, tablet,
  DeX — in the patterns of `src/test/java/app/nagmedar/ui/screenshots/` (light/dark/English
  variants as the neighbours do). The accessibility gate (S-1407: targets ≥ 24 dp pointer /
  48 dp touch, text ≥ 11 sp, contrast ≥ 4.5:1) and i18n tests must pass.
- Every user-visible string in **both** `values/strings.xml` (English) and
  `values-tr/strings.xml` (Turkish), per `application/nagmedar-ui/TERIMLER.md` (read its
  rules: address the user as "siz", "…" single char, Ctrl+K style, "−" U+2212 for minus,
  space between number and unit, Turkish decimal comma; avoid "şarkı"/"song" → "parça"/"track";
  never "sonra çal"). New terms get a TERIMLER.md row.
- Keyboard/pointer: every new action reachable by keyboard; shortcuts registered in
  ShortcutRegistry (no ad-hoc key handling), context menus where the neighbours have them,
  focus/hover states from the component set. Layout adapts to width classes (phone/tablet/DeX).
- Errors and empty states designed (offline, server down, permission denied, nothing yet).
- Long work off the main thread; background jobs with WorkManager respecting constraints;
  foreground service + notification for user-visible long jobs; cancel and resume where the
  story asks.
- CHANGELOG.md: a Turkish bullet in the right section (match its voice).
- No new compiler warnings in your files; no dead code, no TODOs left, no debug logging spam.

## 6. Your final report (your last message; ≤ 350 words)

```
BRANCH: <name>   PR: #<n>   HEAD: <sha>   CI: <green|red + why>
STORIES: S-xxxx — done/partial (one line each)
COMMITS: <sha> <title> (each)
TESTS ADDED: <files>
SCREENSHOTS: <reference files added/changed>
DEVICE CHECKS (Turkish, one per line, "- [ ] …"):
DEVIATIONS / OPEN ISSUES: <what you could not do and why; secrets the user must add>
NOTES FOR THE NEXT STORY OF THIS TRACK: <key types, files, decisions>
```
Also append the "NOTES FOR THE NEXT STORY" part to
`.../scratchpad/orch/notes/<track>.md` so a later agent of the same track can start from it.

## 7. Rebase hygiene (22 Oct, mandatory)
After every rebase onto origin/master run `python3 $ORCH/dupstr.py <worktree>` (strings.xml files are union-merged: a rebase can duplicate a name or leave an XML tag open — the script says which) and the i18n tests, before pushing. Several branches land every hour: rebase right before your final push, and once more if the coordinator says master moved.
