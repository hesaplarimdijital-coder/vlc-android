package app.nagmedar.tags.files

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Nağmedar 1.4.0: records kept in a file, one a line, by key, for what the library's checks found (Metamatik keeps
 * them in Room). A record saved is added at the end, a key removed is a line of its own, and what is read is each
 * key's last line: a write is a line, however large the library. The file is written again whole, holding only what
 * is kept, when it has grown to twice that and more; a line cut short by the app being stopped is skipped.
 *
 * A record is its key and its fields, none of them holding a tab or a line break.
 */
class LineStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()

    /** What is kept, by key, each record's fields in the order they were saved. */
    suspend fun load(): Map<String, List<String>> = lock.withLock {
        withContext(io) {
            if (!file.isFile) return@withContext emptyMap()
            val kept = LinkedHashMap<String, List<String>>()
            var lines = 0
            file.forEachLine(Charsets.UTF_8) { line ->
                lines++
                val parts = line.split(SEPARATOR)
                when {
                    parts.size >= 2 && parts[0] == SAVED -> kept[parts[1]] = parts.drop(2)
                    parts.size == 2 && parts[0] == REMOVED -> kept.remove(parts[1])
                    // a line cut short, or of a version that wrote another kind: passed over
                }
            }
            if (lines > kept.size * 2 + SLACK) rewrite(kept)
            kept
        }
    }

    /** Keeps [records] (key to fields), each in place of what its key had. */
    suspend fun save(records: List<Pair<String, List<String>>>) {
        if (records.isEmpty()) return
        append(records.joinToString("") { (key, fields) -> line(SAVED, key, fields) })
    }

    /** Forgets what [keys] had. */
    suspend fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        append(keys.joinToString("") { line(REMOVED, it, emptyList()) })
    }

    private suspend fun append(text: String) = lock.withLock {
        withContext(io) {
            file.parentFile?.mkdirs()
            file.appendText(text, Charsets.UTF_8)
        }
    }

    /** What is kept, written whole to a file beside it, which then takes its place. */
    private fun rewrite(kept: Map<String, List<String>>) {
        val next = File(file.parentFile, file.name + ".new")
        runCatching {
            next.bufferedWriter(Charsets.UTF_8).use { out -> kept.forEach { (key, fields) -> out.write(line(SAVED, key, fields)) } }
            if (!next.renameTo(file)) next.delete()
        }.onFailure { next.delete() }
    }

    private fun line(kind: String, key: String, fields: List<String>): String {
        require((fields + key).none { SEPARATOR in it || '\n' in it || '\r' in it }) { "a tab or a line break in $key" }
        return (listOf(kind, key) + fields).joinToString(SEPARATOR) + "\n"
    }

    private companion object {
        const val SEPARATOR = "\t"
        const val SAVED = "+"
        const val REMOVED = "-"

        /** Lines over twice the records before the file is written again: a small library is not rewritten at each start. */
        const val SLACK = 64
    }
}
