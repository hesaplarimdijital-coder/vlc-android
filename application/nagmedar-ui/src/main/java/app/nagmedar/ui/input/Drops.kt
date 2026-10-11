package app.nagmedar.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/*
 * Drag and drop (S-406): files dropped on the window from another app or window are played,
 * subtitles dropped while a video plays are loaded at once, a subtitle dropped on a film goes
 * with it, media dropped on the player bar join the queue. Films and tracks are dragged out as
 * files.
 */

/** What a dropped file is, by its name or the type its sender gave. */
enum class DropKind { Video, Audio, Subtitle, Playlist, Other }

private val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "sup", "smi", "sami", "ttml", "dfxp", "usf")
private val playlistExtensions = setOf("m3u", "m3u8", "pls", "xspf", "asx", "wpl")
private val videoExtensions = setOf(
    "mkv", "mp4", "m4v", "avi", "mov", "wmv", "webm", "ts", "m2ts", "mts", "mpg", "mpeg", "flv", "3gp", "ogv", "vob", "divx", "rmvb",
)
private val audioExtensions = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "wv", "dsf", "dff", "aiff", "aif", "mka", "ac3", "eac3", "dts", "alac",
)
private val subtitleTypes = setOf("application/x-subrip", "text/vtt", "text/x-ssa", "text/x-ass", "application/x-ass", "application/ttml+xml")
private val playlistTypes = setOf(
    "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/x-scpls", "application/xspf+xml",
)

fun dropKind(name: String?, mime: String?): DropKind {
    val type = mime?.lowercase().orEmpty()
    val extension = name?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when {
        extension in subtitleExtensions || type in subtitleTypes -> DropKind.Subtitle
        extension in playlistExtensions || type in playlistTypes -> DropKind.Playlist
        extension in videoExtensions || type.startsWith("video/") -> DropKind.Video
        extension in audioExtensions || type.startsWith("audio/") -> DropKind.Audio
        else -> DropKind.Other
    }
}

/** A file dropped on the window: where it is, and its name and type as the sender gave them. */
data class DroppedFile(val uri: String, val name: String? = null, val mime: String? = null) {
    val kind: DropKind = dropKind(name ?: uri, mime)

    val playable: Boolean get() = kind == DropKind.Video || kind == DropKind.Audio || kind == DropKind.Playlist
}

/** What a drop on the window does. */
sealed interface DropPlan {
    /** Subtitles for the video playing: loaded at once and kept with it. */
    data class LoadSubtitles(val uris: List<String>) : DropPlan

    /** Media and playlists, played in the order dropped; subtitles dropped with them are left out. */
    data class Play(val uris: List<String>) : DropPlan

    /** Subtitles with no video to go with them. */
    data object SubtitlesNeedAVideo : DropPlan

    /** Nothing that plays. */
    data object Nothing : DropPlan
}

fun planDrop(files: List<DroppedFile>, videoPlaying: Boolean): DropPlan {
    val media = files.filter { it.playable }.map { it.uri }
    val subtitles = files.filter { it.kind == DropKind.Subtitle }.map { it.uri }
    return when {
        media.isNotEmpty() -> DropPlan.Play(media)
        subtitles.isNotEmpty() && videoPlaying -> DropPlan.LoadSubtitles(subtitles)
        subtitles.isNotEmpty() -> DropPlan.SubtitlesNeedAVideo
        else -> DropPlan.Nothing
    }
}

/** A place in the window that takes dropped files: a film, the player bar. */
class DropTarget internal constructor() {
    /** Whether files of these types are this target's (types only: names come with the drop). */
    internal var accepts: (mimeTypes: List<String>) -> Boolean = { false }

    /** The files dropped here; false when none were its own, so the window takes them. */
    var onDrop: (List<DroppedFile>) -> Boolean = { false }
        internal set

    internal var bounds: Rect? = null
}

/**
 * The window's drop targets (S-406), told by the activity where a drag is: [incoming] while files
 * from another app or window are carried over the window, [hovered] the target under a drag.
 */
@Stable
class DropTargets {
    private val targets = LinkedHashMap<Any, DropTarget>()

    /** Files from outside are carried over the window: dropped anywhere, they play. */
    var incoming by mutableStateOf(false)
        internal set

    /** The key of the target under the drag. */
    var hovered by mutableStateOf<Any?>(null)
        internal set

    internal fun put(key: Any, target: DropTarget) {
        targets[key] = target
    }

    internal fun remove(key: Any, target: DropTarget) {
        if (targets[key] === target) targets.remove(key)
    }

    /** The innermost target at [x], [y] (window coordinates) taking [mimeTypes]: the last one laid out wins. */
    fun at(x: Float, y: Float, mimeTypes: List<String>): Pair<Any, DropTarget>? =
        targets.entries.lastOrNull { (_, target) -> target.bounds?.contains(Offset(x, y)) == true && target.accepts(mimeTypes) }
            ?.let { it.key to it.value }

    /** The activity's report: a drag with [mimeTypes] at [x], [y]; [outside] when it comes from elsewhere. */
    fun moved(x: Float, y: Float, mimeTypes: List<String>, outside: Boolean) {
        incoming = outside
        hovered = at(x, y, mimeTypes)?.first
    }

    fun ended() {
        incoming = false
        hovered = null
    }
}

val LocalDropTargets = staticCompositionLocalOf { DropTargets() }

/** Makes this element a drop target under [key] while it is composed; with a [shape] it is ringed while a drag is over it. */
fun Modifier.dropTarget(
    key: Any,
    accepts: (List<String>) -> Boolean,
    shape: Shape? = null,
    onDrop: (List<DroppedFile>) -> Boolean,
): Modifier = composed {
    val targets = LocalDropTargets.current
    val target = remember(key) { DropTarget() }
    SideEffect {
        target.accepts = accepts
        target.onDrop = onDrop
    }
    DisposableEffect(targets, key, target) {
        targets.put(key, target)
        onDispose { targets.remove(key, target) }
    }
    val ring = if (shape != null && targets.hovered == key) Modifier.border(2.dp, NagTheme.colors.accent, shape) else Modifier
    onGloballyPositioned { target.bounds = it.boundsInWindow() }.then(ring)
}

/** Types that may be a subtitle: senders call .srt files many things. */
fun mayBeSubtitle(types: List<String>): Boolean = types.any { it in subtitleTypes || it.startsWith("text/") || it == "application/octet-stream" }

/** Types that may be a video, a track or a playlist. */
fun mayBePlayable(types: List<String>): Boolean = types.any {
    it.startsWith("video/") || it.startsWith("audio/") || it in playlistTypes || it == "application/octet-stream" || it == "application/x-matroska"
}

/** Whether a drag is over the target of [key] now. */
@Composable
fun isDropHovered(key: Any): Boolean = LocalDropTargets.current.hovered == key

/** The local state of drags started in the app: dropped back on their own list they do nothing. */
const val NAGMEDAR_DRAG = "nagmedar-drag"

/** Types of files a drag may carry that the window can do something with. */
fun isMediaOrSubtitleType(mime: String): Boolean = mime.startsWith("video/") || mime.startsWith("audio/") ||
    mime in subtitleTypes || mime in playlistTypes || mime == "application/octet-stream" || mime.startsWith("text/") ||
    mime == "application/x-matroska"
