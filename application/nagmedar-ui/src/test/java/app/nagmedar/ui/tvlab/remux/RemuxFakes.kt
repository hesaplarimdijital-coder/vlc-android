package app.nagmedar.ui.tvlab.remux

import android.net.Uri
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.PlannedStream
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.RemuxResult
import app.nagmedar.lab.RemuxVerification
import app.nagmedar.lab.StreamRef
import app.nagmedar.lab.StreamType
import app.nagmedar.ui.jobs.OutputState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * The remux queue's stand-ins (S-1206): a queue kept in memory, an engine that answers as told,
 * and the sample jobs the screenshots show.
 */

/** The sample film's plan: the video, TrueHD, DTS-HD and the commentary, PGS and SRT, profile 7 converted. */
val samplePlan = RemuxPlan(
    streams = listOf(
        PlannedStream(StreamRef(StreamType.Video, 0), "hevc", "HEVC Main 10 · 3840×2160 · 23,976 fps"),
        PlannedStream(StreamRef(StreamType.Audio, 0), "truehd", "Dolby TrueHD + Dolby Atmos 7.1 · İngilizce"),
        PlannedStream(StreamRef(StreamType.Audio, 1), "dts", "DTS-HD MA 5.1 · Türkçe"),
        PlannedStream(StreamRef(StreamType.Audio, 2), "ac3", "AC-3 2.0 · Türkçe · Yorum"),
        PlannedStream(StreamRef(StreamType.Subtitle, 0), "hdmv_pgs_subtitle", "PGS · Türkçe"),
        PlannedStream(StreamRef(StreamType.Subtitle, 1), "hdmv_pgs_subtitle", "PGS · İngilizce"),
        PlannedStream(StreamRef(StreamType.Subtitle, 2), "subrip", "SRT · Türkçe"),
    ),
    dolbyVision = DolbyVisionMode.ConvertToProfile81,
)

const val SAMPLE_INPUT = "content://com.android.externalstorage.documents/document/1234-ABCD%3AFilmler%2FKuzey_Isigi_2160p_UHD_Remux.mkv"
const val SAMPLE_OUTPUT = "content://com.android.externalstorage.documents/document/1234-ABCD%3AFilmler%2FKuzey_Isigi_2160p_UHD_Remux%20(remux).mkv"
const val SAMPLE_HASH = "fad1fd51acd1907c42b579fb6bce1b59f26cc7197a6741296332f0a55cde091c"

/** The engine's stats of the sample film: 189 720 frames, the enhancement layer gone. */
val sampleResultJson = """{"error":"","cancelled":false,"streams":[{"ref":"v:0","source_index":0,"output_index":0,"codec":"hevc","packets":189720,"bytes":52100000000}],""" +
    """"attachments":0,"chapters":32,"video_codec":"hevc","video_packets":189720,"video_units":1138320,"hash_scope":"base_layer_nal_units",""" +
    """"video_hash":"$SAMPLE_HASH","source_seconds":7905.0,"media_seconds":7904.9,"bytes_in":58400000000,"bytes_out":54100000000,""" +
    """"wall_seconds":1180.0,"paused_seconds":0.0,"peak_rss_kb":41000,"dv":{"input_profile":7,"input_el_type":"MEL","output_profile":8,""" +
    """"rpus_converted":189720,"rpus_failed":0,"el_nals_dropped":189720,"el_bytes_dropped":4300000000,"first_rpu_error":""}}"""

val sampleVerificationJson = """{"ok":true,"error":"","cancelled":false,"hash_scope":"base_layer_nal_units","expected_hash":"$SAMPLE_HASH",""" +
    """"hash":"$SAMPLE_HASH","expected_packets":189720,"packets":189720,"units":1138320,"duration_seconds":7905.0,"dv_expected_profile":8,""" +
    """"dv_profile":8,"dv_compatibility":1,"dv_el_present":0,"rpus":189720,"rpus_bad":0,"packets_without_rpu":0}"""

val sampleBadVerificationJson = sampleVerificationJson.replace("\"ok\":true", "\"ok\":false").replace("\"hash\":\"$SAMPLE_HASH\"", "\"hash\":\"0f3a9c11d2e4b6a8c0e2f4a6b8d0c2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6\"")

fun sampleJob(
    id: Long = 1,
    state: String = RemuxJobState.QUEUED,
    progress: Float = 0f,
    mediaSeconds: Double = 0.0,
    bytesOut: Long = 0,
    error: String? = null,
    plan: RemuxPlan = samplePlan,
    events: String = "",
    attempts: Int = 0,
    startedAt: Long? = null,
    finishedAt: Long? = null,
    result: String? = null,
    verification: String? = null,
    videoHash: String? = null,
    ownsOutput: Boolean = false,
) = RemuxJobEntry(
    id = id,
    inputUri = SAMPLE_INPUT,
    inputName = "Kuzey_Isigi_2160p_UHD_Remux.mkv",
    inputSize = 58_400_000_000,
    outputUri = SAMPLE_OUTPUT,
    outputName = "Kuzey_Isigi_2160p_UHD_Remux (remux).mkv",
    plan = plan.toJson(),
    state = state,
    progress = progress,
    mediaSeconds = mediaSeconds,
    totalSeconds = 7905.0,
    bytesOut = bytesOut,
    attempts = attempts,
    createdAt = 1_791_837_600_000L + id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    result = result,
    verification = verification,
    videoHash = videoHash,
    error = error,
    events = events,
    ownsOutput = ownsOutput,
)

/** The sample film remuxed at 42 %, 2,1 GB written. */
fun sampleRunningJob() = RemuxJobView.of(
    sampleJob(id = 3, state = RemuxJobState.RUNNING, progress = 0.42f, mediaSeconds = 3320.0, bytesOut = 2_100_000_000, attempts = 1, startedAt = 1_791_837_600_000L),
)

/** Done: verified, 19 minutes, 54,1 GB. */
fun sampleDoneJob() = RemuxJobView.of(
    sampleJob(
        id = 2,
        state = RemuxJobState.DONE,
        progress = 1f,
        mediaSeconds = 7904.9,
        bytesOut = 54_100_000_000,
        attempts = 1,
        startedAt = 1_791_837_600_000L,
        finishedAt = 1_791_837_600_000L + 1_180_000L,
        result = sampleResultJson,
        verification = sampleVerificationJson,
        videoHash = SAMPLE_HASH,
    ),
)

/** Failed: the output read back differed. */
fun sampleFailedJob() = RemuxJobView.of(
    sampleJob(
        id = 1,
        state = RemuxJobState.FAILED,
        progress = 1f,
        attempts = 2,
        startedAt = 1_791_830_000_000L,
        finishedAt = 1_791_830_000_000L + 1_200_000L,
        error = app.nagmedar.data.RemuxJobError.VERIFY,
        result = sampleResultJson,
        verification = sampleBadVerificationJson,
        events = RemuxEvent.line(1_791_829_000_000L, RemuxEvent.STARTED, "1") + RemuxEvent.line(1_791_829_500_000L, RemuxEvent.INTERRUPTED) +
            RemuxEvent.line(1_791_830_000_000L, RemuxEvent.RESTARTED, "2"),
    ),
)

/** Paused for the battery, half way. */
fun samplePausedJob() = RemuxJobView.of(
    sampleJob(id = 4, state = RemuxJobState.PAUSED, progress = 0.5f, mediaSeconds = 3952.0, bytesOut = 27_000_000_000, attempts = 1, startedAt = 1_791_837_600_000L, error = app.nagmedar.data.RemuxJobError.BATTERY),
)

/** The queue in memory: the rows by id, observed through a flow. */
class MemoryRemuxJobDao : RemuxJobDao {
    private val rows = MutableStateFlow<Map<Long, RemuxJobEntry>>(emptyMap())
    private var nextId = 1L
    val updates = mutableListOf<RemuxJobEntry>()

    private fun sorted(desc: Boolean) = rows.value.values.sortedWith(compareBy<RemuxJobEntry> { it.createdAt }.thenBy { it.id }).let { if (desc) it.reversed() else it }

    override fun observe(): Flow<List<RemuxJobEntry>> = rows.map { sorted(desc = true) }

    override fun observeInput(inputUri: String): Flow<List<RemuxJobEntry>> = rows.map { sorted(desc = true).filter { it.inputUri == inputUri } }

    override fun get(id: Long): RemuxJobEntry? = rows.value[id]

    override fun all(): List<RemuxJobEntry> = sorted(desc = true)

    override fun next(): RemuxJobEntry? = sorted(desc = false).firstOrNull { it.state == RemuxJobState.QUEUED }

    override fun interrupted(): List<RemuxJobEntry> =
        sorted(desc = false).filter { it.state in setOf(RemuxJobState.RUNNING, RemuxJobState.PAUSED, RemuxJobState.VERIFYING) }

    override fun insert(entry: RemuxJobEntry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        nextId = maxOf(nextId, id + 1)
        rows.value = rows.value + (id to entry.copy(id = id))
        return id
    }

    override fun update(entry: RemuxJobEntry) {
        rows.value = rows.value + (entry.id to entry)
        updates += entry
    }

    private fun change(id: Long, block: (RemuxJobEntry) -> RemuxJobEntry) {
        rows.value[id]?.let { row -> block(row).also { rows.value = rows.value + (id to it); updates += it } }
    }

    override fun saveProgress(id: Long, progress: Float, mediaSeconds: Double, totalSeconds: Double, bytesOut: Long) =
        change(id) { it.copy(progress = progress, mediaSeconds = mediaSeconds, totalSeconds = totalSeconds, bytesOut = bytesOut) }

    override fun saveState(id: Long, state: String, error: String?, events: String) = change(id) { it.copy(state = state, error = error, events = events) }

    override fun claim(id: Long, attempts: Int, startedAt: Long, events: String): Int {
        if (rows.value[id]?.state != RemuxJobState.QUEUED) return 0
        change(id) { it.copy(state = RemuxJobState.RUNNING, error = null, progress = 0f, attempts = attempts, startedAt = startedAt, finishedAt = null, events = events) }
        return 1
    }

    override fun markWaiting(id: Long, error: String): Int {
        if (rows.value[id]?.state != RemuxJobState.QUEUED) return 0
        change(id) { it.copy(error = error) }
        return 1
    }

    override fun requeueInterrupted(id: Long, events: String): Int {
        if (rows.value[id]?.state !in setOf(RemuxJobState.RUNNING, RemuxJobState.PAUSED, RemuxJobState.VERIFYING)) return 0
        change(id) { it.copy(state = RemuxJobState.QUEUED, progress = 0f, mediaSeconds = 0.0, bytesOut = 0, error = null, events = events) }
        return 1
    }

    override fun leftovers(): List<RemuxJobEntry> =
        sorted(desc = false).filter { it.ownsOutput && (it.state == RemuxJobState.FAILED || it.state == RemuxJobState.CANCELLED) }

    override fun othersUsing(outputUri: String, id: Long): Int = rows.value.values.count {
        it.outputUri == outputUri && it.id != id && (it.state !in RemuxJobState.FINAL || it.ownsOutput)
    }

    override fun saveResult(id: Long, state: String, mediaSeconds: Double, totalSeconds: Double, bytesOut: Long, result: String) =
        change(id) { it.copy(state = state, progress = 0f, mediaSeconds = mediaSeconds, totalSeconds = totalSeconds, bytesOut = bytesOut, result = result) }

    override fun saveOwnsOutput(id: Long, owns: Boolean) = change(id) { it.copy(ownsOutput = owns) }

    override fun cancelQueued(id: Long, error: String, at: Long): Int {
        val row = rows.value[id] ?: return 0
        if (row.state != RemuxJobState.QUEUED) return 0
        change(id) { it.copy(state = RemuxJobState.CANCELLED, error = error, finishedAt = at, cancelRequested = false) }
        return 1
    }

    override fun requestCancel(id: Long) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(cancelRequested = true)) }
    }

    override fun delete(id: Long) {
        rows.value = rows.value - id
    }
}

/**
 * An engine that answers as told: [resultJson] after [steps] steps of the remux (each a step of
 * [stepSeconds] of media, the steer answered as the policy says), [verificationJson] after as
 * many steps of the verification; [onStep] runs at every step, to change the device or cancel.
 */
class FakeRemuxEngine(
    override val available: Boolean = true,
    var resultJson: String = sampleResultJson,
    var verificationJson: String = sampleVerificationJson,
    var steps: Int = 4,
    var stepSeconds: Double = 100.0,
    var openInput: Boolean = true,
    var openOutput: Boolean = true,
) : RemuxEngine {
    val calls = mutableListOf<String>()
    val waits = mutableListOf<Int>()
    var onStep: (String, Int) -> Unit = { _, _ -> }
    var removed = mutableListOf<Uri>()
    var emptied = mutableListOf<Uri>()
    /** What [outputState] answers: empty, holding something, or (null) not readable. */
    var outputsEmpty: Boolean? = true
    /** What the engine refuses with, before writing ("same_file", "output_not_empty"). */
    var refused = ""

    override fun remux(input: Uri, output: Uri, plan: RemuxPlan, steer: (RemuxProgress) -> Int): RemuxResult? {
        calls += "remux:" + plan.toNative().lines().count { it.startsWith("stream") }
        if (!openInput) throw RemuxOpenException(output = false)
        if (!openOutput) throw RemuxOpenException(output = true)
        if (refused.isNotEmpty()) return RemuxResult.parse("""{"error":"refused","refused":"$refused","cancelled":false}""")
        val cancelled = !drive("remux", steer)
        return RemuxResult.parse(if (cancelled) """{"error":"cancelled","cancelled":true}""" else resultJson)
    }

    override fun verify(output: Uri, result: RemuxResult, dvProfile: Int, steer: (RemuxProgress) -> Int): RemuxVerification? {
        calls += "verify:${result.hashScope}:${result.videoPackets}:$dvProfile"
        val cancelled = !drive("verify", steer)
        return RemuxVerification.parse(if (cancelled) """{"ok":false,"error":"cancelled","cancelled":true}""" else verificationJson)
    }

    /** The steps of a phase, then "<phase>-done"; false when the steer said stop. */
    private fun drive(phase: String, steer: (RemuxProgress) -> Int): Boolean {
        var done = 0.0
        repeat(steps) { i ->
            onStep(phase, i)
            var answer = steer(RemuxProgress(done, steps * stepSeconds, (done * 1_000_000).toLong()))
            while (answer > 0) {
                waits += answer
                onStep("$phase-wait", i)
                answer = steer(RemuxProgress(done, steps * stepSeconds, (done * 1_000_000).toLong()))
            }
            if (answer < 0) return false
            done += stepSeconds
        }
        onStep("$phase-done", steps)
        return true
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun removeOutput(output: Uri): Boolean {
        removed += output
        return true
    }

    override fun emptyOutput(output: Uri): Boolean {
        emptied += output
        return true
    }

    override fun outputState(output: Uri): OutputState = when (outputsEmpty) {
        true -> OutputState.Empty
        false -> OutputState.Holds
        null -> OutputState.Unreadable
    }
}
