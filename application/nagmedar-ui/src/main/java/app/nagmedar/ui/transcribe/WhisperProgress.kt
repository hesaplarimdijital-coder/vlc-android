package app.nagmedar.ui.transcribe

import android.content.Context
import app.nagmedar.ui.podcast.EpisodeTranscripts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * How far a Whisper job got: the passages of the episode whisper has finished (it transcribes five minutes at a time and
 * hands each piece's lines out once it is done), with the [model] and the [language] it was asked for and the one it
 * [heard]. A job stopped before its end (Android stopped the app, or a job that ran without the foreground) is taken up
 * again after its last passage ([resumeAtMs]), not from the start.
 */
data class WhisperCheckpoint(val model: String, val language: String, val heard: String?, val segments: List<SpeechSegment>) {
    /** Where the job goes on: the end of its last passage. */
    val resumeAtMs: Long get() = segments.maxOfOrNull { it.endMs } ?: 0L

    fun toJson(): String = JSONObject()
        .put("model", model)
        .put("language", language)
        .put("heard", heard ?: "")
        .put("segments", JSONArray().apply { segments.forEach { put(JSONArray().put(it.startMs).put(it.endMs).put(it.text)) } })
        .toString()

    companion object {
        /** The checkpoint [json] holds; null for what cannot be read. */
        fun parse(json: String): WhisperCheckpoint? = runCatching {
            val o = JSONObject(json)
            val list = o.getJSONArray("segments")
            val segments = (0 until list.length()).map { i ->
                val s = list.getJSONArray(i)
                SpeechSegment(s.getLong(0), s.getLong(1), s.getString(2))
            }
            WhisperCheckpoint(o.getString("model"), o.getString("language"), o.optString("heard").ifEmpty { null }, segments)
        }.getOrNull()
    }
}

/** The checkpoints of the Whisper jobs, in the app's storage (`files/whisper-progress/<episode>.json`). Every call reads or writes the disk. */
object WhisperProgress {
    private const val FOLDER = "whisper-progress"
    private const val EXTENSION = ".json"

    private fun folder(context: Context): File = File(context.filesDir, FOLDER)

    private fun file(context: Context, episodeId: Long): File = File(folder(context), "$episodeId$EXTENSION")

    /** [episodeId]'s checkpoint for a job with [model] in [language]; null with none, or one of another model or language. */
    fun read(context: Context, episodeId: Long, model: WhisperModel, language: String): WhisperCheckpoint? {
        val file = file(context, episodeId)
        if (!file.isFile) return null
        val saved = runCatching { file.readText() }.getOrNull()?.let(WhisperCheckpoint::parse)
        return saved?.takeIf { it.model == model.key && it.language == language && it.segments.isNotEmpty() }
    }

    fun write(context: Context, episodeId: Long, checkpoint: WhisperCheckpoint): Boolean =
        EpisodeTranscripts.writeSynced(file(context, episodeId), checkpoint.toJson().toByteArray())

    fun delete(context: Context, episodeId: Long) {
        file(context, episodeId).delete()
    }

    /** The checkpoints of episodes no longer asked for ([kept]: those queued, and those that failed and may be asked again) removed. */
    fun tidy(context: Context, kept: Set<Long>) {
        folder(context).listFiles()?.forEach { file ->
            // a checkpoint being written (its .part) goes with its episode's
            val id = file.name.removeSuffix(".part").removeSuffix(EXTENSION).toLongOrNull()
            if (id == null || id !in kept) file.delete()
        }
    }
}
