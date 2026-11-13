package app.nagmedar.ui.search

/** Harness copy of SearchScreen.kt's isOnNetwork (android.net.Uri there): a scheme that is not a file's or a document's. */
fun isOnNetwork(key: String): Boolean {
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(key)?.groupValues?.get(1)?.lowercase()
    return scheme !in setOf(null, "file", "content")
}
