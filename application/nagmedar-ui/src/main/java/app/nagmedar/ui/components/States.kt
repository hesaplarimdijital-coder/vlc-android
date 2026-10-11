package app.nagmedar.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.mixSrgb
import kotlinx.coroutines.delay

/*
 * S-308 state components: empty, permission, loading, no results, error, warning, success and
 * the undo bar. Every state names the next step (a button, a suggestion or a progress line) and
 * is a live region, so TalkBack reads it when it appears or changes.
 */

/** Tone of a state: its badge, icon tile, alert edge and, for success, its primary button. */
enum class StateTone { Neutral, Info, Warning, Error, Success }

/** Colours of a [StateTone] in the current theme; each text pair keeps 4.5:1 (PaletteContrastTest). */
@Immutable
data class ToneColors(
    /** Badge and tinted backgrounds. */
    val container: Color,
    /** Text and icons on [container] and on the plain surfaces. */
    val content: Color,
    val iconContainer: Color,
    val icon: Color,
    /** The tone itself: borders, the alert's edge. */
    val base: Color,
)

fun NagColors.tone(tone: StateTone): ToneColors {
    val base = when (tone) {
        StateTone.Neutral -> textSecondary
        StateTone.Info -> accentText
        StateTone.Warning -> warning
        StateTone.Error -> danger
        StateTone.Success -> success
    }
    return ToneColors(
        container = mixSrgb(base, surface, if (isDark) 0.16f else 0.10f),
        // on the dark themes the tones are lightened for text, on the light theme they are already deep
        content = if (isDark && tone != StateTone.Neutral) mixSrgb(base, Color.White, 0.72f) else base,
        iconContainer = mixSrgb(base, surface, if (isDark) 0.14f else 0.10f),
        icon = if (tone == StateTone.Neutral) textTertiary else base,
        base = base,
    )
}

/** Background of an [AlertBanner] of [tone]: the raised surface with a hint of the tone. */
fun NagColors.alertBackground(tone: StateTone): Color = mixSrgb(this.tone(tone).base, surfaceRaised, if (isDark) 0.08f else 0.06f)

@Composable
@ReadOnlyComposable
fun toneColors(tone: StateTone): ToneColors = NagTheme.colors.tone(tone)

/** The next step a state offers. */
@Immutable
data class StateAction(val label: String, val onClick: () -> Unit)

/** Small coloured label: "BOŞ", "4 sorun", "4 yeni". */
@Composable
fun ToneBadge(text: String, tone: StateTone, modifier: Modifier = Modifier) {
    val c = toneColors(tone)
    Text(
        text = text,
        style = NagTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em),
        color = c.content,
        maxLines = 1,
        modifier = modifier
            .background(c.container, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
fun StateIconTile(icon: ImageVector, tone: StateTone, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = toneColors(tone)
    Box(
        modifier
            .size(size)
            .background(c.iconContainer, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(size / 2))
    }
}

private fun liveMode(tone: StateTone) =
    if (tone == StateTone.Error || tone == StateTone.Warning) LiveRegionMode.Assertive else LiveRegionMode.Polite

/**
 * A state in the middle of a screen or a card: icon, title, what happened and what to do, then
 * the actions. [content] sits between the text and the actions (a progress bar, a switch).
 * [dense] is the card size of the states board: smaller type and gaps.
 */
@Composable
fun StatePanel(
    tone: StateTone,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    primary: StateAction? = null,
    secondary: StateAction? = null,
    dense: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimens = NagTheme.dimens
    Column(
        modifier = modifier.widthIn(max = 460.dp).padding(if (dense) dimens.gapM else dimens.gapL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateText(tone, icon, title, description, dense, liveMode(tone))
        if (content != null) {
            Spacer(Modifier.height(if (dense) dimens.gapS else dimens.gapM))
            content()
        }
        if (primary != null || secondary != null) {
            Spacer(Modifier.height(if (dense) 10.dp else dimens.gapL))
            StateActions(tone, primary, secondary)
        }
    }
}

/** Icon tile, title and description, read out together as one live region. */
@Composable
private fun StateText(tone: StateTone, icon: ImageVector, title: String, description: String?, dense: Boolean, mode: LiveRegionMode) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = mode },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIconTile(icon, tone)
        Spacer(Modifier.height(if (dense) dimens.gapS else dimens.gapM))
        Text(
            title,
            style = if (dense) NagTheme.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold) else NagTheme.type.title,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Spacer(Modifier.height(dimens.gapXs))
            Text(
                description,
                style = if (dense) NagTheme.type.labelSmall else NagTheme.type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateActions(tone: StateTone, primary: StateAction?, secondary: StateAction?) {
    val colors = NagTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS),
    ) {
        if (primary != null) {
            val success = tone == StateTone.Success
            NagButton(
                text = primary.label,
                onClick = primary.onClick,
                primary = true,
                fill = if (success) colors.success else null,
                onFill = if (success) successOnFill(colors) else null,
            )
        }
        if (secondary != null) NagButton(text = secondary.label, onClick = secondary.onClick)
    }
}

/** Text on the success colour used as a fill: deep green on the light green, white on the deep one. */
fun successOnFill(colors: NagColors): Color = if (colors.isDark) Color(0xFF0B1F15) else Color.White

/** Determinate ([progress] 0..1) or indeterminate (null) progress line with progress semantics. */
@Composable
fun NagProgressBar(progress: Float?, label: String, modifier: Modifier = Modifier, color: Color = NagTheme.colors.accent) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(3.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(shape)
            .background(colors.hover)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = if (progress == null) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                }
            },
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color, shape))
        } else {
            val loop = NagTheme.motion.loop(NagMotion.SWEEP)
            if (loop == null) {
                // motion reduced: a still dashed line says "working" without moving
                Canvas(Modifier.fillMaxSize()) {
                    val dash = 8.dp.toPx()
                    val step = dash + 6.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, 0f),
                            size = Size(minOf(dash, size.width - x), size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                        x += step
                    }
                }
            } else {
                val transition = rememberInfiniteTransition(label = "progress")
                val position by transition.animateFloat(initialValue = -0.3f, targetValue = 1f, animationSpec = loop, label = "sweep")
                Box(
                    Modifier
                        .offset(x = maxWidth * position)
                        .width(maxWidth * 0.3f)
                        .fillMaxHeight()
                        .background(color, shape),
                )
            }
        }
    }
}

/**
 * Loading: placeholders shaped like what is coming, then what is happening and how far it got
 * ("Kütüphane taranıyor · 1 248 / 3 900"). [posters] placeholders in a row, 0 for none.
 */
@Composable
fun LoadingState(
    title: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressText: String? = null,
    detail: String? = null,
    posters: Int = 4,
    dense: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.widthIn(max = 560.dp).padding(if (dense) 14.dp else dimens.gapL)) {
        if (posters > 0) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(posters) { index ->
                    // posters are 2:3; the dense card size crops them to 3:4 so the progress line fits
                    SkeletonBlock(Modifier.weight(1f).aspectRatio(if (dense) 3f / 4f else 2f / 3f), shimmer = index == 0)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(9.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(9.dp))
            }
            Spacer(Modifier.height(if (dense) dimens.gapM else dimens.gapL))
        }
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NagTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                if (progressText != null) {
                    Spacer(Modifier.width(dimens.gapS))
                    Text(progressText, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            NagProgressBar(progress, label = title)
            if (detail != null) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Grey placeholder block; [shimmer] sweeps a lighter band across it while loading, unless motion
 * is reduced (S-203).
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shimmer: Boolean = false) {
    val colors = NagTheme.colors
    val base = mixSrgb(colors.hover, colors.surface, 0.6f)
    val shape = RoundedCornerShape(7.dp)
    val loop = if (shimmer) NagTheme.motion.loop(NagMotion.SHIMMER) else null
    if (loop == null) {
        Box(modifier.background(base, shape))
        return
    }
    val highlight = colors.hover
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = loop, label = "shimmer")
    BoxWithConstraints(modifier.clip(shape).background(base)) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val start = -width + shift * width * 2
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0.3f to base,
                        0.5f to highlight,
                        0.7f to base,
                        startX = start,
                        endX = start + width,
                    ),
                ),
        )
    }
}

/** A pill that applies a suggestion: "Filtreleri temizle", "Ağ kaynaklarında ara". */
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(50)
    val rest = if (highlighted) mixSrgb(colors.accent, colors.background, 0.14f) else Color.Transparent
    val border = if (highlighted) mixSrgb(colors.accent, colors.background, 0.55f) else colors.borderStrong
    Box(
        modifier = modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, rest = rest), shape)
            .border(1.dp, border, shape)
            .nagClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NagTheme.type.label, color = if (highlighted) colors.text else colors.textSecondary, maxLines = 1)
    }
}

/** Nothing matched: what was searched, which filters narrowed it, and ways out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoResultsState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    suggestions: List<StateAction> = emptyList(),
    dense: Boolean = false,
) {
    val dimens = NagTheme.dimens
    Column(modifier.widthIn(max = 480.dp).padding(if (dense) dimens.gapM else dimens.gapL), horizontalAlignment = Alignment.CenterHorizontally) {
        StateText(StateTone.Neutral, NagIcons.SearchOff, title, detail, dense, LiveRegionMode.Polite)
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(if (dense) dimens.gapS else dimens.gapM))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    SuggestionChip(suggestion.label, suggestion.onClick, highlighted = index == 0)
                }
            }
        }
    }
}

fun defaultStateIcon(tone: StateTone): ImageVector = when (tone) {
    StateTone.Neutral, StateTone.Info -> NagIcons.Info
    StateTone.Warning -> NagIcons.Warning
    StateTone.Error -> NagIcons.ErrorCircle
    StateTone.Success -> NagIcons.SuccessCircle
}

/**
 * A warning or error that interrupts without blocking: "Bit-perfect devre dışı". Read out at
 * once (assertive live region); the last action is outlined as the way back.
 */
@Composable
fun AlertBanner(
    tone: StateTone,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = defaultStateIcon(tone),
    actions: List<StateAction> = emptyList(),
) {
    val colors = NagTheme.colors
    val c = toneColors(tone)
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .widthIn(max = 480.dp)
            .height(IntrinsicSize.Min)
            .shadow(12.dp, shape)
            .background(colors.alertBackground(tone), shape)
            .border(1.dp, mixSrgb(c.base, colors.surfaceRaised, 0.45f), shape)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                paneTitle = title
            },
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.base))
        Column(Modifier.weight(1f).padding(start = 11.dp, top = 12.dp, end = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = c.base, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = NagTheme.type.bodyStrong, color = c.content)
            }
            Spacer(Modifier.height(6.dp))
            Text(message, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEachIndexed { index, action ->
                        ToneTextButton(action, tone, outlined = actions.size > 1 && index == actions.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneTextButton(action: StateAction, tone: StateTone, outlined: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val c = toneColors(tone)
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    val background = when {
        state.pressed -> mixSrgb(c.base, colors.surfaceRaised, 0.26f)
        state.hovered -> mixSrgb(c.base, colors.surfaceRaised, 0.16f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .heightIn(min = NagTheme.dimens.minTarget.coerceAtLeast(28.dp))
            .focusRing(state, shape)
            .background(background, shape)
            .then(if (outlined) Modifier.border(1.dp, mixSrgb(c.base, colors.surfaceRaised, 0.5f), shape) else Modifier)
            .nagClickable(interaction, action.onClick, role = Role.Button)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(action.label, style = NagTheme.type.label, color = c.content, maxLines = 1)
    }
}

// ---------------------------------------------------------------- undo bar

/** A change that can still be taken back: "3 parça sıradan kaldırıldı · Geri al". */
@Immutable
class UndoMessage internal constructor(
    val id: Long,
    val text: String,
    val actionLabel: String,
    val shortcut: String?,
    val onUndo: () -> Unit,
)

/**
 * The window's undo bars, newest last. At most [MAX_VISIBLE] stay on screen; each one leaves
 * after [VISIBLE_MILLIS] of display time, which stops while the pointer rests on it or it has
 * keyboard focus. Ctrl+Z ([undoLatest]) takes back the newest.
 */
@Stable
class UndoBarState {
    private var nextId = 0L
    val messages = mutableStateListOf<UndoMessage>()

    fun show(text: String, actionLabel: String, shortcut: String? = null, onUndo: () -> Unit): UndoMessage {
        val message = UndoMessage(nextId++, text, actionLabel, shortcut, onUndo)
        messages.add(message)
        while (messages.size > MAX_VISIBLE) messages.removeAt(0)
        return message
    }

    fun dismiss(message: UndoMessage) {
        messages.remove(message)
    }

    /** Takes back the newest change still on screen; false when there is none. */
    fun undoLatest(): Boolean {
        val message = messages.lastOrNull() ?: return false
        undo(message)
        return true
    }

    fun undo(message: UndoMessage) {
        if (messages.remove(message)) message.onUndo()
    }

    companion object {
        const val VISIBLE_MILLIS = 8_000L
        const val MAX_VISIBLE = 2
        internal const val TICK_MILLIS = 100L
    }
}

@Composable
fun rememberUndoBarState(): UndoBarState = remember { UndoBarState() }

/** The undo bars, stacked: the older one is dimmed. */
@Composable
fun UndoBarHost(state: UndoBarState, modifier: Modifier = Modifier) {
    val messages = state.messages
    if (messages.isEmpty()) return
    Column(modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        messages.forEachIndexed { index, message ->
            key(message.id) {
                UndoBar(
                    message = message,
                    onUndo = { state.undo(message) },
                    onTimeout = { state.dismiss(message) },
                    older = index < messages.lastIndex,
                )
            }
        }
    }
}

@Composable
fun UndoBar(message: UndoMessage, onUndo: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier, older: Boolean = false) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val paused = hovered || focused
    var remaining by remember(message.id) { mutableLongStateOf(UndoBarState.VISIBLE_MILLIS) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(message.id, paused) {
        while (!paused && remaining > 0) {
            delay(UndoBarState.TICK_MILLIS)
            remaining -= UndoBarState.TICK_MILLIS
        }
        if (remaining <= 0) timeout()
    }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .alpha(if (older) 0.75f else 1f)
            .shadow(10.dp, shape)
            .background(colors.inverseSurface, shape)
            .hoverable(interaction)
            .onFocusChanged { focused = it.hasFocus }
            .heightIn(min = 42.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message.text,
            style = NagTheme.type.body,
            color = colors.onInverseSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(12.dp))
        InverseTextButton(message.actionLabel, onUndo)
        if (message.shortcut != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                message.shortcut,
                style = NagTheme.type.mono,
                color = mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.78f),
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun InverseTextButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val background = when {
        state.pressed -> mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.16f)
        state.hovered -> mixSrgb(colors.onInverseSurface, colors.inverseSurface, 0.08f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .heightIn(min = NagTheme.dimens.minTarget.coerceAtLeast(30.dp))
            .then(if (state.focused) Modifier.border(2.dp, colors.inverseAccent, shape) else Modifier)
            .background(background, shape)
            .nagClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NagTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = colors.inverseAccent, maxLines = 1)
    }
}
