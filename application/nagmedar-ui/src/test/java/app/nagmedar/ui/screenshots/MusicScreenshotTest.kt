package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.R
import app.nagmedar.ui.TestShell
import app.nagmedar.audio.AbSide
import app.nagmedar.audio.EngineState
import app.nagmedar.audio.ReplayGainMode
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.audio.SignalPathCard
import app.nagmedar.ui.components.InspectorState
import app.nagmedar.ui.components.InspectorToggleButton
import app.nagmedar.ui.components.rememberInspectorState
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricsLoad
import app.nagmedar.ui.lyrics.LyricsPane
import app.nagmedar.ui.lyrics.LyricsSamples
import app.nagmedar.ui.music.Depth
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicBrowser
import app.nagmedar.ui.music.MusicBrowserState
import app.nagmedar.ui.music.MusicColumn
import app.nagmedar.ui.music.MusicInspectorContent
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.NowPlayingPanel
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.music.RatingSurface
import app.nagmedar.ui.music.TechFilter
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.music.TrackInspector
import app.nagmedar.ui.music.TrackTags
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.playback.QueuePanel
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.UnderShellBars
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * S-803, S-1001, S-1002, for approval: the music library in DeX as three panes (the albums, an
 * album's table with its format columns and, Desktop Kit 1.0, the inspector on what plays with its
 * waveform, or on a selected track; its lyrics), on a phone by artist in English, on a tablet by
 * genre with the 24-bit filter (the inspector's queue) and by album (its signal path), and the phone's player.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private object NoMusicActions : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit
    }

    private fun library(language: String) = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag(language))

    private val playing = NowPlayingInfo(
        title = "Neva Kâr",
        artist = "Kadri Erdem",
        album = "Itrî'nin İzinde",
        format = "FLAC 24/96 · USB DAC · 96 kHz · Bit-perfect",
        lengthMs = 498_000,
        playing = true,
        rating = 4,
    )

    private val upNext = listOf(
        QueueEntry("a#1", "Nihâvend Saz Semâîsi", "Kadri Erdem", 401_000),
        QueueEntry("b#1", "Gece Yolcusu", "Cemre Kaya", 254_000),
        QueueEntry("c#1", "Yağmur", "Cemre Kaya", 231_000),
    )

    private fun capture(name: String, theme: ThemeMode, input: InputMode, act: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    /**
     * The music page as the app has it: its header (with the inspector's button on tablet and DeX) and the browser;
     * with [inspector] (Desktop Kit 1.0) laid out under a floating player bar, as the app's music page is.
     */
    @Composable
    private fun Library(library: MusicLibrary, state: MusicBrowserState, inspector: MusicInspectorContent? = null, inspectorState: InspectorState? = null) {
        val panel = inspectorState ?: rememberInspectorState()
        val page = @Composable {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.nag_nav_music),
                    subtitle = pluralStringResource(R.plurals.nag_tracks_count, library.tracks.size, library.tracks.size),
                ) {
                    if (inspector != null && currentWidthClass() >= WidthClass.Expanded) InspectorToggleButton(panel)
                }
                MusicBrowser(library, state, NoMusicActions, inspector = inspector, inspectorState = panel, stats = MusicSamples.stats)
            }
        }
        if (inspector != null) UnderShellBars(content = page) else page()
    }

    private val neva = MusicSamples.tracks.first { it.title == "Neva Kâr" }

    /** A 24/96 FLAC to the USB DAC, untouched: bit-perfect. */
    private val usbPath = SignalPath(
        state = EngineState.Playing,
        source = SignalPath.Source(1, "file:///music/neva.flac", "flac", "flac", 96_000, 2, 24, false, 498_000, 0, 0, false),
        output = SignalPath.Output("usb", 96_000, 2, "s24", 24, true),
        volume = 1.0,
        crossfadeMs = 0,
        replayGain = SignalPath.ReplayGainStage(ReplayGainMode.Off, 0.0, 0.0, true, false),
        tempo = 1.0,
        pitch = 0.0,
        balance = 0.0,
        mono = false,
        dsp = listOf(SignalPath.DspStage("bypass", null, null, null, null, null)),
        dspActive = false,
        dspLevelDb = 0.0,
        abSide = AbSide.A,
        abGainDb = 0.0,
        bitPerfect = true,
        reason = SignalPath.Reason.None,
    )

    private val queue = FakeQueue(
        listOf(QueueEntry("n#1", "Neva Kâr", "Kadri Erdem", 498_000)) + upNext,
        current = 0,
    )

    /** The inspector's tabs on samples, as the app's on the playback service: what plays is [playing] ("Neva Kâr"). */
    private val inspector = MusicInspectorContent(
        tags = { subject ->
            val track = subject.track
            if (track == null || track.uri == neva.uri) {
                NowPlayingPanel(
                    info = playing,
                    progress = { 0.38f },
                    waveform = MusicSamples.waveform,
                    upNext = emptyList(),
                    onSeek = {},
                    onPlayUpNext = {},
                    artwork = { size -> Artwork(null, size, corner = 8.dp, video = false) },
                    onRate = {},
                    below = { TrackTags(neva, MusicSamples.stats, Modifier.padding(top = 12.dp)) },
                )
            } else {
                TrackInspector(
                    track = track,
                    selected = subject.selected,
                    waveform = MusicSamples.waveform,
                    rating = MusicSamples.stats[track.statsKey]?.rating ?: 0,
                    onRate = {},
                    stats = MusicSamples.stats,
                    cover = { size -> Artwork(null, size, corner = 8.dp, video = false) },
                )
            }
        },
        signal = { _ -> Column(Modifier.fillMaxSize().padding(16.dp)) { SignalPathCard(usbPath, "USB DAC") } },
        lyrics = { _ ->
            LyricsPane(
                load = LyricsLoad.Found(LyricsSamples.track, LyricsSamples.synced.copy(offsetMs = 300)),
                timeMs = { 23_500L },
                onSeek = {},
                onDelay = {},
                modifier = Modifier.fillMaxSize().padding(16.dp),
                onFullScreen = {},
            )
        },
        queue = { QueuePanel(queue.entries, queue.current, queue, onClose = null, modifier = Modifier.fillMaxSize(), background = NagTheme.colors.surface) },
    )

    private fun itri(library: MusicLibrary) = library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key

    private val dexColumns = listOf(MusicColumn.Title, MusicColumn.Number, MusicColumn.Composer, MusicColumn.Format, MusicColumn.Duration, MusicColumn.Rating, MusicColumn.Plays)

    /** Desktop Kit 1.0: DeX as MusicBee: the albums, an album's table, the inspector on what plays; the bar floats. */
    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi")
    fun dex() {
        val library = library("tr")
        val state = MusicBrowserState(view = MusicView.Albums, group = itri(library), columns = dexColumns)
        capture("music-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Music, floatingPlayer = true) { _, _ ->
                Library(library, state, inspector)
            }
        }
    }

    /** The inspector on a track clicked in the table, which is not what plays: its tags, waveform and stars. */
    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi")
    fun dexLight() {
        val library = library("tr")
        val state = MusicBrowserState(view = MusicView.Albums, group = itri(library), columns = dexColumns)
        capture("music-dex-light", ThemeMode.Light, InputMode.Pointer, act = { compose.onAllNodesWithText("Segâh Tekbîr").onFirst().performClick() }) {
            TestShell(WidthClass.Large, startDestination = Destination.Music, floatingPlayer = true) { _, _ ->
                Library(library, state, inspector)
            }
        }
    }

    /** S-1004 in the inspector's lyrics tab: the line sung lit, a delay set. */
    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi")
    fun lyricsDex() {
        val library = library("tr")
        val album = library.groups(MusicView.Albums).first { it.title == "Gece Yolcusu" }.key
        capture("music-lyrics-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Music, floatingPlayer = true) { _, _ ->
                Library(library, MusicBrowserState(view = MusicView.Albums, group = album), inspector, remember { InspectorState(384f, initialTab = 2) })
            }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneEnglish() {
        val library = library("en")
        capture("music-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Music) { _, _ ->
                Library(library, MusicBrowserState(view = MusicView.Artists))
            }
        }
    }

    /**
     * A tablet by genre with the 24-bit filter, a genre open (beside the inspector the groups and the table take turns,
     * as on a narrow window); the inspector on its queue tab, the mini player floating.
     */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletGenres() {
        val library = library("tr")
        capture("music-tablet-dark-genres", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Music, floatingPlayer = true) { _, _ ->
                val classical = library.groups(MusicView.Genres).first { it.title == "Klasik" }.key
                Library(library, MusicBrowserState(view = MusicView.Genres, group = classical, filter = TechFilter(depths = setOf(Depth.Bits24))), inspector, remember { InspectorState(384f, initialTab = 3) })
            }
        }
    }

    /** A tablet held upright in the light: an album's tracks, the inspector on the signal path of what plays. */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletLight() {
        val library = library("tr")
        capture("music-tablet-light", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Music, floatingPlayer = true) { _, _ ->
                Library(library, MusicBrowserState(view = MusicView.Albums, group = itri(library)), inspector, remember { InspectorState(384f, initialTab = 1) })
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun ratingWindow() {
        val tracks = MusicSamples.tracks.filter { it.title == "Neva Kâr" }
        capture("music-rating-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RatingSurface(tracks, MusicSamples.stats, onRate = {}, onDismiss = {})
            }
        }
    }

    /**
     * S-1002: the phone's player as the music apps lay it out: where it plays from, the artwork on a backdrop of its
     * colour, the line sung, the title with its round "Listeye ekle…", the slim timeline, the controls, the output,
     * sharing and the queue; [from], [tint] and the lyrics' line as given.
     */
    @Composable
    private fun Player(
        info: NowPlayingInfo = playing.copy(format = "FLAC 24/96 · AAudio · 48 kHz"),
        tint: Color? = null,
        from: PlaySource? = null,
        lineAtMs: Long? = null,
        shuffling: Boolean = false,
        repeatOne: Boolean = false,
    ) {
        PhonePlayer(
            info = info,
            progress = { 0.42f },
            waveform = MusicSamples.waveform,
            actions = NoPlayerActions,
            onClose = {},
            shuffling = shuffling,
            repeating = repeatOne,
            repeatOne = repeatOne,
            artwork = { size -> Artwork(null, size, corner = NagTheme.dimens.radiusCard, video = false) },
            onRate = {},
            tint = tint,
            from = from,
            lyricLine = if (lineAtMs == null) null else { modifier -> LyricLine(LyricsSamples.synced, { lineAtMs }, onClick = {}, modifier = modifier) },
            onAddToPlaylist = {},
            onShare = {},
            onEqualizer = {},
        )
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phonePlayer() = capture("music-player-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        Player(tint = Color(0xFFC0392B), from = PlaySource(PlayFrom.Album, "Itrî'nin İzinde"), lineAtMs = 23_500L, shuffling = true)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phonePlayerLight() = capture("music-player-phone-light", ThemeMode.Light, InputMode.Touch) {
        Player(tint = Color(0xFF2E86C1), from = PlaySource(PlayFrom.Playlist, "Gece yolculuğu"), lineAtMs = 18_000L, repeatOne = true)
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phonePlayerOledEnglish() = capture("music-player-phone-oled-en", ThemeMode.Oled, InputMode.Touch) {
        Player(tint = Color(0xFF27AE60), from = PlaySource(PlayFrom.Artist, "Kadri Erdem"))
    }

    /** As narrow as the user's phone: smaller gutters and controls, nothing wraps; without a source, "Şimdi çalıyor". */
    @Test @Config(qualifiers = "tr-w350dp-h760dp-xxhdpi")
    fun phonePlayerNarrow() = capture("music-player-phone-narrow-light", ThemeMode.Light, InputMode.Touch) {
        Player(tint = Color(0xFFF1C40F), lineAtMs = 29_800L)
    }

    /** Held sideways: the artwork on the left, the rest beside it. */
    @Test @Config(qualifiers = "tr-w891dp-h411dp-land-xhdpi")
    fun phonePlayerSideways() = capture("music-player-phone-landscape-dark", ThemeMode.Dark, InputMode.Touch) {
        Player(tint = Color(0xFF8E44AD), from = PlaySource(PlayFrom.Queue, ""), lineAtMs = 23_500L)
    }
}
