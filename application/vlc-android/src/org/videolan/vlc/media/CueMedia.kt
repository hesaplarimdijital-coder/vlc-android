package org.videolan.vlc.media

import android.content.Context
import android.net.Uri
import app.nagmedar.audio.cue.CueFile
import app.nagmedar.audio.cue.CueSheet
import app.nagmedar.audio.cue.CueSheets
import app.nagmedar.audio.cue.CueTrack
import app.nagmedar.audio.cue.CueUri
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * S-808: a CUE sheet's tracks as items the playback service, the queue and the library carry:
 * medialibrary-less [MediaWrapper]s named "file#cue=<n>" ([CueUri]), with what the sheet says
 * of each (title, performer, the album and its performer, genre, year, numbers) and the part's
 * length. The engine plays the part the sheet gives ([CueSheets]).
 */
object CueMedia {

    /** The items of the tracks of [image] (a whole-file item) in its sheet; null when no sheet gives it two tracks or more. */
    fun expand(context: Context, image: MediaWrapper, siblings: List<Pair<String, String>>? = null): List<MediaWrapper>? {
        val uri = image.uri ?: return null
        if (CueUri.isTrack(uri)) return null
        val found = if (siblings != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: return null
            CueSheets.among(context, name, siblings)
        } else {
            CueSheets.forFile(context, uri)
        } ?: return null
        val (sheet, file) = found.first.sheet to found.second
        return file.tracks.map { track -> wrapper(uri, sheet, file, track, image.length, image.lastModified, image.insertionDate, image.artworkMrl) }
    }

    /** The item of the track [uri] names, from the sheet beside its file; null when there is none. */
    fun restore(context: Context, uri: Uri, fileLengthMs: Long = 0L): MediaWrapper? {
        val number = CueUri.trackNumber(uri) ?: return null
        val file = CueUri.file(uri)
        val (found, cueFile) = CueSheets.forFile(context, file) ?: return null
        val track = cueFile.tracks.firstOrNull { it.number == number } ?: return null
        return wrapper(file, found.sheet, cueFile, track, fileLengthMs, 0L, 0L, null)
    }

    /** [track] of [sheet], in [file] (which lasts [fileLengthMs], for the last track's length), as an item. */
    fun wrapper(file: Uri, sheet: CueSheet, cueFile: CueFile, track: CueTrack, fileLengthMs: Long, lastModified: Long, insertionDate: Long, artwork: String?): MediaWrapper {
        val startMs = track.from * 1000 / CueSheet.FRAMES_PER_SECOND
        val length = track.lengthMs ?: (fileLengthMs - startMs).coerceAtLeast(0L)
        val albumArtist = sheet.performer
        val artist = track.performer ?: albumArtist
        val title = track.title ?: "${cueFile.name.substringBeforeLast('.')} ${track.number}"
        val uri = CueUri.of(file, track.number)
        return MLServiceLocator.getAbstractMediaWrapper(
            0L, uri.toString(), 0L, 0f, length, MediaWrapper.TYPE_AUDIO, title, file.lastPathSegment?.substringAfterLast('/'), 0L, 0L, artist,
            sheet.genre, 0L, sheet.title, albumArtist, 0, 0, artwork, -2, -2, track.number,
            sheet.discNumber ?: 0, lastModified, 0L, false, false, sheet.year ?: 0, true, insertionDate,
        )
    }
}
