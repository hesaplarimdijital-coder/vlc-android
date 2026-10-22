package app.nagmedar.audio

/**
 * S-1005: what becomes of the copy that a process which died while writing a file's tags left
 * beside it ([TagBackups.recover]), when the file does not read as it did. The copy goes back
 * only over what that write left: never over a file changed elsewhere since (an SD card edited on
 * a computer), whose copy is kept where the user sees it instead, as is the copy of a file not
 * found for [KEEP_MS].
 */
internal object TagLeftovers {

    /** A file as fstat saw it: its size in bytes and its last change, in seconds since 1970. */
    data class Stat(val size: Long, val modifiedS: Long)

    /** What becomes of the copy. */
    enum class Fate {
        /** The interrupted write was the last to change the file: the copy goes back over it. */
        PutBack,

        /** The file is not there now (a card taken out, a permission taken back): the copy waits for it. */
        Wait,

        /** The file stays as it is (changed elsewhere since, or gone for [KEEP_MS]): the copy goes into the folder the user sees. */
        KeepAside,
    }

    /** A copy whose file is not there is kept this long for it, then moved where the user sees it. */
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /** The longest a write runs (TagLib moving the audio of a big file on a slow card): a change later than this is another's. */
    const val WRITE_MS = 60L * 60 * 1000

    /** How coarse a modification time is: whole seconds, and two of them on a FAT card. */
    const val SLACK_MS = 5_000L

    /** The copy of a file that is not there now, its write begun at [startedMs]: it waits [KEEP_MS], then is kept aside. */
    fun missing(startedMs: Long, nowMs: Long): Fate = if (nowMs - startedMs > KEEP_MS) Fate.KeepAside else Fate.Wait

    /**
     * The copy of a file that is there but does not read as it did: put back when the file's last change ([now]; null
     * when fstat could not tell) came while the write begun at [startedMs] could have run, and the file is not as it was
     * before that write ([before]; null in a note that did not keep it), else kept aside.
     */
    fun damaged(now: Stat?, before: Stat?, startedMs: Long): Fate {
        if (now == null || now == before) return Fate.KeepAside
        val modifiedMs = now.modifiedS * 1000
        return if (modifiedMs >= startedMs - SLACK_MS && modifiedMs <= startedMs + WRITE_MS) Fate.PutBack else Fate.KeepAside
    }

    /**
     * [name] as a file name of its own in a folder that has the names [taken] says it has: "Gece.flac", else
     * "Gece (2).flac", "Gece (3).flac"… No separator, and never a name that leaves the folder.
     */
    fun freeName(name: String, taken: (String) -> Boolean): String {
        val clean = name.replace('/', '_').replace('\u0000', '_').trim().takeUnless { it.all { c -> c == '.' } } ?: "_"
        if (!taken(clean)) return clean
        val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: clean.length
        val stem = clean.substring(0, dot)
        val extension = clean.substring(dot)
        return generateSequence(2) { it + 1 }.map { "$stem ($it)$extension" }.first { !taken(it) }
    }
}
