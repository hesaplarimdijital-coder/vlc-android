# Test phase plan (starts when the last feature branch is on master)

Order of landing before it: fix-robustness (5d022532, PR 23) → fix-subtitles (852908ec, PR 25, based on
robustness) → S-1205 (PR 35, Room 20: rebase, 20.json must still match) → music-player (PR ?) →
guide-update → then this phase.

## 1. CI tests back on
- PR #40 (story/test-phase, 7a58ebed) = master + NAGMEDAR_RUN_TESTS '1'. Its first run lists what the
  feature phase broke: unit/Robolectric/Compose tests (nagmedar-ui, tools, data, session, lab, audio),
  screenshot references (`Ekran görüntülerini karşılaştır` step; refs in nagmedar-ui/src/test/screenshots;
  new ones from ci/shots-pr-40), native host tests (lab-native job: build.sh host + test-host.sh).
- Test-fix agents (opus), by group, each on a branch from story/test-phase:
  a. nagmedar-ui unit/Robolectric/Compose failures (expected: classic UI removal, music toolbar, playlists,
     podcast row opens episode (known), films pages, settings renderer, KeyboardMap/MouseOnlyFlows).
  b. screenshot references: regenerate from ci/shots-pr-<n> after a visual check of each diff (the agent
     reads the PNGs; a wrong-looking screen is a bug to fix, not a reference to replace).
  c. nagmedar-data migration chain (NagmedarDatabaseTest, schemas 1..20), nagmedar-audio, lab, session, tools.
  d. native host tests (test-host.sh) incl. S-1406 host regression; remux/compat-track/subtitle-extract tests.
- Merge story/test-phase last (flips the flag on master); from then on merges need green tests.

## 2. Emulator smoke (emulator.yml, workflow_dispatch; ref = master sha; passes tablet-en,phone-en,phone-tr)
- Evidence on ci/emulator (report.md). Driver gaps noted earlier: equaliser button, classic settings entry
  (gone now), new pages (Videolar, Afiş/list views, TV Lab, Tüm ayarlar, playlists cards, music player).
- StrictMode: 16–18 violations last time, frames often "?": make emulator_smoke.py keep the whole
  StrictMode block per violation (logcat -v long, 60 lines) in the evidence, then fix each at its source
  (main-thread IO: SharedPreferences commit, File.exists on media, Room on main, untagged sockets, leaked
  closables).

## 3. Independent reviews (REVIEW_BRIEF.md), whole diff 02f6d17f..master by area (one reviewer each, opus,
   worktree per reviewer, read-only): player/casting; library+films+playlists+music; podcast/radio/network;
   TV Lab remux (S-1205..S-1210, native); subtitles (S-707..S-710 + extraction); classic-UI removal (every
   entry point still works: intents, notifications, widget, Auto, shortcuts, DeX, back stack); data safety
   (tag writes, remux outputs, backups). Findings → fix agents; no self-review of own work.

## 4. S-1406 (LG B4 protocol): host regression already in test-host.sh; write the manual device protocol
   doc (Turkish) as a backlog row's device checks + a docs tab section.

## 5. Polish-later list (LOG.md "POLISH LATER" lines) → one agent, small commits, each item verified.

## 6. Final report to the user (Turkish): what to install (debug APK from CI artefact, signed release only on
   request — S-1503 ask first), secrets to add (NAGMEDAR_LASTFM_API_KEY/SECRET, NAGMEDAR_PODCASTINDEX_KEY/SECRET,
   NAGMEDAR_TMDB_API_KEY, NAGMEDAR_OMDB_API_KEY, NAGMEDAR_OPENSUBTITLES_API_KEY), device-check tabs to walk,
   branches left undeleted, remaining classic layouts (TV leanback only), known limits (projectM absent,
   H.265/WebM encode refused, whisper not run in CI, embedded picture subtitles not extractable).

## Notes from the test-phase agent (PR #40, story/test-phase)
- "Compose did not get idle" at ComposeUiTest.android.kt:326 (Compose 1.7.6) is the idle wait BEFORE the test body.
  Cause here was never a composable: a Compose test in Robolectric's LEGACY graphics sandbox starts Compose's
  GlobalSnapshotManager there; a plain Robolectric test writing Compose state afterwards leaves its posted work,
  Robolectric drops it, the global snapshot stays dirty → every later Compose test in that sandbox hangs 60 s.
  Rule: every Compose test has @GraphicsMode(GraphicsMode.Mode.NATIVE); ComposeSandboxTest (plain JUnit, reads
  src/test/java) enforces it.
- Timing: whole debug build + all module tests ≈ 8 min; nagmedar-ui 2114 tests ≈ 2 min test time. The 25-min Test
  timeout only bites when something hangs.
- CI: native host tests are the job `native-host` (runs whenever NAGMEDAR_RUN_TESTS is '1', independent of the
  lab-native cache). API keys live only in the Gradle steps' env (debug/release build, signed release, manual dev).
- Screenshots: tools to review — composite ref|new|diff per changed screen (PIL), read each; 143 replaced, 32 added.
- Playback timelines: SmoothProgress.glide() steps a pixel at a time (glideStepMs, ≥ 20 ms, none when a reading moves
  < 1 px or with reduced motion); rememberPlaybackProgress/Time are lifecycle-aware (STARTED), paused progress
  re-reads on PlayerController.progress LiveData changes (seeks of either engine).
- POLISH: films page header with the details pane on a tablet cuts "7 film · 3 klasör" to "7 fil…" (PageHeader
  keeps the actions' room by design) — a compact header by width would help.
- State at hand-back: 2224c338 green on master 492fcc23 (round 4 of 6). Master then reached 22e37e21 (+21 commits):
  `git merge-tree --write-tree` is clean and none of its new tests is a Compose test, but S-1407 (TransportBar
  heightIn(min = 72.dp)) and the Settings/Library/Films changes will move screenshots. Final rebase = two rounds:
  one to render (review ci/shots-pr-40, amend "Screenshots: references of the test phase"), one to confirm.
- Final round (rebased onto ef19e331): round 5 had 3 failures, all from fixers' first-run tests. TvLabController:
  the engine's cancel only when engine.loaded == true (open() and cancel(); cancel() read engine.available = loads
  the lib on the main thread); the profiles test now makes the lab like TvLabSession (Main scope + IO, idles the
  main looper). PlayerSheetView: the focus LaunchedEffect must live INSIDE BoxWithConstraints (SubcomposeLayout:
  its content is composed at measure; Compose tests run effects eagerly (Unconfined) so a request beside it finds no
  attached FocusRequester, and runCatching hid it). 6 screenshots changed as master's fixes meant (backup note's
  PIN, bookmarks note); the DeX player refs from 12 Oct lack S-1303's ⋮ menu button but stay under the 0.1 % bar.
- Round 6 (05478d0f): all green but PlayerSheetsTest.escapeClosesIt: with the first item focused, Compose turns
  Esc/Back into a focus Exit (item -> sheet), so NagDialog's Esc rule never saw it. Fix efde30cd (sheet takes Esc and
  Back in onPreviewKeyEvent, closes on release, like MenuPopup) is committed locally on story/test-phase (HEAD
  0be9ac7b), NOT pushed: the 6-round budget was spent. Push it (force-with-lease over 05478d0f) for one more round.
