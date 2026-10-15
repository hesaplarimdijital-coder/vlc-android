package app.nagmedar.ui.radio.record

/** S-1107: the station a recording is made from, with the address its stream plays from now. */
data class RecordingStation(
    val name: String,
    val streamUrl: String,
    val uuid: String? = null,
    val logoUrl: String? = null,
) {
    /** The same station however it was reached: the directory's id, else the address. */
    val key: String get() = uuid ?: "url:$streamUrl"
}

/** S-1107: one recording to make: [endAt] (epoch ms) is when it stops by itself, null to go on until stopped; [scheduleId] is the schedule it came from. */
data class RecordingRequest(
    val sessionId: String,
    val station: RecordingStation,
    val endAt: Long? = null,
    val scheduleId: Long? = null,
)

/**
 * S-1107: a track saved to a file. [complete] is true when the recording saw both the title change
 * that began it and the one that ended it: the first and last tracks of a recording, and any
 * track kept though shorter than the limit, are partial. [fileUri] is the file's address (a file
 * or a document of the folder chosen), [fileName] its name. [trashedAt] is set (ms) while a
 * deletion can still be taken back.
 */
data class RecordedTrack(
    val id: Long = 0,
    val sessionId: String,
    val station: RecordingStation,
    val sessionStartedAt: Long,
    val startedAt: Long,
    val fileUri: String,
    val fileName: String,
    val artist: String?,
    val title: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val complete: Boolean,
    val format: String,
    val trashedAt: Long = 0,
) {
    /** What the track is called on screen: its title, else its file name without the extension. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
}

/** S-1107: the user's choices for recording (Settings › Radio): where files go, which tracks are kept, how much space recording may take. */
data class RecordingSettings(
    /** The folder chosen (a document tree address); null: the app's music folder. */
    val folder: String? = null,
    /** Tracks shorter than this many seconds are not kept (or are kept partial: [keepShort]). */
    val minSeconds: Int = DEFAULT_MIN_SECONDS,
    val keepShort: Boolean = false,
    /** Seconds of the previous track's end added to the start of a track, and of the next one's start to the end of a track. */
    val padBeforeSeconds: Int = 0,
    val padAfterSeconds: Int = 0,
    val limits: StorageLimits = StorageLimits(),
) {
    companion object {
        const val DEFAULT_MIN_SECONDS = 30

        /** Offered for the shortest track kept, seconds. */
        val MIN_SECONDS = listOf(0, 10, 20, 30, 45, 60, 90)

        /** Offered for the padding, seconds (the audio is cut by frames and the titles arrive late: a little padding hides it). */
        val PADDING_SECONDS = listOf(0, 1, 2, 3, 5)
    }
}

/** Why a recording ended. */
sealed interface SessionEnd {
    /** The user stopped it. */
    data object UserStopped : SessionEnd

    /** It ran for the time set. */
    data object TimeUp : SessionEnd

    /** Stopped for the space left on the device. */
    data object StorageLow : SessionEnd

    /** Stopped at the limit the user set for all recordings. */
    data object LimitReached : SessionEnd

    /** The station's stream could not be had (offline, refused, cut again and again). */
    data class Failed(val status: Int? = null) : SessionEnd

    /** The stream is of a kind that is not recorded. */
    data class Unsupported(val reason: UnsupportedStream) : SessionEnd

    /** The folder for the files cannot be written to. */
    data object NoFolder : SessionEnd
}

/** Streams that are not recorded. */
enum class UnsupportedStream {
    /** HLS: a playlist of segments. */
    Segmented,

    /** A container other than MP3, AAC (ADTS), Ogg and Opus. */
    Format,
}
