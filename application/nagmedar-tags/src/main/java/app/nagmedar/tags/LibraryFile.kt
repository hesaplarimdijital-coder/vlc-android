package app.nagmedar.tags

/**
 * Nağmedar 1.4.0: a song's file as the library's background checks see it (Metamatik's Track has more): where it is,
 * what its name and MIME type say it is, and what tells that it changed since it was checked: its size and when it
 * was last written. Where those are not known they are 0, and a file is checked once.
 */
data class LibraryFile(
    val ref: ContentRef,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val dateModifiedEpochSeconds: Long = 0,
) {
    /** What its name ends in, lower case: "flac". */
    val fileExtension: String
        get() = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}
