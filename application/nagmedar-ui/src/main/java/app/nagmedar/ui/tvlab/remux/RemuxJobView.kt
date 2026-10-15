package app.nagmedar.ui.tvlab.remux

import androidx.compose.runtime.Immutable
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobKind
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.RemuxResult
import app.nagmedar.lab.RemuxVerification
import app.nagmedar.ui.convert.ConvertProfile

/** A job as the card shows it (S-1206): the row with its plan, stats and verdict read. */
@Immutable
data class RemuxJobView(
    val id: Long,
    val inputName: String,
    val outputName: String,
    val state: String,
    val progress: Float,
    val mediaSeconds: Double,
    val totalSeconds: Double,
    val bytesOut: Long,
    val attempts: Int,
    val startedAt: Long?,
    val finishedAt: Long?,
    val error: String?,
    val videoHash: String?,
    val plan: RemuxPlan?,
    val result: RemuxResult?,
    val verification: RemuxVerification?,
    val events: List<RemuxEvent.Line>,
    /** S-1303: a [RemuxJobKind]; a conversion has its [profile] and no [plan]. */
    val kind: String = RemuxJobKind.REMUX,
    val profile: ConvertProfile? = null,
) {
    val active: Boolean get() = state !in RemuxJobState.FINAL

    /** S-1303: a conversion by libVLC's stream output, not a remux. */
    val converts: Boolean get() = kind == RemuxJobKind.CONVERT
    val convertsDolbyVision: Boolean get() = plan?.dolbyVision == DolbyVisionMode.ConvertToProfile81

    /** Done with the output read back and found the same: the badge. */
    val verified: Boolean get() = state == RemuxJobState.DONE && verification?.ok == true && !videoHash.isNullOrEmpty()

    /** Why it waits: the battery or the heat. */
    val pauseReason: String? get() = if (state == RemuxJobState.PAUSED) error else null

    /** Whether an interruption made it start over. */
    val restarted: Boolean get() = events.any { it.kind == RemuxEvent.INTERRUPTED }

    /** Why it was last interrupted ([RemuxEvent.WORKER_STOPPED] …), or null. */
    val interruption: String? get() = events.lastOrNull { it.kind == RemuxEvent.INTERRUPTED }?.detail

    /** Queued, waiting for the app to show so that the job may run in the foreground. */
    val waitingForApp: Boolean get() = state == RemuxJobState.QUEUED && error == RemuxJobError.WAITING_APP

    /** Whether the heat slowed it on the way. */
    val slowed: Boolean get() = events.any { it.kind == RemuxEvent.SLOWED }

    companion object {
        fun of(entry: RemuxJobEntry): RemuxJobView = RemuxJobView(
            id = entry.id,
            inputName = entry.inputName,
            outputName = entry.outputName,
            state = entry.state,
            progress = entry.progress,
            mediaSeconds = entry.mediaSeconds,
            totalSeconds = entry.totalSeconds,
            bytesOut = entry.bytesOut,
            attempts = entry.attempts,
            startedAt = entry.startedAt,
            finishedAt = entry.finishedAt,
            error = entry.error,
            videoHash = entry.videoHash,
            plan = if (entry.kind == RemuxJobKind.CONVERT) null else RemuxPlan.parse(entry.plan),
            result = entry.result?.let(RemuxResult::parse),
            verification = entry.verification?.let(RemuxVerification::parse),
            events = RemuxEvent.parse(entry.events),
            kind = entry.kind,
            profile = if (entry.kind == RemuxJobKind.CONVERT) ConvertProfile.parse(entry.plan) else null,
        )
    }
}
