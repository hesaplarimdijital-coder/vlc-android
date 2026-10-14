package app.nagmedar.ui.player.record

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Why a recording (S-607) was not kept. */
enum class RecordingFailure {
    /** libVLC did not start recording what plays: the music engine plays it, or VLC cannot record this input. */
    NotSupported,

    /** The device's storage is (nearly) full: no recording starts, a running one stops and is kept. */
    NoSpace,

    /** The chosen folder is gone or no longer ours to write in. */
    Folder,

    /** Copying the recording failed: a drive pulled out, a provider that refused it. */
    Write,

    /** libVLC stopped without writing anything. */
    Empty,
}

/** What the indicator over the video shows (S-607): nothing, a recording starting, one being made, one being kept. */
@Immutable
sealed interface RecordingStatus {
    data object Off : RecordingStatus

    data object Starting : RecordingStatus

    /** [elapsedMs]: the time played while it records (a pause does not count); [bytes] written so far. */
    data class On(val elapsedMs: Long, val bytes: Long) : RecordingStatus

    /** The recording copied into its folder: [copied] of [total] bytes. */
    data class Saving(val copied: Long, val total: Long) : RecordingStatus {
        val fraction: Float get() = if (total <= 0) 0f else (copied.toFloat() / total).coerceIn(0f, 1f)
    }
}

/** A recording made, or why not, as the card over the video tells it. */
@Immutable
sealed interface RecordingOutcome {
    data class Saved(
        /** The file where it is kept, for "Play"; null when it cannot be opened from here. */
        val uri: String?,
        val fileName: String,
        /** The folder as a file manager names it: "Movies/Nağmedar", "Kayıtlar". */
        val folder: String,
        /** The chosen folder's tree, or null for the default one, for "Open folder". */
        val tree: String?,
        val mime: String,
        val bytes: Long,
        val durationMs: Long,
        /** It stopped because the storage was nearly full. */
        val stoppedForSpace: Boolean = false,
    ) : RecordingOutcome

    /** [canRetry]: the recording is still in the app's storage and can be kept again, in another folder. */
    data class Failed(val reason: RecordingFailure, val canRetry: Boolean = false) : RecordingOutcome
}

/** Where a recording was kept: its uri, its folder's name, its type. */
data class KeptRecording(val uri: String?, val folder: String, val tree: String?, val mime: String)

/** Keeping a recording failed, and why. */
class RecordingException(val reason: RecordingFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * "TRT Radyo 1 2026-10-15 21-04-33.mp3": what plays and when its recording started, sortable;
 * what file systems refuse (FAT and exFAT drives too) becomes "_"; [fallback] names what has no
 * title. [extension] is the one libVLC chose for what it wrote (ts, mkv, mp3…).
 */
fun recordingFileName(title: String?, startedAtMs: Long, extension: String, fallback: String, timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
    val clean = title.orEmpty().map { c -> if (c in FORBIDDEN || c.isISOControl()) '_' else c }
        .joinToString("")
        .trim()
        .trim('.')
        .take(MAX_TITLE)
        .trim()
        .ifEmpty { fallback }
    val format = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).apply { this.timeZone = timeZone }
    val suffix = extension.trim().trimStart('.').lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    return "$clean ${format.format(Date(startedAtMs))}" + (suffix?.let { ".$it" } ?: "")
}

private const val FORBIDDEN = "/\\:*?\"<>|"
private const val MAX_TITLE = 96

/** The type of what libVLC wrote, by the extension it chose: a stream's own container, or the audio's. */
fun recordingMime(extension: String): String = when (extension.lowercase(Locale.ROOT)) {
    "ts", "m2ts", "mts" -> "video/mp2t"
    "mkv" -> "video/x-matroska"
    "mka" -> "audio/x-matroska"
    "mp4", "m4v", "mov" -> "video/mp4"
    "webm" -> "video/webm"
    "ogv" -> "video/ogg"
    "ps", "mpg", "mpeg", "vob" -> "video/mpeg"
    "avi" -> "video/x-msvideo"
    "asf", "wmv" -> "video/x-ms-asf"
    "flv" -> "video/x-flv"
    "mp3" -> "audio/mpeg"
    "aac", "adts" -> "audio/aac"
    "m4a" -> "audio/mp4"
    "ogg", "oga", "opus" -> "audio/ogg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "ac3" -> "audio/ac3"
    "eac3" -> "audio/eac3"
    else -> "application/octet-stream"
}

/** The folder a recording goes to without a chosen one: Movies, Music or Download, then Nağmedar ([appName]). */
fun defaultRecordingFolder(mime: String, appName: String): String = when {
    mime.startsWith("video/") -> "Movies/$appName"
    mime.startsWith("audio/") -> "Music/$appName"
    else -> "Download/$appName"
}
