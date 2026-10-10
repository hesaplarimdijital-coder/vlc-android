package org.videolan.vlc.media

import app.nagmedar.playback.EngineMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper

private val LOCAL_SCHEMES = setOf("file", "content")

/** What the engine router needs to know about a medialibrary item (S-501). */
fun MediaWrapper.toEngineMedia(): EngineMedia {
    val uri = uri
    return EngineMedia(
        uri = uri?.toString() ?: location ?: "",
        isAudio = type == MediaWrapper.TYPE_AUDIO,
        isLocal = uri?.scheme in LOCAL_SCHEMES || location?.startsWith("/") == true,
    )
}
