package app.nagmedar.ui.tags

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.audio.FileTags
import app.nagmedar.audio.TagWrite
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.TagBackupStore
import app.nagmedar.tags.TagField
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReader
import app.nagmedar.tags.TagWriteResult
import app.nagmedar.tags.TagWriter
import app.nagmedar.tags.batch.BatchRunner
import app.nagmedar.tags.files.LocalAudioFiles
import app.nagmedar.tags.files.MediaIndexer
import app.nagmedar.tags.quality.LosslessCheckResult
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.save.BackupHold
import app.nagmedar.tags.save.FileTagBackupStore
import app.nagmedar.tags.save.RestoreBackupUseCase
import app.nagmedar.tags.save.SaveTagChangesUseCase
import app.nagmedar.tags.taglib.TagLibTagFiles
import app.nagmedar.ui.covers.CoverCenter
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.quality.LibraryChecks
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.AppScope

/**
 * 1.2.0 (Metamatik in Nağmedar): what the tag editor reads, writes and keeps with: TagLib's reads, writes guarded as the
 * lyrics' are (a copy of the file kept while it is written), the backups each save leaves for its undo, the library told
 * of what changed, the covers of what was written found again ([covers]: the files, their tags before and after), and
 * the batches of many files. Its work goes on in [scope], the app's: a save carries on when its screen goes.
 */
class TagFiles(
    val reader: TagReader,
    val writer: TagWriter,
    val backups: TagBackupStore,
    val indexer: MediaIndexer,
    val scope: CoroutineScope,
    val covers: suspend (refs: List<ContentRef>, before: List<TagMap>, after: List<TagMap>) -> Unit = { _, _, _ -> },
    val hold: BackupHold = BackupHold(),
    /** 1.4.0: whether a FLAC or WAV file's audio is what it says (Metamatik's check); none in tests that do not ask. */
    val lossless: (suspend (ContentRef) -> LosslessCheckResult)? = null,
    /** 1.4.0: where the editor's verdict goes, so that the library says the same without checking the file again. */
    val verdicts: (ContentRef, LosslessVerdict?) -> Unit = { _, _ -> },
) {
    val save = SaveTagChangesUseCase(reader, writer, backups, indexer, hold)
    val restore = RestoreBackupUseCase(backups, writer, indexer)
    val batch = BatchRunner(scope, save, backups, hold)
}

/** The tests' tag files (fakes); the app's are [TagCenter]'s. */
val LocalTagFiles = staticCompositionLocalOf<TagFiles?> { null }

@Composable
fun rememberTagFiles(): TagFiles = LocalTagFiles.current ?: TagCenter.files(LocalContext.current)

/** 1.2.0: the app's one [TagFiles]. */
object TagCenter {
    @Volatile
    private var files: TagFiles? = null

    private val count = MutableStateFlow(0L)

    /** Grows when the editor wrote or put back files' tags: what shows them reads them again. */
    val written: StateFlow<Long> = count.asStateFlow()

    fun changed() {
        count.value++
    }

    fun files(context: Context): TagFiles = files ?: synchronized(this) {
        files ?: build(context.applicationContext).also { files = it }
    }

    /** The folder of the saves' backups, in the app's own storage. */
    fun backupFolder(context: Context): File = File(context.applicationContext.filesDir, "tag-backups")

    /** The backups kept, for Settings: how many there are and their bytes. */
    suspend fun backupSize(context: Context): Pair<Int, Long> =
        (files(context).backups as? FileTagBackupStore)?.size() ?: (0 to 0L)

    private fun build(context: Context): TagFiles {
        val opener = LocalAudioFiles(context)
        val taglib = TagLibTagFiles(opener)
        return TagFiles(
            reader = taglib,
            writer = GuardedTagWriter(context, taglib),
            backups = FileTagBackupStore({ backupFolder(context) }),
            indexer = LibraryIndexer(context),
            scope = AppScope,
            covers = { refs, before, after -> refreshCovers(context, refs, before, after) },
            lossless = { ref -> LibraryChecks.lossless(context)(ref) },
            verdicts = { ref, verdict -> LibraryChecks.record(context, ref, verdict) },
        )
    }

    /**
     * The covers of files whose pictures were written found again: their albums' as they were named (what was kept for
     * them is forgotten) and as they are now; the views read theirs again.
     */
    private suspend fun refreshCovers(context: Context, refs: List<ContentRef>, before: List<TagMap>, after: List<TagMap>) {
        val finder = CoverCenter.finder(context)
        val tracks = refs.indices.flatMap { i ->
            listOfNotNull(before.getOrNull(i), after.getOrNull(i)).map { tags -> coverTrack(refs[i], tags) }
        }.distinctBy { it.key }
        tracks.forEach { runCatching { finder.again(it) } }
        CoverCenter.changed()
    }

    private fun coverTrack(ref: ContentRef, tags: TagMap) = CoverTrack(
        ref.uri,
        tags.first(TagField.Album.key)?.takeIf { it.isNotBlank() },
        tags.first(TagField.AlbumArtist.key)?.takeIf { it.isNotBlank() } ?: tags.first(TagField.Artist.key)?.takeIf { it.isNotBlank() },
    )
}

/**
 * 1.2.0: TagLib's writes through [FileTags.write], as the lyrics editor's go (S-1005): the file is copied aside, written,
 * read back with its audio as it was, else put back from the copy; a write cut short by a dead process is put right at
 * the next start. A CUE sheet's track shares its file with the others: not written.
 */
class GuardedTagWriter(context: Context, private val taglib: TagLibTagFiles) : TagWriter {
    private val context = context.applicationContext

    override suspend fun write(ref: ContentRef, tags: TagMap, pictures: List<EmbeddedPicture>?): TagWriteResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.uri)
        if (CueUri.isTrack(uri)) return@withContext TagWriteResult.Failed("A CUE sheet's track")
        // can it be written at all: the access to all files, a card that is not read only, another app's grant
        probe(uri)?.let { return@withContext it }
        var result: TagWriteResult = TagWriteResult.Failed("Not written")
        val written = FileTags.write(context, uri) { fd, _ ->
            // TagLib closes what it is given: a descriptor of its own each time
            result = taglib.writeOn({ ParcelFileDescriptor.fromFd(fd).detachFd() }, tags, pictures)
            result == TagWriteResult.Written
        }
        when (written) {
            TagWrite.Written -> TagWriteResult.Written
            TagWrite.NoRoom -> TagWriteResult.Failed("No room for the copy kept while the file is written")
            TagWrite.Failed -> (result as? TagWriteResult.Failed) ?: TagWriteResult.Failed("The file did not read as it was: put back")
        }
    }

    /** Why [uri] cannot be opened to be written; null when it can. */
    private fun probe(uri: Uri): TagWriteResult? = try {
        when (uri.scheme) {
            null, "file" -> {
                val file = File(uri.path ?: return TagWriteResult.NotFound)
                if (!file.isFile) return TagWriteResult.NotFound
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).close()
                null
            }
            "content" -> {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw") ?: return TagWriteResult.NotFound
                descriptor.close()
                null
            }
            else -> TagWriteResult.Failed("Not a file")
        }
    } catch (e: FileNotFoundException) {
        // there, but not to be written: no access to all files, a card read only, a grant to read only
        if (uri.scheme == "content" || File(uri.path.orEmpty()).isFile) TagWriteResult.NeedsAccess else TagWriteResult.NotFound
    } catch (e: SecurityException) {
        TagWriteResult.NeedsAccess
    } catch (e: Exception) {
        // an app that hands over a stream alone (UnsupportedOperationException), a provider that failed
        TagWriteResult.NeedsAccess
    }
}

/**
 * 1.2.0: what was written told to the library and to the system, a moment after the last write (a batch writes many
 * files of one folder): the medialibrary looks over each file's folder again (a file changed since its last look is
 * read again: its new title, artist, album), the system's index reads the files for the other apps, and the views read
 * their tags again ([TagCenter.written]).
 */
class LibraryIndexer(context: Context, private val quietMs: Long = QUIET_MS) : MediaIndexer {
    private val context = context.applicationContext
    private val lock = Any()
    private val pending = LinkedHashSet<File>()
    private var flush: Job? = null

    override suspend fun rescan(refs: List<ContentRef>) {
        val files = refs.mapNotNull { ref -> Uri.parse(ref.uri).takeIf { it.scheme == null || it.scheme == "file" }?.path?.let(::File) }
        synchronized(lock) {
            pending += files
            flush?.cancel()
            flush = AppScope.launch(Dispatchers.IO) {
                delay(quietMs)
                val batch = synchronized(lock) { pending.toList().also { pending.clear() } }
                tell(batch)
            }
        }
        TagCenter.changed()
    }

    private fun tell(files: List<File>) {
        val library = Medialibrary.getInstance()
        if (library.isStarted) {
            files.mapNotNull { it.parentFile }.distinct().forEach { folder -> runCatching { library.reload(Uri.fromFile(folder).toString() + "/") } }
        }
        if (files.isNotEmpty()) runCatching { MediaScannerConnection.scanFile(context, files.map { it.path }.toTypedArray(), null, null) }
        TagCenter.changed()
    }

    private companion object {
        const val QUIET_MS = 1_500L
    }
}

/**
 * 1.2.0: the editor of the files last asked for, kept while the app runs: a panel closed, a tab changed or a page left
 * keeps what was typed. Asked for other files while its changes are not saved, it stays ([TagEditorModel.dirty]); the
 * screen says so, and the new files come once they are saved or dropped.
 */
object TagSessions {
    private var current: TagEditorModel? = null

    fun open(files: TagFiles, targets: List<TagTarget>, style: () -> MultiValueStyle): TagEditorModel {
        val now = current
        if (now != null && now.files === files && (now.targets == targets || now.dirty || now.saving)) return now
        return TagEditorModel(targets, files, style).also {
            current = it
            it.load()
        }
    }

    /** The tests' sessions go with their fakes. */
    internal fun forget() {
        current = null
    }
}
