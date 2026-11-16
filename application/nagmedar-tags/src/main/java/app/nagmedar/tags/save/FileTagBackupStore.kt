package app.nagmedar.tags.save

import app.nagmedar.tags.BackupId
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.PictureType
import app.nagmedar.tags.TagBackup
import app.nagmedar.tags.TagBackupStore
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagSnapshot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1.2.0: the backups of Metamatik's saves, kept in [folder] as files (Metamatik kept them in Room): a backup's tags and
 * where its pictures are in `<id>.json`, each picture in `<id>-<n>.pic` beside it. The newest have the highest ids.
 * Written to a temporary name and renamed, so a backup is either whole or not there.
 */
class FileTagBackupStore(
    private val folder: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TagBackupStore {
    private val lock = Mutex()

    override suspend fun save(ref: ContentRef, snapshot: TagSnapshot, createdAtMillis: Long): BackupId = withContext(io) {
        lock.withLock {
            val dir = dir()
            val id = (ids(dir).maxOrNull() ?: 0L) + 1
            val pictures = JSONArray()
            snapshot.pictures.forEachIndexed { n, picture ->
                val name = "$id-$n.pic"
                writeWhole(File(dir, name), picture.data)
                pictures.put(
                    JSONObject()
                        .put("file", name)
                        .put("mime", picture.mimeType)
                        .put("description", picture.description)
                        .put("type", picture.type.name),
                )
            }
            val tags = JSONObject()
            snapshot.tags.toMap().forEach { (key, values) -> tags.put(key, JSONArray(values)) }
            val json = JSONObject()
                .put("ref", ref.uri)
                .put("created", createdAtMillis)
                .put("tags", tags)
                .put("pictures", pictures)
            writeWhole(File(dir, "$id.json"), json.toString().toByteArray(Charsets.UTF_8))
            BackupId(id)
        }
    }

    override suspend fun load(id: BackupId): TagBackup? = withContext(io) { lock.withLock { read(dir(), id.value) } }

    override suspend fun history(ref: ContentRef): List<TagBackup> = withContext(io) {
        lock.withLock {
            val dir = dir()
            ids(dir).sortedDescending().mapNotNull { read(dir, it) }.filter { it.ref == ref }
        }
    }

    override suspend fun delete(id: BackupId) = withContext(io) { lock.withLock { remove(dir(), id.value) } }

    override suspend fun clear() = withContext(io) {
        lock.withLock {
            val dir = dir()
            ids(dir).forEach { remove(dir, it) }
        }
    }

    override suspend fun prune(keep: Int) = withContext(io) {
        lock.withLock {
            val dir = dir()
            ids(dir).sortedDescending().drop(keep.coerceAtLeast(0)).forEach { remove(dir, it) }
        }
    }

    /** How many backups there are, and their files' bytes: for Settings. */
    suspend fun size(): Pair<Int, Long> = withContext(io) {
        lock.withLock {
            val dir = dir()
            ids(dir).size to (dir.listFiles()?.sumOf { it.length() } ?: 0L)
        }
    }

    private fun dir(): File = folder().also { if (!it.isDirectory && !it.mkdirs()) throw IOException("No folder for backups: $it") }

    private fun ids(dir: File): List<Long> =
        dir.list()?.mapNotNull { name -> name.removeSuffix(".json").takeIf { name.endsWith(".json") }?.toLongOrNull() }.orEmpty()

    private fun read(dir: File, id: Long): TagBackup? {
        val file = File(dir, "$id.json")
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val tags = json.getJSONObject("tags")
            val map = buildMap {
                tags.keys().forEach { key ->
                    val values = tags.getJSONArray(key)
                    put(key, List(values.length()) { values.getString(it) })
                }
            }
            val pictures = json.getJSONArray("pictures")
            val pics = List(pictures.length()) { n ->
                val picture = pictures.getJSONObject(n)
                EmbeddedPicture(
                    data = File(dir, picture.getString("file")).readBytes(),
                    mimeType = picture.getString("mime"),
                    description = picture.optString("description"),
                    type = runCatching { PictureType.valueOf(picture.getString("type")) }.getOrDefault(PictureType.FrontCover),
                )
            }
            TagBackup(BackupId(id), ContentRef(json.getString("ref")), TagSnapshot(TagMap.of(map), pics), json.getLong("created"))
        } catch (e: Exception) {
            // a backup that does not read whole is none
            null
        }
    }

    private fun remove(dir: File, id: Long) {
        File(dir, "$id.json").delete()
        dir.listFiles { _, name -> name.startsWith("$id-") && name.endsWith(".pic") }?.forEach { it.delete() }
    }

    private fun writeWhole(file: File, bytes: ByteArray) {
        val part = File(file.parentFile, file.name + ".part")
        part.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!part.renameTo(file)) {
            part.delete()
            throw IOException("Could not keep the backup: $file")
        }
    }
}
