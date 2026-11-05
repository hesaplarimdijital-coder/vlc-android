package app.nagmedar.library

/**
 * 1.0.3: whether an ordinary start of the app looks over the music folders again (the
 * medialibrary's reload, a walk of every folder) or trusts what it knows. The library is scanned
 * once; afterwards a start compares the system's media index ([MediaStamp], one cheap query)
 * with what it was after the last look, and walks the folders only when it changed, when the
 * last look is older than [MAX_AGE_MS] (a file the index missed, a folder of a drive), or when no
 * look was ever finished.
 */
object LibraryRecheck {

    /** How long a finished look is trusted while the media index says nothing changed. */
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * [now] (the wall clock) and the index as it is ([current], null when it could not be read)
     * against the last finished look: when it was ([lastAt], 0 for never) and the index then
     * ([last]). A clock set back counts as an old look.
     */
    fun needed(current: String?, last: String?, lastAt: Long, now: Long): Boolean = when {
        current == null || last == null || lastAt <= 0L -> true
        current != last -> true
        now < lastAt -> true
        else -> now - lastAt >= MAX_AGE_MS
    }
}

/**
 * The system's media index of audio in a few characters: for each volume its number of audio
 * files and the highest generation it gave a change to them. A file added, changed or removed
 * moves one of them (Android 11 and later count every change in the generation).
 */
object MediaStamp {

    /** The stamp of [volumes]: name, count and highest generation each; the same volumes in any order give the same stamp. */
    fun of(volumes: List<VolumeCount>): String =
        volumes.sortedBy { it.name }.joinToString(";") { "${it.name}:${it.count}:${it.generation}" }

    /** One volume's audio files as the media index counts them. */
    data class VolumeCount(val name: String, val count: Int, val generation: Long)
}
