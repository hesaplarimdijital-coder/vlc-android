package app.nagmedar.ui.music

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.Waveform
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.playback.softLevels
import app.nagmedar.ui.playback.waveShape
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.BodoniHeadline
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.max

/** 1.1.2 (the design's tablet page): what the panel's transport shows and does. */
@Immutable
class PanelTransport(
    val shuffling: Boolean,
    val repeating: Boolean,
    val repeatOne: Boolean,
    val onTogglePlay: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
)

/** The vinyl's colours: a record is black in either theme. */
private val VINYL = Color(0xFF0A0A0B)
private val GROOVE = Color(0xFF18191B)

/** The sleeve's side and the record's, and how far the record slides out from under it. */
private val SLEEVE = 196.dp
private val RECORD_OUT = 136.dp

/** How long a turn of the record takes while it plays (33⅓ a minute). */
private const val TURN_MS = 1_800f

/**
 * 1.1.2 (the design's tablet page, the panel "Şimdi çalıyor"): what plays, as the design draws it. The album's sleeve with
 * its record slid out from under it (the record's label in the band's [inks], turning while it plays); the title in
 * Bodoni's italic, the artist and the album ([links]); the waveform's bars, the played part in the ink, a tap or a drag
 * seeking ([onSeek]); the times; the transport ([transport]: shuffle, previous, play, next, repeat); "Sıradaki" with the
 * next four ([upNext], [onPlayUpNext], numbered from [upNextFrom]); [below] (the visualiser, the format, the stars, the
 * tags), then the keys at the panel's foot with a keyboard.
 */
@Composable
internal fun DesignedPlaying(
    info: NowPlayingInfo,
    progress: () -> Float,
    waveform: Waveform?,
    upNext: List<QueueEntry>,
    upNextFrom: Int,
    transport: PanelTransport,
    onSeek: (Float) -> Unit,
    onPlayUpNext: (Int) -> Unit,
    artwork: @Composable (Dp) -> Unit,
    inks: EbruInks,
    links: Links,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit = {},
) {
    val colors = NagTheme.colors
    val type = NagTheme.type
    Column(modifier.fillMaxSize().testTag("panel-playing")) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Record(artwork, inks, info.playing)
            Spacer(Modifier.height(26.dp))
            Text(
                info.title,
                style = TextStyle(fontFamily = BodoniHeadline, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = type.size(30f), lineHeight = type.size(36f)),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            PlayingArtistAlbum(info, links, designText(15f, 20f))
            Spacer(Modifier.height(22.dp))
            val levels = remember(waveform, info.title) {
                waveform?.let { w -> softLevels(w, BARS).map { max(0.12f, it) }.toFloatArray() } ?: waveShape(info.title, BARS)
            }
            SeekBars(progress, levels, info.lengthMs, onSeek)
            Spacer(Modifier.height(6.dp))
            Times(progress, info.lengthMs)
            Spacer(Modifier.height(16.dp))
            Transport(info.playing, transport)
            if (upNext.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                UpNext(upNext, upNextFrom, transport.shuffling, onPlayUpNext)
            }
            below()
            Spacer(Modifier.height(16.dp))
        }
        PanelKeys()
    }
}

/** The bars of the panel's waveform. */
private const val BARS = 60

/** The sleeve and the record slid out from under it, its label in the band's inks, turning while [spinning]. */
@Composable
private fun Record(artwork: @Composable (Dp) -> Unit, inks: EbruInks, spinning: Boolean) {
    val reduced = NagTheme.motion.reduced
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spinning, reduced) {
        if (!spinning || reduced) return@LaunchedEffect
        var last = -1L
        // an infinite animation's frames: a test's clock does not wait on them
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                if (last >= 0) angle = (angle + (now - last) * 360f / TURN_MS) % 360f
                last = now
            }
        }
    }
    val panel = NagTheme.colors.surface
    BoxWithConstraints(Modifier.fillMaxWidth().height(SLEEVE).clearAndSetSemantics {}) {
        val out = (maxWidth - SLEEVE).coerceIn(0.dp, RECORD_OUT)
        Box(Modifier.offset(x = out).size(SLEEVE).shadow(12.dp, CircleShape).background(VINYL, CircleShape)) {
            // the grooves, then the sheen over them, the label and its hole
            Canvas(Modifier.fillMaxSize()) {
                val step = 2.4.dp.toPx()
                val groove = 1.3.dp.toPx()
                var r = step
                val outer = size.minDimension / 2f
                while (r < outer) {
                    drawCircle(GROOVE, r, style = Stroke(groove))
                    r += step
                }
                val inner = 39.dp.toPx()
                val sheen = Brush.sweepGradient(
                    0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.09f), 0.18f to Color.Transparent,
                    0.5f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.07f), 0.68f to Color.Transparent, 1f to Color.Transparent,
                )
                drawCircle(sheen, (inner + outer) / 2f, style = Stroke(outer - inner))
                drawCircle(VINYL, 38.dp.toPx())
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(68.dp)
                    .graphicsLayer { rotationZ = angle }
                    .clip(CircleShape),
            ) {
                // the design's label: the band's middle, larger than the label
                EbruBand(
                    inks, 96.dp,
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(332.dp.roundToPx(), 96.dp.roundToPx()))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-132.dp.roundToPx(), -10.dp.roundToPx()) }
                    },
                )
            }
            Box(Modifier.align(Alignment.Center).size(6.dp).background(panel, CircleShape))
        }
        Box(Modifier.size(SLEEVE).shadow(16.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) { artwork(SLEEVE) }
    }
}

/** The waveform's bars, the played part in the ink; a tap or a drag seeks, TalkBack hears it as a slider of the times. */
@Composable
private fun SeekBars(progress: () -> Float, levels: FloatArray, lengthMs: Long, onSeek: (Float) -> Unit) {
    val colors = NagTheme.colors
    val accent = colors.accent
    val off = colors.text.copy(alpha = 0.16f)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.nag_position)
    val seconds by remember(lengthMs) { derivedStateOf { (progress().coerceIn(0f, 1f) * lengthMs / 1000).toLong() } }
    val said = formatDuration(seconds * 1000) + " / " + formatDuration(lengthMs)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(lengthMs) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(lengthMs) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragged?.let(onSeek)
                        dragged = null
                    },
                    onDragCancel = { dragged = null },
                ) { change, _ ->
                    change.consume()
                    dragged = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = said
                progressBarRangeInfo = ProgressBarRangeInfo(if (lengthMs > 0) seconds * 1000f / lengthMs else 0f, 0f..1f)
                setProgress { target ->
                    onSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .testTag("panel-seek"),
    ) {
        val n = levels.size
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val played = dragged ?: progress()
        for (i in 0 until n) {
            val h = (4f + levels[i] * 32f).dp.toPx().coerceAtMost(size.height)
            drawRoundRect(if ((i + 0.5f) / n <= played) accent else off, Offset(i * (w + gap), (size.height - h) / 2f), Size(w, h), CornerRadius(2.dp.toPx()))
        }
    }
}

/** The time played and the length, under the bars. */
@Composable
private fun Times(progress: () -> Float, lengthMs: Long) {
    val colors = NagTheme.colors
    val seconds by remember(lengthMs) { derivedStateOf { (progress().coerceIn(0f, 1f) * lengthMs / 1000).toLong() } }
    val style = designText(13f, 18f).copy(fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(seconds * 1000), style = style, color = colors.textTertiary)
        Text(if (lengthMs > 0) formatDuration(lengthMs) else "", style = style, color = colors.textTertiary)
    }
}

/** Shuffle, previous, play or pause in the ink, next, repeat; shuffle and repeat in the ink with a dot while on. */
@Composable
private fun Transport(playing: Boolean, transport: PanelTransport) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        ModeButton(NagIcons.Shuffle, stringResource(R.string.nag_shuffle), transport.shuffling, ShortcutId.Shuffle, transport.onShuffle, "panel-shuffle")
        NagIconButton(NagIcons.Previous, stringResource(R.string.nag_previous), transport.onPrevious, Modifier.testTag("panel-previous"), size = 48.dp, iconSize = 24.dp, tint = colors.text, shortcut = ShortcutId.Previous)
        PlayButton(playing, transport.onTogglePlay)
        NagIconButton(NagIcons.Next, stringResource(R.string.nag_next), transport.onNext, Modifier.testTag("panel-next"), size = 48.dp, iconSize = 24.dp, tint = colors.text, shortcut = ShortcutId.Next)
        ModeButton(NagIcons.Repeat, stringResource(R.string.nag_repeat), transport.repeating, ShortcutId.Repeat, transport.onRepeat, "panel-repeat", one = transport.repeatOne)
    }
}

/** Shuffle's or repeat's button: in the ink with a dot under it while on (repeat one with its "1"). */
@Composable
private fun ModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, shortcut: ShortcutId, onClick: () -> Unit, tag: String, one: Boolean = false) {
    val colors = NagTheme.colors
    val state = stringResource(if (on) R.string.nag_prefs_on else R.string.nag_prefs_off)
    Box(Modifier.size(48.dp).semantics { stateDescription = state }) {
        NagIconButton(icon, label, onClick, Modifier.testTag(tag), size = 48.dp, iconSize = 22.dp, tint = if (on) colors.accentText else colors.textSecondary, shortcut = shortcut)
        if (one) {
            Text("1", style = designText(9f, 10f, FontWeight.Bold), color = colors.accentText, modifier = Modifier.align(Alignment.Center).padding(start = 1.dp))
        }
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).background(colors.accent, CircleShape))
    }
}

/** Play or pause: 64 dp in the ink, its shadow under it. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val label = stringResource(if (playing) R.string.nag_pause else R.string.nag_play)
    Box(
        Modifier
            .size(64.dp)
            .pressScale(state.pressed)
            .focusRing(state, CircleShape)
            .shadow(12.dp, CircleShape)
            .background(colors.accent, CircleShape)
            .background(if (state.pressed) colors.onAccent.copy(alpha = 0.16f) else if (state.hovered) colors.onAccent.copy(alpha = 0.08f) else Color.Transparent, CircleShape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .semantics { contentDescription = label }
            .testTag("panel-play"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) NagIcons.Pause else NagIcons.Play, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(28.dp))
    }
}

/** "Sıradaki" and the next four tracks, numbered as the queue has them; a tap plays one. */
@Composable
private fun UpNext(upNext: List<QueueEntry>, from: Int, shuffled: Boolean, onPlay: (Int) -> Unit) {
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nag_queue_title), style = designText(15f, 20f, WideTitleWeight), color = colors.text, modifier = Modifier.weight(1f))
            Text(
                if (shuffled) stringResource(R.string.nag_music_queue_shuffled) else pluralStringResource(R.plurals.nag_tracks_count, upNext.size, upNext.size),
                style = designText(13f, 18f),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        upNext.take(UP_NEXT_SHOWN).forEachIndexed { i, entry -> UpNextRow(from + i + 1, entry) { onPlay(i) } }
    }
}

/** How many of the next tracks the panel lists (the design's four). */
private const val UP_NEXT_SHOWN = 4

@Composable
private fun UpNextRow(number: Int, entry: QueueEntry, onPlay: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(10.dp)
    val tnum = "tnum"
    Row(
        Modifier
            .layout { measurable, constraints ->
                // the row's hover reaches 12 dp past the panel's margin, as the design's
                val extra = 24.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-12.dp.roundToPx(), 0) }
            }
            .height(48.dp)
            .focusRing(state, shape)
            .clip(shape)
            .background(stateBackground(state))
            .nagClickable(interaction, onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(number.toString().padStart(2, '0'), style = designText(13f, 18f).copy(fontFeatureSettings = tnum), color = colors.textTertiary, modifier = Modifier.width(22.dp))
        Text(entry.title, style = designText(14f, 20f), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (entry.lengthMs > 0) Text(formatDuration(entry.lengthMs), style = designText(13f, 18f).copy(fontFeatureSettings = tnum), color = colors.textTertiary)
    }
}

/** With a keyboard, the panel's foot: Space plays or pauses, the jump keys go 10 seconds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelKeys() {
    val resources = LocalContext.current.resources
    val play = keyHint(ShortcutRegistry.chordLabel(ShortcutId.PlayPause, resources)) ?: return
    val back = keyHint(ShortcutRegistry.chordLabel(ShortcutId.JumpBack, resources))
    val forward = keyHint(ShortcutRegistry.chordLabel(ShortcutId.JumpForward, resources))
    val colors = NagTheme.colors
    val style = designText(12f, 18f)
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap(play)
        Text(stringResource(R.string.nag_music_keys_play), style = style, color = colors.textTertiary, maxLines = 1)
        if (back != null && forward != null) {
            Text("·", style = style, color = colors.textTertiary)
            KeyCap(back)
            KeyCap(forward)
            Text(stringResource(R.string.nag_music_keys_jump), style = style, color = colors.textTertiary, maxLines = 1)
        }
    }
}
