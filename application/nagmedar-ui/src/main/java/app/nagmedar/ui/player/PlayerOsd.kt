package app.nagmedar.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay

/** The levels the OSD shows as a bar. */
enum class OsdLevel { Volume, Brightness }

/** What the OSD shows: one item at a time, each for [durationMs] (0 while it is held, until cleared). */
sealed interface OsdItem {
    val serial: Int
    val durationMs: Long

    /** A line: "Subtitles: English", "Screen locked". */
    data class Line(val text: String, override val durationMs: Long, override val serial: Int) : OsdItem

    /** A level and its bar: the volume up to [maxPercent] (above 100 with audio boost), the brightness. */
    data class Gauge(val level: OsdLevel, val percent: Int, val maxPercent: Int, override val serial: Int) : OsdItem {
        override val durationMs: Long get() = PlayerOsd.LEVEL_MS
    }

    /** The keys' seeks, added up while they follow one another: "+30 s · 12:34 / 1:20:00". */
    data class Seek(val totalMs: Long, val timeMs: Long, val lengthMs: Long, override val serial: Int) : OsdItem {
        override val durationMs: Long get() = PlayerOsd.SEEK_MS
    }
}

/** The double tap's seek on one side of the video, its seconds added up while the taps go on. */
data class SeekRipple(val forward: Boolean, val totalS: Int, val serial: Int)

/**
 * What the player says over the video, where the classic overlay had its own views: a line (the
 * tracks' names, the keys' changes, the lock), the volume or the brightness with a bar, the keys'
 * and the swipe's seeks, the double tap's ripples, and that the media loads. Compose state.
 */
@Stable
class PlayerOsd {
    var shown by mutableStateOf<OsdItem?>(null)
        private set

    var ripple by mutableStateOf<SeekRipple?>(null)
        private set

    /** The media loads or buffers. */
    var loading by mutableStateOf(false)

    private var serial = 0
    private var lastSeekAtMs = 0L

    fun say(text: String, durationMs: Long = SAY_MS) {
        shown = OsdItem.Line(text, durationMs, ++serial)
    }

    /** [text] stays until [clear]: a gesture still going on. */
    fun hold(text: String): Int {
        shown = OsdItem.Line(text, 0L, ++serial)
        return serial
    }

    fun level(level: OsdLevel, percent: Int, maxPercent: Int) {
        shown = OsdItem.Gauge(level, percent, maxPercent.coerceAtLeast(1), ++serial)
    }

    /** A seek by [deltaMs] to [timeMs]: added to the one before while they come within [RUN_MS] the same way. */
    fun seeked(deltaMs: Int, timeMs: Long, lengthMs: Long, nowMs: Long) {
        val before = (shown as? OsdItem.Seek)?.takeIf { nowMs - lastSeekAtMs < RUN_MS && (it.totalMs >= 0) == (deltaMs >= 0) }
        lastSeekAtMs = nowMs
        shown = OsdItem.Seek((before?.totalMs ?: 0L) + deltaMs, timeMs, lengthMs, ++serial)
    }

    /** A double tap's seek of [stepS] seconds, [forward] or back; the seconds added up on the same side. */
    fun rippled(forward: Boolean, stepS: Int): SeekRipple {
        val before = ripple?.takeIf { it.forward == forward }
        return SeekRipple(forward, (before?.totalS ?: 0) + stepS, ++serial).also { ripple = it }
    }

    /** Whether a ripple on that side still shows: a single tap there seeks again. */
    fun rippling(forward: Boolean): Boolean = ripple?.forward == forward

    fun clear(serial: Int) {
        if (shown?.serial == serial) shown = null
    }

    fun clearRipple(serial: Int) {
        if (ripple?.serial == serial) ripple = null
    }

    companion object {
        const val SAY_MS = 1_200L
        const val LEVEL_MS = 1_000L
        const val SEEK_MS = 1_000L
        const val RUN_MS = 1_500L
        const val RIPPLE_MS = 700L
    }
}

/**
 * The OSD over the video: its item under the top bar's place, the double tap's ripples on their
 * sides, the loading spinner in the middle. Nothing in it takes a touch. A screen reader reads
 * the items as they come.
 */
@Composable
fun PlayerOsdHost(osd: PlayerOsd, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val item = osd.shown
        LaunchedEffect(item) {
            if (item != null && item.durationMs > 0) {
                delay(item.durationMs)
                osd.clear(item.serial)
            }
        }
        // the item that last showed, kept while it fades out
        var last by remember { mutableStateOf<OsdItem?>(null) }
        if (item != null) last = item
        AnimatedVisibility(
            visible = item != null,
            enter = fadeIn(NagTheme.motion.enter()),
            exit = fadeOut(NagTheme.motion.exit()),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 16.dp, end = 16.dp),
        ) {
            last?.let { OsdCard(it) }
        }
        val ripple = osd.ripple
        LaunchedEffect(ripple) {
            if (ripple != null) {
                delay(PlayerOsd.RIPPLE_MS)
                osd.clearRipple(ripple.serial)
            }
        }
        if (ripple != null) SeekRippleView(ripple)
        if (osd.loading) LoadingSpinner(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun OsdCard(item: OsdItem) {
    val locale = currentLocale()
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.72f), shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("player-osd"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (item) {
            is OsdItem.Line -> Text(item.text, style = NagTheme.type.bodyStrong, color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            is OsdItem.Seek -> {
                val seconds = signed(item.totalMs / 1000, locale)
                Text(stringResource(R.string.nag_osd_seek, seconds), style = NagTheme.type.title, color = Color.White, maxLines = 1)
                Text(stringResource(R.string.nag_osd_time_of, playerClock(item.timeMs), playerClock(item.lengthMs)), style = NagTheme.type.mono, color = Color.White, maxLines = 1)
            }
            is OsdItem.Gauge -> GaugeRow(item)
        }
    }
}

@Composable
private fun GaugeRow(item: OsdItem.Gauge) {
    val icon: ImageVector = if (item.level == OsdLevel.Volume) NagIcons.Volume else NagIcons.Brightness
    val label = stringResource(if (item.level == OsdLevel.Volume) R.string.nag_volume else R.string.nag_osd_brightness)
    val percent = stringResource(R.string.nag_osd_percent, item.percent)
    Row(
        Modifier.semantics { contentDescription = "$label $percent" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        val accent = NagTheme.colors.accent
        val boost = NagTheme.colors.warning
        // past 100 % (audio boost) the bar's rest is drawn in the warning colour
        Canvas(Modifier.width(160.dp).height(6.dp)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.28f), size = size, cornerRadius = radius)
            val fraction = (item.percent.toFloat() / item.maxPercent).coerceIn(0f, 1f)
            val normal = (100f / item.maxPercent).coerceIn(0f, 1f)
            drawRoundRect(accent, size = Size(size.width * fraction.coerceAtMost(normal), size.height), cornerRadius = radius)
            if (fraction > normal) {
                drawRoundRect(boost, topLeft = Offset(size.width * normal, 0f), size = Size(size.width * (fraction - normal), size.height), cornerRadius = radius)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(percent, style = NagTheme.type.mono, color = Color.White, maxLines = 1, modifier = Modifier.widthIn(min = 40.dp))
    }
}

/** The double tap's ripple: a half circle on its side of the video, with the seconds it went. */
@Composable
private fun SeekRippleView(ripple: SeekRipple) {
    val locale = currentLocale()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player-ripple")) {
        val width = maxWidth * 0.36f
        Box(
            Modifier
                .align(if (ripple.forward) Alignment.CenterEnd else Alignment.CenterStart)
                .width(width)
                .fillMaxHeight()
                .clip(
                    if (ripple.forward) {
                        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                    } else {
                        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    },
                )
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (ripple.forward) NagIcons.Forward else NagIcons.Resume, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.nag_osd_seek, signed(if (ripple.forward) ripple.totalS else -ripple.totalS, locale)),
                    style = NagTheme.type.bodyStrong,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The media loads: a turning arc, still when the system removes animations. */
@Composable
private fun LoadingSpinner(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.nag_player_loading)
    val loop = NagTheme.motion.loop(SPIN_MS)
    val angle = if (loop != null) {
        val transition = rememberInfiniteTransition(label = "player-loading")
        transition.animateFloat(0f, 360f, loop, label = "player-loading-angle").value
    } else {
        0f
    }
    val accent = NagTheme.colors.accent
    Box(
        modifier
            .size(64.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(32.dp))
            .semantics { contentDescription = label }
            .testTag("player-loading"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, useCenter = false, style = Stroke(stroke))
            drawArc(accent, angle - 90f, 100f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

private const val SPIN_MS = 900
