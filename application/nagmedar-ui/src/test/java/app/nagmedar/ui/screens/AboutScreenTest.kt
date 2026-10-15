package app.nagmedar.ui.screens

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The libraries of S-005's list, read as the about page reads them. */
internal fun sampleThirdParties(): List<ThirdParty> {
    val res = ApplicationProvider.getApplicationContext<Context>().resources
    return readThirdParties(res.openRawResource(R.raw.libraries).bufferedReader().use { it.readText() })
}

/**
 * S-209: the about page shows the version with libVLC's and the medialibrary's, opens the source
 * code, the GPL and where to report a problem, names who made it, and lists S-005's libraries,
 * searched and opened to their licences.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// tall enough for the whole page's first part: what is clicked is on the screen
@Config(qualifiers = "tr-w411dp-h2000dp-mdpi")
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val libraries = sampleThirdParties()

    private fun about() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                AboutScreen(libraries, onOpenLink = { opened += it }, facts = AboutFacts("1.2.3", "4.0.0", "0.13.0"))
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theListIsS005s() {
        assertEquals(73, libraries.size)
        val byTitle = libraries.associateBy { it.title }
        // the lab's and the music engine's native additions are there, with their licences
        assertEquals("mit", byTitle.getValue("dovi_tool").license?.id)
        assertEquals("lgpl2.1", byTitle.getValue("libusb").license?.id)
        assertEquals("mit", byTitle.getValue("whisper.cpp").license?.id)
        assertEquals("lgpl2.1", byTitle.getValue("TagLib").license?.id)
        assertEquals("boost", byTitle.getValue("utfcpp").license?.id)
        // S-1010: the converter's MP3 encoder (libopus is the Opus already listed)
        assertEquals("lgpl2.1", byTitle.getValue("LAME").license?.id)
        assertEquals("LGPL v2.1 or later", byTitle.getValue("FFmpeg").license?.name)
        // the remote access page's libraries (S-1304)
        assertEquals("mit", byTitle.getValue("Vue").license?.id)
        assertEquals("mit", byTitle.getValue("Bootstrap").license?.id)
        assertTrue(libraries.all { it.license != null })
    }

    @Test
    fun theVersionsTheSourceAndThePeople() {
        about()
        compose.onNodeWithText("Sürüm 1.2.3 · libVLC 4.0.0 · medialibrary 0.13.0").assertExists()
        compose.onNode(hasText("Kaynak kodu") and hasClickAction()).performClick()
        compose.onNode(hasText("Sorun bildir") and hasClickAction()).performClick()
        compose.onNode(hasText("Tüm katkıcılar") and hasClickAction()).performClick()
        val source = "https://github.com/hesaplarimdijital-coder/vlc-android"
        assertEquals(listOf(source, "$source/issues", "$source/graphs/contributors"), opened)
        compose.onNodeWithText("VideoLAN ve VLC yazarları").assertExists()
        compose.onNodeWithText("Nağmedar katkıcıları").assertExists()
    }

    @Test
    fun theGplOpens() {
        about()
        compose.onNode(hasText("GPL metni") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertExists()
    }

    @Test
    fun aLibraryIsFoundAndOpensToItsLicence() {
        about()
        compose.onNodeWithContentDescription("Kütüphane ya da lisans ara").performTextInput("whisper")
        compose.waitForIdle()
        compose.onNode(hasText("whisper.cpp") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Permission is hereby granted", substring = true).assertExists()
        compose.onNodeWithText("FFmpeg").assertDoesNotExist()
        compose.onNode(hasText("Lisansın tamamı") and hasClickAction()).performClick()
        assertEquals(listOf("https://opensource.org/licenses/mit-license.php"), opened)
        // by licence too
        compose.onNodeWithContentDescription("Kütüphane ya da lisans ara").performTextInput("x")
        compose.waitForIdle()
        compose.onNodeWithText("Eşleşen kütüphane yok").assertExists()
    }

    /** S-1501: a licence's chip names it short, in Turkish where the name has words, and is found so. */
    @Test
    fun licencesAreNamedShortAndInTurkish() {
        val byId = libraries.mapNotNull { it.license }.associateBy { it.id }
        assertEquals("Apache 2.0", compactLicenseName(byId.getValue("apache2.0").name))
        assertEquals("LGPL v2.1+", compactLicenseName(byId.getValue("lgpl2.1").name))
        assertEquals("GPL v2+", compactLicenseName(byId.getValue("gpl2").name))
        assertEquals("SIL Open Font License 1.1", compactLicenseName(byId.getValue("ofl").name))
        about()
        compose.onNodeWithContentDescription("Kütüphane ya da lisans ara").performTextInput("kamu mali")
        compose.waitForIdle()
        // the two public-domain libraries, found by their chip's Turkish name
        assertEquals(2, compose.onAllNodesWithText("Kamu malı").fetchSemanticsNodes().size)
        compose.onNodeWithText("Copyleft © 1996–2026 VideoLAN ve VLC yazarları", substring = true).assertExists()
    }

    /** S-1501: the page in English. */
    @Test @Config(qualifiers = "en-w411dp-h2000dp-mdpi")
    fun inEnglishToo() {
        about()
        compose.onNodeWithText("Version 1.2.3 · libVLC 4.0.0 · medialibrary 0.13.0").assertExists()
        compose.onNodeWithText("Copyleft © 1996–2026 by VideoLAN and the VLC authors", substring = true).assertExists()
        compose.onNodeWithContentDescription("Find a library or a licence").performTextInput("public")
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithText("Public domain").fetchSemanticsNodes().size)
    }
}
