package app.nagmedar.ui.player

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.guide.GuideScreen
import app.nagmedar.ui.guide.ShortcutCardScreen
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.network.LibVlcDialogHost
import app.nagmedar.ui.search.CommandPalette
import app.nagmedar.ui.search.commandItems
import app.nagmedar.ui.search.screenItems
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.sleep.SleepTimerDialog
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION

/** The app's own windows the video player opens over the film (S-1504, S-1007, S-305). */
enum class PlayerAppWindow { Guide, ShortcutCard, Palette, SleepTimer }

/**
 * The app's shortcuts in the full-screen video player: the user guide (F1), the shortcut card
 * (Ctrl+/), the command palette (Ctrl+K) and the sleep timer (Alt+S) open over the film, as
 * they do over the library, so the film goes on under them; Settings (Ctrl+,) opens in the
 * library's window, as the convert page does from the player. The keys are the shortcut table's
 * ([PlayerKeys]); the player's own keys stay its own. Compose state.
 */
@Stable
class PlayerAppWindows {
    var open by mutableStateOf<PlayerAppWindow?>(null)
        private set

    /** Whether the player does [id] here (and the palette offers it): the player's keys and these app shortcuts. */
    fun offered(id: ShortcutId): Boolean {
        val shortcut = ShortcutRegistry[id]
        return when (shortcut.scope) {
            ShortcutScope.Player -> id !in EDITOR_ONLY
            ShortcutScope.Global -> id in PLAYER_GLOBALS || id in APP
            ShortcutScope.Editor -> false
        }
    }

    /** [id] done when it is one of the app's: true then, else false for the player's own keys. */
    fun perform(activity: Activity, id: ShortcutId): Boolean {
        when (id) {
            ShortcutId.UserGuide -> open = PlayerAppWindow.Guide
            ShortcutId.ShortcutList -> open = PlayerAppWindow.ShortcutCard
            ShortcutId.CommandPalette -> open = PlayerAppWindow.Palette
            ShortcutId.SleepTimer -> open = PlayerAppWindow.SleepTimer
            ShortcutId.Settings -> openInLibrary(activity, Destination.Settings)
            else -> return false
        }
        return true
    }

    fun close() {
        open = null
    }

    companion object {
        /** The app's shortcuts these windows answer. */
        val APP = setOf(ShortcutId.UserGuide, ShortcutId.ShortcutList, ShortcutId.CommandPalette, ShortcutId.SleepTimer, ShortcutId.Settings)

        /** The global shortcuts the classic player does itself ([ActivityKeyTarget]) or its controls do. */
        val PLAYER_GLOBALS = setOf(
            ShortcutId.PlayPause, ShortcutId.Stop, ShortcutId.Previous, ShortcutId.Next,
            ShortcutId.JumpBackShort, ShortcutId.JumpForwardShort, ShortcutId.JumpBack, ShortcutId.JumpForward,
            ShortcutId.JumpBackLong, ShortcutId.JumpForwardLong, ShortcutId.Slower, ShortcutId.Faster, ShortcutId.NormalSpeed,
            ShortcutId.Repeat, ShortcutId.Shuffle, ShortcutId.VolumeUp, ShortcutId.VolumeDown, ShortcutId.Mute,
            ShortcutId.Equalizer, ShortcutId.Cast, ShortcutId.Visualizer, ShortcutId.ConvertSave, ShortcutId.StreamHls,
        )

        /** The subtitle tools' line editor keys: theirs while the tools are open, nothing from the palette. */
        private val EDITOR_ONLY = setOf(
            ShortcutId.CueSave, ShortcutId.CueSplit, ShortcutId.CueMerge, ShortcutId.CueSetStart,
            ShortcutId.CueSetEnd, ShortcutId.CueNext, ShortcutId.CuePrevious,
        )
    }
}

/** A screen of the library's window, from the player: in front of it on a phone, beside it on DeX. */
private fun openInLibrary(activity: Activity, destination: Destination) {
    val show = Intent(activity, NagmedarActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(NAGMEDAR_EXTRA_DESTINATION, destination.route)
    runCatching { activity.startActivity(show) }
}

/**
 * The windows of [windows] over the player, and libVLC's questions and sign-ins (S-1301) while
 * the player is in front; [perform] runs what the palette chooses as the player's keys would.
 * In the player's dark theme, sized for touch or a pointer as the device has one.
 */
@Composable
fun PlayerAppWindowsHost(activity: Activity, windows: PlayerAppWindows, prefs: NagmedarPreferences, perform: (ShortcutId) -> Unit) {
    val mode = if (hasPrecisePointer(activity)) InputMode.Pointer else InputMode.Touch
    NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = mode) {
        LibVlcDialogHost()
        when (windows.open) {
            PlayerAppWindow.Guide -> PageWindow(windows, activity) { GuideScreen(onOpenShortcutCard = { windows.perform(activity, ShortcutId.ShortcutList) }) }
            PlayerAppWindow.ShortcutCard -> PageWindow(windows, activity) {
                ShortcutCardScreen(
                    onEdit = {
                        windows.close()
                        openInLibrary(activity, Destination.Settings)
                    },
                    onOpenGuide = { windows.perform(activity, ShortcutId.UserGuide) },
                )
            }
            PlayerAppWindow.Palette -> {
                val res = LocalContext.current.resources
                val items = remember(res) {
                    commandItems(res, windows::offered) { id -> if (!windows.perform(activity, id)) perform(id) } +
                        screenItems(res) { destination -> openInLibrary(activity, destination) }
                }
                CommandPalette(
                    items = items,
                    recent = prefs.recentCommands,
                    onRun = { item ->
                        prefs.rememberCommand(item.id)
                        // the palette goes first: what it runs may open a window of its own
                        windows.close()
                        item.run()
                    },
                    onDismiss = windows::close,
                )
            }
            PlayerAppWindow.SleepTimer -> SleepTimerDialog(onDismiss = windows::close)
            null -> Unit
        }
    }
}

/**
 * A page of the library (the guide, the shortcut card) in a window over the film: most of the
 * screen, with its close button; Esc or a tap beside it closes it, F1, Ctrl+/ and Ctrl+K go from
 * one window to the other as they do over the library.
 */
@Composable
private fun PageWindow(windows: PlayerAppWindows, activity: Activity, page: @Composable () -> Unit) {
    Dialog(onDismissRequest = windows::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = NagTheme.colors
        val shape = RoundedCornerShape(12.dp)
        val focus = remember { FocusRequester() }
        LaunchedEffect(windows.open) { runCatching { focus.requestFocus() } }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { windows.close() } }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || ShortcutRegistry.recording) return@onPreviewKeyEvent false
                    val id = ShortcutRegistry.match(event)?.id
                    id != null && id in WINDOW_KEYS && windows.perform(activity, id)
                },
            contentAlignment = Alignment.Center,
        ) {
            val width = (maxWidth - 32.dp).coerceAtMost(1100.dp)
            val height = (maxHeight - 32.dp).coerceAtMost(800.dp)
            Column(
                Modifier
                    .width(width)
                    .height(height)
                    .shadow(24.dp, shape)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    // taps inside stay inside
                    .pointerInput(Unit) { detectTapGestures { } }
                    .focusRequester(focus)
                    .focusable()
                    .testTag("player-app-window"),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
                    Box(Modifier.weight(1f))
                    NagIconButton(NagIcons.Close, stringResource(R.string.nag_player_window_close), windows::close)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    CompositionLocalProvider(
                        LocalWidthClass provides WidthClass.fromWidth(width.value),
                        // the page's own title shows, as in the library's wide window
                        LocalNavKind provides NavKind.Sidebar,
                    ) { page() }
                }
            }
        }
    }
}

/** The keys that go from one of these windows to another. */
private val WINDOW_KEYS = setOf(ShortcutId.UserGuide, ShortcutId.ShortcutList, ShortcutId.CommandPalette)
