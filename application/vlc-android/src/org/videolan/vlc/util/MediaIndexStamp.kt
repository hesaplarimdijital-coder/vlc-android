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
            // the highest generation first: one row, and the count of every row that answers
            val args = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
            }
            val generation = resolver.query(uri, arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED), args, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: return@runCatching null
            val count = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null)?.use { it.count } ?: return@runCatching null
            MediaStamp.VolumeCount(volume, count, generation)
        }
        MediaStamp.of(volumes)
    }.getOrNull()
}
