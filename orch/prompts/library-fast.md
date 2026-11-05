You fix Nağmedar's slow start: the music library is scanned again every time the app opens. Nağmedar is a VLC-Android fork that is a pure music player (Music, Podcasts, Radio); the user runs it on a Galaxy S24 (Android 17) and a Galaxy Tab S11 Ultra, with a large local music collection. His words (5 Nov 08:54):
"Uygulamayı her açtığımda müzik listemi baştan tarıyor. Bu da çok uzun sürüyor. Diğer uygulamalarda bir kere tarıyorum, sonra müzik listem hemen açılıyor."

The goal: **opening the app shows the music list at once, as it was last time.** Scanning happens once (first run, or when the user adds/removes a folder or asks for it); afterwards only changes are picked up, in the background, without emptying, hiding or blocking the list, and without a "Kitaplık taranıyor" state in front of a library that is already known. A change found later (a new album, a deleted file) appears in the list when it is found.

First read fully $ORCH/AGENT_BRIEF.md (section 0000 first), then $ORCH/notes/library.md, $ORCH/notes/music-services.md and the parts of $ORCH/notes/desktop-kit-diff.md §0 about stale-while-revalidate (the 0.2.0 "every tab switch shows a loading screen" fix: what is already cached in memory).

Worktree: $WT/library-fast, branch `story/library-fast` (from origin/master). Scratch: $SCRATCH/agents/library-fast/. Force-pushes are refused: never rewrite pushed commits; `git merge origin/master` when master moves.

**The machine restarts about every two hours and kills you with it.** Keep `$SCRATCH/agents/library-fast/progress.md` current (findings / done / next / decisions) after every push, read it first if it exists, and push within 45 minutes of starting and then at least every 40 minutes (WIP commits are fine; never force-push).

## 1. Find out what happens at start (evidence first)
Trace a cold start with media permission and without it (folders picked with the system picker), and write down in progress.md which of these make the user wait, with file:line:
- the medialibrary's start: `startMedialibrary(...)` callers (onboarding/FirstRunHost.kt `completeFirstRun` passes `firstRun = true, upgrade = true` — is it called on every start?), `MediaParsingService.startScan` (`KEY_MEDIALIBRARY_AUTO_RESCAN` → `reload(null)` on every start, application/vlc-android/src/org/videolan/vlc/MediaParsingService.kt:375), whether a reload re-parses files whose modification time did not change;
- what the Music page waits for before it lists anything (library/LibraryStates.kt `ScanState`, `Medialibrary.isWorking`, library/MediaRepository.kt, music/MusicScreen.kt's read path, `ScreenMemory`): a known library must show while a scan runs;
- the folders picked with the system picker: library/SafLibrary.kt keeps its listing only in memory (`cache`), so every process start walks every document tree again (DocumentsContract queries, up to 5 000 items each) and reads tags again; find where titles/artists/covers of those files come from and whether they are kept;
- anything else on the start path that is proportional to the library's size (tech index, waveforms, covers, play stats, the Auto catalog).

## 2. Fix it without rewriting what works
Extend what exists (the brief's rule). Likely pieces, decided by your findings: a persistent store of the picked folders' listing (Room in nagmedar-data: a migration to DB 24 with its test and exported schema; or a file in the app's no-backup storage) read at once and refreshed incrementally by comparing the providers' `COLUMN_LAST_MODIFIED`/size; the medialibrary's start not reloading everything on every launch (a reload only when a folder may have changed, or kept but never blocking the UI: the user must not see it), no `firstRun`/`upgrade` start on ordinary launches; the Music page always drawing the last known list first. Keep "Kitaplığı yeniden tara" (or add it in Settings › Müzik if it does not exist) for a full rescan on demand, with progress shown without hiding the list.

## Tests and evidence
Unit tests for the incremental refresh (added, changed, removed files; a folder that became unreachable keeps its rows until the user removes it), the migration test, a Compose test that the Music page shows the stored list while a scan runs. In the emulator walk: after the first run's scan, the app is killed and started again and the Music list shows its tracks within a short time with no "Scanning the library" state (one fast step; the walk's music is 64 "Profile track" files in /sdcard/Music). Measure and report the cold start → first row time before and after (logcat timestamps from the walk are enough).

## Coordination
Running at the same time: `story/platform` (the build: Compose 1.12, AGP 9.4, minSdk 34; it removes the TV and film modules), `story/walk-robust` (emulator.yml, emulator_smoke.py, testmedia.yml), `story/auto-podcasts` (Android Auto). Keep your walk step a small separate function; merge origin/master before each push. Do not edit CHANGELOG.md or the version.

## Done
All CI jobs green, the walk green with your restart check, PR opened as draft (title like "1.0.3: müzik listesi açılışta hemen geliyor, her açılışta yeniden taranmıyor"). Final report per brief §6 with Turkish device checks (e.g. "- [ ] Uygulama kapatılıp açılınca müzik listesi hemen geliyor, “Kitaplık taranıyor” görünmüyor"; "- [ ] Müzik klasörüne yeni bir albüm eklenince birkaç saniye içinde listede çıkıyor"). Append notes to $ORCH/notes/library.md. Do not merge.
