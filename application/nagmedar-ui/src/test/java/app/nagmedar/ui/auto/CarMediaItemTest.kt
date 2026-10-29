package app.nagmedar.ui.auto

import android.support.v4.media.MediaDescriptionCompat
import androidx.media.utils.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.resources.CONTENT_STYLE_BROWSABLE_HINT
import org.videolan.resources.CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_GRID_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_LIST_ITEM_HINT_VALUE
import org.videolan.resources.CONTENT_STYLE_PLAYABLE_HINT
import org.videolan.resources.EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import org.videolan.vlc.media.MediaSessionBrowser

/** 1.0: the catalog's entries as Android Auto's browser gets them: flags, how their lists are drawn, headings, an episode's progress. */
@RunWith(AndroidJUnit4::class)
class CarMediaItemTest {

    @Test
    fun aBrowsableEntrySaysHowItsListIsDrawn() {
        val albums = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.ALBUMS, title = "Albümler", iconUri = "android.resource://app/drawable/x", browsable = true, children = CarStyle.Grid))
        assertTrue(albums.isBrowsable)
        assertFalse(albums.isPlayable)
        assertEquals(CarIds.ALBUMS, albums.mediaId)
        assertEquals("android.resource://app/drawable/x", albums.description.iconUri.toString())
        assertEquals(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, albums.description.extras!!.getInt(CONTENT_STYLE_BROWSABLE_HINT))
        assertEquals(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, albums.description.extras!!.getInt(CONTENT_STYLE_PLAYABLE_HINT))

        val music = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.MUSIC, title = "Müzik", browsable = true, children = CarStyle.Category))
        assertEquals(CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE, music.description.extras!!.getInt(CONTENT_STYLE_BROWSABLE_HINT))
        assertEquals(CONTENT_STYLE_LIST_ITEM_HINT_VALUE, music.description.extras!!.getInt(CONTENT_STYLE_PLAYABLE_HINT))
    }

    @Test
    fun aTrackIsAPlayableRowWithNothingElse() {
        val track = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.track(CarIds.RECENT, 0), title = "Gülümse", subtitle = "Sezen Aksu", playable = true))
        assertTrue(track.isPlayable)
        assertFalse(track.isBrowsable)
        assertEquals("Sezen Aksu", track.description.subtitle)
        assertNull(track.description.extras)
    }

    @Test
    fun anEpisodeCarriesHowFarItWasHeardAndItsDownload() {
        val begun = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.episode(1), title = "Bölüm", playable = true, progress = 0.25, downloaded = true)).description.extras!!
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED, begun.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS))
        assertEquals(0.25, begun.getDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE), 0.0)
        assertEquals(MediaDescriptionCompat.STATUS_DOWNLOADED, begun.getLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS))

        val played = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.episode(2), title = "Bölüm", playable = true, progress = 1.0, played = true)).description.extras!!
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED, played.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS))
        assertFalse(played.containsKey(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS))

        val fresh = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.episode(3), title = "Bölüm", playable = true, progress = 0.0)).description.extras!!
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED, fresh.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS))
    }

    @Test
    fun aSearchResultIsListedUnderItsHeading() {
        val found = MediaSessionBrowser.toMediaItem(CarItem(id = CarIds.station(1), title = "Radyo Eksen", playable = true, group = "Radyo istasyonları"))
        assertEquals("Radyo istasyonları", found.description.extras!!.getString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT))
    }
}
