package app.nagmedar.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.components.rememberMenuBarState
import app.nagmedar.ui.components.rememberUndoBarState
import app.nagmedar.ui.input.DropTargets
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricsSamples
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.music.WindowPlayer
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlayerBarActions
import app.nagmedar.ui.playback.QueuePanelState
import app.nagmedar.ui.shell.CaptionBar
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.NagTheme

/**
 * 1.0.2: a tablet's or a desktop's window as NagmedarMain builds it around the big player: the real
 * shell with its floating bar, whose button, artwork and words open the player ([open]) and close it
 * again; the big player over the window ([WindowPlayer] with the real [PhonePlayer] and sample
 * words); the window's keys (WindowKeys) and the app's actions, whose Esc closes it. The library's
 * screens are the samples.
 */
class BigPlayerWindow(private val widthClass: WidthClass, private val caption: CaptionBar? = null, open: Boolean = false) {
    var open by mutableStateOf(open)
    lateinit var inputModes: InputModeManager
        private set

    /** What the player shows: a track of an album, 42 % played, its artwork [tint]ed, the line sung under it ([lyrics]). */
    @Composable
    fun Content(tint: Color? = null, lyrics: Boolean = false) {
        val context = LocalContext.current
        inputModes = LocalInputModeManager.current
        val prefs = remember { NagmedarPreferences(context.getSharedPreferences("nagmedar-big-player", Context.MODE_PRIVATE)) }
        val nav = rememberNavController()
        val navigator = remember(nav) { ShellNavigator(nav) }
        val undo = rememberUndoBarState()
        val menuBar = rememberMenuBarState()
        val screenCommands = remember { ScreenCommands() }
        val actions = remember {
            AppActions(
                context, prefs, navigator, undo, menuBar, screenCommands, DropTargets(), QueuePanelState(), {}, {}, {},
                closePlayer = {
                    val was = open
                    open = false
                    was
                },
            )
        }
        val keys = remember { WindowKeys({ actions }, { false }) }
        val window = remember { FocusRequester() }
        // the bar's button, artwork and words: the player, and again closed
        val bar = remember {
            object : PlayerBarActions by NoPlayerActions {
                override fun openPlayer() {
                    open = !open
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(NagTheme.colors.background)
                .onPreviewKeyEvent { keys.beforeViews(it.nativeKeyEvent, rtl = false) }
                .onKeyEvent { it.type == KeyEventType.KeyDown && keys.afterViews(it.nativeKeyEvent) }
                .focusRequester(window)
                .focusable(),
        ) {
            NagmedarShell(
                widthClass = widthClass,
                sidebarCollapsed = false,
                onSidebarCollapsedChange = {},
                badges = emptyMap(),
                onSearch = {},
                menus = { MenuBarMenus(menuSpecs(context, actions), menuBar) },
                player = { kind -> SamplePlayerBar(kind, actions = bar) },
                navController = nav,
                navigator = navigator,
                caption = caption,
                floatingPlayer = true,
                overlay = {
                    WindowPlayer(open, onClose = { open = false }) { modifier, drop ->
                        PhonePlayer(
                            info = bigPlayerInfo,
                            progress = { 0.42f },
                            waveform = MusicSamples.waveform,
                            actions = NoPlayerActions,
                            onClose = { open = false },
                            modifier = modifier,
                            shuffling = true,
                            artwork = { size -> Artwork(null, size, corner = NagTheme.dimens.radiusCard, video = false) },
                            onRate = {},
                            tint = tint,
                            from = PlaySource(PlayFrom.Album, "Itrî'nin İzinde"),
                            lyricLine = if (!lyrics) null else { line -> LyricLine(LyricsSamples.synced, { 23_500L }, onClick = {}, modifier = line) },
                            onAddToPlaylist = {},
                            onShare = {},
                            onEqualizer = {},
                            drop = drop,
                            captionAbove = caption?.height ?: 0.dp,
                        )
                    }
                },
                screen = { destination, _ -> SampleScreen(destination) },
            )
        }
        LaunchedEffect(Unit) { window.requestFocus() }
    }
}

/** The big player's sample: Neva Kâr, 8:18, through a USB DAC. */
val bigPlayerInfo = NowPlayingInfo(
    title = "Neva Kâr",
    artist = "Kadri Erdem",
    album = "Itrî'nin İzinde",
    format = "FLAC 24/96 · USB DAC · 96 kHz · Bit-perfect",
    lengthMs = 498_000,
    playing = true,
    rating = 4,
)
