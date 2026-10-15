package app.nagmedar.ui.network

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/** S-1301: a server or a share found on this network: libVLC's SMB, UPnP/DLNA and mDNS discoverers. */
@Immutable
data class DiscoveredServer(val url: String, val name: String) {
    val protocol: NetworkProtocol? get() = NetworkProtocol.of(url.substringBefore("://", ""))
}

/** What a file of a network folder is, by its name. */
enum class EntryKind {
    Folder, Video, Audio, Playlist, Subtitle, Other;

    /** Whether the playback service plays it. */
    val playable: Boolean get() = this == Video || this == Audio || this == Playlist

    companion object {
        private val VIDEO = setOf(
            "3g2", "3gp", "asf", "avi", "divx", "dv", "f4v", "flv", "h264", "h265", "hevc", "m1v", "m2t", "m2ts", "m2v", "m4v", "mjpeg",
            "mkv", "mov", "mp4", "mpe", "mpeg", "mpg", "mpv", "mts", "mxf", "ogm", "ogv", "rm", "rmvb", "tod", "trp", "ts", "vob", "webm", "wmv",
        )
        private val AUDIO = setOf(
            "aac", "ac3", "aif", "aifc", "aiff", "alac", "amr", "ape", "au", "caf", "dff", "dsf", "dts", "eac3", "flac", "it", "m4a", "m4b",
            "mka", "mod", "mp2", "mp3", "mpc", "mptm", "oga", "ogg", "opus", "ra", "s3m", "spx", "tak", "tta", "w64", "wav", "wma", "wv", "xm",
        )
        private val PLAYLIST = setOf("asx", "b4s", "cue", "m3u", "m3u8", "pls", "wpl", "xspf")
        private val SUBTITLE = setOf("ass", "idx", "smi", "srt", "ssa", "sub", "sup", "ttml", "vtt")

        /** The kind of the file named [name]; [folder] when libVLC says it is one. */
        fun of(name: String, folder: Boolean): EntryKind {
            if (folder) return Folder
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when (extension) {
                in VIDEO -> Video
                in AUDIO -> Audio
                in PLAYLIST -> Playlist
                in SUBTITLE -> Subtitle
                else -> Other
            }
        }
    }
}

/** A file or a folder of a network folder, by its address (libVLC's, encoded) and its name. */
@Immutable
data class NetworkEntry(val url: String, val name: String, val kind: EntryKind)

/** How reading a folder ended. */
enum class BrowseResult {
    /** Read: what it holds was found (maybe nothing). */
    Done,

    /** The server did not answer, is not there, or would not give the folder. */
    Unreachable,

    /** The server asks for a sign-in and none was given. */
    SignInNeeded,

    /** The server turned the sign-in down. */
    SignInRejected,
}

sealed interface BrowseEvent {
    data class Found(val entry: NetworkEntry) : BrowseEvent

    /** libVLC asks something while it reads (a server's certificate); null: the question is gone. */
    data class Asked(val prompt: LibVlcPrompt?) : BrowseEvent

    data class Ended(val result: BrowseResult) : BrowseEvent
}

sealed interface DiscoveryEvent {
    data class Found(val server: DiscoveredServer) : DiscoveryEvent

    data class Lost(val url: String) : DiscoveryEvent
}

/** S-1301: where the network browser reads: libVLC in the app, a fake in the tests. */
interface NetworkSource {
    /** The servers on this network as they come and go, for as long as it is collected. */
    fun discover(): Flow<DiscoveryEvent>

    /**
     * [url]'s files and folders, then how the reading ended; signed in with [credentials] when
     * the server asks. Cancelled, libVLC stops being listened to.
     */
    fun browse(url: String, credentials: Credentials?): Flow<BrowseEvent>
}
