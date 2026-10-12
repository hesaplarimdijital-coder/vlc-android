/*
 * Nağmedar (S-507): films the hardware decoder would show wrong are decoded in software.
 */
package org.videolan.resources

import android.content.Context
import android.net.Uri

/**
 * Says, before a film starts, whether it must be decoded in software: a Dolby Vision profile 5
 * film, whose colours only libplacebo's reshaping on the OpenGL output gets right, and which
 * the hardware decoder would tint purple and green. Nağmedar's new interface sets it; without
 * it the settings stand.
 */
interface SoftwareDecodingAdviser {
    suspend fun needsSoftwareDecoding(context: Context, media: Uri): Boolean
}

object DecodingHook {
    @Volatile
    var adviser: SoftwareDecodingAdviser? = null
}
