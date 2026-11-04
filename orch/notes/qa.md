
## qa track: emulator smoke test (branch qa/emulator, PR #1)
- Workflow .github/workflows/emulator.yml + driver buildsystem/ci/emulator_smoke.py. Evidence on branch ci/emulator
  (report.md, progress.txt, shots/, dumps/, logcat-warn.txt); the driver also pushes partial evidence every 10 steps.
- Read: git fetch -q origin ci/emulator; git show FETCH_HEAD:report.md; screenshots: git show FETCH_HEAD:shots/<n>.png > x.png
- Dispatch only works once emulator.yml is on master; until then the PR run (paths filter) is the way. Run log is not
  downloadable from the sandbox (blob host blocked): read the branch instead.
- ci.sh hides notices except titles containing "Ekran": the summary notice is titled "Emulator Ekran görüntüleri ve rapor".
- Package app.nagmedar.debug, launcher StartActivity; run is ~36-45 min (167 steps, ~11 s each). Passes: tablet-en, phone-en, phone-tr.
- Driver finds destinations from nag_nav_* strings; sidebar scrolls on tablet; phone uses More.
- Known driver gaps: "Open the equaliser" (hidden without engine) and "All settings (classic interface)" buttons not found
  by scrolling; StrictMode frames show "?" (no app frame in the trace).

## robustness (story/fix-robustness, PR #23)
- Process-wide Compose state must be made in the global snapshot: `var x by Snapshot.global { mutableStateOf<T>(v) }` in objects, `Snapshot.global { Controller(...) }` in lazy factories (`shared ?: synchronized { ... }`). Done for Casting, ShortcutRegistry, PhysicalKeys, LyricsEditorRequest, Recordings, SubtitleToolsLauncher, WhisperLauncher, TranscodeRequests, AbTest, AdvancedVideoSession, LyricsSession, RadioCenter, RadioRecordings, SubtitleProviders, LatencyMeasuring. Worker-thread state writes go to the main thread (post/scope.launch), not withMutableSnapshot (it throws on conflicts).
- Android 15 FGS time limit: `app.nagmedar.ui.work.TimeLimit` (`reached(worker)`, `onReached(worker) { }` at the start of doWork, `tell(context, channel, id, icon, text)`; strings nag_time_limit_*). Services: implement `onTimeout(startId, fgsType)` and stop the foreground within seconds (MediaParsingService, RecordingKeeper, RecordingService, HlsService do).
- Tag writes (S-1005): `FileTags.write` → `TagBackups.guarded` (copy in noBackupFilesDir/tag-backup, `<uuid>.audio` + `<uuid>.json`; verify via native `nag_tags_audio` / `NativeAudio.tagAudio`; put back on failure); `FileTags.recover` from NagmedarActivity.onStart. `TagLyrics.write` returns `TagWrite` (Written/NoRoom/Failed); `SaveResult.NoRoom`, `EditorMessage.NoRoom`.
