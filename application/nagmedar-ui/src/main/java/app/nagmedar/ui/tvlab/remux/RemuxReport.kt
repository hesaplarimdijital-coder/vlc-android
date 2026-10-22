package app.nagmedar.ui.tvlab.remux

import android.content.res.Resources
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.PlannedStream
import app.nagmedar.lab.RemuxVerification
import app.nagmedar.ui.R
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.tvlab.elapsedLabel
import app.nagmedar.ui.tvlab.megabytesLabel
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * A finished job's report (S-208): its files, times, sizes and speed, its plan, the
 * verification (what was hashed, the hash, the packets compared, the Dolby Vision checks), what
 * happened on the way and why it failed; as lines for the screen and the text report, or as JSON.
 */
object RemuxReport {
    /** How long it ran: from its last start to its end. */
    fun durationMs(job: RemuxJobView): Long? = job.startedAt?.let { start -> job.finishedAt?.let { end -> (end - start).coerceAtLeast(0) } }

    /** The engine's time without its pauses, seconds; the job's own times when the engine's are not known. */
    private fun workSeconds(job: RemuxJobView): Double? {
        val result = job.result
        if (result != null && result.wallSeconds > 0) return (result.wallSeconds - result.pausedSeconds).coerceAtLeast(0.0).takeIf { it > 0 }
        return durationMs(job)?.let { it / 1000.0 }?.takeIf { it > 0 }
    }

    /** "312 MB/s · gerçek zamanın 24 katı": the source read per second, and how much faster than playing it. */
    fun speed(res: Resources, job: RemuxJobView, locale: Locale): String? {
        val seconds = workSeconds(job) ?: return null
        val read = job.result?.bytesIn?.takeIf { it > 0 } ?: job.inputSize.takeIf { it > 0 } ?: return null
        val rate = megabytesLabel(res, read / seconds / 1e6, locale)
        val media = job.result?.mediaSeconds?.takeIf { it > 0 } ?: return rate
        val factor = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (media / seconds < 10) 1 else 0 }.format(media / seconds)
        return res.getString(R.string.nag_tvlab_history_speed, rate, factor)
    }

    /** The line under a job in the history: when it ended, how long it took, what it wrote, how fast. */
    fun line(res: Resources, job: RemuxJobView, locale: Locale): String = listOfNotNull(
        job.finishedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it)) },
        durationMs(job)?.let { elapsedLabel(res, it) },
        job.bytesOut.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
        speed(res, job, locale),
    ).joinToString(" · ")

    /** A stream of the plan: "a:1 · DTS-HD MA 5.1 · Türkçe", with what the plan sets. */
    fun streamLine(res: Resources, stream: PlannedStream): String = listOfNotNull(
        stream.source.toString(),
        stream.label.ifEmpty { stream.codec },
        stream.language?.let { res.getString(R.string.nag_tvlab_history_set_language, it) },
        stream.title?.let { res.getString(R.string.nag_tvlab_history_set_title, it) },
        stream.isDefault?.let { res.getString(if (it) R.string.nag_tvlab_history_set_default else R.string.nag_tvlab_history_unset_default) },
        stream.forced?.let { res.getString(if (it) R.string.nag_tvlab_history_set_forced else R.string.nag_tvlab_history_unset_forced) },
    ).joinToString(" · ")

    /** The verification in lines: the verdict, what was hashed, the packets, the Dolby Vision checks. */
    fun verificationLines(res: Resources, verification: RemuxVerification, locale: Locale): List<String> {
        val number = NumberFormat.getIntegerInstance(locale)
        val lines = mutableListOf(
            res.getString(if (verification.ok) R.string.nag_tvlab_history_v_ok else R.string.nag_tvlab_history_v_differs),
            res.getString(R.string.nag_tvlab_history_v_scope, scopeLabel(res, verification.hashScope)),
            res.getString(R.string.nag_tvlab_history_v_packets, number.format(verification.packets), number.format(verification.expectedPackets)),
        )
        if (verification.units > 0) lines += res.getString(R.string.nag_tvlab_history_v_units, number.format(verification.units))
        if (verification.dvExpectedProfile > 0) {
            lines += res.getString(
                R.string.nag_tvlab_history_v_dv,
                verification.dvProfile,
                verification.dvCompatibility,
                res.getString(if (verification.dvEnhancementPresent) R.string.nag_tvlab_history_v_el_present else R.string.nag_tvlab_history_v_el_absent),
            )
            lines += res.getString(
                R.string.nag_tvlab_history_v_rpus,
                number.format(verification.rpus),
                number.format(verification.rpusBad),
                number.format(verification.packetsWithoutRpu),
            )
        }
        if (verification.error.isNotEmpty()) lines += res.getString(R.string.nag_tvlab_remux_e_engine, verification.error)
        return lines
    }

    /** What an event of the job's way was, in words. */
    fun eventText(res: Resources, line: RemuxEvent.Line): String = when (line.kind) {
        RemuxEvent.STARTED -> res.getString(R.string.nag_tvlab_history_e_started, line.detail)
        RemuxEvent.RESTARTED -> res.getString(R.string.nag_tvlab_history_e_restarted, line.detail)
        RemuxEvent.PAUSED -> res.getString(if (line.detail == RemuxJobError.BATTERY) R.string.nag_tvlab_history_e_paused_battery else R.string.nag_tvlab_history_e_paused_thermal)
        RemuxEvent.HELD -> res.getString(R.string.nag_tvlab_queue_held)
        RemuxEvent.RESUMED -> res.getString(R.string.nag_tvlab_history_e_resumed)
        RemuxEvent.SLOWED -> res.getString(R.string.nag_tvlab_history_e_slowed)
        RemuxEvent.STOPPED -> res.getString(if (line.detail == RemuxJobError.BATTERY) R.string.nag_tvlab_remux_e_battery else R.string.nag_tvlab_remux_e_thermal)
        RemuxEvent.INTERRUPTED -> res.getString(
            when (line.detail) {
                RemuxEvent.FOREGROUND_TIMEOUT -> R.string.nag_tvlab_history_e_timeout
                RemuxEvent.PROCESS_DIED -> R.string.nag_tvlab_history_e_died
                RemuxEvent.WORKER_STOPPED -> R.string.nag_tvlab_history_e_stopped
                else -> R.string.nag_tvlab_history_e_interrupted
            },
        )
        else -> line.kind
    }

    /** The report as text, in the interface's language: what the share sheet sends and the .txt file holds. */
    fun text(res: Resources, job: RemuxJobView, locale: Locale): String {
        val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
        val times = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
        val out = StringBuilder()
        fun line(text: String) = out.append(text).append('\n')
        line(res.getString(R.string.nag_tvlab_history_report_title))
        line(res.getString(R.string.nag_tvlab_history_input, listOfNotNull(job.inputName, job.inputSize.takeIf { it > 0 }?.let { sizeLabel(it, locale) }).joinToString(" · ")))
        line(res.getString(R.string.nag_tvlab_history_output, job.outputName))
        line(res.getString(R.string.nag_tvlab_history_state, listOfNotNull(stateLabel(res, job), res.getString(R.string.nag_tvlab_remux_identical).takeIf { job.verified }).joinToString(" · ")))
        job.startedAt?.let { line(res.getString(R.string.nag_tvlab_history_started, dates.format(Date(it)))) }
        job.finishedAt?.let { line(res.getString(R.string.nag_tvlab_history_finished, dates.format(Date(it)))) }
        durationMs(job)?.let { line(res.getString(R.string.nag_tvlab_history_duration, elapsedLabel(res, it))) }
        line(res.getString(R.string.nag_tvlab_history_attempts, job.attempts))
        job.result?.let { result ->
            line(res.getString(R.string.nag_tvlab_history_bytes, sizeLabel(result.bytesIn, locale), sizeLabel(result.bytesOut, locale)))
        }
        speed(res, job, locale)?.let { line(res.getString(R.string.nag_tvlab_history_rate, it)) }
        job.plan?.let { plan ->
            line(res.getString(R.string.nag_tvlab_history_plan, planLine(res, job)))
            plan.streams.forEach { line("  " + streamLine(res, it)) }
        }
        job.verification?.let { verification ->
            line(res.getString(R.string.nag_tvlab_history_verification))
            line("  SHA-256 " + verification.hash)
            if (!verification.ok && verification.expectedHash.isNotEmpty()) line("  " + res.getString(R.string.nag_tvlab_history_v_expected, verification.expectedHash))
            verificationLines(res, verification, locale).forEach { line("  $it") }
        }
        if (job.events.isNotEmpty()) {
            line(res.getString(R.string.nag_tvlab_history_events))
            job.events.forEach { event -> line("  " + times.format(Date(event.at)) + "  " + eventText(res, event)) }
        }
        if (job.state != RemuxJobState.DONE && !job.error.isNullOrEmpty()) line(res.getString(R.string.nag_tvlab_history_error, errorText(res, job)))
        return out.toString()
    }

    /** The report as JSON: the job's row with the engine's stats and the verdict as they came. */
    fun json(job: RemuxJobView): String = JSONObject().apply {
        put("version", 1)
        put("input", JSONObject().put("name", job.inputName).put("size", job.inputSize))
        put("output", JSONObject().put("name", job.outputName))
        put("state", job.state)
        job.error?.let { put("error", it) }
        put("created_at", job.createdAt)
        job.startedAt?.let { put("started_at", it) }
        job.finishedAt?.let { put("finished_at", it) }
        put("attempts", job.attempts)
        put("bytes_out", job.bytesOut)
        job.videoHash?.let { put("video_hash", it) }
        put("verified", job.verified)
        job.plan?.let { put("plan", JSONObject(it.toJson())) }
        job.result?.json?.takeIf { it.isNotEmpty() }?.let { runCatching { put("result", JSONObject(it)) } }
        job.verification?.json?.takeIf { it.isNotEmpty() }?.let { runCatching { put("verification", JSONObject(it)) } }
        put(
            "events",
            JSONArray().apply {
                job.events.forEach { put(JSONObject().put("at", it.at).put("kind", it.kind).put("detail", it.detail)) }
            },
        )
    }.toString(2)

    /** The report's file name: the output's, with ".rapor.txt" or ".json". */
    fun fileName(job: RemuxJobView, extension: String): String = job.outputName.substringBeforeLast('.') + "." + extension
}
