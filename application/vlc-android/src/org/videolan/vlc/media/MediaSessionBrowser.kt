/*
 * ************************************************************************
 *  MediaSessionBrowser.kt
 * *************************************************************************
 *  Copyright © 2016-2020 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */
package org.videolan.vlc.media

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.media.utils.MediaConstants
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.CONTENT_STYLE_BROWSABLE_HINT
import org.videolan.resources.CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_GRID_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_PLAYABLE_HINT
import org.videolan.resources.EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT

/**
 * The media session's browse tree, Android Auto's library. The mediaId is an opaque string token; it is kept as a
 * URI-like string prefixed with the app's namespace so that ids of several apps never mix.
 *
 * 1.0: the tree is Nağmedar's ([CarCatalog], kept by the new interface's module): the root has exactly four entries,
 * Auto's tabs ([ROOT_TABS]: Music, Podcasts, Radio, Recently played), and everything under them is the catalog's. VLC's
 * own nodes (home, shuffle all, last added, library, genres, streams) are gone. What plays keeps its medialibrary id
 * ([generateMediaId]) in the session's metadata and queue.
 */
class MediaSessionBrowser {

    companion object {
        // Root item
        // MediaIds are all strings. Maintain in uri parsable format.
        /**
         * 1.0.3: one root for every build. It was "//" + BuildConfig.APP_ID + "/r", a constant that each module inlines with
         * the build type it was compiled as: nagmedar-ui has no personal build type, so the personal APK's catalog
         * (nagmedar-ui's release classes) made "//app.nagmedar/r/…" ids under the "//app.nagmedar.debug/r" root the service
         * gave, and Android Auto found no tabs, only its search. Ids of those builds are read through [canonical].
         */
        const val ID_ROOT = "//app.nagmedar/r"
        const val ID_MEDIA = "$ID_ROOT/media"
        const val ID_SUGGESTED = "$ID_ROOT/suggested"
        const val ID_NO_MEDIA = "$ID_ROOT/error/media"

        // 1.0: Auto's tabs
        const val ID_MUSIC = "$ID_ROOT/music"
        const val ID_PODCASTS = "$ID_ROOT/podcasts"
        const val ID_RADIO = "$ID_ROOT/radio"
        const val ID_RECENT = "$ID_ROOT/recent"

        /** The root's entries, Auto's tabs, in their order. */
        val ROOT_TABS = listOf(ID_MUSIC, ID_PODCASTS, ID_RADIO, ID_RECENT)

        /** 1.0.3: the roots the debug, benchmark and personal builds of 1.0–1.0.2 gave Android Auto and the system. */
        private val EARLIER_ROOTS = listOf("//app.nagmedar.debug/r", "//app.nagmedar.benchmark/r")

        /**
         * 1.0.3: [mediaId] under today's [ID_ROOT]. An id of 1.0–1.0.2 whose root named the build type's application id
         * (what Auto or the system's media resumption kept from then) is read as the same entry today; any other id is
         * returned as it is.
         */
        fun canonical(mediaId: String): String {
            for (earlier in EARLIER_ROOTS) {
                if (mediaId == earlier || mediaId.startsWith("$earlier/") || mediaId.startsWith("$earlier?")) {
                    return ID_ROOT + mediaId.substring(earlier.length)
                }
            }
            return mediaId
        }

        /**
         * The entries under [parentId], as the catalog makes them; none without it (a build without the new interface).
         * The suggestions (the system's media resumption, the quick search box) are the tracks played last.
         */
        @WorkerThread
        suspend fun browse(context: Context, parentId: String): List<MediaBrowserCompat.MediaItem> {
            val catalog = CarHook.catalog ?: return emptyList()
            val id = canonical(parentId).let { if (it == ID_SUGGESTED) ID_RECENT else it }
            return catalog.children(context, id)?.map(::toMediaItem).orEmpty()
        }

        /** 1.0.3: the entry [mediaId] alone (Auto reads one again after a browse action changed it); null when unknown. */
        @WorkerThread
        suspend fun item(context: Context, mediaId: String): MediaBrowserCompat.MediaItem? =
            CarHook.catalog?.item(context, canonical(mediaId))?.let(::toMediaItem)

        /**
         * 1.0.3: the custom browse actions as the root's extras give them to Auto (androidx.media 1.7): one bundle each, its
         * id, label and icon; none when the catalog offers none.
         */
        fun browseActions(context: Context): ArrayList<Bundle> = CarHook.catalog?.browseActions(context).orEmpty().mapTo(ArrayList()) { action ->
            Bundle().apply {
                putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID, action.id)
                putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL, action.label)
                putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI, action.iconUri)
            }
        }

        /**
         * 1.0.3: Auto's custom browse action [action] on the entry [mediaId], done by the catalog: the result's bundle (the
         * message Auto shows, the entry it reads again), or null when the action is none of the catalog's.
         */
        @WorkerThread
        suspend fun act(context: Context, action: String, mediaId: String): Bundle? {
            val done = CarHook.catalog?.act(context, action, canonical(mediaId)) ?: return null
            return Bundle().apply {
                putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE, done.message)
                done.refresh?.let { putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM, it) }
            }
        }

        /** Android Auto's search: the catalog's results, grouped under their headings. */
        @WorkerThread
        suspend fun search(context: Context, query: String): List<MediaBrowserCompat.MediaItem> =
            CarHook.catalog?.search(context, query)?.map(::toMediaItem).orEmpty()

        /**
         * [item] as the browser's: its flags, its artwork, how its own entries are drawn, its search heading, an episode's
         * progress, the browse actions it offers.
         */
        fun toMediaItem(item: CarItem): MediaBrowserCompat.MediaItem {
            val extras = Bundle()
            item.children?.let { style ->
                val hint = when (style) {
                    CarStyle.List -> CONTENT_STYLE_LIST_ITEM_HINT_VALUE
                    CarStyle.Grid -> CONTENT_STYLE_GRID_ITEM_HINT_VALUE
                    CarStyle.Category -> CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE
                }
                extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, hint)
                extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, if (style == CarStyle.Grid) CONTENT_STYLE_GRID_ITEM_HINT_VALUE else CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            }
            item.group?.let { extras.putString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT, it) }
            item.progress?.let { progress ->
                val status = when {
                    item.played -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                    progress > 0.0 -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    else -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                }
                extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS, status)
                if (status == MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED) {
                    extras.putDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, progress.coerceIn(0.0, 1.0))
                }
            }
            if (item.downloaded) extras.putLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS, MediaDescriptionCompat.STATUS_DOWNLOADED)
            // 1.0.3: the custom browse actions Auto offers on it ("Abone ol" on a show not followed)
            if (item.actions.isNotEmpty()) extras.putStringArrayList(MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST, ArrayList(item.actions))
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(item.id)
                .setTitle(item.title)
                .setSubtitle(item.subtitle)
                .setIconUri(item.iconUri?.toUri())
                .setExtras(extras.takeUnless { it.isEmpty })
                .build()
            var flags = 0
            if (item.browsable) flags = flags or MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            if (item.playable) flags = flags or MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            return MediaBrowserCompat.MediaItem(description, flags)
        }

        /** How the root's entries are drawn: Auto's default for every list that does not say. */
        fun getContentStyle(browsableHint: Int = CONTENT_STYLE_LIST_ITEM_HINT_VALUE, playableHint: Int = CONTENT_STYLE_LIST_ITEM_HINT_VALUE): Bundle {
            return Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
            }
        }

        /** Whether [mediaId] is a line that plays nothing (an empty list's): [ID_NO_MEDIA], with the reason it gives after "?". */
        fun isNoMedia(mediaId: String): Boolean = canonical(mediaId).let { it == ID_NO_MEDIA || it.startsWith("$ID_NO_MEDIA?") }

        /** The id a medialibrary media is known by in the session's metadata and queue. */
        fun generateMediaId(libraryItem: MediaLibraryItem): String = "$ID_MEDIA/${libraryItem.id}"
    }
}
