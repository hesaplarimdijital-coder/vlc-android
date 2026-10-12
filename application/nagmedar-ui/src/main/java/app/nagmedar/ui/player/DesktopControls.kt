package app.nagmedar.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.contextMenuGesture
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.snapshot.HdrSnapshot
import app.nagmedar.ui.player.snapshot.SnapshotFormat
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.snapshot.defaultSnapshotFolder
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The desktop controls over the video (S-601): the title along the top and the control bar along
 * the bottom, shown while the mouse moves and hidden two seconds after it stops ([ChromeVisibility]).
 * A click on the video shows them; a double click fills the screen, as on desktops.
 */
@Composable
fun DesktopControls(
    state: PlayerChromeState,
    actions: PlayerChromeActions,
    visibility: ChromeVisibility,
    previews: FramePreviews?,
    modifier: Modifier = Modifier,
    /** For tests and screenshots: the mouse over the timeline at 0..1. */
    timelineHover: Float? = null,
) {
    LaunchedEffect(visibility.moves, visibility.hiding) {
        if (!visibility.hiding) return@LaunchedEffect
        delay(ChromeVisibility.HIDE_AFTER_MS)
        visibility.hide()
    }
    val latest by rememberUpdatedState(state)
    Box(
        modifier
            .fillMaxSize()
            .testTag("player-chrome")
            .pointerInput(actions, visibility) {
                detectTapGestures(onTap = { visibility.show() }, onDoubleTap = { if (latest.canFullscreen) actions.toggleFullscreen() })
            },
    ) {
        AnimatedVisibility(
            visible = visibility.visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TitleBar(state, actions, Modifier.holdsChrome(visibility))
        }
        AnimatedVisibility(
            visible = visibility.visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar(state, actions, visibility, previews, timelineHover, Modifier.holdsChrome(visibility))
        }
    }
}

/** The pointer resting on a bar keeps the controls shown. */
private fun Modifier.holdsChrome(visibility: ChromeVisibility): Modifier = pointerInput(visibility) {
    awaitPointerEventScope {
        while (true) {
            when (awaitPointerEvent().type) {
                PointerEventType.Enter -> visibility.overBars = true
                PointerEventType.Exit -> visibility.overBars = false
            }
        }
    }
}

@Composable
private fun TitleBar(state: PlayerChromeState, actions: PlayerChromeActions, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)))
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
            .testTag("player-title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_player_close), actions::close, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(state.title, style = NagTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The control bar: the timeline over a row of controls. Wide, every control with its name; on a
 * tablet's width the tracks' names give way to their icons, and below 780, 720 and 700 dp picture
 * in picture, the screenshot button and the volume's slider go; on a phone's, the speed and the
 * A-B loop make room for the rest. The full-screen button stays at every width.
 */
@Composable
private fun ControlBar(
    state: PlayerChromeState,
    actions: PlayerChromeActions,
    visibility: ChromeVisibility,
    previews: FramePreviews?,
    timelineHover: Float?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            .testTag("player-controls"),
    ) {
        // the tracks' names need a window of their own: beside the panel they give way to icons
        val wide = maxWidth >= 1100.dp
        val narrow = maxWidth < 600.dp
        // the volume's slider needs a little more than a tablet's narrowest window, beside the panel
        // too: the full-screen button stays, the volume's icon and the wheel set it
        val sliderRoom = maxWidth >= 700.dp
        // the camera a little more again; Shift+S stays
        val roomy = maxWidth >= 720.dp
        // picture in picture a little more again
        val roomier = maxWidth >= 780.dp
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 8.dp)) {
            PlayerTimeline(state, actions::seekTo, previews, initialHover = timelineHover)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PlayPause(state.playing, actions::togglePlayPause)
                Spacer(Modifier.width(6.dp))
                if (!narrow) {
                    BarButton(NagIcons.Previous, stringResource(R.string.nag_sc_previous), actions::previous, ShortcutId.Previous, enabled = state.hasPrevious)
                    BarButton(NagIcons.Next, stringResource(R.string.nag_sc_next), actions::next, ShortcutId.Next, enabled = state.hasNext)
                }
                BarButton(NagIcons.Resume, stringResource(R.string.nag_sc_back_10s), { actions.seekBy(-10_000) }, ShortcutId.JumpBack)
                BarButton(NagIcons.Forward, stringResource(R.string.nag_sc_forward_10s), { actions.seekBy(10_000) }, ShortcutId.JumpForward)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (narrow) playerClock(state.timeMs) else playerClock(state.timeMs) + " / " + playerClock(state.lengthMs),
                    style = NagTheme.type.mono,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.testTag("player-time"),
                )
                Spacer(Modifier.weight(1f))
                if (!narrow) RateMenu(state, actions, visibility)
                AudioMenu(state, actions, visibility, labelled = wide)
                SubtitleMenu(state, actions, visibility, labelled = wide)
                if (!narrow) AbButton(state, actions)
                if (roomy) SnapshotButton(state, actions, visibility)
                BarButton(NagIcons.Effects, stringResource(R.string.nag_sc_effects), actions::toggleEffects, ShortcutId.Effects, selected = state.effectsOpen)
                if (roomier && state.canPictureInPicture) {
                    BarButton(NagIcons.PictureInPicture, stringResource(R.string.nag_player_pip), actions::pictureInPicture)
                }
                Volume(state, actions, slider = sliderRoom)
                if (state.canFullscreen) {
                    BarButton(
                        NagIcons.Fullscreen,
                        stringResource(if (state.fullscreen) R.string.nag_player_exit_fullscreen else R.string.nag_sc_fullscreen),
                        actions::toggleFullscreen,
                        ShortcutId.Fullscreen,
                        selected = state.fullscreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    shortcut: ShortcutId? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    NagIconButton(icon, label, onClick, enabled = enabled, selected = selected, tint = Color.White, shortcut = shortcut)
}

@Composable
private fun PlayPause(playing: Boolean, onClick: () -> Unit) {
    val size = 40.dp
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White))
        NagIconButton(
            icon = if (playing) NagIcons.Pause else NagIcons.Play,
            contentDescription = stringResource(if (playing) R.string.nag_pause else R.string.nag_play),
            onClick = onClick,
            size = size,
            tint = Color.Black,
            shortcut = ShortcutId.PlayPause,
        )
    }
}

/**
 * A control that opens a menu above itself: a name ("1,0×", the audio track's) with an icon, or
 * the icon alone. Its menu keeps the controls shown while it is open.
 */
@Composable
private fun MenuChip(
    label: String,
    text: String?,
    icon: ImageVector?,
    entries: () -> List<MenuEntry>,
    visibility: ChromeVisibility,
    shortcut: ShortcutId? = null,
    testTag: String,
    selected: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarChip(label, text, icon, { open = true; visibility.menuOpen = true }, shortcut, testTag, selected || open)
        MenuPopup(
            visible = open,
            onDismiss = {
                open = false
                visibility.menuOpen = false
            },
            entries = if (open) entries() else emptyList(),
            offset = IntOffset(0, 0),
            minWidth = 180.dp,
        )
    }
}

/** A bar control with a name: "1,0×", "A-B", the audio track's. */
@Composable
private fun BarChip(
    label: String,
    text: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    shortcut: ShortcutId?,
    testTag: String,
    selected: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    val keys = shortcut?.let { ShortcutRegistry.chordLabel(it, LocalContext.current.resources) }
    val lit = selected || state.hovered
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .then(if (text != null) Modifier.widthIn(max = 200.dp) else Modifier.width(36.dp))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.6f else 0.22f), shape)
            .background(Color.White.copy(alpha = if (lit) 0.14f else 0.04f), shape)
            .nagClickable(interaction, onClick, role = Role.Button, onClickLabel = label)
            .semantics { contentDescription = label }
            .padding(horizontal = if (text != null) 10.dp else 0.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) Text(text, style = NagTheme.type.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        HoverTooltip(state.hovered && !state.pressed, label, keys)
    }
}

@Composable
private fun RateMenu(state: PlayerChromeState, actions: PlayerChromeActions, visibility: ChromeVisibility) {
    val locale = currentLocale()
    MenuChip(
        label = stringResource(R.string.nag_player_speed),
        text = rateLabel(state.rate, locale),
        icon = null,
        entries = { playbackRates.map { rate -> MenuEntry.Item(rateLabel(rate, locale), { actions.setRate(rate) }, checked = abs(state.rate - rate) < 0.01f) } },
        visibility = visibility,
        testTag = "player-speed",
        selected = abs(state.rate - 1f) >= 0.01f,
    )
}

@Composable
private fun AudioMenu(state: PlayerChromeState, actions: PlayerChromeActions, visibility: ChromeVisibility, labelled: Boolean) {
    val none = stringResource(R.string.nag_player_no_audio)
    val current = state.audioTracks.firstOrNull { it.id == state.audioTrack }?.name
    MenuChip(
        label = stringResource(R.string.nag_player_audio_track),
        text = if (labelled) current ?: none else null,
        icon = NagIcons.AudioTrack,
        entries = {
            if (state.audioTracks.isEmpty()) {
                listOf(MenuEntry.Item(none, {}, enabled = false))
            } else {
                state.audioTracks.map { track -> MenuEntry.Item(track.name, { actions.setAudioTrack(track.id) }, checked = track.id == state.audioTrack) }
            }
        },
        visibility = visibility,
        shortcut = ShortcutId.NextAudioTrack,
        testTag = "player-audio",
    )
}

@Composable
private fun SubtitleMenu(state: PlayerChromeState, actions: PlayerChromeActions, visibility: ChromeVisibility, labelled: Boolean) {
    val off = stringResource(R.string.nag_player_subtitles_off)
    val current = state.subtitleTracks.firstOrNull { it.id == state.subtitleTrack }?.name
    // S-701: a file of subtitles, from the system's picker
    val addFile = stringResource(R.string.nag_sc_add_subtitle)
    val addKeys = ShortcutRegistry.chordLabel(ShortcutId.AddSubtitleFile, LocalContext.current.resources)
    MenuChip(
        label = stringResource(R.string.nag_player_subtitles),
        text = if (labelled) current ?: off else null,
        icon = NagIcons.Subtitle,
        entries = {
            listOf(MenuEntry.Item(off, { actions.setSubtitleTrack(PlayerChromeActions.SUBTITLES_OFF) }, checked = current == null)) +
                state.subtitleTracks.map { track -> MenuEntry.Item(track.name, { actions.setSubtitleTrack(track.id) }, checked = track.id == state.subtitleTrack) } +
                listOf(MenuEntry.Separator, MenuEntry.Item(addFile, actions::addSubtitleFile, shortcut = addKeys))
        },
        visibility = visibility,
        shortcut = ShortcutId.NextSubtitle,
        testTag = "player-subtitles",
        selected = current != null && !labelled,
    )
}

/** "A-B": sets A, then B, then turns the loop off; lit while a point is set. */
@Composable
private fun AbButton(state: PlayerChromeState, actions: PlayerChromeActions) {
    val label = stringResource(
        when (state.abState) {
            AbState.Off -> R.string.nag_player_ab_set_a
            AbState.HasA -> R.string.nag_player_ab_set_b
            AbState.Looping -> R.string.nag_player_ab_off
        },
    )
    BarChip(label, "A-B", null, actions::abRepeat, null, "player-ab", selected = state.abState != AbState.Off)
}

/**
 * The camera takes a screenshot (S-606); a right click, or a long press, opens its choices: the
 * subtitles, the format, HDR and the folder, as Settings has them.
 */
@Composable
private fun SnapshotButton(state: PlayerChromeState, actions: PlayerChromeActions, visibility: ChromeVisibility) {
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val entries = snapshotMenu(state.snapshot, actions)
    val optionsLabel = stringResource(R.string.nag_snap_options)
    fun open(at: IntOffset) {
        menuAt = at
        visibility.menuOpen = true
    }
    Box(
        Modifier
            .contextMenuGesture { at -> open(IntOffset(at.x.roundToInt(), at.y.roundToInt())) }
            .semantics { customActions = listOf(CustomAccessibilityAction(optionsLabel) { open(IntOffset.Zero); true }) }
            .testTag("player-snapshot"),
    ) {
        BarButton(NagIcons.Screenshot, stringResource(R.string.nag_sc_snapshot), actions::takeSnapshot, ShortcutId.Snapshot)
        MenuPopup(
            visible = menuAt != null,
            onDismiss = {
                menuAt = null
                visibility.menuOpen = false
            },
            entries = if (menuAt != null) entries else emptyList(),
            offset = menuAt ?: IntOffset.Zero,
            minWidth = 240.dp,
        )
    }
}

/** The screenshot button's menu: take one, then the choices Settings > Screenshots has. */
@Composable
internal fun snapshotMenu(settings: SnapshotSettings, actions: PlayerChromeActions): List<MenuEntry> {
    val keys = ShortcutRegistry.chordLabel(ShortcutId.Snapshot, LocalContext.current.resources)
    val folder = snapshotFolderLabel(settings.folder, defaultSnapshotFolder(stringResource(R.string.nagmedar_shell_title)))
    val keepHdr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    return listOf(
        MenuEntry.Item(stringResource(R.string.nag_sc_snapshot), actions::takeSnapshot, shortcut = keys),
        MenuEntry.Separator,
        MenuEntry.Item(
            stringResource(R.string.nag_snap_with),
            { actions.setSnapshotSettings(settings.copy(subtitles = !settings.subtitles)) },
            checked = settings.subtitles,
        ),
        MenuEntry.Separator,
    ) + SnapshotFormat.entries.map { format ->
        MenuEntry.Item(format.label, { actions.setSnapshotSettings(settings.copy(format = format)) }, checked = settings.format == format)
    } + listOf(
        MenuEntry.Separator,
        MenuEntry.Item(
            stringResource(R.string.nag_snap_menu_tone_map),
            { actions.setSnapshotSettings(settings.copy(hdr = HdrSnapshot.ToneMap)) },
            checked = settings.hdr == HdrSnapshot.ToneMap || !keepHdr,
        ),
        MenuEntry.Item(
            stringResource(R.string.nag_snap_menu_keep_hdr),
            { actions.setSnapshotSettings(settings.copy(hdr = HdrSnapshot.Keep)) },
            enabled = keepHdr,
            checked = settings.hdr == HdrSnapshot.Keep && keepHdr,
        ),
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.nag_snap_menu_folder, folder), {}, enabled = false),
        MenuEntry.Item(stringResource(R.string.nag_snap_menu_change_folder), actions::chooseSnapshotFolder),
    ) + listOfNotNull(
        settings.folder?.let { MenuEntry.Item(stringResource(R.string.nag_snap_menu_default_folder), { actions.setSnapshotSettings(settings.copy(folder = null)) }) },
    )
}

/** The speaker mutes and brings the sound back; the slider sets the volume, past 100 % with audio boost. */
@Composable
private fun Volume(state: PlayerChromeState, actions: PlayerChromeActions, slider: Boolean) {
    val muted = state.muted || state.volume == 0
    BarButton(
        NagIcons.Volume,
        stringResource(if (muted) R.string.nag_unmute else R.string.nag_mute),
        actions::toggleMute,
        ShortcutId.Mute,
        selected = muted,
    )
    if (slider) {
        val label = stringResource(R.string.nag_volume)
        val max = state.maxVolume.coerceAtLeast(1)
        NagSlider(
            value = if (muted) 0f else state.volume.coerceIn(0, max) / max.toFloat(),
            onValueChange = { fraction -> actions.setVolume((fraction * max).roundToInt()) },
            modifier = Modifier.width(96.dp).semantics { contentDescription = label }.testTag("player-volume"),
        )
    }
}
