package app.nagmedar.ui.player.record

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import app.nagmedar.ui.R
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.tools.Settings
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * The recordings' files on the device (S-607). libVLC writes into a path, a folder of the
 * recording's own:
 * - without a chosen folder, on Android 11 and later (direct paths to the shared folders) and
 *   before Android 10 (VLC's storage permission): a hidden folder in Download/Nağmedar, which
 *   takes a file of any type (libVLC picks the container: ts, mkv, mp3…), on the volume the
 *   recording stays on. Stopped, it is renamed into Movies, Music or Download/Nağmedar by its
 *   type: no copy, no room needed twice, however full the storage is.
 * - else (a folder chosen in Settings › Recording, a tree the system's picker gave, which libVLC
 *   cannot write by a path; Android 10): the app's own storage (Android/data/…/files/recordings),
 *   always writable. Stopped, it is copied into the chosen folder or into MediaStore, while a
 *   foreground service (dataSync) keeps the app alive and shows the copy's progress; a mark in
 *   the recording's folder names the document being written, so a copy cut short is deleted
 *   before it is made again.
 */
internal class RecordingFiles(
    private val context: Context,
    private val sdk: Int = Build.VERSION.SDK_INT,
    /** How long the media scanner is waited for, once a recording is in its folder. */
    private val scanTimeoutMs: Long = SCAN_TIMEOUT_MS,
) : RecordingStorage {
    private val appName: String get() = context.getString(R.string.nagmedar_shell_title)

    private val appRoot: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT)

    private fun chosenTree(): String? = Settings.getInstance(context).getString(KEY_RECORDING_FOLDER, null)?.takeIf { it.isNotEmpty() }

    /** The internal storage's root, where Movies, Music and Download are. */
    @Suppress("DEPRECATION")
    private fun sharedRoot(): File = Environment.getExternalStorageDirectory()

    /** The hidden folder recordings are made in beside where they stay; null where paths cannot be written. */
    private fun publicRoot(): File? =
        if (RecordingPlaces.stagesShared(sdk, folderChosen = false)) File(sharedRoot(), "${defaultRecordingFolder(ANY_TYPE, appName)}/$STAGING") else null

    override fun newFolder(): File? {
        val name = System.currentTimeMillis().toString()
        if (RecordingPlaces.stagesShared(sdk, chosenTree() != null)) {
            publicRoot()?.let { root -> File(root, name).takeIf(::writable)?.let { return it } }
        }
        return File(appRoot, name).takeIf { it.mkdirs() }
    }

    /** [folder] made, new, and a file can be written in it by its path. */
    private fun writable(folder: File): Boolean {
        if (!folder.mkdirs()) return false
        val probe = File(folder, PROBE)
        val ok = runCatching { probe.createNewFile() }.getOrDefault(false)
        probe.delete()
        if (!ok) folder.delete()
        return ok
    }

    override fun freeBytes(folder: File): Long = folder.usableSpace

    override fun bytes(folder: File): Long = recorded(folder).sumOf { it.length() }

    override fun written(folder: File, path: String?): File? {
        val said = path?.removePrefix("file://")?.let(::File)?.takeIf { it.isFile && it.length() > 0 && it.parentFile == folder }
        return said ?: recorded(folder).filter { it.length() > 0 }.maxByOrNull { it.length() }
    }

    /** What libVLC wrote in [folder]: not the app's own marks. */
    private fun recorded(folder: File): List<File> = folder.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }.orEmpty()

    override fun discard(folder: File) {
        folder.deleteRecursively()
    }

    override fun leftovers(): List<File> {
        val folders = listOfNotNull(appRoot, publicRoot()).flatMap { root -> root.listFiles()?.filter { it.isDirectory }.orEmpty() }
        // a notification of a copy an earlier run did not finish
        if (folders.isNotEmpty()) Notice.clear(context)
        return folders
    }

    override suspend fun keep(file: File, name: String, durationMs: Long, progress: (Long, Long) -> Unit): KeptRecording = withContext(Dispatchers.IO) {
        val folder = file.parentFile ?: throw RecordingException(RecordingFailure.Write)
        // a copy an earlier run cut short goes first
        deletePartial(folder)
        val total = file.length()
        val mime = recordingMime(file.extension)
        val tree = chosenTree()
        val staged = publicRoot()?.let { file.path.startsWith(it.path + File.separator) } == true
        val plan = RecordingPlaces.keep(sdk, tree != null, staged)
        if (plan == RecordingPlaces.Keep.Rename) {
            move(file, name, mime)?.let { kept ->
                progress(total, total)
                return@withContext kept
            }
        }
        val notice = Notice(context, name, total)
        try {
            val copying: (Long) -> Unit = { copied ->
                progress(copied, total)
                notice.progress(copied)
            }
            val kept = when {
                tree != null -> writeToTree(Uri.parse(tree), file, name, mime, copying)
                sdk >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> writeToMediaStore(file, name, mime, copying)
                else -> writeToFile(file, name, mime, copying)
            }
            // done: the copy is not a partial one any more
            File(folder, MARK).delete()
            file.delete()
            notice.done(kept)
            kept
        } catch (e: RecordingException) {
            notice.failed()
            throw e
        } catch (e: CancellationException) {
            notice.failed()
            throw e
        } catch (e: Exception) {
            notice.failed()
            throw RecordingException(RecordingFailure.Write, e)
        }
    }

    /**
     * The recording renamed into Movies, Music or Download/Nağmedar, on the volume it was made on;
     * into Download/Nağmedar, which takes any type, when the system refuses the other; null when it
     * refuses both.
     */
    private suspend fun move(file: File, name: String, mime: String): KeptRecording? {
        val places = listOf(defaultRecordingFolder(mime, appName), defaultRecordingFolder(ANY_TYPE, appName)).distinct()
        var moved: Pair<String, File>? = null
        for (relative in places) {
            val target = File(sharedRoot(), relative)
            if (!target.isDirectory && !target.mkdirs()) continue
            val destination = unique(target, name)
            if (file.renameTo(destination)) {
                moved = relative to destination
                break
            }
        }
        val (relative, destination) = moved ?: return null
        val uri = withTimeoutOrNull(scanTimeoutMs) {
            suspendCancellableCoroutine { done ->
                MediaScannerConnection.scanFile(context, arrayOf(destination.path), arrayOf(mime)) { _, uri -> if (done.isActive) done.resume(uri) }
            }
        } ?: runCatching { FileProvider.getUriForFile(context, context.packageName + ".provider", destination) }.getOrNull()
        return KeptRecording(uri?.toString(), relative, null, mime)
    }

    /** The document a copy cut short left, named by the mark in [folder], deleted. */
    private fun deletePartial(folder: File) {
        val mark = File(folder, MARK)
        if (!mark.isFile) return
        val uri = runCatching { Uri.parse(mark.readText().trim()) }.getOrNull()
        if (uri != null) {
            runCatching {
                when {
                    uri.scheme == "file" -> uri.path?.let { File(it).delete() }
                    DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
                    else -> context.contentResolver.delete(uri, null, null)
                }
            }
        }
        mark.delete()
    }

    /** The document being written into, marked in the recording's [folder] until it is whole. */
    private fun mark(folder: File?, uri: Uri) {
        runCatching { folder?.let { File(it, MARK).writeText(uri.toString()) } }
    }

    private suspend fun writeToTree(tree: Uri, file: File, name: String, mime: String, progress: (Long) -> Unit): KeptRecording {
        val resolver = context.contentResolver
        val treeId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            throw RecordingException(RecordingFailure.Folder, e)
        }
        // a folder on the internal storage shares its room with the recording
        if (treeId.startsWith("primary:") && file.parentFile?.let(::freeBytes)?.let { it < file.length() } == true) {
            throw RecordingException(RecordingFailure.NoSpace)
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        // the provider makes the name unique ("… (1).ts") when the folder has it
        val document = try {
            DocumentsContract.createDocument(resolver, parent, mime, name)
        } catch (e: SecurityException) {
            throw RecordingException(RecordingFailure.Folder, e)
        } catch (e: FileNotFoundException) {
            throw RecordingException(RecordingFailure.Folder, e)
        } catch (e: IllegalArgumentException) {
            throw RecordingException(RecordingFailure.Folder, e)
        } catch (e: IllegalStateException) {
            throw RecordingException(RecordingFailure.Folder, e)
        } ?: throw RecordingException(RecordingFailure.Folder)
        mark(file.parentFile, document)
        try {
            copy(file, resolver.openOutputStream(document, "w") ?: throw IOException("no stream"), progress)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            File(file.parentFile, MARK).delete()
            throw failure(e)
        }
        return KeptRecording(document.toString(), snapshotFolderLabel(tree.toString(), tree.toString()), tree.toString(), mime)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun writeToMediaStore(file: File, name: String, mime: String, progress: (Long) -> Unit): KeptRecording {
        val resolver = context.contentResolver
        val folder = defaultRecordingFolder(mime, appName)
        // the internal storage, where the app's own folder is too, holds the recording twice while it is copied
        if (file.parentFile?.let(::freeBytes)?.let { it < file.length() } == true) throw RecordingException(RecordingFailure.NoSpace)
        val collection = when {
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            throw RecordingException(RecordingFailure.Write, e)
        } ?: throw RecordingException(RecordingFailure.Write)
        mark(file.parentFile, uri)
        try {
            copy(file, resolver.openOutputStream(uri, "w") ?: throw IOException("no stream"), progress)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            File(file.parentFile, MARK).delete()
            throw failure(e)
        }
        return KeptRecording(uri.toString(), folder, null, mime)
    }

    /** Before Android 10, when the recording was made in the app's storage: the file itself, with VLC's storage permission. */
    private suspend fun writeToFile(file: File, name: String, mime: String, progress: (Long) -> Unit): KeptRecording {
        val relative = defaultRecordingFolder(mime, appName)
        val folder = File(sharedRoot(), relative)
        if (!folder.isDirectory && !folder.mkdirs()) throw RecordingException(RecordingFailure.Write)
        if (freeBytes(folder) < file.length()) throw RecordingException(RecordingFailure.NoSpace)
        val target = unique(folder, name)
        mark(file.parentFile, Uri.fromFile(target))
        try {
            copy(file, target.outputStream(), progress)
        } catch (e: Exception) {
            target.delete()
            File(file.parentFile, MARK).delete()
            throw failure(e)
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".provider", target) }.getOrNull()
        return KeptRecording(uri?.toString(), relative, null, mime)
    }

    private suspend fun copy(file: File, out: OutputStream, progress: (Long) -> Unit) {
        val job = coroutineContext
        file.inputStream().use { input: InputStream ->
            out.use { output ->
                val buffer = ByteArray(BUFFER)
                var copied = 0L
                var told = 0L
                while (true) {
                    job.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied - told >= TELL_EVERY) {
                        told = copied
                        progress(copied)
                    }
                }
                progress(copied)
            }
        }
    }

    private fun unique(folder: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var file = File(folder, name)
        var n = 1
        while (file.exists()) file = File(folder, if (extension.isEmpty()) "$base ($n)" else "$base ($n).$extension").also { n++ }
        return file
    }

    /** A full drive says so; any other failure is the copy's. */
    private fun failure(e: Exception): RecordingException {
        if (e is RecordingException) return e
        val errno = (e.cause as? ErrnoException)?.errno
        val full = errno == OsConstants.ENOSPC || e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
        return RecordingException(if (full) RecordingFailure.NoSpace else RecordingFailure.Write, e)
    }

    /**
     * A big recording's copy: its progress in a notification, which the foreground service
     * ([RecordingKeeper]) holds while it goes, for the copy outlives the player; once done, a tap
     * plays it. Small ones only have the card over the video.
     */
    internal class Notice(private val context: Context, private val name: String, private val total: Long) {
        private val shown = total >= NOTIFY_FROM && NotificationManagerCompat.from(context).areNotificationsEnabled()
        private var percent = -1

        init {
            if (shown) {
                channel(context)
                val first = progressNotification(context, name, 0)
                // the service holds the notification; where the system refuses it, the notification alone
                if (!RecordingKeeper.start(context, first)) post(first)
            }
        }

        fun progress(copied: Long) {
            if (!shown) return
            val now = (copied * 100 / total.coerceAtLeast(1)).toInt()
            if (now == percent) return
            percent = now
            post(progressNotification(context, name, now))
        }

        fun done(kept: KeptRecording) {
            if (!shown) return
            RecordingKeeper.stop()
            val play = kept.uri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), kept.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(context, DONE_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            post(
                builder(context, name).setAutoCancel(true)
                    .setContentText(context.getString(R.string.nag_rec_notice_saved, kept.folder))
                    .apply { play?.let(::setContentIntent) }
                    .build(),
                DONE_ID,
            )
        }

        fun failed() {
            if (!shown) return
            RecordingKeeper.stop()
            post(builder(context, name).setAutoCancel(true).setContentText(context.getString(R.string.nag_rec_failed)).build(), DONE_ID)
        }

        @SuppressLint("MissingPermission")
        private fun post(notification: Notification, id: Int = ID) {
            // without the permission the card over the video says it all
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }

        companion object {
            /** The copy's notification: the foreground service's too. */
            const val ID = 6070
            private const val DONE_ID = 6071

            /** Android's time limit ended the copy's foreground service (S-607). */
            internal const val TIME_LIMIT_ID = 6072
            internal const val CHANNEL = "nagmedar_recordings"

            /** A copy this big takes a while: its progress shows. */
            private const val NOTIFY_FROM = 64L * 1024 * 1024

            internal fun builder(context: Context, name: String): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
                .setContentTitle(name)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

            internal fun progressNotification(context: Context, name: String, percent: Int): Notification = builder(context, name)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, false)
                .setContentText(context.getString(R.string.nag_rec_notice_saving))
                .build()

            internal fun channel(context: Context) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL) != null) return
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.nag_rec_channel), NotificationManager.IMPORTANCE_LOW))
            }

            fun clear(context: Context) {
                RecordingKeeper.stop()
                runCatching { NotificationManagerCompat.from(context).cancel(ID) }
            }
        }
    }

    companion object {
        private const val ROOT = "recordings"

        /** The hidden folder in Download/Nağmedar recordings are made in. */
        private const val STAGING = ".recording"

        /** A type no media folder claims: Download's. */
        private const val ANY_TYPE = "application/octet-stream"

        /** A media file's name: the system lets it be written in a media folder, the folder works. */
        private const val PROBE = ".probe.ts"

        /** The mark of a copy under way: the document it writes. */
        private const val MARK = ".keeping"
        private const val BUFFER = 1 shl 20
        private const val TELL_EVERY = 4L shl 20
        const val SCAN_TIMEOUT_MS = 5_000L
    }
}

/** Where a recording is made and how it is kept (S-607), by Android's version and whether a folder is chosen. */
internal object RecordingPlaces {
    /** Made beside where it stays, in the shared storage by its path: Android 11 and later, and before 10. */
    fun stagesShared(sdk: Int, folderChosen: Boolean): Boolean =
        !folderChosen && (sdk >= Build.VERSION_CODES.R || sdk < Build.VERSION_CODES.Q)

    enum class Keep { Rename, Tree, MediaStore, File }

    /** How a recording is kept: renamed where it was made, or copied into the chosen tree, MediaStore or a file. */
    fun keep(sdk: Int, folderChosen: Boolean, stagedShared: Boolean): Keep = when {
        folderChosen -> Keep.Tree
        stagedShared -> Keep.Rename
        sdk >= Build.VERSION_CODES.Q -> Keep.MediaStore
        else -> Keep.File
    }
}
