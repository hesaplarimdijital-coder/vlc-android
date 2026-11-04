
## S-1105 + S-1106 (PR #3, story/S-1105)
- Radio code is in `nagmedar-ui/.../ui/radio`; pure ICY mapper `RadioMetadata` is in `application/tools` (package `app.nagmedar.playback`) because `vlc-android` cannot see `nagmedar-ui`.
- Extension points: `RadioNowPlaying.flow()` (current ICY title/station as a Flow, S-1107 records from it), `RadioSession.playing`, `RadioPlayer.resolve` (stream URL resolution, S-207), `StationSink`.
- Room DB is 8 (`MIGRATION_7_8`); renumber if master takes 8. `RadioDao.keep()` returns the row id explicitly (@Upsert returns -1 on update).
- Radio strings sit in a block before "Placeholders" in both strings.xml; CHANGELOG has "0.4.0 > Radyo".
- Tests: Compose/Robolectric radio tests need the keyboard input mode for focus (`inputModes.requestInputMode(Keyboard)`), see RadioPageTest.
- GitHub Actions was stalled for ~8h on 2026-10-14/15; PR runs were not created until it recovered.

NOTES FOR THE NEXT STORY (S-1107 + S-207, PR #11)
- Recording lives in app.nagmedar.ui.radio.record. Pure engine (IcyStream, AudioFraming, OggFraming, TrackNames, Id3Tag, StoragePolicy, RecordingSchedule, TrackRecorder, RecordingSession) has no Android imports and is unit-tested; Android glue is RecordingService (dataSync FGS), RecordingAlarms, RecordingStore, RadioRecordings (singleton, controller() for the UI).
- UI: RecordingsController + RecordingsViews + ScheduleEditor; RadioTab.Recordings is the last tab; RadioTabs scrolls to the end when it is selected. Tests use recordingsLive=false (autoRefresh off), an infinite delay loop hangs waitForIdle.
- DB is now version 15 (MIGRATION_14_15, radio_recording and radio_schedule tables; 13 is S-1008, 14 is S-1303). Version numbers collide between parallel stories: rebase, renumber, take schema JSON from CI, never hand-write it.
- Shortcuts: RadioRecord Alt+R, RadioRecordings Alt+Shift+R (group Tools). S-607 Shift+R libVLC recorder is separate.
- Not done: HLS, resume of an in-progress recording after reboot, catch-up of missed runs except when the tab opens, backup of recordings and schedules, a Share icon (share is in the track menu only). Ogg is split only on chained streams.
- Sizes use 1024 units (RecordingText.size). Dialog windows are not captured by screenshot tests: render DialogFrame{...} in tree.
- K2 reports compile errors in batches and there is no local Android SDK; CI is the compile check.

## fix-radio (PR #22, story/fix-radio): the S-1107/S-207 leftovers
- HLS: record/HlsPlaylist.kt (parser, variant choice), TransportStream.kt (TS audio extraction), HlsReader.kt (poll loop, pure + unit-testable with a fake HlsClient), OkHttpHlsClient in OkHttpStreamOpener.kt. The opener returns OpenedStream.playlist (HlsStart) for HLS; RecordingSession.pumpHls feeds a TrackRecorder (splits=false, one file). New AudioFormat Ts/M4a/Mp4 (`taggable` = ID3 only for Mp3/Aac). UnsupportedStream.Encrypted added.
- Runs: Room 16 (radio_schedule_run, radio_schedule.checked_at); RunStatus/ScheduleRun/ScheduleRunLog in RecordingModels; RecordingStore implements ScheduleRunLog; RecordingCenter.startRun(station, window, scheduleId): Boolean (false = service refused; run not marked started). RecordingAlarms: fire / catchUp / onProcessStart / requestCatchUp / watch. Boot cannot start a dataSync FGS on Android 15, so boot/process start sets an exact catch-up alarm (3 s) that starts it. RecordingService re-arms a watchdog alarm (4 min) every minute while a scheduled run records. App hook: AppSetupDelegate.backgroundInit -> RecordingAlarms.onProcessStart.
- Known limit: a track being written when the process is killed stays as a file in the folder but is not listed (rows are added when a track is finished).
- Backup: BackupPart.RadioSchedules; recording settings travel in the generic settings part (folder excluded).
- Sizes: RecordingText.size = "N B" or library.sizeLabel (powers of 1000); StorageLimits.MB/GB are decimal now.
- NagIcons.Share added through buildsystem/branding/gen_icons.py (run it, do not hand-edit NagIcons.kt).
- Local check of the HLS logic: scratchpad/agents/story-fix-radio/t/Harness.kt (kc3.sh with stub okhttp).
