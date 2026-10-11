package app.nagmedar.ui.onboarding

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.LibraryAccess
import app.nagmedar.ui.library.LibraryContent
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.ScanState
import app.nagmedar.ui.library.SafLibrary
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * S-309: four steps, each skippable; refused media access leaves the app working with chosen
 * folders; the library asks for access when it may read nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w390dp-h844dp-hdpi")
class FirstRunTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = FirstRunActions(
        requestMediaAccess = { calls += "media" },
        requestAllFiles = { calls += "allFiles" },
        openAppSettings = { calls += "appSettings" },
        pickFolder = { calls += "pickFolder" },
        removeFolder = { calls += "remove:$it" },
        playTestSound = { calls += "sound" },
        requestNotifications = { calls += "notifications" },
        sourcesChosen = { calls += "sources" },
        finish = { calls += "finish" },
    )

    private val drive = StorageSource("/storage/1A2B-3C4D", "SAMSUNG T7", removable = true, totalBytes = 2_000_000_000_000)

    private fun render(state: FirstRunState, folders: List<Pair<String, String>> = emptyList()) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                FirstRunFlow(state, listOf(StorageSource("/storage/emulated/0", "", false, 0), drive), folders, actions)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everyStepCanBeSkipped() {
        val state = FirstRunState()
        render(state)
        compose.onNodeWithText("Adım 1 / 4 · Medya erişimi").assertExists()
        compose.onNodeWithText("Atla").performClick()
        compose.onNodeWithText("Adım 2 / 4 · Kaynaklar").assertExists()
        compose.onNodeWithText("Atla").performClick()
        compose.onNodeWithText("Adım 3 / 4 · Ses çıkışı").assertExists()
        compose.onNodeWithText("Atla").performClick()
        compose.onNodeWithText("Adım 4 / 4 · Bildirimler").assertExists()
        compose.onNodeWithText("Atla").performClick()
        assertEquals(listOf("sources", "finish"), calls)
    }

    @Test
    fun backReturnsToThePreviousStep() {
        val state = FirstRunState(FirstRunStep.Audio)
        render(state)
        compose.onNodeWithContentDescription("Önceki adım").performClick()
        compose.onNodeWithText("Adım 2 / 4 · Kaynaklar").assertExists()
    }

    @Test
    fun refusedAccessOffersFolders() {
        val state = FirstRunState().apply {
            mediaAccess = false
            accessDenied = true
        }
        render(state, folders = listOf("content://tree/primary%3AFilmler" to "Filmler"))
        compose.onNodeWithText("Medya erişimi verilmedi").assertExists()
        compose.onNodeWithText("Filmler").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Filmler klasörünü kaldır").performScrollTo().performClick()
        assertTrue("remove:content://tree/primary%3AFilmler" in calls)
        // the alert and the list both offer the picker
        compose.onAllNodesWithTextCount("Klasör seç").let { assertTrue("no folder button", it >= 1) }
        compose.onNodeWithText("Devam").performClick()
        // the app goes on: the chosen folder is a source, this device needs access
        compose.onNodeWithText("Adım 2 / 4 · Kaynaklar").assertExists()
        // this device and the drive both wait for media access
        assertEquals(2, compose.onAllNodesWithTextCount("Medya erişimi gerekiyor"))
        compose.onNodeWithText("Seçilen klasör").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun grantedAccessAndTheSourceCount() {
        val state = FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }
        render(state)
        // this device, the T7, podcasts and radio
        compose.onNodeWithText("Devam · 3 kaynak").assertExists()
        compose.onNodeWithText("SAMSUNG T7").performClick()
        compose.onNodeWithText("Devam · 2 kaynak").assertExists()
        assertEquals(listOf("/storage/emulated/0"), state.scanPaths("/storage/emulated/0", listOf(drive)))
    }

    @Test
    fun touchTargetsAndNames() {
        render(FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true })
        checkTargets()
    }

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: ""

    private fun checkTargets() {
        val density = compose.density
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val small = nodes.filter { node -> with(density) { node.size.width.toDp() < 47.5.dp || node.size.height.toDp() < 47.5.dp } }
        assertTrue(small.joinToString("\n") { "'${it.label()}' ${it.size}" }, small.isEmpty())
        val unnamed = nodes.filter { it.label().isBlank() }
        assertTrue("${unnamed.size} unnamed", unnamed.isEmpty())
    }

    @Test
    fun scanPathsNeedMediaAccess() {
        val state = FirstRunState()
        state.mediaAccess = false
        assertEquals(emptyList<String>(), state.scanPaths("/storage/emulated/0", listOf(drive)))
        state.mediaAccess = true
        assertEquals(listOf("/storage/emulated/0", drive.path), state.scanPaths("/storage/emulated/0", listOf(drive)))
        state.deviceSelected = false
        state.drivesSelected[drive.path] = false
        assertEquals(emptyList<String>(), state.scanPaths("/storage/emulated/0", listOf(drive)))
    }

    @Test
    fun folderNames() {
        assertEquals("Filmler", folderName("content://com.android.externalstorage.documents/tree/primary%3AMovies%2FFilmler"))
        assertEquals("Movies", folderName("content://com.android.externalstorage.documents/tree/primary%3AMovies"))
        assertEquals("1A2B-3C4D", folderName("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"))
    }

    @Test
    fun pickedFileTypes() {
        assertEquals(MediaWrapper.TYPE_VIDEO, SafLibrary.typeOf("Kuzey Işığı.mkv", "video/x-matroska"))
        assertEquals(MediaWrapper.TYPE_AUDIO, SafLibrary.typeOf("Sabaha Kadar.flac", "application/octet-stream"))
        assertEquals(MediaWrapper.TYPE_VIDEO, SafLibrary.typeOf("film.m2ts", null))
        assertEquals(MediaWrapper.TYPE_ALL, SafLibrary.typeOf("notlar.txt", "text/plain"))
    }

    @Test
    fun emptyLibraryWithoutAccessAsksForIt() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalLibraryAccess provides LibraryAccess(false, emptySet(), { calls += "media" }, { calls += "pickFolder" })) {
                    LibraryContent(
                        loading = false,
                        empty = true,
                        scan = ScanState.Idle,
                        emptyTitle = "Henüz video yok",
                        emptyDescription = "",
                        onAddFolder = { calls += "addFolder" },
                    ) {}
                }
            }
        }
        compose.onNodeWithText("Medya dosyalarına erişim").assertExists()
        compose.onNodeWithText("İzin ver").performClick()
        compose.onNodeWithText("Yalnızca klasör seç").performClick()
        assertEquals(listOf("media", "pickFolder"), calls)
    }
}
