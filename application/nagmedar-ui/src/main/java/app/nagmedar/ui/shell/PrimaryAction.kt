package app.nagmedar.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.TooltipArea
import app.nagmedar.ui.components.TooltipPlacement
import app.nagmedar.ui.components.animateInteractionColor
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/**
 * 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 5): the screen's main action ("Yeni liste"), where each layout keeps it:
 * - on a phone a floating button at the end, above the bottom bar and the player: it goes down with the bars when the
 *   list scrolls them away, shows its [label] while the list is at its top (only the [icon] once it is scrolled), and is
 *   gone while the on-screen keyboard is up and under the expanding player. Lists under the bars end above it, and the
 *   notices sit over it;
 * - on the rail an accent button at the rail's top, named by a tooltip;
 * - in the sidebar an accent button with the label at the sidebar's top, the icon alone while the sidebar is collapsed.
 *
 * Call it in the screen. The last call wins (the screen coming in over the one leaving); leaving composition takes the
 * button away. Outside the shell nothing shows.
 */
@Composable
fun ShellPrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val host = LocalPrimaryActionHost.current ?: return
    val latestClick by rememberUpdatedState(onClick)
    val stableClick: () -> Unit = remember { { latestClick() } }
    val action = PrimaryAction(label, icon, stableClick)
    val entry = remember(host) { PrimaryActionHost.Entry(action) }
    SideEffect { entry.value = action }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** What a [ShellPrimaryAction] call asks for. */
@Immutable
internal data class PrimaryAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** The [ShellPrimaryAction] calls in composition, oldest first; the newest shows. */
@Stable
internal class PrimaryActionHost {
    class Entry(initial: PrimaryAction) {
        var value by mutableStateOf(initial)
    }

    val entries = mutableStateListOf<Entry>()

    val current: PrimaryAction? get() = entries.lastOrNull()?.value
}

internal val LocalPrimaryActionHost = staticCompositionLocalOf<PrimaryActionHost?> { null }

/**
 * The accent button of a [PrimaryAction]: [height] tall, the label beside the icon while [extended], the icon alone
 * (square) otherwise; the label comes and goes sideways. [floating] lifts it with a shadow.
 */
@Composable
internal fun PrimaryActionButton(action: PrimaryAction, extended: Boolean, height: Dp, modifier: Modifier = Modifier, floating: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val tier = LocalMotionTier.current
    val shape = RoundedCornerShape(if (floating) dimens.radiusCard.coerceAtLeast(16.dp) else dimens.radiusControl)
    val fill by animateInteractionColor(
        when {
            state.pressed -> lerp(colors.accent, colors.onAccent, 0.24f)
            state.hovered -> lerp(colors.accent, colors.onAccent, 0.12f)
            else -> colors.accent
        },
    )
    // named by a tooltip as well once the label is gone (the mouse's rest, the keyboard's focus)
    TooltipArea(action.label, placement = TooltipPlacement.End, enabled = !extended) {
        Row(
            modifier
                .height(height)
                .widthIn(min = height)
                .pressScale(state.pressed)
                .then(if (floating) Modifier.shadow(8.dp, shape) else Modifier)
                .focusRing(state, shape)
                .background(fill, shape)
                .semantics { if (!extended) contentDescription = action.label }
                .nagClickable(interaction, action.onClick)
                .padding(horizontal = (height - dimens.icon) / 2)
                .testTag("primary-action"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(action.icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(dimens.icon))
            AnimatedVisibility(
                visible = extended,
                enter = fadeIn(NagMotion.fade(tier, tween(NagMotion.MEDIUM))) + expandHorizontally(NagMotion.fade(tier, NagTheme.motion.fastSpatial())),
                exit = fadeOut(NagMotion.fade(tier, tween(NagMotion.SHORT))) + shrinkHorizontally(NagMotion.fade(tier, NagTheme.motion.fastSpatial())),
                label = "primaryActionLabel",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(dimens.gapS))
                    Text(action.label, style = NagTheme.type.label, color = colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(dimens.gapXs))
                }
            }
        }
    }
}

/**
 * The phone's floating button: at the end, a margin above what [modifier] keeps it above, gone (scaled down into its
 * corner) while not [shown] or without an [action]; with the label while [extended]. Its height with the margin goes to
 * [metrics] (lists end above it, the notices sit over it). An expanding player covers it: it fades with the player's
 * way up and, once it is [covered] (the player expanded, a page over the window), it is gone, neither tapped, focused
 * nor read out, its room kept for when the player goes down again.
 */
@Composable
internal fun PhoneActionButton(
    action: PrimaryAction?,
    shown: Boolean,
    extended: Boolean,
    metrics: ShellChromeMetrics,
    expandProgress: () -> Float,
    modifier: Modifier,
    covered: Boolean = false,
) {
    val dimens = NagTheme.dimens
    val tier = LocalMotionTier.current
    // the last action stays on the button while it goes
    val last = remember { LastAction() }
    if (action != null) last.value = action
    val placed = action != null && shown
    val visible = placed && !covered
    // its room, also when it comes back before its way out has ended (the size does not change then, so no new
    // measurement says it); kept while the player covers it
    SideEffect { metrics.actionPx = if (placed) last.height else 0 }
    val corner = TransformOrigin(1f, 1f)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.graphicsLayer { alpha = 1f - expandProgress().coerceIn(0f, 1f) },
        enter = fadeIn(NagMotion.fade(tier, tween(NagMotion.MEDIUM))) + scaleIn(NagMotion.fade(tier, NagMotion.open()), initialScale = 0.6f, transformOrigin = corner),
        exit = fadeOut(NagMotion.fade(tier, tween(NagMotion.SHORT))) + scaleOut(NagMotion.fade(tier, NagTheme.motion.fastSpatial()), targetScale = 0.6f, transformOrigin = corner),
        label = "phoneAction",
    ) {
        val current = last.value ?: return@AnimatedVisibility
        Box(
            Modifier
                .onSizeChanged {
                    last.height = it.height
                    if (placed) metrics.actionPx = it.height
                }
                .padding(end = dimens.gapL, bottom = dimens.gapL),
        ) {
            PrimaryActionButton(current, extended, height = 56.dp, floating = true)
        }
    }
}

/** The action the phone's button last showed and its height with the margin; nothing redraws from them. */
private class LastAction {
    var value: PrimaryAction? = null
    var height = 0
}
