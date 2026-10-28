package app.nagmedar.ui.transcribe

import java.io.File

/**
 * [bytes] in a NEW file named [name] in [folder], or "Name (2).srt" when that is taken. The name
 * is claimed with an atomic create, so a file that exists is never returned, written or replaced;
 * null when every name is taken, when the file would be [source] itself, or when the write fails
 * (the file made for it is removed then).
 */
internal fun writeNewFile(folder: File, name: String, bytes: ByteArray, source: File?, write: (File, ByteArray) -> Unit = File::writeBytes): File? =
    writeNewFile(folder, name, source) { file -> write(file, bytes) }

/** As the one above, [write] filling the new file itself: a large file is copied into it, never held whole. */
internal fun writeNewFile(folder: File, name: String, source: File?, write: (File) -> Unit): File? {
    val file = createUniqueFile(folder, name) ?: return null
    if (source != null && sameFile(file, source)) return null
    val written = runCatching { write(file) }.isSuccess
    if (!written) {
        runCatching { file.delete() }
        return null
    }
    return file
}

/** An empty new file named [name] in [folder] (or "Name (2).srt" …), made atomically; null when all [MAX_COPIES] names are taken. */
internal fun createUniqueFile(folder: File, name: String): File? {
    val base = name.substringBeforeLast('.')
    val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    for (n in 1..MAX_COPIES) {
        val file = File(folder, if (n == 1) name else "$base ($n)$extension")
        if (runCatching { file.createNewFile() }.getOrDefault(false)) return file
    }
    return null
}

private fun sameFile(a: File, b: File): Boolean =
    runCatching { a.canonicalPath == b.canonicalPath }.getOrDefault(a.absolutePath == b.absolutePath)

internal const val MAX_COPIES = 99
