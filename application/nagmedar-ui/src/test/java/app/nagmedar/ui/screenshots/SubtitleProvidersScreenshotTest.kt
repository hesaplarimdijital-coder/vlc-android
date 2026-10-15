package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.subtitles.FakeOpenSubtitles
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.OnlineSearchState
import app.nagmedar.ui.player.subtitles.OnlineSubtitlesSurface
import app.nagmedar.ui.player.subtitles.providers.LocalFolderProvider
import app.nagmedar.ui.player.subtitles.providers.LocalSubtitleFile
import app.nagmedar.ui.player.subtitles.providers.LocalSubtitleFiles
import app.nagmedar.ui.player.subtitles.providers.OpenSubtitlesProvider
import app.nagmedar.ui.player.subtitles.providers.ProviderReport
import app.nagmedar.ui.player.subtitles.providers.ProviderRow
import app.nagmedar.ui.player.subtitles.providers.SubtitleProvidersSection
import app.nagmedar.ui.player.subtitles.providers.SubtitleResult
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-707: Settings › Subtitles with the providers (OpenSubtitles on, the local folder off, then on
 * with a folder chosen) in DeX, on a tablet and a phone, in English too; and the search window
 * with the results of two providers in one list, a provider that did not answer told beside its
 * name, and a result that two providers have.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleProvidersScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String) {
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private val none = object : LocalSubtitleFiles {
        override fun hasFolder() = false

        override suspend fun list() = emptyList<LocalSubtitleFile>()

        override suspend fun read(uri: String): ByteArray? = null
    }

    private fun rows(localOn: Boolean, folder: Boolean, keyMissing: Boolean = false, local: String = "Yerel klasör") = listOf(
        ProviderRow("opensubtitles", OpenSubtitlesProvider.NAME, OpenSubtitlesProvider(FakeOpenSubtitles()).capabilities, enabled = true, keyMissing = keyMissing),
        ProviderRow("local-folder", local, LocalFolderProvider(none).capabilities, enabled = localOn, needsFolder = true, folderChosen = folder),
    )

    private fun settings(name: String, theme: ThemeMode, input: InputMode, rows: List<ProviderRow>, folder: String? = null) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) {
                    SubtitleProvidersSection(rows, folder, { _, _ -> }, { _, _ -> }, {}, {})
                }
            }
        }
        snap(name)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h520dp-mdpi")
    fun settingsDex() = settings("settings-providers-dex-dark", ThemeMode.Dark, InputMode.Pointer, rows(localOn = true, folder = true), "Download/Altyazılar")

    @Test @Config(qualifiers = "tr-w800dp-h560dp-mdpi")
    fun settingsTablet() = settings("settings-providers-tablet-light", ThemeMode.Light, InputMode.Touch, rows(localOn = false, folder = false))

    @Test @Config(qualifiers = "tr-w390dp-h860dp-hdpi")
    fun settingsPhone() = settings("settings-providers-phone-dark", ThemeMode.Dark, InputMode.Touch, rows(localOn = true, folder = false, keyMissing = true))

    @Test @Config(qualifiers = "en-w800dp-h560dp-mdpi")
    fun settingsEnglish() = settings("settings-providers-tablet-light-en", ThemeMode.Light, InputMode.Touch, rows(localOn = true, folder = true, local = "Local folder"), "Download/Subtitles")

    private fun result(provider: String, id: String, name: String, release: String, language: String, downloads: Long, hash: Boolean = false, also: List<String> = emptyList()) =
        SubtitleResult(provider, id, name, "$release.$language.srt", release, language, downloads, null, hash, alsoFrom = also, fps = 23.976, title = "Kuzey Işığı", year = 2024)

    private fun found(local: String) = listOf(
        result("opensubtitles", "11", "OpenSubtitles", "Kuzey.Isigi.2024.1080p.WEB-DL", "tr", 12_345, hash = true, also = listOf(local)),
        result("local-folder", "a", local, "Kuzey.Isigi.2024.720p.BluRay", "tr", 0),
        result("opensubtitles", "12", "OpenSubtitles", "Northern.Light.2024.1080p.WEB", "en", 48_210),
    )

    private fun search(name: String, input: InputMode, state: OnlineSearchState) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Backdrop { OnlineSubtitlesSurface(state, OnlineQuota(4, 5, null), 0, {}, {}, {}, {}, {}, {}) }
            }
        }
        snap(name)
    }

    @Composable
    private fun Backdrop(window: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        SampleVideo(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        window()
    }

    private fun both(local: String) = OnlineSearchState(
        "Kuzey Işığı 2024", listOf("tr", "en"), listOf("tr", "en"),
        results = found(local),
        reports = listOf(ProviderReport("opensubtitles", "OpenSubtitles", 2), ProviderReport("local-folder", local, 1)),
    )

    private fun partial(local: String) = both(local).copy(
        results = found(local).filter { it.provider == "opensubtitles" },
        reports = listOf(ProviderReport("opensubtitles", "OpenSubtitles", 2), ProviderReport("local-folder", local, 0, OnlineProblem.Timeout)),
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun searchMerged() = search("subproviders-search-dex", InputMode.Pointer, both("Yerel klasör"))

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun searchPartialEnglish() = search("subproviders-search-partial-dex-en", InputMode.Pointer, partial("Local folder"))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun searchPhone() = search("subproviders-search-phone", InputMode.Touch, partial("Yerel klasör"))
}
