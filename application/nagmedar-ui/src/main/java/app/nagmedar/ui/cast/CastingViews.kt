package app.nagmedar.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.player.PlayerChromeState
import app.nagmedar.ui.player.PlayerTimeline
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.theme.NagTheme

/**
 * S-1302: the cast button of the player bars, the music panels and the video player: it opens the
 * device picker; lit while [casting], with where it plays in its name.
 */
@Composable
fun CastButton(
    casting: CastDevice?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight),
    iconSize: Dp = NagTheme.dimens.icon,
) {
    NagIconButton(
        NagIcons.Cast,
        if (casting != null) stringResource(R.string.nag_cast_casting_to, casting.name) else stringResource(R.string.nag_sc_cast),
        onClick,
        modifier = modifier.testTag("cast-button"),
        selected = casting != null,
        size = size,
        iconSize = iconSize,
        shortcut = ShortcutId.Cast,
    )
}

/**
 * S-1302: "Casting to Salon TV" over a music panel while a renderer plays: the device's kind, its
 * name, and casting stopped (the track comes back to this device where it was); in a narrow pane
 * the stop button is its icon, named by its tooltip.
 */
@Composable
fun CastingStrip(device: CastDevice, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < STRIP_TEXT_BUTTON
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.selection, RoundedCornerShape(dimens.radiusControl))
                .padding(start = dimens.gapM, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("casting-strip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kindIcon(device.kind), contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(dimens.gapS))
            Text(
                stringResource(R.string.nag_cast_casting_to, device.name),
                style = NagTheme.type.bodyStrong,
                color = colors.onSelection,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val stop = stringResource(R.string.nag_cast_stop)
            if (narrow) {
                NagIconButton(NagIcons.Stop, stop, onStop, tint = colors.onSelection)
            } else {
                NagButton(stop, onStop, icon = NagIcons.Stop)
            }
        }
    }
}

/** Below this width the strip's stop button is its icon. */
private val STRIP_TEXT_BUTTON = 360.dp

/** What the video player's casting controls show (S-1302): the playback service's, read twice a second. */
@Immutable
data class CastingScreenState(
    val device: CastDevice,
    val title: String,
    val playing: Boolean,
    val timeMs: Long,
    val lengthMs: Long,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

/** What the casting controls do: the playback service's, which acts on the renderer. */
interface CastingActions {
    fun togglePlayPause()

    fun previous()

    fun next()

    fun seekTo(ms: Long)

    fun seekBy(deltaMs: Int)

    /** The device picker: another device, or this one. */
    fun choose()

    /** Casting stopped: the film goes on here. */
    fun stopCasting()

    /** Leaves the player. */
    fun close()
}

/**
 * S-1302: the video player while a renderer plays the film. The picture is not drawn here: the
 * screen is the remote, saying where the film plays, with its timeline, the transport and the
 * queue's previous and next, the picker, casting stopped. libVLC's renderers take no volume: it is
 * set on the device.
 */
@Composable
fun CastingScreen(state: CastingScreenState, actions: CastingActions, modifier: Modifier = Modifier, timelineHover: Float? = null) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background).testTag("casting-screen")) {
        val narrow = maxWidth < 600.dp
        Column(Modifier.fillMaxSize().padding(horizontal = dimens.gapL, vertical = dimens.gapM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_player_close), actions::close)
                Spacer(Modifier.width(dimens.gapS))
                Text(state.title, style = NagTheme.type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CastButton(state.device, actions::choose)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(if (narrow) 88.dp else 112.dp).clip(CircleShape).background(colors.selection), contentAlignment = Alignment.Center) {
                    Icon(kindIcon(state.device.kind), contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(if (narrow) 40.dp else 52.dp))
                }
                Spacer(Modifier.height(dimens.gapL))
                Text(
                    stringResource(R.string.nag_cast_casting_to, state.device.name),
                    style = NagTheme.type.headline,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.gapXs))
                Text(
                    stringResource(R.string.nag_cast_remote_hint, state.device.name),
                    style = NagTheme.type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
            val timeline = PlayerChromeState(title = state.title, playing = state.playing, timeMs = state.timeMs, lengthMs = state.lengthMs)
            PlayerTimeline(timeline, actions::seekTo, previews = null, initialHover = timelineHover)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(playerClock(state.timeMs), style = NagTheme.type.mono, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(playerClock(state.lengthMs), style = NagTheme.type.mono, color = colors.textSecondary)
            }
            Spacer(Modifier.height(dimens.gapS))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else dimens.gapM, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NagIconButton(NagIcons.Previous, stringResource(R.string.nag_sc_previous), actions::previous, enabled = state.hasPrevious, shortcut = ShortcutId.Previous)
                NagIconButton(NagIcons.Resume, stringResource(R.string.nag_sc_back_10s), { actions.seekBy(-10_000) }, shortcut = ShortcutId.JumpBack)
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(colors.accent))
                    NagIconButton(
                        if (state.playing) NagIcons.Pause else NagIcons.Play,
                        stringResource(if (state.playing) R.string.nag_pause else R.string.nag_play),
                        actions::togglePlayPause,
                        size = 64.dp,
                        iconSize = 26.dp,
                        tint = colors.onAccent,
                        shortcut = ShortcutId.PlayPause,
                    )
                }
                NagIconButton(NagIcons.Forward, stringResource(R.string.nag_sc_forward_10s), { actions.seekBy(10_000) }, shortcut = ShortcutId.JumpForward)
                NagIconButton(NagIcons.Next, stringResource(R.string.nag_sc_next), actions::next, enabled = state.hasNext, shortcut = ShortcutId.Next)
            }
            Spacer(Modifier.height(dimens.gapS))
            if (narrow) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    VolumeNote()
                    NagButton(stringResource(R.string.nag_cast_stop), actions::stopCasting, icon = NagIcons.Stop, modifier = Modifier.testTag("casting-stop"))
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VolumeNote(Modifier.weight(1f))
                    NagButton(stringResource(R.string.nag_cast_stop), actions::stopCasting, icon = NagIcons.Stop, modifier = Modifier.testTag("casting-stop"))
                }
            }
        }
    }
}

/** libVLC's renderers take no volume: it is the device's. */
@Composable
private fun VolumeNote(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(NagIcons.Volume, contentDescription = null, tint = NagTheme.colors.textTertiary, modifier = Modifier.size(NagTheme.dimens.icon))
        Spacer(Modifier.width(NagTheme.dimens.gapS))
        Text(stringResource(R.string.nag_cast_volume_note), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}
