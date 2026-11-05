
## S-1504 (user guide + shortcut card) - NOTES FOR THE NEXT STORY
- Package app.nagmedar.ui.guide: GuideDocument.kt (Markdown parser, {key:Id} spans), GuideScreen.kt, ShortcutCard.kt.
- Guide text: nagmedar-ui/src/main/assets/guide/guide-{tr,en}.md, picked via string nag_guide_asset. "## Title {id}" starts a section; ids must match in both files. Facts must match the code; keys always via {key:Id}.
- New Destinations Guide and ShortcutCard are unlisted (highlight Settings). ShortcutId.UserGuide = F1; ShortcutId.ShortcutList (Ctrl+/) now opens the read-only card, editor still in Settings (AppActions.editShortcuts).
- AppActions constructor unchanged (DexWindow.kt calls it positionally). FilterField in AboutScreen is internal and reused.
- New features should add a section to both guide files (and a TERIMLER row if a new term). TV Lab is deliberately absent from the guide; when remove-tvlab lands nothing in guide/ references it.
- Known limit: full-screen video player ignores F1 and Ctrl+/.

## 1.0 release build (story/release-build, PR #79, 29 Oct)
- R8 (minify + shrinkResources) on release/signedRelease/benchmark/personal; `-dontobfuscate`. Rules: application/app/proguard-rules.pro (Nağmedar 1.0 section); resource keeps: application/app/src/main/res/raw/nagmedar_keep.xml. New class found by name or JNI callback → add a keep rule there.
- `personal` build type: app/build.gradle (+ `personal` types in vlc-android and resources for APP_ID/tv authority/shortcuts, personalApi deps). arm64 = 14010006; split by ABI for assemble*Release/assemblePersonal; -PnagmedarAbis picks split ABIs.
- Baseline Profile: application/app/src/main/baselineProfiles/{baseline,startup}-prof.txt; generator application/benchmark/.../BaselineProfileGenerator.kt; run: emulator.yml dispatch `-F inputs[baseline_profile]=true` → ci/baseline-profile; apply: `git fetch origin ci/baseline-profile && git checkout origin/ci/baseline-profile -- application/app/src/main/baselineProfiles`. report.md lists steps reached and missing elements (hierarchy XML in device/). Profile build = benchmark without R8 (-PnagmedarProfileSource).
- Compose dialogs: test tags are NOT resource ids inside Dialog windows (generator falls back to EditText + Enter).
- Delivery: ci.yml dispatch `-F inputs[personal_release]=true` (ref master) → ci/personal-release (ciphertext + README). buildsystem/release/{deliver_personal.sh,encrypt_apk.sh,decrypt_apk.sh,delivery.pub,README.md}.
- Emulator walk of the R8 build: `-f inputs[build]=personal [-f inputs[api_levels]=35,37.0]`; API 37.0 google_apis image exists; evidence ci/emulator (35) and ci/emulator-api37.0.
- compileSdk 37, targetSdk 36 (local network permission, background audio, widget bitmap limit pending).

## Final check (story/final-check, PR #86, 29 Oct)
- Walk passes: tablet-en, phone-en, workstation (tablet Music: inspector, Ctrl+I, column chooser), phone-tr. Report has "## 1.0 checks" (a failed check fails the run) and "## Frames" (gfxinfo janky %, p50/90/95/99 per gesture; swiftshader emulator: ~80-100 % janky, informational).
- Evidence branches: ci/emulator (debug, API 35), ci/emulator-api37.0 (debug), ci/emulator-personal, ci/emulator-api37.0-personal. The PR's own run is the debug API 35 walk (paths filter), so dispatch only `api_levels=37.0` for debug while a PR run is going (both would write ci/emulator).
- Walk media: 64 tracks "Profile track a.t" (3 min, mono 32k, so none ends mid-check) + the feed at 10.0.2.2:8765 for every run; the walk follows the feed itself (Podcasts › add).
- Driver traps: a tablet's sidebar always shows Downloads/Settings/Playlists (never press Back for "the ⋯ sheet" there); dumps take ~2 s, so the episode's play-button check starts ~2 s after the tap; app-in-front is read from `dumpsys activity activities` (topResumedActivity).

## 1.0.1 fixes (story/fix-1.0.1, PR #87, 29 Oct) - NOTES FOR THE NEXT STORY
- Lost podcast subscription: root cause seen in a logged emulator run (branch diag/fix-1.0.1-podcast, evidence ci/baseline-profile-diag; neither for merge): Enter's KeyDown in the add field sent the address, the field became disabled and focus went to "Cancel", Enter's KeyUp clicked it (Compose 1.7.6 clickable fires on a release without its press), dismissAdd() cancelled the job after the feed was read. Fix: field readOnly while working + consumes Enter's KeyUp; submitAddress/subscribe(hit) run on `background` (AppScope); dismiss no longer cancels (closed window: undo bar / notice).
- Same trap elsewhere: any text field disabled on Enter (ScrobbleSection's NagTextFields with enabled = !working, BackupViews) can hand Enter's release to the next button. Prefer readOnly while working.
- The diag generator trick (BaselineProfileGenerator#diagSubscribeLoop + full logcat in baseline-profile.sh, profile pushed to ci/baseline-profile-diag) is a cheap way to reproduce generator-only issues without touching ci/baseline-profile.
- DenseTable: header Column has coversRowsBeneath() (whole header band is hit, rows beneath never get a press); fixed column widths are max(fixed, title width + 2×8 dp + 16 dp arrow if sortable), via fittedWidths(); TableRow takes the widths list.

## 1.0.3 walk (story/walk-robust, PR #98) - NOTES FOR THE NEXT STORY
- The flaky "a drag down from the sheet's middle closes it" (1.0.2, personal API 37.0, 1 in 6) was the WALK's input, not the
  app. `input swipe … 300` sends no more moves once its 300 ms are up and lifts the finger at the end point. After a stall the
  lift jumps to the end, Compose does not drag by it, the pause leaves no fling, and the sheet springs back. Proof, from the
  on-demand pass `sheet-trials` on API 37.0, 10 trials each: swipe 10/10, drag() 10/10, "stalled" 0/10, with master's app
  (run 42001225449) and with a changed app (42001241247) alike. Evidence: sheet-trials.md and input-trials.txt (dumpsys input).
  The app is unchanged. Use `self.drag(x0, y0, x1, y1)` (input motionevent, 12 moves) for any drag whose distance matters.
- Split: the plan job gives runs [{api, group, passes, evidence}]; groups "tablet" (tablet-en, workstation, phone-tr) and
  "phone" (phone-en); any other pass gets a job of its own. Evidence: ci/emulator[-api<l>][-personal]-<group> (the old
  ci/emulator* branches are no longer written). Each job builds its own APK. Deadline 38 min, step timeout 50.
  The "Emülatör · özet" job reads each job's conclusion and its "Emulator Ekran görüntüleri" notice through the API, and fails
  when a job failed. Walks of 1d37fbc: 25-31 min per job (was 47-50), jobs 33-46 min (was 56-70).
- A pass must stand on its own: pass_phone_en goes through a fresh app's first run, and workstation/phone-tr call
  ensure_library() (media + "first run again") when phone-en is not in their job. open_by_scrolling() stops at the end of the
  page (the equaliser button is absent without the engine and used to cost 30 swipes).
- `inputs[ref]` walks another commit's app with the run's own driver (the "Kaynak" step copies emulator_smoke.py).
- System dialogs: dump() handles them everywhere. A dialog that names the app (label, package or process) fails the run.
  Any other is recorded in report.md "## The system's ANRs and crashes" and as an annotation warning: "Wait" the first time,
  "Close app" when the same process stops responding again. step() repeats its action, or the pending tap/key/combo recorded
  by remember_input(), when a dialog swallowed it. Logcat "ANR in <other>" and other processes' FATAL EXCEPTIONs are recorded
  only. unlock() = KEYCODE_WAKEUP + `wm dismiss-keyguard` (the menu key 82 led to a windowless googlesdksetup ANR, 268 times).
  Offline checks (no device): $SCRATCH/agents/walk-robust/pytest/check_dialogs.py and check_step.py, run with
  `python3 -I <script> <worktree>/buildsystem/ci/emulator_smoke.py`.
- Test media: testmedia/ is audio, podcast and radio only (13 samples, 11 generated, ~41 MB). pgs.py and the
  dovi_tool/hdr10plus_tool/mkvtoolnix downloads are gone; numpy stays (tools/dsf.py).
