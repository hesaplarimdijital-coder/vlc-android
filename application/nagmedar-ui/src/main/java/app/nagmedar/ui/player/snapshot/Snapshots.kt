package app.nagmedar.ui.player.snapshot

import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.VideoTech
import java.util.Locale

/** The screenshot's file (S-606): PNG, lossless; WebP, lossless too and smaller. [label] is the format's own name. */
enum class SnapshotFormat(val extension: String, val mime: String, val label: String) {
    Png("png", "image/png", "PNG"),
    WebP("webp", "image/webp", "WebP"),
}

/**
 * What the screenshot of an HDR film becomes: an SDR picture, tone mapped, that every viewer shows
 * right; or an HDR one, kept in a 16-bit PNG that says it is BT.2020 PQ or HLG (Android 14 and later).
 */
enum class HdrSnapshot { ToneMap, Keep }

/** S-606's choices, in the app's preferences: the file's format, the subtitles, HDR, the folder. */
@Immutable
data class SnapshotSettings(
    val format: SnapshotFormat = SnapshotFormat.Png,
    /** The subtitles on the screen go into the picture. */
    val subtitles: Boolean = true,
    val hdr: HdrSnapshot = HdrSnapshot.ToneMap,
    /** A folder chosen with the system's picker (its tree URI); null is Pictures/Nağmedar. */
    val folder: String? = null,
) {
    fun write(editor: SharedPreferences.Editor): SharedPreferences.Editor = editor
        .putString(KEY_FORMAT, format.name)
        .putBoolean(KEY_SUBTITLES, subtitles)
        .putString(KEY_HDR, hdr.name)
        .putString(KEY_FOLDER, folder)

    companion object {
        const val KEY_FORMAT = "nagmedar_snapshot_format"
        const val KEY_SUBTITLES = "nagmedar_snapshot_subtitles"
        const val KEY_HDR = "nagmedar_snapshot_hdr"
        const val KEY_FOLDER = "nagmedar_snapshot_folder"
        val keys = setOf(KEY_FORMAT, KEY_SUBTITLES, KEY_HDR, KEY_FOLDER)

        fun read(prefs: SharedPreferences) = SnapshotSettings(
            format = SnapshotFormat.entries.firstOrNull { it.name == prefs.getString(KEY_FORMAT, null) } ?: SnapshotFormat.Png,
            subtitles = prefs.getBoolean(KEY_SUBTITLES, true),
            hdr = HdrSnapshot.entries.firstOrNull { it.name == prefs.getString(KEY_HDR, null) } ?: HdrSnapshot.ToneMap,
            folder = prefs.getString(KEY_FOLDER, null)?.takeIf { it.isNotEmpty() },
        )
    }
}

/** How an HDR film's picture is coded: the PQ transfer (HDR10, HDR10+, Dolby Vision) or HLG; its cICP code. */
enum class HdrTransfer(val cicp: Int) { Pq(16), Hlg(18) }

/**
 * The transfer of the picture the player shows, from the media probe (S-802): Dolby Vision shows
 * its base layer (HDR10 or HLG) on a device without it, profile 5's PQ when the decoder has it.
 */
fun hdrTransferOf(video: VideoTech?): HdrTransfer? = when (video?.baseLayer) {
    null, DynamicRange.SDR -> null
    DynamicRange.HLG -> HdrTransfer.Hlg
    DynamicRange.HDR10, DynamicRange.HDR10Plus, DynamicRange.DolbyVision -> HdrTransfer.Pq
}

/**
 * "Kuzey Işığı 01-12-33.png": the film's title and the time in it, sortable; what file systems
 * refuse (FAT and exFAT drives too) becomes "_"; [fallback] names a film without a title.
 */
fun snapshotFileName(title: String, positionMs: Long, format: SnapshotFormat, fallback: String): String {
    val clean = title.map { c -> if (c in FORBIDDEN || c.isISOControl()) '_' else c }
        .joinToString("")
        .trim()
        .trim('.')
        .take(MAX_TITLE)
        .trim()
        .ifEmpty { fallback }
    val seconds = positionMs.coerceAtLeast(0L) / 1000
    val time = "%02d-%02d-%02d".format(Locale.ROOT, seconds / 3600, seconds % 3600 / 60, seconds % 60)
    return "$clean $time.${format.extension}"
}

private const val FORBIDDEN = "/\\:*?\"<>|"
private const val MAX_TITLE = 96

/** Where screenshots go unless a folder is chosen: Pictures/Nağmedar ([appName]) on the device. */
fun defaultSnapshotFolder(appName: String): String = Environment.DIRECTORY_PICTURES + "/" + appName

/**
 * The folder as a file manager names it: [default] ("Pictures/Nağmedar") without a chosen one,
 * else the chosen folder's path on its drive ("Pictures/Screens", "Shots"); a drive's root is its
 * own name.
 */
fun snapshotFolderLabel(folder: String?, default: String): String {
    folder ?: return default
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(folder)) }.getOrNull() ?: return folder
    val path = id.substringAfter(':', "").trim('/')
    return path.ifEmpty { id.substringBefore(':') }
}

/** What became of the HDR picture: none was there, it was tone mapped, it was kept. */
enum class HdrOutcome { None, ToneMapped, Kept }

/** Why a screenshot was not saved. */
enum class SnapshotFailure {
    /** Nothing to take: no video shows. */
    NoVideo,

    /** The system could not copy the picture. */
    Capture,

    /** The chosen folder is gone or no longer ours to write in. */
    Folder,

    /** Writing the file failed: a full drive, a drive pulled out. */
    Write,

    /** Below Android 7 the picture cannot be copied. */
    Unsupported,
}

/** A screenshot taken, or why not, as the card over the video tells it. */
@Immutable
sealed interface SnapshotOutcome {
    data class Saved(
        val uri: Uri?,
        val fileName: String,
        val folder: String,
        val format: SnapshotFormat,
        val hdr: HdrOutcome,
        /** Subtitles were on the screen and went into the picture. */
        val subtitles: Boolean,
        val thumbnail: ImageBitmap?,
        val width: Int,
        val height: Int,
    ) : SnapshotOutcome

    data class Failed(val reason: SnapshotFailure) : SnapshotOutcome
}

/** The screenshots' card over the video: the last outcome, a flash for each picture taken. */
@Stable
class SnapshotState {
    var outcome by mutableStateOf<SnapshotOutcome?>(null)
    var saving by mutableStateOf(false)

    /** Counts the pictures taken: each one flashes the video once. */
    var flashes by mutableIntStateOf(0)

    fun dismiss() {
        outcome = null
    }
}
