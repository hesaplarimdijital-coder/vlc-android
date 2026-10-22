package app.nagmedar.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuItemRow
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay

/** The touch layout's sheets: what the classic overlay's option sheets and pickers were. */
enum class PlayerSheet { More, Audio, Speed, Picture, Orientation, Chapters }

/** Which sheet is open over the video, if any, and whether a key opened it. Compose state. */
@Stable
class PlayerSheets {
    var open by mutableStateOf<PlayerSheet?>(null)
        private set

    /** A key opened [open] (Up, V, the menu key): its first item has the keyboard. */
    var byKeys by mutableStateOf(false)
        private set

    fun show(sheet: PlayerSheet, byKeys: Boolean = false) {
        open = sheet
        this.byKeys = byKeys
    }

    fun close() {
        open = null
        byKeys = false
    }
}

/**
 * A sheet over the video, along the bottom: its title with a close button, then its items as
 * the menus have them, touch-sized; a tap beside it, Back or Esc closes it, an item closes it as
 * it acts. As wide as a phone at most, centred on a tablet. It is a window of its own, which has
 * the keyboard while it shows (the player's frame keeps the focus from its own views): opened by
 * a key ([focusFirst]) its first item has the focus, otherwise Down and Up go to its first or
 * last item; the arrows move from item to item, Enter acts.
 */
@Composable
internal fun PlayerSheetView(title: String, entries: List<MenuEntry>, onDismiss: () -> Unit, focusFirst: Boolean = false) {
    NagDialog(onDismissRequest = onDismiss) {
        val colors = NagTheme.colors
        val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        val sheet = remember { FocusRequester() }
        val first = remember { FocusRequester() }
        val last = remember { FocusRequester() }
        var sheetFocused by remember { mutableStateOf(false) }
        val firstEnabled = entries.indexOfFirst { it is MenuEntry.Item && it.enabled }
        val lastEnabled = entries.indexOfLast { it is MenuEntry.Item && it.enabled }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .testTag("player-sheet"),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.8f)
                    .background(colors.surface, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    // taps inside stay inside
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .semantics { paneTitle = title }
                    .onPreviewKeyEvent { event ->
                        // the sheet itself has the focus (a finger opened it): Down and Up go to its items
                        if (!sheetFocused || firstEnabled < 0) return@onPreviewKeyEvent false
                        val target = when (event.key) {
                            Key.DirectionDown -> first
                            Key.DirectionUp -> last
                            else -> return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown) target.requestFocus()
                        true
                    }
                    .onFocusChanged { sheetFocused = it.isFocused }
                    .focusRequester(sheet)
                    .focusTarget()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = NagTheme.type.title,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    NagIconButton(NagIcons.Close, stringResource(R.string.nag_player_window_close), onDismiss)
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, entry ->
                        when (entry) {
                            is MenuEntry.Item -> {
                                val focus = Modifier
                                    .then(if (index == firstEnabled) Modifier.focusRequester(first) else Modifier)
                                    .then(if (index == lastEnabled) Modifier.focusRequester(last) else Modifier)
                                MenuItemRow(entry, focus) {
                                    onDismiss()
                                    entry.onClick()
                                }
                            }
                            MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            // asked here, in what BoxWithConstraints composes as it measures: asked beside it, the
            // request ran before the sheet and its items existed, and the window's own focus went
            // to the sheet, never to its first item
            LaunchedEffect(Unit) {
                runCatching { if (focusFirst && firstEnabled >= 0) first.requestFocus() else sheet.requestFocus() }
            }
        }
    }
}

/**
 * The touch lock's only control: once the video is tapped ([taps] counts the taps), the unlock
 * button shows for a few seconds, then goes again. Nothing else reaches the player meanwhile.
 */
@Composable
internal fun LockOverlay(taps: Int, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(taps) {
        shown = true
        delay(LOCK_HINT_MS)
        shown = false
    }
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(NagTheme.motion.enter()),
            exit = fadeOut(NagTheme.motion.exit()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    NagIconButton(
                        NagIcons.Unlock,
                        stringResource(R.string.nag_player_unlock),
                        onUnlock,
                        size = 64.dp,
                        iconSize = 26.dp,
                        tint = Color.White,
                        modifier = Modifier.testTag("player-unlock"),
                    )
                }
                Text(
                    stringResource(R.string.nag_player_locked_hint),
                    style = NagTheme.type.label,
                    color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private const val LOCK_HINT_MS = 3_000L

/**
 * Whether the video [title] goes on where it stopped, as VLC asked with its own dialog: resume,
 * or start again, and the box to do so for every video until the player closes. Back or Escape
 * leaves the player, as before; a tap beside the card does nothing.
 */
@Composable
internal fun ResumePrompt(title: String, onAnswer: (resume: Boolean?, always: Boolean) -> Unit) {
    var always by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { onAnswer(null, false) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        DialogFrame(onDismiss = {}) {
            DialogCard(stringResource(R.string.nag_resume_title), Modifier.testTag("player-resume")) {
                Text(title, style = NagTheme.type.body, color = NagTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                NagCheckbox(always, { always = it }, stringResource(R.string.nag_resume_always))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_resume_start_over), { onAnswer(false, always) }, modifier = Modifier.testTag("resume-start-over"))
                    NagButton(stringResource(R.string.nag_resume_continue), { onAnswer(true, always) }, primary = true, modifier = Modifier.testTag("resume-continue"))
                }
            }
        }
    }
}
