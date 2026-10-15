package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobKind
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.convert.AudioCodec
import app.nagmedar.ui.convert.AudioSettings
import app.nagmedar.ui.convert.ConvertContainer
import app.nagmedar.ui.convert.ConvertMedia
import app.nagmedar.ui.convert.ConvertPage
import app.nagmedar.ui.convert.ConvertProfile
import app.nagmedar.ui.convert.ConvertProfiles
import app.nagmedar.ui.convert.ConvertSurface
import app.nagmedar.ui.convert.HlsFailure
import app.nagmedar.ui.convert.HlsQuality
import app.nagmedar.ui.convert.HlsState
import app.nagmedar.ui.convert.HlsSurface
import app.nagmedar.ui.convert.ProfileEditorSurface
import app.nagmedar.ui.convert.SubtitleMode
import app.nagmedar.ui.convert.VideoCodec
import app.nagmedar.ui.convert.VideoSettings
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.tvlab.remux.RemuxJobView
import app.nagmedar.ui.tvlab.remux.sampleRunningJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1303, for approval: the convert page (the jobs shared with the TV Lab's remux, the stream on
 * the air with its QR code, the profiles) in DeX and on a phone; the convert window, the profile
 * editor and the HLS sheet on a tablet, a phone and in DeX, in both themes and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConvertScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, centred: Boolean = false, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(
                    Modifier.fillMaxSize().background(if (centred) NagTheme.colors.scrim else NagTheme.colors.background),
                    contentAlignment = if (centred) Alignment.Center else Alignment.TopStart,
                ) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private val custom = ConvertProfile(
        id = 4,
        name = "Arşiv · 1080p",
        container = ConvertContainer.Mkv,
        video = VideoSettings(VideoCodec.H264, 6500, 1080),
        audio = AudioSettings(AudioCodec.Ac3, 448, 6),
        subtitles = SubtitleMode.Keep,
    )

    private val profiles = ConvertProfiles.presets + custom

    private fun convertJob(id: Long, state: String, progress: Float, profile: ConvertProfile, output: String, error: String? = null) = RemuxJobView.of(
        RemuxJobEntry(
            id = id,
            inputUri = "content://docs/$id",
            inputName = "Gece Yolcusu (2019).mkv",
            inputSize = 6_400_000_000,
            outputUri = "content://docs/$id-out",
            outputName = output,
            plan = profile.toJson(),
            state = state,
            progress = progress,
            mediaSeconds = 2_310.0 * progress / 0.9,
            totalSeconds = 6_420.0,
            bytesOut = (1_240_000_000 * progress).toLong(),
            attempts = 1,
            createdAt = 1_791_837_600_000L + id,
            startedAt = 1_791_837_600_000L,
            finishedAt = if (state == RemuxJobState.DONE || state == RemuxJobState.FAILED) 1_791_838_830_000L else null,
            error = error,
            kind = RemuxJobKind.CONVERT,
        ),
    )

    private val jobs = listOf(
        convertJob(3, RemuxJobState.RUNNING, 0.36f, ConvertProfiles.presets.single { it.id == -2L }, "Gece Yolcusu (2019).mp4"),
        sampleRunningJob(),
        convertJob(2, RemuxJobState.DONE, 1f, ConvertProfiles.presets.single { it.id == -6L }, "Gece Yolcusu — müzik.mp3"),
    )

    private val live = HlsState.Live("Kuzey Işığı", HlsQuality.P720, "http://192.168.1.20:41234/stream.m3u8", 0, served = 14)

    @Composable
    private fun Page(hls: HlsState) = ConvertPage(
        jobs = jobs,
        profiles = profiles,
        hls = hls,
        onConvert = {},
        onStream = {},
        onCancel = {},
        onRemove = {},
        onNewProfile = {},
        onEditProfile = {},
        onDeleteProfile = {},
        onStopStream = {},
        onCopy = {},
        onDismissFailure = {},
    )

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi")
    fun pageDex() = capture("convert-page-dex-dark", ThemeMode.Dark, InputMode.Pointer) { Page(live) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun pagePhone() = capture("convert-page-phone-light", ThemeMode.Light, InputMode.Touch) { Page(HlsState.Idle) }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun pageDexEnglish() = capture("convert-page-dex-light-en", ThemeMode.Light, InputMode.Pointer) { Page(HlsState.Failed(HlsFailure.NoLan)) }

    private val film = ConvertMedia(Uri.parse("content://docs/kuzey.mkv"), "Kuzey Işığı (2023) 2160p.mkv", 58_400_000_000)

    @Composable
    private fun Convert(selected: ConvertProfile) =
        ConvertSurface(film, profiles, selected, onSelect = {}, onPickMedia = {}, onNewProfile = {}, onStart = {}, onDismiss = {})

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun convertTablet() = capture("convert-dialog-tablet-light", ThemeMode.Light, InputMode.Touch, centred = true) {
        Convert(ConvertProfiles.presets.single { it.id == -2L })
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun convertPhoneH265() = capture("convert-dialog-phone-dark-h265", ThemeMode.Dark, InputMode.Touch, centred = true) {
        Convert(ConvertProfiles.presets.single { it.id == -3L })
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
    fun editorDex() = capture("convert-editor-dex-dark", ThemeMode.Dark, InputMode.Pointer, centred = true) {
        ProfileEditorSurface(custom, problemsOf = { ConvertProfiles.validate(it) }, onSave = {}, onDismiss = {})
    }

    @Test @Config(qualifiers = "en-w1000dp-h800dp-mdpi")
    fun editorTabletEnglish() = capture("convert-editor-tablet-light-en", ThemeMode.Light, InputMode.Touch, centred = true) {
        ProfileEditorSurface(
            custom.copy(name = "", container = ConvertContainer.Mp4, subtitles = SubtitleMode.Keep),
            problemsOf = { ConvertProfiles.validate(it) },
            onSave = {},
            onDismiss = {},
        )
    }

    @Composable
    private fun Hls(state: HlsState) =
        HlsSurface(film, state, HlsQuality.P720, onQuality = {}, onPickMedia = {}, onStart = {}, onStop = {}, onCopy = {}, onDismiss = {})

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun hlsLivePhone() = capture("hls-sheet-phone-dark-live", ThemeMode.Dark, InputMode.Touch, centred = true) { Hls(live) }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun hlsIdleTablet() = capture("hls-sheet-tablet-light", ThemeMode.Light, InputMode.Touch, centred = true) { Hls(HlsState.Idle) }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun hlsFailedDexEnglish() = capture("hls-sheet-dex-dark-en", ThemeMode.Dark, InputMode.Pointer, centred = true) { Hls(HlsState.Failed(HlsFailure.NoLan)) }
}
