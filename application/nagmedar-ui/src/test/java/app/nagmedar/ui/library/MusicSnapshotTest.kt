package app.nagmedar.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 1.0.3: the Music's list kept on the device between starts reads back as it was written (tags,
 * ids, addresses); a list kept for other folders is not shown; a track whose tags changed is a
 * change even with the same id; and a screen that has its list keeps showing it while the library
 * is scanned, with the strip above it instead of the loading state.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w420dp-h900dp-mdpi")
class MusicSnapshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(id: Long, path: String, title: String, album: String?, artist: String? = "Cemre Kaya"): MediaWrapper =
        MLServiceLocator.getAbstractMediaWrapper(
            id, Uri.fromFile(java.io.File(path)).toString(), 0L, 0f, 241_000L, MediaWrapper.TYPE_AUDIO, title, path.substringAfterLast('/'),
            7L, 7L, artist, "Caz", 11L, album, artist, 0, 0, null, -2, -2, 3, 1, 1_760_000_000L, 0L, false, false, 2026, true, 1_760_000_100L,
        )

    private val tracks = listOf(
        track(1, "/storage/emulated/0/Music/Kaya/01 Gece.flac", "Gece Yolcusu", "Kıyı"),
        track(2, "/storage/emulated/0/Music/Kaya/02 Şafak.flac", "Şafak", "Kıyı"),
        track(3, "/storage/emulated/0/Music/tek.mp3", "Tek", null, artist = null),
    )

    @After
    fun forget() = MusicSnapshot.forget()

    @Test
    fun theKeptListReadsBackAsWritten() {
        val bytes = ByteArrayOutputStream().also { MusicSnapshot.encode(it, "media", tracks) }.toByteArray()
        val (scope, read) = MusicSnapshot.decode(ByteArrayInputStream(bytes))!!
        assertEquals("media", scope)
        assertEquals(tracks.map { it.id }, read.map { it.id })
        assertEquals(tracks.map { it.uri }, read.map { it.uri })
        assertEquals(tracks.map { it.title }, read.map { it.title })
        assertEquals(tracks.map { it.albumName }, read.map { it.albumName })
        assertEquals(tracks.map { it.artistName }, read.map { it.artistName })
        assertEquals(tracks.map { it.length }, read.map { it.length })
        assertEquals(tracks.map { it.trackNumber }, read.map { it.trackNumber })
        assertEquals(tracks.map { it.releaseYear }, read.map { it.releaseYear })
        // what the list shows is the same
        assertEquals(MusicRead(tracks), MusicRead(read))
    }

    @Test
    fun aListKeptForOtherFoldersIsNotShown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MusicSnapshot.save(context, "media\n--\n+/storage/emulated/0/Music", MusicRead(tracks))
        MusicSnapshot.forget()
        assertNull(MusicSnapshot.read(context, "media\n--\n"))
        assertEquals(3, MusicSnapshot.read(context, "media\n--\n+/storage/emulated/0/Music")?.media?.size)
    }

    @Test
    fun aRetaggedTrackIsAChange() {
        val retagged = tracks.toMutableList().apply { this[1] = track(2, "/storage/emulated/0/Music/Kaya/02 Şafak.flac", "Şafak", "Kıyı (2026)") }
        // MediaWrapper's own equality is the id: the lists are "equal" though the album changed
        assertEquals(tracks, retagged)
        assertNotEquals(MusicRead(tracks), MusicRead(retagged))
    }

    @Test
    fun aKnownListStaysOnScreenWhileTheLibraryIsScanned() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalLibraryAccess provides LibraryAccess(true, emptySet(), {}, {})) {
                    LibraryContent(
                        loading = false,
                        empty = false,
                        scan = ScanState(active = true, discovering = true),
                        emptyTitle = "Henüz müzik yok",
                        emptyDescription = "",
                        onAddFolder = {},
                    ) {
                        Text("Gece Yolcusu")
                    }
                }
            }
        }
        compose.onNodeWithText("Gece Yolcusu").assertExists()
        compose.onNodeWithText("Medya klasörleri aranıyor").assertExists()
    }
}
