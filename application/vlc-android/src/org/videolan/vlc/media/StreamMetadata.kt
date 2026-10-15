package org.videolan.vlc.media

import app.nagmedar.playback.RadioMetadata
import app.nagmedar.playback.StreamDisplay
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * S-1106: what a radio stream says is playing, as the notification, the lock screen and the media
 * session show it ("Artist - Title" split, the station under it); null for anything but a stream
 * that sends a title, which shows as it always did.
 */
fun MediaWrapper.streamDisplay(): StreamDisplay? =
    if (type == MediaWrapper.TYPE_STREAM && nowPlaying != null) RadioMetadata.display(title, nowPlaying).takeIf { it.hasTrack } else null

/** S-1106: the logo of a radio stream as artwork: any stream that has a web one, with a title or without. */
fun MediaWrapper.streamLogo(): String? = RadioMetadata.logo(type == MediaWrapper.TYPE_STREAM, artworkMrl)
