package app.nagmedar.ui.music

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** 1.3.0: "Sil…" asks first, says how many files go and that a CUE sheet's tracks stay; Delete hands over the files. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class TrackDeletionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(id: Long, uri: String, title: String) =
        Track(id, uri, title, "Moğollar", null, "Moğollar", null, 1971, id.toInt(), 1, 200_000, "/storage/emulated/0/Music")

    private fun media(uri: String): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(uri)).apply { type = MediaWrapper.TYPE_AUDIO }

    private val garip = "file:///storage/emulated/0/Music/02%20Garip.flac"
    private val cue = "file:///storage/emulated/0/Music/album.flac#cue=3"

    @Test
    fun theQuestionCountsTheFilesAndDeleteHandsThemOver() {
        val deleted = mutableListOf<List<MediaWrapper>>()
        TrackDeleteRequest.ask(listOf(track(2, garip, "Garip"), track(3, cue, "Haliç")), listOf(media(garip), media(cue)))
        compose.setContent { NagmedarTheme { TrackDeleteHost(delete = { deleted += it }) } }
        compose.onNodeWithText("1 parça silinsin mi?").assertExists()
        compose.onNodeWithText("Garip · Moğollar").assertExists()
        compose.onNodeWithText("CUE sayfasındaki 1 parça silinmez: albümün tamamı tek dosyadadır.").assertExists()
        compose.onNodeWithTag("delete-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(garip), deleted.single().map { it.uri.toString() })
        assertNull(TrackDeleteRequest.pending)
    }

    @Test
    fun cancelDeletesNothingAndACueSheetsTrackAloneCannotBe() {
        val deleted = mutableListOf<List<MediaWrapper>>()
        TrackDeleteRequest.ask(listOf(track(3, cue, "Haliç")), listOf(media(cue)))
        compose.setContent { NagmedarTheme { TrackDeleteHost(delete = { deleted += it }) } }
        compose.onNodeWithTag("delete-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("delete-cancel").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<List<MediaWrapper>>(), deleted)
        assertNull(TrackDeleteRequest.pending)
    }
}
