/*
 * Nağmedar (S-1301): a saved network server's sign-in for what plays from it.
 */
package org.videolan.resources

import android.net.Uri

/**
 * The libVLC options that sign in to the network server [media] is on: ":smb-user=…" and
 * ":smb-pwd=…", the FTP and SFTP ones likewise, from the servers saved in Nağmedar's network
 * browser. Options are handed to libVLC with the media and kept nowhere, so the password is never
 * in the address the history, the queue or the logs keep. Nağmedar's new interface sets it.
 */
interface NetworkMediaOptions {
    /** The options for [media]; empty when no saved server holds it. Called off the main thread. */
    fun optionsFor(media: Uri): List<String>
}

object NetworkMediaHook {
    @Volatile
    var options: NetworkMediaOptions? = null
}
