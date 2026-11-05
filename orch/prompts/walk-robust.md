You make Nağmedar's emulator walk sturdy and fast enough for the 1.0.3 session, and clean the CI's test media. Nağmedar is a VLC-Android fork that is a pure audio player; every story of this session must leave the walk green on phone and tablet, so the walk itself must be trustworthy.

First read fully $ORCH/AGENT_BRIEF.md (section 0000 first), then $ORCH/notes/release.md ("Final check" and "release build" parts), $ORCH/notes/crash-sweep.md (the walk's helpers), $ORCH/notes/sheet-drag.md and the end of $ORCH/LOG.md (the 1.0.2 walks). The walk is `.github/workflows/emulator.yml` + `buildsystem/ci/emulator_smoke.py`; the test media workflow is `.github/workflows/testmedia.yml`.

Worktree: $WT/walk-robust, branch `story/walk-robust` (from origin/master). Scratch: $SCRATCH/agents/walk-robust/. Force-pushes are refused: never rewrite pushed commits; `git merge origin/master` when master moves.

**The machine restarts about every two hours and kills you with it.** Keep `$SCRATCH/agents/walk-robust/progress.md` current (done / next / decisions / run ids and results) after every push, read it first if it exists (a predecessor left it), and push at least every 40 minutes of work (WIP commits are fine; never force-push). The user wants testing done well ("test yapılması"): this story is what makes the walk a trustworthy test.

## 1. The flaky check
On the 1.0.2 personal walk (API 37.0) "a drag down from the sheet's middle closes it" failed once in six runs: the ⋯ sheet stayed 1.5 s after `input swipe` (it passed in the other five, debug 35/37 included). Find out whether it is the app (a race in `components/Sheets.kt`'s SheetDrag / `rememberSheetDrag`: e.g. the swipe arriving before the sheet's layout or its `held` watch, the fling settling, a recomposition dropping the drag) or the emulator's injected input (timing of `input swipe` on API 37, the frame it lands on). Prove it with evidence (logcat, the dump around the step, a few repeated runs of only that step if you can make one), then fix what is wrong: in the app if the app races (with a test), in the walk if the walk asks too early or reads too soon (wait for the sheet to be idle, swipe with a realistic duration, re-read with a deadline). Do not just retry blindly.

## 2. The walk's time
The walk takes 45–48 min against `--deadline-min 50` (step timeout 65): every new story adds steps. Split it so each emulator job has room again, without losing a check: e.g. a matrix over pass groups (tablet-en + workstation, phone-en + phone-tr) per API level, each with its own evidence branch name and report, and one summary that the PR and `ci.sh` show. Keep the dispatch inputs (`build`, `api_levels`, `passes`, `baseline_profile`) working as documented at the top of emulator.yml, and update that comment. No artifact uploads (the project's rule: no APK or artifact leaves CI except the encrypted personal release); each job may build its APK itself, or share it through the Actions cache if that is faster and allowed by the rule.

## 3. The system's ANRs
"System UI isn't responding", Pixel Launcher and other system ANRs are the emulator's, not the app's, and today a run with one fails or needs a manual re-run. Tell them apart in the walk: an ANR or crash of `app.nagmedar.debug` (or any of its processes) fails the run as today; a system one is recorded in the report, dismissed ("Wait"), and the step is retried once. Same for crash dialogs.

## 4. Test media
`testmedia.yml` still makes video and Dolby Vision samples, which nothing uses since video was removed (1.0): keep what the audio, podcast and radio tests and the walk use, remove the rest (and whatever only they needed).

## Evidence
Dispatch the walk on your branch for debug and personal (`gh api repos/hesaplarimdijital-coder/vlc-android/actions/workflows/emulator.yml/dispatches -f ref=story/walk-robust -f 'inputs[api_levels]=35,37.0'` and again with `-f 'inputs[build]=personal'`), several times for the flaky check; evidence branches are shared with other agents: check that report.md's source line names your sha. Report the wall time of each job before and after.

## Coordination
Another agent (story/auto-browse) adds one Android Auto step to the personal walk in emulator_smoke.py; others may port Desktop Kit parts in the app. Merge origin/master before each push; keep your changes to the walk's structure, its helpers and the workflows. Do not edit CHANGELOG.md or the version.

## Done
All CI jobs green, the walk green on API 35 and 37.0 in debug and personal, PR opened as draft (title like "CI: yürüyüş ikiye bölündü, sistem ANR'leri ayrıldı"). Final report per brief §6. Append notes to $ORCH/notes/release.md under "## 1.0.3 walk". Do not merge.
