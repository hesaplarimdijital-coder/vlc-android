## remove-tvlab (PR #16, 33828a61)
- TV Lab (E12) gone: ui/tvlab, Destination.TvLab, ShortcutId.TvLab, NagIcons.TvLab, SP-03 DvConversion, native bitrate/disktest/dvconvert.
- resources() helper now in library/MediaDetails.kt (next to currentLocale()).
- BackupSettings.LEGACY_TVLAB_PROFILE stays in LEFT_OUT; old backups' "profiles"/"selected_profile" ignored.
- NagmedarActivity.forgetTvLab() deletes files/tvlab + that pref at cold start (IO).
- LabInput has only None/Media; LabRun has no output. mkvtoolnix + dovi_tool still needed by test-host.sh (mediaprobe P7 stream).
## remove-tvlab (PR #70, story/remove-tvlab) — notes for the next story
- One-time upgrade cleanup lives in app.nagmedar.ui.work.RetiredWork.onProcessStart (called from AppSetupDelegate.backgroundInit, main process, IO). Extend it (new flag key) for other removed features' WorkManager names / alarms / channels instead of adding a parallel cleaner.
- Room: remux_job table + RemuxJobDao + RemuxJobEntry/State/Error stay in nagmedar-data, unused. The drop-unused-tables story removes them; also delete files/tvlab/profiles (TV Lab device profiles JSON) then, and consider releasing the persisted URI grants of remux jobs (input/output URIs in remux_job) before dropping the table.
- libdovi kept: native/lab/mediaprobe.c parses the first DV RPU with it (dovi_capi.h). Dropping the video-only parts of mediaprobe would let libdovi + cargo go from build.sh/versions.env/CI.
- FFmpeg config trimmed: no matroska/mp4/mov muxers (ipod selects movenc), no eac3/ac3/pcm encoders, bsf only extract_extradata (find_stream_info uses it).
- build.sh: translate configure needs -DCMAKE_POLICY_VERSION_MINIMUM=3.5 under the runners' CMake 4 (commit "CI: CTranslate2's configure under CMake 4").
- NagIcons.TvLab renamed NagIcons.Lab (also in buildsystem/branding/gen_icons.py); used by the palette's Tools group.
- BackupPart.Profiles gone; old backups' "profiles"/"selected_profile" keys are ignored by the parser.
