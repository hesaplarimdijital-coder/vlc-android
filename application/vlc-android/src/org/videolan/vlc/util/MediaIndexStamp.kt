package org.videolan.vlc.util

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import app.nagmedar.library.MediaStamp

/**
 * 1.0.3: the system's media index of audio as one [MediaStamp] (a query per volume, a few
 * milliseconds): what an ordinary start compares to decide whether the library's folders need a
 * look. Null when the index cannot be read (no media access, a volume that went while asked, a
 * system before Android 11, which has no generations).
 */
object MediaIndexStamp {

    @WorkerThread
    fun read(context: Context): String? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) null else runCatching {
        val resolver = context.contentResolver
        val volumes = MediaStore.getExternalVolumeNames(context).sorted().map { volume ->
            val uri = MediaStore.Audio.Media.getContentUri(volume)
            // the highest generation: one row of the newest change
            val newest = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, LIBRARY_AUDIO)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
            }
            val generation = resolver.query(uri, arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED), newest, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: return@runCatching null
            // the number of files, which a deletion lowers (a deleted file leaves no generation behind)
            val all = Bundle().apply { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, LIBRARY_AUDIO) }
            val count = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), all, null)?.use { it.count } ?: return@runCatching null
            MediaStamp.VolumeCount(volume, count, generation)
        }
        MediaStamp.of(volumes)
    }.getOrNull()

    /**
     * The audio the library takes: not under Android/ (the medialibrary bans Android/data and
     * Android/media, where messengers keep their voice notes) and no ringtone, alarm or notification
     * sound, so that their comings and goings do not make the library look again.
     */
    private const val LIBRARY_AUDIO = "(${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE 'Android/%') AND " +
        "${MediaStore.Audio.AudioColumns.IS_RINGTONE} = 0 AND ${MediaStore.Audio.AudioColumns.IS_ALARM} = 0 AND " +
        "${MediaStore.Audio.AudioColumns.IS_NOTIFICATION} = 0"
}
