package app.nagmedar.ui.onboarding

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.library.LibraryAccess
import app.nagmedar.ui.library.LibraryContent
import app.nagmedar.ui.library.LibraryScopes
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.ScanState
import app.nagmedar.ui.library.SafLibrary
import app.nagmedar.ui.library.SourceActions
import app.nagmedar.ui.library.SourceFolder
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * S-309: four steps, each skippable; refused media access leaves the app working with chosen
 * folders; the library asks for access when it may read nothing; a permission that is given says
 * so on its card, also when it was given while the window was away; the Sources step offers the
 * folders of Films, Series, Videos and Music, and asks for none of them.
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
    fun aGivenPermissionSaysSoWhereItsButtonWas() {
        render(FirstRunState().apply { mediaAccess = true })
        // media files: given, so "Verildi" instead of the button, the description as it was
        compose.onNodeWithText("Verildi").assertExists()
        compose.onNodeWithText("Video, müzik ve ses klasörleri").assertExists()
        compose.onNodeWithText("İzin ver").assertDoesNotExist()
        // all files: still asked for
        compose.onNodeWithText("Ayarları aç").performClick()
        assertEquals(listOf("allFiles"), calls)
        compose.onNodeWithText("Devam").assertExists()
    }

    @Test
    fun everyGivenPermissionOfTheStepIsMarked() {
        render(FirstRunState().apply {
            mediaAccess = true
            allFiles = true
        })
        assertEquals(2, compose.onAllNodesWithTextCount("Verildi"))
        compose.onNodeWithText("İzin ver").assertDoesNotExist()
        compose.onNodeWithText("Ayarları aç").assertDoesNotExist()
    }

    @Test
    fun theNotificationsStepSaysSoToo() {
        render(FirstRunState(FirstRunStep.Notifications).apply { notifications = true })
        compose.onNodeWithText("Verildi").assertExists()
        compose.onNodeWithText("Oynatma, tarama ve indirmeler").assertExists()
        compose.onNodeWithText("İzin ver").assertDoesNotExist()
        compose.onNodeWithText("Başla").assertExists()
    }

    @Test
    fun aPermissionNotGivenKeepsItsButton() {
        render(FirstRunState(FirstRunStep.Notifications))
        compose.onNodeWithText("Verildi").assertDoesNotExist()
        compose.onNodeWithText("İzin ver").performClick()
        assertEquals(listOf("notifications"), calls)
    }

    /** A lifecycle the test moves by hand: the window going away to a system prompt, and coming back. */
    private class TestWindow : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** The host's wiring, with the real controller: a permission given while the window was away shows once it resumes. */
    @Test
    fun aPermissionGivenAwayFromTheWindowShowsWhenItComesBack() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val window = TestWindow().apply { registry.currentState = Lifecycle.State.RESUMED }
        val prefs = NagmedarPreferences(app.getSharedPreferences("first-run-test", Context.MODE_PRIVATE))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalLifecycleOwner provides window) {
                    val state = rememberFirstRunState(rememberAccessController(prefs), FirstRunStep.Notifications)
                    FirstRunFlow(state, emptyList(), emptyList(), actions)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("İzin ver").assertExists()
        compose.onNodeWithText("Verildi").assertDoesNotExist()

        // the system's prompt takes the window away; notifications are allowed there
        compose.runOnUiThread { window.registry.currentState = Lifecycle.State.STARTED }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnUiThread { window.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()

        compose.onNodeWithText("Verildi").assertExists()
        compose.onNodeWithText("İzin ver").assertDoesNotExist()
    }

    private val device = StorageSource("/storage/emulated/0", "", removable = false, totalBytes = 0)

    /** What the section rows ask of the app, written down: no picker, no preferences. */
    private fun sourceActions() = SourceActions(
        suggestions = emptyList(),
        storages = listOf(device, drive),
        pick = { calls += "pick:${it.key}" },
        add = { section, folder -> calls += "add:${section.key}:${folder.location}" },
        toggleExclude = { section, folder -> calls += "leaveOut:${section.key}:${folder.location}" },
        remove = { section, folder -> calls += "take:${section.key}:${folder.location}" },
    )

    /** The Sources step the way the host builds it, over [scopes] as the library's store keeps them. */
    private fun renderSources(scopes: LibraryScopes) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                FirstRunFlow(FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, listOf(device, drive), emptyList(), actions) {
                    SectionFolders(scopes, sourceActions())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theSourcesStepOffersFoldersForEverySectionAndAsksForNone() {
        renderSources(LibraryScopes.Empty)
        compose.onNodeWithText("Bölüm başına klasörler").performScrollTo().assertExists()
        listOf("Filmler", "Diziler", "Videolar", "Müzik").forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
        // none chosen: every section shows all of the library
        assertEquals(4, compose.onAllNodesWithTextCount("Kütüphanenin tümü"))
        LibrarySection.entries.forEach { compose.onNodeWithTag("library-sources-add-${it.key}").performScrollTo().assertExists() }
        // choosing is optional: the step goes on as it did
        compose.onNodeWithText("Devam · 3 kaynak").performClick()
        compose.onNodeWithText("Adım 3 / 4 · Ses çıkışı").assertExists()
        assertEquals(listOf("sources"), calls)
    }

    @Test
    fun aFolderChosenForASectionShowsInItsRow() {
        renderSources(
            LibraryScopes.Empty
                .plus(LibrarySection.Movies, SourceFolder("/storage/emulated/0/Movies/Belgeseller"))
                .plus(LibrarySection.Music, SourceFolder("/storage/1A2B-3C4D/Konserler")),
        )
        compose.onNodeWithText("Belgeseller").performScrollTo().assertExists()
        compose.onNodeWithText("Bu cihaz › Movies › Belgeseller").assertExists()
        compose.onNodeWithText("Konserler").performScrollTo().assertExists()
        compose.onNodeWithText("SAMSUNG T7 › Konserler").assertExists()
        // Films and Music show one folder each; Series and Videos still all of the library
        assertEquals(2, compose.onAllNodesWithTextCount("1 klasör"))
        assertEquals(2, compose.onAllNodesWithTextCount("Kütüphanenin tümü"))
        compose.onNodeWithText("Devam · 3 kaynak").assertExists()
    }

    @Test
    fun aFolderPickedLaterShowsInItsRowAtOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = NagmedarPreferences(app.getSharedPreferences("first-run-sections", Context.MODE_PRIVATE))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                FirstRunFlow(FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, listOf(device, drive), emptyList(), actions) {
                    SectionFolders(prefs, sourceActions())
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Belgeseller").assertDoesNotExist()
        assertEquals(4, compose.onAllNodesWithTextCount("Kütüphanenin tümü"))

        // the picker's answer goes to the library's own store, which the rows follow
        compose.runOnUiThread {
            prefs.updateLibrarySources(prefs.librarySources.plus(LibrarySection.Movies, SourceFolder("/storage/emulated/0/Movies/Belgeseller")))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Belgeseller").performScrollTo().assertExists()
        assertEquals(3, compose.onAllNodesWithTextCount("Kütüphanenin tümü"))
        assertEquals(listOf(LibrarySection.Movies), LibrarySection.entries.filter { prefs.librarySources.constrained(it) })
    }

    @Test
    fun theSectionRowsKeepTheTargetsBigAndNamed() {
        renderSources(LibraryScopes.Empty.plus(LibrarySection.Movies, SourceFolder("/storage/emulated/0/Movies/Belgeseller")))
        checkTargets()
    }

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
        // storageSources() lists the device's own storage first; it is the "this device" row
        val sources = listOf(StorageSource("/storage/emulated/0", "", removable = false, totalBytes = 0), drive)
        state.mediaAccess = false
        assertEquals(emptyList<String>(), state.scanPaths("/storage/emulated/0", sources))
        state.mediaAccess = true
        assertEquals(listOf("/storage/emulated/0", drive.path), state.scanPaths("/storage/emulated/0", sources))
        assertEquals(3, state.sourceCount(sources, emptySet()))
        state.deviceSelected = false
        state.drivesSelected[drive.path] = false
        assertEquals(emptyList<String>(), state.scanPaths("/storage/emulated/0", sources))
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
        // S-911: a tracker module the provider does not know is a track
        assertEquals(MediaWrapper.TYPE_AUDIO, SafLibrary.typeOf("modül.mptm", "application/octet-stream"))
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
