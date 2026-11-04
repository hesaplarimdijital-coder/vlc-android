VERDICT: ready

FINDINGS (most severe first):
1. [major] application/nagmedar-ui/src/main/java/app/nagmedar/ui/work/RetiredWork.kt:94: the only files cleaned up are `files/tvlab`. 0.5's other video features leave app-private data that nothing in 1.0 reads, lists or deletes. The biggest is `noBackupFilesDir/translate-models`, the CTranslate2 subtitle-translation models. Each `model.bin` is 471,794,189 bytes, so with both directions downloaded that is about 0.95 GB. 1.0's Downloads page manages only `whisper-models`. Also left: `noBackupFilesDir/translate-jobs`, `noBackupFilesDir/extract-jobs`, `filesDir/hls`, `filesDir/subtitles` (re-encoded subtitle copies), and `getExternalFilesDir()/transcode`. Scenario: on the tablet the space stays used, and only "Clear storage" frees it, which also wipes everything else. Fix: add a new flag (for example `video_files`) to RetiredWork that calls `deleteRecursively()` on those folders, keeping `whisper-models`, and a test for it. (CONFIRMED)
2. [minor] RetiredWork.kt:60-61: only TV Lab's unique work `nagmedar-remux` is cancelled. 0.5 also queued `nagmedar-transcode`, `nagmedar-subtitle-extract`, `nagmedar-subtitle-translate` and `nagmedar-translate-model-<id>`. `nagmedar-whisper` also holds 0.5's worker class `app.nagmedar.ui.player.subtitles.whisper.WhisperWorker`, which has since moved to `ui.transcribe`. WorkManager cannot load any of those classes, so it logs "Could not create Worker" and marks the work failed. There is no crash, and 1.0's WhisperJobs replaces the leftover job (REPLACE when nothing runs). Fix: in the same flag, `cancelUniqueWork` for each name, plus `cancelAllWorkByTag(<old class FQN>)`. WorkManager tags every request with its worker class, so this also catches the per-model and old-Whisper work. (CONFIRMED)
3. [minor] RetiredWork.kt / BackupRules.kt:39: the folders of 0.5's video features (`nagmedar_snapshot_folder`, `nagmedar_recording_folder`, `nagmedar_subtitle_folder`) are only kept out of backups. Their persisted tree grants are never released and the keys stay forever. They count against the system's persisted-grant limit, and `lyrics/editor/DeviceStores.pickedTrees()` (every writable persisted tree) offers them as places to write `.lrc` files. Fix: read the three keys, remove them, and call `FolderGrants.release` on each. The holder check already protects music and podcast folders. (CONFIRMED)
4. [minor] transcribe/WhisperWorker.kt:468: the `nagmedar_whisper` channel is created only when it is missing. On a device upgraded from 0.5 the system's notification settings keep showing the old name, "Altyazı üretimi" / "Subtitle generation", instead of "Döküm (Whisper)". Fix: call `createNotificationChannel` every time; it updates the name of an existing channel. (CONFIRMED)
5. [minor] onboarding/FolderGrants.kt:27 combined with RetiredWork's release of the TV Lab batch folders: if a batch folder was the same tree as a music library section, `LibrarySections(false)` means only its write bit is dropped (reading stays). The lyrics editor then answers NeedsFolder where it wrote `.lrc` files before. It recovers by asking for a folder. Optional: keep WRITE for trees that `pickedTrees` would use, or accept this. (PLAUSIBLE, edge case)
6. [minor] Tests: RetiredWorkTest covers only the note and the `files/tvlab` deletion. Nothing tests the alarm cancel (PendingIntent key: action + component + request code 1210 + FLAG_IMMUTABLE, which I checked against 0.5's RemuxSchedule and it matches), the unique-work cancel, or that a library or podcast tree grant survives the release. (CONFIRMED)

Checked, nothing found:
- **Room.**
  - 20→21→22 is idempotent: `addColumn` checks PRAGMA first, and 22 uses `DROP TABLE IF EXISTS`.
  - No destructive fallback in any of the three Room builders.
  - Schema 22 equals 20 for every kept table except the three added nullable TEXT columns. Its indices are unchanged, and 21 equals 22 for every kept table.
  - The grants note sits in `noBackupFilesDir` and is wrapped in `runCatching`.
  - Migration tests run from Robolectric assets: 1→22, 20 with rows→22, 21→22, and 20 with missing tables.
- **Grant release.** Releasing a document URI never touches the tree grant above it, and a SecurityException is caught.
- **Backups.** 0.5's `profiles`, `selected_profile` and `file_settings` are ignored. Shortcut IDs, palette recents, `Destination.fromRoute` (falls back to Music), the remote-access `LibraryPart` list and WS message types all tolerate removed names.
- **Manifests, XML and reflection.**
  - Every manifest component and every class named in XML exists. The only exceptions (`NextTvFragment`, `PreferencesAndroidAuto`) are upstream and were already missing in 0.5.
  - The PlaybackObserver list matches the proguard keeps.
  - JNI exports match `NativeLab`'s externals.
  - The remote web app's `/icon` names all exist.
- **Notifications and channels.** `RemovedChannels` covers all six removed channel ids.
- **Shortcuts and intents.**
  - The static video shortcut is gone and pinned media shortcuts still play.
  - Audio, playlist and stream intents remain (m3u, m3u8, pls, xspf); the video MIME types and extensions are gone.
  - A shared video file plays as audio.
- **No video decoding.**
  - The global `--no-video` is applied to the shared libVLC instance, which the media library uses too.
  - PlaylistManager forces audio for TYPE_VIDEO.
  - `EpisodeMedia` makes video enclosures TYPE_AUDIO + MEDIA_FORCE_AUDIO.
  - PlaybackService has no video or PiP code left.

Outside this area: versionName/versionCode are still `0.1.0`/4000100, the same as at c7c4f898. Installing over 0.5 still works, but the release story should bump them.

GOOD: The upgrade logic is sound. RetiredWork's flag-per-step design, Migration21To22 (note first, then IF EXISTS drops, all inside the migration), and FolderGrants' holder-based release are correct; extend them rather than rework them. The 20→22 migration test with real rows of every table is thorough.
