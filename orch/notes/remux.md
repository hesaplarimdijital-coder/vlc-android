# remux track — notes from S-1206 / S-1209 (branch story/S-1206, PR #7)

- Native engine: `native/lab/remux.c|h` (+ `remux_cli.c` host CLI `remux run|verify`). Plan is tab-separated text
  (see remux.h): `dv keep|convert81`, `chapters/attachments/metadata 0|1`, `stream <v:N|a:N|s:N> [default=] [forced=] [lang=] [title=]`
  in output order. Refs follow the probe's ordering (S-802: video = first non-cover video, audio/subs in file order).
  `remux_run(input_fd, output_fd, plan, cancel, progress_cb, stats)`; progress_cb returns 0 go / <0 stop / N ms wait.
  Hash scopes: `packets` (other codecs), `nal_units` (H.264/HEVC, framing stripped), `base_layer_nal_units` (DV convert:
  no type 62/63, no nuh_layer_id>0). SHA-256 over BE32 length + bytes per unit. `remux_verify(output_fd, scope, hash,
  packets, dv_profile)` reads the output back; with dv_profile 8 also checks record (profile 8, bl compat 1, no EL) and
  every RPU parses as profile 8 (libdovi guessed_profile) and every packet has one.
  dvconvert.c now exports `dvc_*` helpers (NAL walker, rewrite, hvcC filter, 8.1 record, DTS generator) shared by both.
  JNI: `NativeLab.remux(inFd, outFd, planText, RemuxProgress)`, `remuxVerify(outFd, scope, hash, packets, dvProfile, l)`,
  `remuxCancel()` (own flag; `NativeLab.cancel()` does not touch a remux).
- Kotlin models (nagmedar-lab `RemuxPlan.kt`): `RemuxPlan(streams: List<PlannedStream>, dolbyVision, chapters, attachments,
  metadata)` with `toNative()`, `toJson()/parse()`, `default(tech, convertDolbyVision, label)`; `PlannedStream(source: StreamRef,
  codec, label, language?, title?, isDefault?, forced?, encode?)` — `encode` reserved for S-1207 (a codec to encode the source
  stream into, beside the original); `toNative()` throws while encode != null, RemuxRunner fails such a job → S-1207 must extend
  the native plan (e.g. `encode\t<ref>\t<codec>` lines) and the engine. `RemuxResult`, `RemuxVerification` parse the JSON.
- Room (nagmedar-data, schema 11): `RemuxJobEntry` (schema 11) table `remux_job`, `RemuxJobState` (queued/running/paused/verifying/done/
  failed/cancelled), `RemuxJobError` codes (verify, cancelled, no-tools, open-input, open-output, battery, thermal, "engine:<msg>"),
  `events` lines "<millis>\t<kind>\t<detail>" (RemuxEvent in ui). `RemuxJobDao`: observe/observeInput/next/interrupted/
  insert/update/requestCancel/delete.
- Queue (nagmedar-ui `tvlab/remux/`): `RemuxQueue.get(ctx).enqueue(input, name, size, output, name, plan)` inserts a row and
  `RemuxWorker.ensure()` (unique work, APPEND_OR_REPLACE); `cancel(id)`, `remove(id)`, `observe(inputUri?)`, `resume()` (called
  from NagmedarActivity.onCreate). `RemuxRunner(engine, dao, condition, now, onUpdate)` is the testable state machine;
  `RemuxEngine` interface (NativeRemuxEngine opens SAF fds: input "r", output "rwt"; removeOutput deletes or truncates).
  `RemuxPolicy.decide(DeviceCondition, paused)` → Go/Slow/Pause/Stop (15 % pause, 20 % resume, 5 % stop; moderate heat slow,
  severe pause, critical stop). Worker: foreground type mediaProcessing (API 35+) / dataSync; notification channel
  `nagmedar_remux`, Cancel via `RemuxCancelReceiver`. Resume after process death = restart from scratch (documented).
- UI: `RemuxCard(jobs, onCancel, onRemove)` / `RemuxJobRow`, `RemuxJobView.of(entry)`; TvLabView.jobs; TvLabActions.remux/
  cancelRemux/removeRemux; strings `nag_tvlab_remux_*`; badge string `nag_tvlab_remux_identical` ("Video bit bit aynı").
  Test fakes for later stories: `test/.../tvlab/remux/RemuxFakes.kt` (MemoryRemuxJobDao, FakeRemuxEngine, sample jobs).
- S-1210 batch: insert rows via RemuxQueue.enqueue per file; the single worker drains them in order.
- S-208 history: read `RemuxJobDao.observe()`, parse `result`/`verification` JSON with RemuxResult/RemuxVerification.
- After the PR #7 review: WorkManager 2.10.5 (`workManagerVersion` in build.gradle); `RemuxRunner.drain/run` take an
  `interruption: () -> String?` (null = go on; RemuxEvent.WORKER_STOPPED / FOREGROUND_TIMEOUT / PROCESS_DIED recorded as
  the INTERRUPTED event's detail); the DAO has column writes (`saveProgress`, `saveState`, `saveStart`, `saveResult`) —
  never `update()` a running job's whole row (it would overwrite `cancel_requested`); `RemuxEngine.emptyOutput` truncates
  for a requeue; `RemuxQueue.enqueue` returns `RemuxQueue.REFUSED` (-1) when output == input; `RemuxJobError.SAME_FILE`,
  `WAITING_APP` (queued but no foreground start allowed; `RemuxQueue.resume()` runs from NagmedarActivity.onStart).
  Hash scopes now also `av1_obus`. `dvc_reorder_depth(codec_id, extradata, size)` covers H.264 (VUI) and HEVC.

## 22 Oct — S-1206 rebased onto 242742cd+ (PR #31, branch story/S-1206)
- Room: remux queue is migration **15 → 16** (`MIGRATION_15_16`), schema `16.json` from CI (ci/shots-pr-31). If master
  takes 16 first, renumber (and re-export from CI).
- Plan model unchanged and the contract for S-1205/S-1207/S-1210/S-208: `RemuxPlan(streams: List<PlannedStream>,
  dolbyVision: DolbyVisionMode, chapters, attachments, metadata)`; `toNative()` = native plan text (remux.h; one line per
  item, tab-separated: `dv\tkeep|convert81`, `chapters\t0|1`, `attachments\t0|1`, `metadata\t0|1`, then
  `stream\t<v:N|a:N|s:N>[\tdefault=0|1][\tforced=0|1][\tlang=xxx][\ttitle=...]` in OUTPUT order, refs in the probe's
  order); `toJson()/parse()` is what `remux_job.plan` stores. `PlannedStream.encode` (S-1207) still makes `toNative()`
  throw and the runner fail the job ("the engine copies streams only") — S-1207 extends remux.h's plan (e.g.
  `encode\t<ref>\t<codec>`), `toNative()` and `RemuxPlan.copiesEverything`.
- Shared now: battery/heat = `ui/convert/ConvertPolicy` + `DeviceState` + `AndroidDeviceState` (RemuxPolicy deleted);
  `ui/jobs/JobOutputs` (`OutputState` Empty/Holds/NotAFile/Unreadable, `remove`, `empty`, `open`) used by
  NativeRemuxEngine and S-1303's DeviceTranscodeFiles; `ui/jobs/JobForeground.kt` (`jobForegroundInfo`,
  `ListenableWorker.stoppedForForegroundTime()`) used by the convert, transcode and remux workers. Generic card strings
  are S-1303's `nag_jobs_*`.
- Runner rules: `RemuxJobDao.claim(id, attempts, startedAt, events)` (queued → running, 0 rows = skip), `markWaiting`,
  `requeueInterrupted` (conditional); `leftovers()` = failed/cancelled with owns_output=1 (removal pending; card line
  `nag_tvlab_remux_left`), retried by `RemuxRunner.cleanUp()` at every worker start; `othersUsing(uri, id)` makes an
  output exclusive (enqueue → `REFUSED_IN_USE`, runner → `output-in-use`). `removeOwnedOutput(engine, dao, job)` is the
  one place that deletes a finished job's output. Worker: a process-wide RUN_LOCK, `setForegroundAsync().get()` inside it,
  a notification on the foreground-time stop. New error codes `output-not-file`, `output-in-use`; native refusal
  `output_not_file` (S_ISREG). `DVC_FALLBACK_REORDER` (16) is the DTS depth without an SPS for both dvconvert and remux.
- S-1210 batch: enqueue each file into its own new output (an output used by another job is refused).

## 22 Oct — S-1207 compatibility tracks (branch story/S-1207)
- Native plan line (remux.h): `encode\t<a:N>\t<eac3|ac3|lpcm|pcm_s16le|pcm_s24le>[\tbitrate=kbps][\tchannels=1..8]
  [\tdefault=0|1][\tforced=0|1][\tlang=][\ttitle=]`, written where the line stands (the planner puts it right after the
  original's `stream` line). Several per source allowed; the source may also be left out (only encoded). eac3/ac3: 48 kHz,
  ≤ 5.1 (source layout kept if the encoder takes it, else 5.1(side)/5.0(side)), bitrate default 640/448/256/128 by
  channels, ceiling 1024 (eac3, 6-block frames) / 640 (ac3), dialnorm −31; lpcm: 24 bit unless the source's
  bits_per_raw_sample ≤ 16, source rate, ≤ 7.1 in FFmpeg's default (WAV) order. TrueHD/MLP/DTS get the decoder's own
  `downmix` (5.1(side)/5.0/stereo), AC-3/E-AC-3 sources `drc_scale=0`; otherwise swresample (rematrix_maxval 1 on a
  downmix, triangular dither to s16). Timestamps: origin = first decoded frame, output pts = origin + samples; a source
  gap > 20 ms becomes silence (> 600 s fails), a frame > 20 ms back is dropped; first E-AC-3 packet's priming clamped to
  0, codecpar.initial_padding = 0 (no Matroska CodecDelay). default=1 on an encode line clears inherited default on every
  copied audio stream. Run fails when the new stream has no packets, > 5 % undecodable packets, or its span differs from
  the source's by > 0.5 s. Stats: each stream object of an added track has `"encoded":{source_codec, channels, layout,
  sample_rate, bit_rate, frames_decoded, decode_errors, frames_dropped, padded_seconds, source_seconds, seconds,
  start_offset_seconds}`. Verification unchanged (video hash only). Lab FFmpeg: `--enable-encoder=eac3,ac3,pcm_s16le,pcm_s24le`.
- Kotlin API (nagmedar-lab, for the planner S-1205): `PlannedStream(source, codec, label, language, title, isDefault, forced,
  encode: EncodeSpec?)` — an entry WITH encode is the added track (the original stays its own entry); `EncodeSpec(codec:
  EncodeCodec.EAC3|AC3|LPCM, bitrateKbps: Int?, channels: Int?, makeDefault)`, `spec.describe(audioTech)` = "E-AC-3 5.1"
  (mirrors the native layout choice), `spec.lossless`; `PlannedStream.fidelity` = StreamFidelity.Kept / AddedLossy /
  AddedLossless (labels: ui `StreamFidelity.label` → nag_tvlab_remux_fidelity_*: "Kayıpsız", "Kayıplı (eklenen iz)",
  "Kayıpsız (eklenen iz)"). `RemuxPlan.added`, `copiesEverything` (false with an added track), `toNative()` never throws
  now (makeDefault: the first such added track gets default=1, every other audio entry default=0; bitrate/channels coerced).
  JSON: `"encode":{"codec":"eac3","bitrate_kbps":640,"channels":6,"default":true}`.
  Suggestion: `CompatibilityTracks.suggest(tech, plays)` → List<CompatibilityTrack(source, track, spec)>: E-AC-3
  (640/448/256/128 kbps) beside every track `plays` refuses (commentaries skipped unless main), makeDefault for the main
  (default-flagged, else first) track; `eac3For(track, makeDefault)`, `lpcmFor(makeDefault)`.
  `RemuxPlan.withCompatibilityTracks(tracks) { title }` inserts each after its original. UI helpers
  (ui/tvlab/remux/CompatibilityTrackLabels.kt): `DeviceProfile.compatibilityTracks(tech)` (profile.allowsAudio(audioKey)),
  `compatibilityTitle(res, track)` = "Uyumluluk izi (E-AC-3 5.1)", `RemuxPlan.withCompatibilityTracks(res, profile, tech)`.
  TvLabScreen's "Remux" now queues `RemuxPlan.default(...).withCompatibilityTracks(res, controller.profile, tech)`; the
  planner should start from that and let the user toggle/choose codec. RemuxRunner no longer refuses encode plans.
  `RemuxStreamResult.encoded: EncodedTrackResult?` parses the stats. Card: plurals nag_tvlab_remux_plan_compat in planLine.

## 22 Oct — S-1205 / S-1210 / S-208 (branch story/S-1205, PR #35)
- Planner (ui/tvlab/remux/RemuxPlanner.kt): `PlanDraft.of(tech, report, profile, names, outputName, options)` — Compose-state
  model: `audio`/`subtitles: List<PlanTrack>` (kept, isDefault, forced, language, title; `move(type, from, to)`,
  `setDefault` exclusive per kind), `convertDolbyVision`, chapters/attachments/metadata, `outputName`/`outputFileName`,
  `suggestions: List<PlanSuggestion>` (ConvertDolbyVision, DefaultAudio [sets default + moves first], NoPlayableAudio,
  DropSubtitles) with `applied/apply(s, on)`, `applyOptions(PlanOptions)`; `fidelity(track)`/`videoFidelity` →
  `Fidelity.Lossless | LosslessMetadata | Lossy`; `plan()` writes only what changed. **S-1207 slot**:
  `PlanDraft.compatibility: List<PlannedStream>` — plan() writes each right after its original (also when the original is
  dropped); the dialog's "Ek ses izi" (`ExtraAudio()` in RemuxPlannerDialog.kt) is a disabled button + note: S-1207 should
  fill `compatibility` from `CompatibilityTracks.suggest` in `PlanDraft.of` (or after) and turn the row into a toggle/codec
  choice; TvLabScreen's `remux()` builds the PlanDraft (not RemuxPlan.default any more) — merge S-1207's
  withCompatibilityTracks there into the draft.
- Dialog: `RemuxPlannerDialog(draft, fileName, onQueue, onCancel, confirm?, cancellable)`; the batch uses it with "Tamam".
- Room schema 17 (if not renumbered): `remux_job.position` (DAO `nextPosition`, `savePosition`; `next()` orders by position).
  `RemuxJobError.HELD`, `RemuxEvent.HELD`. Runner has `held`/`waits` lambdas (user pause holds the running job in place;
  night gate only stops new starts); `RemuxQueueControl` (own prefs file) + `RemuxNight` (alarm → `RemuxNightReceiver`,
  `RemuxWorker.ensure(charging = true)` adds a requiresCharging constraint), rescheduled in AlarmRescheduler and
  `RemuxQueue.resume()`. `RemuxQueue.enqueue(..., start = false)` + `start()`, `reorder(ids)`, `hold(on)`.
- Batch: `RemuxBatchController`/`RemuxBatchSession` (RemuxBatch.kt), page RemuxBatchPage.kt; outputs "<name>.remux.mkv"
  created with DocumentsContract in the output tree (existing name → refused, refused-by-queue → its empty doc removed).
- Pages: TvLabContent got `tabs/tab/onTab/counts/page` (TvLabTab Analysis/Batch/Queue/History; no tabs = old layout).
  Queue page RemuxQueuePage.kt, history RemuxHistoryPage.kt, reports RemuxReport.kt (text via strings, JSON with raw
  result/verification). RemuxJobView gained position, inputUri, outputUri, inputSize, createdAt.
- 22 Oct, after S-1207 merged: migration renumbered to **19 → 20** (MIGRATION_19_20, schema 20.json from CI). PlanDraft's
  S-1207 tracks are `added: List<PlanAddedTrack>` (codec/bitrate/stereo/makeDefault editable; `addCompatibility`,
  `removeAdded`, `setAddedDefault`), filled by SuggestionKind.Compatibility from `CompatibilityTracks.suggest`
  (PlanOptions.addCompatibility, batch switch); PlanDraft.of(..., title = compatibilityTitle). History/report show
  `RemuxReport.addedLines` from `RemuxStreamResult.encoded`.

## 22 Oct — fix-tvlab-review (branch story/fix-tvlab-review): the TV Lab review's findings 1–9
- Native: remux.c, dvconvert.c, bitrate.c keep `streams` = nb_streams after open; packets of a stream the demuxer adds
  later are skipped, loops go to `streams`. `dvc_copy_attachment(oc, ist)` (dvconvert.c/h) writes attachments and
  ATTACHED_PIC covers (extradata = attached_pic, filename/mimetype kept, "cover.jpg" etc. when missing) — remux and
  dvconvert's MKV use it. remux_run fsyncs the output after the trailer; remux_verify fsyncs + POSIX_FADV_DONTNEED
  first (1.6 GB on the host: 18.1 → 22.6 s). build.sh host also builds remux-asan, bitrate-asan, dvconvert-asan
  (ASan+UBSan, -fno-sanitize-recover) when cc has them; test-host.sh: `sanitized <tool>`, `failed_run`; late-stream
  TS made by rewriting the PMT (python in test-host.sh), covers test with mkvmerge + mkvextract.
- Night run: no WorkManager constraint ever (RemuxWorker.ensure(context) only; RemuxWorker.resume(context) for
  RemuxQueue.resume: REPLACE when nothing RUNNING and something ENQUEUED/BLOCKED, else KEEP). QueueControl
  .waitsForCharger(now) = nightCharging && nightAt in 1..now: fire() clears the night only without charging; the
  runner's `charger: NightCharger` (worker: ChargerWatch, ACTION_POWER_CONNECTED + 5 s polls) waits before the first
  job and calls begin() (= clearNight) once charging. Worker asks setForeground before cleanUp/recoverInterrupted.
  Strings nag_tvlab_queue_charger(_short). With nothing to run the runner ends such a night run (endNight, from
  drain and from the worker's empty queue); RemuxQueue.resume fires it again only when nothing waits (no stack of
  workers). Trade-off kept: the wait on battery holds the FGS + WorkManager's wakelock and eats Android 15's 6 h.
- Ownership: RemuxQueue.enqueue sets ownsOutput when the output is Empty; never-written (attempts == 0) owned outputs
  are removed only while Empty (removeOwnedOutput; runner finish(written = false)); Holds → given up.
- TvLabController(engine, profiles, scope, io = Dispatchers.IO, now): describe() and the profile store on io; the
  built-ins until loaded; saved(id) from state; cancel() only when engine.available; reloadProfiles (suspend,
  492fcc23) goes through the same load. PlanDraft remembers defaultsBeforeAdded and restores them (RemuxPlannerTest).
- Review finding 4 (Snapshot.global around TvLabSession/RemuxBatchSession) was NOT kept: NagmedarApp asks
  TvLabSession.get inside its composition and reads bitrateStep there, so a state made in Snapshot.global (newer
  than that composition's snapshot) is the launch crash c9847d41 removed. Both are made in the caller's snapshot,
  like RadioCenter; the branch has the commit and its revert (dropping a commit was refused by the permission
  classifier). Never wrap a holder that a composition creates in Snapshot.global.
- Local stub compile for the remux logic: agents/fix-tvlab-review/scripts/ktmain.sh, kttest.sh, ktlab.sh,
  ktworker2.sh (RemuxWorker with NotificationIds).
