package app.nagmedar.ui.tags

import app.nagmedar.tags.AudioProperties
import app.nagmedar.tags.BackupId
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.TagBackup
import app.nagmedar.tags.TagBackupStore
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReadResult
import app.nagmedar.tags.TagReader
import app.nagmedar.tags.TagSnapshot
import app.nagmedar.tags.TagWriteResult
import app.nagmedar.tags.TagWriter
import app.nagmedar.tags.files.MediaIndexer
import kotlinx.coroutines.CoroutineScope

/** 1.2.0: files' tags in memory, read and written as TagLib would: a key left out of a write is gone. */
class MemoryTags : TagReader, TagWriter {
    val files = LinkedHashMap<ContentRef, TagSnapshot>()

    /** What every write comes to instead, when set (a file the app may not write, a failure). */
    var refuse: TagWriteResult? = null

    val written = mutableListOf<ContentRef>()

    fun put(ref: ContentRef, vararg tags: Pair<String, List<String>>, pictures: List<EmbeddedPicture> = emptyList()) {
        files[ref] = TagSnapshot(TagMap.of(*tags), pictures)
    }

    fun tags(ref: ContentRef): TagMap = files.getValue(ref).tags

    override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult {
        val snapshot = files[ref] ?: return TagReadResult.NotFound
        return TagReadResult.Success(if (includePictures) snapshot else snapshot.copy(pictures = emptyList()))
    }

    override suspend fun readAudioProperties(ref: ContentRef): AudioProperties? =
        if (ref in files) AudioProperties(durationMs = 215_000, bitrateKbps = 1_011, sampleRateHz = 44_100, channels = 2) else null

    override suspend fun write(ref: ContentRef, tags: TagMap, pictures: List<EmbeddedPicture>?): TagWriteResult {
        refuse?.let { return it }
        val now = files[ref] ?: return TagWriteResult.NotFound
        files[ref] = TagSnapshot(tags, pictures ?: now.pictures)
        written += ref
        return TagWriteResult.Written
    }
}

/** The backups in memory, the newest with the highest id. */
class MemoryBackups : TagBackupStore {
    val kept = LinkedHashMap<Long, TagBackup>()
    private var next = 1L

    override suspend fun save(ref: ContentRef, snapshot: TagSnapshot, createdAtMillis: Long): BackupId {
        val id = BackupId(next++)
        kept[id.value] = TagBackup(id, ref, snapshot, createdAtMillis)
        return id
    }

    override suspend fun load(id: BackupId): TagBackup? = kept[id.value]

    override suspend fun history(ref: ContentRef): List<TagBackup> = kept.values.filter { it.ref == ref }.sortedByDescending { it.id.value }

    override suspend fun delete(id: BackupId) {
        kept.remove(id.value)
    }

    override suspend fun clear() = kept.clear()

    override suspend fun prune(keep: Int) {
        kept.keys.sortedDescending().drop(keep).forEach { kept.remove(it) }
    }
}

/** What the library was told to look at again. */
class RecordingIndexer : MediaIndexer {
    val rescanned = mutableListOf<ContentRef>()

    override suspend fun rescan(refs: List<ContentRef>) {
        rescanned += refs
    }
}

/** The editor's files over [tags], in [scope]; the covers looked for again are counted in [coversFound]. */
class FakeTagFiles(val tags: MemoryTags = MemoryTags(), scope: CoroutineScope) {
    val backups = MemoryBackups()
    val indexer = RecordingIndexer()
    var coversFound = 0
    val files = TagFiles(tags, tags, backups, indexer, scope, covers = { refs, _, _ -> coversFound += refs.size })
}

/** A file of the samples. */
fun sampleRef(name: String) = ContentRef("file:///storage/emulated/0/Music/Mo%C4%9Follar/$name")

fun sampleTarget(name: String, title: String? = null) = TagTarget(sampleRef(name), name, title)
