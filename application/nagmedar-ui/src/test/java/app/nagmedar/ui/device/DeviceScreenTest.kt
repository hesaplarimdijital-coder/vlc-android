package app.nagmedar.ui.device

import android.content.Context
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.network.EntryKind
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.1: Sources › This device on screen. A drive's folder listed its rows under keys a lazy list
 * cannot keep (the locations themselves), and the app ended as the first row composed (the user's
 * video of 29 Oct, Bu cihaz › Dahili depolama): a drive's folder and a picked folder now list,
 * open, go back up and keep their state.
 *
 * 1.0.3: under the shell's top bar (a phone, a rail) the open folder has one arrow, the bar's, which goes up a folder
 * as Back does, and is named once, by its trail; the trail keeps its end, the open folder, in view.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
class DeviceScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val prefs = NagmedarPreferences(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("device-screen", Context.MODE_PRIVATE))

    /** [navKind]: the shell's navigation the screen is shown under; null: the screen on its own (no top bar). */
    private fun show(inputMode: InputMode = InputMode.Touch, device: DeviceSource = SampleDevice(), navKind: NavKind? = null) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                if (navKind == null) {
                    DeviceScreen(prefs, device)
                } else {
                    CompositionLocalProvider(LocalNavKind provides navKind) { DeviceScreen(prefs, device) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(description: String) = compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(5_000) { shown(text) }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun theInternalStorageListsItsFoldersAndFiles() {
        show()
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("tone.mp3")
        compose.onNodeWithText("Music").assertExists()
    }

    @Test
    fun aFolderOpensAndBackGoesUpToTheDriveAndThePlaces() {
        show()
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("Music")
        compose.onNodeWithText("Music").performClick()
        waitFor("walk.mp3")
        back()
        waitFor("tone.mp3")
        back()
        waitFor("Depolama ve diskler")
        assertEquals(false, shown("tone.mp3"))
    }

    @Test
    fun underThePhonesTopBarTheFolderHasNoArrowOfItsOwnAndIsNamedOnce() {
        show(navKind = NavKind.BottomBar)
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("Music")
        compose.onNodeWithText("Music").performClick()
        waitFor("walk.mp3")
        // the top bar's arrow is the one that goes up: the trail has none beside it
        assertEquals(0, count("Üst klasör"))
        // the trail's last name alone says where this is: the header does not say it again
        assertEquals(1, compose.onAllNodesWithText("Music").fetchSemanticsNodes().size)
        // what the bar's arrow presses
        back()
        waitFor("tone.mp3")
        assertEquals(0, count("Üst klasör"))
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h900dp-xhdpi")
    fun underTheRailsTopBarTooTheFolderHasNoArrowOfItsOwn() {
        show(navKind = NavKind.Rail)
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("tone.mp3")
        assertEquals(0, count("Üst klasör"))
    }

    @Test
    fun whereNoBarHasAnArrowTheTrailKeepsItsOwn() {
        // the screen on its own: no top bar over it
        show()
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("tone.mp3")
        compose.onNodeWithContentDescription("Üst klasör").performClick()
        compose.waitForIdle()
        waitFor("Depolama ve diskler")
        assertEquals(false, shown("tone.mp3"))
    }

    @Test
    fun aLongWayKeepsTheOpenFolderInSight() {
        show(device = DeepDevice(), navKind = NavKind.BottomBar)
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("Music")
        // a way that fits is all there
        compose.onNodeWithText("Dahili depolama").assertIsDisplayed()
        compose.onNodeWithText("Music").performClick()
        waitFor(DEEP_ARTIST)
        compose.onNodeWithText(DEEP_ARTIST).performClick()
        waitFor(DEEP_ALBUM)
        compose.onNodeWithText(DEEP_ALBUM).performClick()
        waitFor("son.flac")
        compose.waitForIdle()
        // four folders are longer than the phone's bar: the open one, the last, is in sight, the drive scrolled out
        compose.onNodeWithText(DEEP_ALBUM).assertIsDisplayed()
        compose.onNodeWithText("Dahili depolama").assertIsNotDisplayed()
        // up a folder: that one is the last now, and in sight
        back()
        waitFor(DEEP_ALBUM)
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText(DEEP_ARTIST).fetchSemanticsNodes().size)
        compose.onNodeWithText(DEEP_ARTIST).assertIsDisplayed()
    }

    @Test
    fun aPickedFolderListsItsDocuments() {
        show()
        compose.onNodeWithText("Arşiv").performClick()
        waitFor("a.flac")
        compose.onNodeWithText("b.flac").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun aClickSelectsARowBesideThePlaces() {
        show(InputMode.Pointer)
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("tone.mp3")
        compose.onNodeWithText("tone.mp3").performClick()
        compose.onNodeWithText("tone.mp3").assertIsSelected()
        compose.onNodeWithText("Music").assertIsNotSelected()
    }

    @Test
    fun anOpenFolderKeepsItsStateWhenTheWindowSavesIt() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) { DeviceScreen(prefs, SampleDevice()) }
        }
        compose.onNodeWithText("Dahili depolama").performClick()
        waitFor("tone.mp3")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        waitFor("Depolama ve diskler")
    }
}

private val DRIVE = DeviceLocation.Path("/storage/emulated/0")
private val MUSIC = DeviceLocation.Path("/storage/emulated/0/Music")
private val TREE = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Arşiv").toString()
private val PICKED = DeviceLocation.Document(TREE, "primary:Arşiv")

private fun entry(name: String, location: DeviceLocation, kind: EntryKind, size: Long = 0) =
    DeviceEntry(name, location, kind, sizeBytes = size, modifiedMs = 1_760_000_000_000L)

/** The internal storage with a Music folder and a file, and a picked folder of two documents. */
private class SampleDevice : DeviceSource {
    override suspend fun roots() = listOf(
        DeviceRoot("", RootKind.Internal, DRIVE, totalBytes = 128_000_000_000L, freeBytes = 52_100_000_000L),
        DeviceRoot("Arşiv", RootKind.Picked, PICKED, tree = TREE),
    )

    override suspend fun list(location: DeviceLocation): List<DeviceEntry>? = when (location) {
        DRIVE -> listOf(
            entry("Music", MUSIC, EntryKind.Folder),
            entry("tone.mp3", DeviceLocation.Path("/storage/emulated/0/tone.mp3"), EntryKind.Audio, 480_000),
        )
        MUSIC -> listOf(entry("walk.mp3", DeviceLocation.Path("/storage/emulated/0/Music/walk.mp3"), EntryKind.Audio, 720_000))
        PICKED -> listOf(
            entry("a.flac", DeviceLocation.Document(TREE, "primary:Arşiv/a.flac"), EntryKind.Audio, 24_000_000),
            entry("b.flac", DeviceLocation.Document(TREE, "primary:Arşiv/b.flac"), EntryKind.Audio, 26_000_000),
        )
        else -> null
    }
}

private const val DEEP_ARTIST = "Cemre Kaya ve Arkadaşları"
private const val DEEP_ALBUM = "Kıyıdan Uzakta Bir Yaz Akşamı"
private val ARTIST = DeviceLocation.Path("/storage/emulated/0/Music/Cemre Kaya ve Arkadaşları")
private val ALBUM = DeviceLocation.Path("/storage/emulated/0/Music/Cemre Kaya ve Arkadaşları/Kıyıdan Uzakta Bir Yaz Akşamı")

/** The internal storage with an album three folders down, the way to it longer than a phone's bar. */
private class DeepDevice : DeviceSource {
    override suspend fun roots() = listOf(DeviceRoot("", RootKind.Internal, DRIVE, totalBytes = 128_000_000_000L, freeBytes = 52_100_000_000L))

    override suspend fun list(location: DeviceLocation): List<DeviceEntry>? = when (location) {
        DRIVE -> listOf(entry("Music", MUSIC, EntryKind.Folder))
        MUSIC -> listOf(entry(DEEP_ARTIST, ARTIST, EntryKind.Folder))
        ARTIST -> listOf(entry(DEEP_ALBUM, ALBUM, EntryKind.Folder))
        ALBUM -> listOf(entry("son.flac", DeviceLocation.Path("/storage/emulated/0/Music/Cemre Kaya ve Arkadaşları/Kıyıdan Uzakta Bir Yaz Akşamı/son.flac"), EntryKind.Audio, 24_000_000))
        else -> null
    }
}
