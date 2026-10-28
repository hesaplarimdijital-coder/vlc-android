package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.LibraryScopes
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.SourceActions
import app.nagmedar.ui.library.SourceFolder
import app.nagmedar.ui.onboarding.AudioRoute
import app.nagmedar.ui.onboarding.FirstRunActions
import app.nagmedar.ui.onboarding.FirstRunFlow
import app.nagmedar.ui.onboarding.FirstRunState
import app.nagmedar.ui.onboarding.FirstRunStep
import app.nagmedar.ui.onboarding.SectionFolders
import app.nagmedar.ui.onboarding.StorageSource
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-309: the first-run steps on a phone (390 x 844, the mockup's size) and in a DeX window; the
 * permission cards as they look once given, and the Sources step with the folders of its sections.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstRunScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val noop = {}
    private val actions = FirstRunActions(noop, noop, noop, noop, {}, noop, noop, noop, noop)
    private val drives = listOf(
        StorageSource("/storage/emulated/0", "", removable = false, totalBytes = 512_000_000_000),
        StorageSource("/storage/1A2B-3C4D", "SAMSUNG T7", removable = true, totalBytes = 2_000_000_000_000),
    )

    /** Settings › Library's rows with nothing to pick: only what they show is of interest here. */
    private val sourceActions = SourceActions(
        suggestions = emptyList(),
        storages = drives,
        pick = {},
        add = { _, _ -> },
        toggleExclude = { _, _ -> },
        remove = { _, _ -> },
    )

    /** The music on the device and on the drive. */
    private val chosen = LibraryScopes.Empty
        .plus(LibrarySection.Music, SourceFolder("/storage/emulated/0/Music"))
        .plus(LibrarySection.Music, SourceFolder("/storage/1A2B-3C4D/Konserler"))

    private fun capture(name: String, state: FirstRunState, theme: ThemeMode = ThemeMode.Dark, input: InputMode = InputMode.Touch,
                        folders: List<Pair<String, String>> = emptyList(), scopes: LibraryScopes = LibraryScopes.Empty) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                FirstRunFlow(state, drives, folders, actions) { SectionFolders(scopes, sourceActions) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun access() = capture("first-run-1-access", FirstRunState().apply { mediaAccess = true })

    /** Both permissions given: a check where each icon was, "Verildi" where each button was. */
    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun accessGranted() = capture("first-run-1-access-granted", FirstRunState().apply {
        mediaAccess = true
        allFiles = true
    })

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun accessGrantedLight() = capture("first-run-1-access-granted-light", FirstRunState().apply {
        mediaAccess = true
        allFiles = true
    }, ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun accessRefused() = capture(
        "first-run-1-refused",
        FirstRunState().apply { accessDenied = true },
        folders = listOf("content://tree/primary%3AFilmler" to "Filmler"),
    )

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun sources() = capture("first-run-2-sources", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true })

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun sourcesLight() = capture("first-run-2-sources-light", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, ThemeMode.Light)

    /** The whole Sources step, tall enough to show the sections' folders under the sources. */
    @Test @Config(qualifiers = "tr-w390dp-h1500dp-hdpi")
    fun sourcesWithSectionFolders() = capture(
        "first-run-2-sources-folders",
        FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true },
        scopes = chosen,
    )

    @Test @Config(qualifiers = "en-w390dp-h1500dp-hdpi")
    fun sourcesWithSectionFoldersEnglish() = capture(
        "first-run-2-sources-folders-en",
        FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true },
        ThemeMode.Light,
        scopes = chosen,
    )

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun audio() = capture(
        "first-run-3-audio",
        FirstRunState(FirstRunStep.Audio).apply {
            audioRoute = AudioRoute("USB · FiiO K7", 48_000)
            audioHeard = true
        },
    )

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun notifications() = capture("first-run-4-notifications", FirstRunState(FirstRunStep.Notifications))

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun notificationsGranted() = capture("first-run-4-notifications-granted", FirstRunState(FirstRunStep.Notifications).apply { notifications = true })

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dexWindow() = capture("first-run-dex", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, input = InputMode.Pointer)

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w390dp-h844dp-hdpi")
    fun sourcesEnglish() = capture("first-run-2-sources-en", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true })
}
