package app.nagmedar.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Which of the video's gestures are on, as the classic settings (Video › Controls) have them; the
 * long press is on unless it was turned off there, and plays at the speed set there.
 */
internal data class TouchGestureSettings(
    val volume: Boolean = true,
    val brightness: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val doubleTapPlay: Boolean = true,
    val swipeSeek: Boolean = true,
    val pinch: Boolean = true,
    val fastPlay: Boolean = true,
    val fastRate: Float = FAST_RATE,
)

/** What the touch layout does beyond the desktop controls: its gestures, its sheets, the panel's tabs, the lock. */
internal interface TouchActions {
    /** The gestures on and off. */
    val gestures: TouchGestureSettings

    /** A seek by [deltaMs] from where the film is, said by the double tap's ripple rather than the OSD. */
    fun jump(deltaMs: Int)

    // read when a gesture starts: the controls' state is not read while they are hidden
    fun timeMs(): Long

    fun lengthMs(): Long

    fun playing(): Boolean

    fun rate(): Float

    /** The volume in percent, 0 while muted, above 100 with audio boost. */
    fun volumePercent(): Int

    /** The highest volume a swipe may set: 200 % with audio boost, else 100 %. */
    fun maxVolumePercent(): Int

    /** The window's brightness, 0..1: the system's until a swipe set it. */
    fun brightness(): Float

    fun setBrightness(level: Float)

    /** The pinch: the picture fills the screen (cropped) or fits it again. */
    fun zoom(fill: Boolean)

    fun openSheet(sheet: PlayerSheet)

    fun openPanel(tab: PanelTab)

    fun lock()

    fun unlock()
}

/**
 * The player's controls for a finger or a pen (S-601's touch layout): along the top the way back,
 * the title (and the chapter playing), casting, picture in picture, the lock and the menu; in the
 * middle the transport (previous, 10 s back, play or pause, 10 s forward, next); along the bottom
 * the time, the timeline and the film's choices (subtitles, audio, speed, the picture's size,
 * effects…). Every target is at least 48 dp. A tap shows or hides them; they hide by themselves
 * while the film plays.
 *
 * The video takes VLC's gestures: a double tap on the left or the right goes 10 s back or forward
 * (taps that follow add 10 s each), in the middle plays or pauses; a swipe up or down on the left
 * sets the brightness, on the right the volume (the system's media volume); a swipe sideways seeks,
 * showing where to, on release; a pinch fills the screen or fits the picture again; a long press
 * plays at 2× while held. The lock puts all of it away until its button is tapped.
 */
@Composable
internal fun TouchControls(
    state: PlayerChromeState,
    actions: PlayerChromeActions,
    touch: TouchActions,
    visibility: ChromeVisibility,
    osd: PlayerOsd,
    previews: FramePreviews?,
    modifier: Modifier = Modifier,
    /** Whether the screen can turn (a phone or a tablet, not a desktop window): the bar has the orientation then. */
    canRotate: Boolean = true,
) {
    LaunchedEffect(visibility.moves, visibility.hiding, state.playing) {
        // paused, the controls stay, as VLC's do
        if (!visibility.hiding || !state.playing) return@LaunchedEffect
        delay(ChromeVisibility.TOUCH_HIDE_AFTER_MS)
        visibility.hide()
    }
    val fastLabel = stringResource(R.string.nag_osd_fast, rateLabel(touch.gestures.fastRate, currentLocale()))
    // a gesture outlives the compositions it spans: what it started from is kept here
    val session = remember { GestureSession() }
    val gestures = rememberUpdatedState(
        TouchGestures(actions, touch, visibility, osd, session, fastLabel),
    )
    // picture in picture takes the video from under a gesture: its fast play and its line end with it
    LaunchedEffect(visibility.pictureInPicture) {
        if (visibility.pictureInPicture) gestures.value.interrupted()
    }
    Box(modifier.fillMaxSize().testTag("player-touch")) {
        // the gestures, under the bars: a touch on a bar is the bar's
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { at -> gestures.value.tap(at, size) },
                        onDoubleTap = { at -> gestures.value.doubleTap(at, size) },
                        onLongPress = { gestures.value.longPress() },
                        onPress = {
                            // the layout may go mid-press (a mouse moved, the controls changed): the fast play ends then too
                            try {
                                tryAwaitRelease()
                            } finally {
                                gestures.value.released()
                            }
                        },
                    )
                }
                .pointerInput(Unit) { swipes { gestures.value } }
                .testTag("player-gestures"),
        )
        // locked, a tap brings the lock's button (drawn over every layout by the player), nothing else
        if (visibility.locked) return@Box
        AnimatedVisibility(visibility.visible, enter = fadeIn(NagTheme.motion.enter(NagMotion.COLOR)), exit = fadeOut(NagTheme.motion.exit(NagMotion.MEDIUM))) {
            // the video dims under the controls, so white reads on any picture
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        }
        AnimatedVisibility(
            visibility.visible,
            enter = fadeIn(NagTheme.motion.enter(NagMotion.COLOR)),
            exit = fadeOut(NagTheme.motion.exit(NagMotion.MEDIUM)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TouchTopBar(state, actions, touch, Modifier.pokes(visibility))
        }
        AnimatedVisibility(
            visibility.visible,
            enter = fadeIn(NagTheme.motion.enter(NagMotion.COLOR)),
            exit = fadeOut(NagTheme.motion.exit(NagMotion.MEDIUM)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TouchTransport(state, actions, Modifier.pokes(visibility))
        }
        AnimatedVisibility(
            visibility.visible,
            enter = fadeIn(NagTheme.motion.enter(NagMotion.COLOR)),
            exit = fadeOut(NagTheme.motion.exit(NagMotion.MEDIUM)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TouchBottomBar(state, actions, touch, previews, canRotate, Modifier.pokes(visibility))
        }
    }
}

/** The speed a long press plays at while it is held, unless the settings say another. */
internal const val FAST_RATE = 2f

/**
 * A finger on a bar starts the hide timer again; the bar's buttons still get the touch. A mouse's
 * wheel over the bar is the bar's, not the volume's.
 */
private fun Modifier.pokes(visibility: ChromeVisibility): Modifier = chromeSurface(visibility.surfaces).pointerInput(visibility) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed }) visibility.poke()
        }
    }
}

@Composable
private fun TouchTopBar(state: PlayerChromeState, actions: PlayerChromeActions, touch: TouchActions, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 20.dp)
            .testTag("player-touch-top"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TouchButton(NagIcons.ChevronLeft, stringResource(R.string.nag_player_close), actions::close)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(state.title, style = NagTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            chapterName(state)?.let { Text(it, style = NagTheme.type.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (state.canCast) TouchButton(NagIcons.Cast, stringResource(R.string.nag_sc_cast), actions::cast)
        if (state.canPictureInPicture) TouchButton(NagIcons.PictureInPicture, stringResource(R.string.nag_player_pip), actions::pictureInPicture)
        TouchButton(NagIcons.Lock, stringResource(R.string.nag_player_lock), touch::lock, testTag = "player-lock")
        TouchButton(NagIcons.MoreVertical, stringResource(R.string.nag_player_menu), { touch.openSheet(PlayerSheet.More) }, testTag = "player-more")
    }
}

/** The chapter playing, "Chapter 3 — The storm", when the film has chapters. */
@Composable
private fun chapterName(state: PlayerChromeState): String? {
    val index = state.chapterAt(state.timeMs) ?: return null
    val name = state.chapters[index].name
    return if (name.isBlank()) stringResource(R.string.nag_player_chapter, index + 1) else stringResource(R.string.nag_player_chapter_named, index + 1, name)
}

@Composable
private fun TouchTransport(state: PlayerChromeState, actions: PlayerChromeActions, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("player-touch-transport"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.hasPrevious || state.hasNext) TouchButton(NagIcons.Previous, stringResource(R.string.nag_sc_previous), actions::previous, enabled = state.hasPrevious, iconSize = 24.dp)
        TouchButton(NagIcons.Resume, stringResource(R.string.nag_sc_back_10s), { actions.seekBy(-SEEK_STEP_MS) }, iconSize = 26.dp)
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).background(Color.White, CircleShape))
            NagIconButton(
                icon = if (state.playing) NagIcons.Pause else NagIcons.Play,
                contentDescription = stringResource(if (state.playing) R.string.nag_pause else R.string.nag_play),
                onClick = actions::togglePlayPause,
                size = 72.dp,
                iconSize = 30.dp,
                tint = Color.Black,
                modifier = Modifier.testTag("player-touch-play"),
            )
        }
        TouchButton(NagIcons.Forward, stringResource(R.string.nag_sc_forward_10s), { actions.seekBy(SEEK_STEP_MS) }, iconSize = 26.dp)
        if (state.hasPrevious || state.hasNext) TouchButton(NagIcons.Next, stringResource(R.string.nag_sc_next), actions::next, enabled = state.hasNext, iconSize = 24.dp)
    }
}

/** The transport's and the double tap's step. */
private const val SEEK_STEP_MS = 10_000

@Composable
private fun TouchBottomBar(
    state: PlayerChromeState,
    actions: PlayerChromeActions,
    touch: TouchActions,
    previews: FramePreviews?,
    canRotate: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            // clear of the cutout and of the home gesture's strip, where a drag would leave the app
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .testTag("player-touch-bottom"),
    ) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(playerClock(state.timeMs), style = NagTheme.type.mono, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("player-time"))
                Spacer(Modifier.weight(1f))
                Text(playerClock(state.lengthMs), style = NagTheme.type.mono, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayerTimeline(state, actions::seekTo, previews, touch = true)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp, if (wide) Alignment.Start else Alignment.CenterHorizontally),
            ) {
                val subtitlesOn = state.subtitleTrack != null
                TouchButton(NagIcons.Subtitle, stringResource(R.string.nag_player_subtitles), { touch.openPanel(PanelTab.Subtitles) }, selected = subtitlesOn, testTag = "player-touch-subtitles")
                TouchButton(NagIcons.AudioTrack, stringResource(R.string.nag_player_audio_track), { touch.openSheet(PlayerSheet.Audio) }, testTag = "player-touch-audio")
                SpeedButton(rateLabel(state.rate, locale), abs(state.rate - 1f) >= 0.01f) { touch.openSheet(PlayerSheet.Speed) }
                TouchButton(NagIcons.AspectRatio, stringResource(R.string.nag_player_picture), { touch.openSheet(PlayerSheet.Picture) }, testTag = "player-touch-picture")
                if (canRotate) TouchButton(NagIcons.Rotate, stringResource(R.string.nag_player_orientation), { touch.openSheet(PlayerSheet.Orientation) }, testTag = "player-touch-rotate")
                TouchButton(NagIcons.Effects, stringResource(R.string.nag_sc_effects), { touch.openPanel(PanelTab.Effects) }, selected = state.effectsOpen)
                if (wide) {
                    if (state.chapters.isNotEmpty()) TouchButton(NagIcons.Chapters, stringResource(R.string.nag_player_chapters), { touch.openSheet(PlayerSheet.Chapters) })
                    TouchButton(NagIcons.Bookmark, stringResource(R.string.nag_sc_bookmarks), { touch.openPanel(PanelTab.Bookmarks) })
                    TouchButton(NagIcons.Screenshot, stringResource(R.string.nag_sc_snapshot), actions::takeSnapshot)
                    if (state.canFullscreen) {
                        TouchButton(
                            NagIcons.Fullscreen,
                            stringResource(if (state.fullscreen) R.string.nag_player_exit_fullscreen else R.string.nag_sc_fullscreen),
                            actions::toggleFullscreen,
                            selected = state.fullscreen,
                        )
                    }
                }
            }
        }
    }
}

/** The speed, "1,0×", as a button that opens its sheet; lit while it is not 1×. */
@Composable
private fun SpeedButton(text: String, changed: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.nag_player_speed)
    val interaction = remember { MutableInteractionSource() }
    val interactionState = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Box(
        Modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 48.dp)
            .focusRing(interactionState, shape)
            .background(stateBackground(interactionState, selected = changed), shape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .semantics { contentDescription = "$label $text" }
            .padding(horizontal = 8.dp)
            .testTag("player-touch-speed"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = NagTheme.type.label,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = if (changed) 0.7f else 0.35f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TouchButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconSize: Dp = 22.dp,
    testTag: String? = null,
) {
    NagIconButton(
        icon,
        label,
        onClick,
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        enabled = enabled,
        selected = selected,
        size = 48.dp,
        iconSize = iconSize,
        tint = Color.White,
    )
}

/** What a swipe on the video sets, once it has gone far enough to tell. */
private enum class SwipeMode { Seek, Brightness, Volume, Pinch, None }

/**
 * The video's gestures, made again at every composition; what they start from is read from
 * the player as they start ([TouchActions]), since the controls' state is not read while hidden. Taps and the long press come from [detectTapGestures]; the swipes and the pinch from
 * [swipes].
 */
private class TouchGestures(
    val actions: PlayerChromeActions,
    val touch: TouchActions,
    val visibility: ChromeVisibility,
    val osd: PlayerOsd,
    val session: GestureSession,
    val fastLabel: String,
) {
    fun tap(at: Offset, size: IntSize) {
        if (visibility.locked) return visibility.show()
        // taps that follow a double tap on the same side go on seeking
        val side = side(at, size)
        if (side != null && osd.rippling(side)) return seek(side)
        visibility.toggle()
    }

    fun doubleTap(at: Offset, size: IntSize) {
        if (visibility.locked) return visibility.show()
        val side = side(at, size)
        when {
            side != null && touch.gestures.doubleTapSeek -> seek(side)
            side == null && touch.gestures.doubleTapPlay -> actions.togglePlayPause()
            // a double tap that does nothing else shows or hides the controls, as a tap does
            else -> visibility.toggle()
        }
    }

    private fun seek(forward: Boolean) {
        if (touch.lengthMs() <= 0) return
        osd.rippled(forward, SEEK_STEP_MS / 1000)
        touch.jump(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
    }

    /** Forward on the right third, back on the left, null in the middle. */
    private fun side(at: Offset, size: IntSize): Boolean? = when {
        at.x < size.width * SIDE -> false
        at.x > size.width * (1 - SIDE) -> true
        else -> null
    }

    fun longPress() {
        val fast = touch.gestures.fastRate
        if (visibility.locked || !touch.gestures.fastPlay || session.fastFrom != null || !touch.playing()) return
        val rate = touch.rate()
        if (rate >= fast) return
        session.fastFrom = rate
        actions.setRate(fast)
        session.holdSerial = osd.hold(fastLabel)
    }

    fun released() {
        val rate = session.fastFrom ?: return
        session.fastFrom = null
        actions.setRate(rate)
        osd.clear(session.holdSerial)
    }

    fun start(mode: SwipeMode): SwipeMode {
        if (visibility.locked) return SwipeMode.None
        val on = touch.gestures
        val allowed = when (mode) {
            SwipeMode.Seek -> on.swipeSeek
            SwipeMode.Brightness -> on.brightness
            SwipeMode.Volume -> on.volume
            SwipeMode.Pinch -> on.pinch
            SwipeMode.None -> true
        }
        if (!allowed) return SwipeMode.None
        return when (mode) {
            SwipeMode.Seek -> if (touch.lengthMs() > 0) {
                session.startMs = touch.timeMs()
                session.lengthMs = touch.lengthMs()
                session.seekTarget = -1L
                mode
            } else {
                SwipeMode.None
            }
            SwipeMode.Brightness -> {
                session.startLevel = touch.brightness()
                mode
            }
            SwipeMode.Volume -> {
                session.startLevel = touch.volumePercent().toFloat()
                session.setVolume = -1
                mode
            }
            else -> mode
        }
    }

    fun move(mode: SwipeMode, drag: Offset, size: IntSize, swipeSeekText: (Long, Long, Long) -> String) {
        when (mode) {
            SwipeMode.Seek -> {
                val length = session.lengthMs
                val range = length.coerceAtMost(SWIPE_RANGE_MS)
                val delta = (drag.x / size.width.coerceAtLeast(1) * range).roundToLong()
                val target = (session.startMs + delta).coerceIn(0L, length)
                session.seekTarget = target
                session.holdSerial = osd.hold(swipeSeekText(target - session.startMs, target, length))
            }
            SwipeMode.Brightness -> {
                val level = (session.startLevel - drag.y / size.height.coerceAtLeast(1) * SWIPE_GAIN).coerceIn(MIN_BRIGHTNESS, 1f)
                touch.setBrightness(level)
                osd.level(OsdLevel.Brightness, (level * 100).roundToInt(), 100)
            }
            SwipeMode.Volume -> {
                val max = touch.maxVolumePercent().coerceAtLeast(1)
                val percent = (session.startLevel - drag.y / size.height.coerceAtLeast(1) * SWIPE_GAIN * 100).roundToInt().coerceIn(0, max)
                if (percent != session.setVolume) {
                    session.setVolume = percent
                    // the player says the level it set (the system's steps), as with the keys
                    actions.setVolume(percent)
                }
            }
            else -> Unit
        }
    }

    fun end(mode: SwipeMode, zoom: Float) {
        when (mode) {
            SwipeMode.Seek -> {
                osd.clear(session.holdSerial)
                if (session.seekTarget >= 0) actions.seekTo(session.seekTarget)
            }
            SwipeMode.Pinch -> when {
                zoom > PINCH_IN -> touch.zoom(fill = true)
                zoom < PINCH_OUT -> touch.zoom(fill = false)
            }
            else -> Unit
        }
    }

    /** A swipe cut short (the controls went from under it): it seeks nowhere, and its line goes. */
    fun cancel(mode: SwipeMode) {
        if (mode != SwipeMode.Seek) return
        session.seekTarget = -1L
        osd.clear(session.holdSerial)
    }

    /** The video was taken from under the gestures (picture in picture): whatever one held ends. */
    fun interrupted() {
        released()
        cancel(SwipeMode.Seek)
    }

    companion object {
        /** The double tap's sides: a third of the width each. */
        const val SIDE = 0.35f

        /** A swipe across the whole width seeks this far at most (the film's length when shorter). */
        const val SWIPE_RANGE_MS = 120_000L

        /** A swipe along the whole height takes the level this much further than all of it. */
        const val SWIPE_GAIN = 1.25f

        const val MIN_BRIGHTNESS = 0.01f
        const val PINCH_IN = 1.12f
        const val PINCH_OUT = 0.9f
    }
}

/** Where the gesture under way started from: the time, the level, the speed before the long press. */
private class GestureSession {
    var startMs = 0L
    var lengthMs = 0L
    var startLevel = 0f
    var seekTarget = -1L
    var setVolume = -1
    var holdSerial = 0

    /** The speed before the long press, while it is held. */
    var fastFrom: Float? = null
}

/**
 * The swipes and the pinch on the video. The screen's top and bottom edges are left to the
 * system's own gestures; a touch a bar or the timeline took is theirs.
 */
private suspend fun PointerInputScope.swipes(gestures: () -> TouchGestures) {
    val edge = 24.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed || down.position.y < edge || down.position.y > size.height - edge) return@awaitEachGesture
        var mode: SwipeMode? = null
        var drag = Offset.Zero
        var zoom = 1f
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2 && (mode == null || mode == SwipeMode.Pinch)) {
                    if (mode == null) mode = gestures().start(SwipeMode.Pinch)
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (mode == SwipeMode.Pinch || mode == SwipeMode.None) continue
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // a long press took the touch: nothing to swipe
                if (change.isConsumed) {
                    if (mode == null) return@awaitEachGesture
                    break
                }
                drag += change.positionChange()
                if (mode == null) {
                    if (drag.getDistance() < viewConfiguration.touchSlop) continue
                    val wanted = when {
                        abs(drag.x) > abs(drag.y) -> SwipeMode.Seek
                        down.position.x < size.width / 2 -> SwipeMode.Brightness
                        else -> SwipeMode.Volume
                    }
                    mode = gestures().start(wanted)
                    if (mode == SwipeMode.None) continue
                }
                val current = mode ?: continue
                gestures().move(current, drag, size, ::swipeSeekText)
                change.consume()
            }
            ended = true
        } finally {
            // the layout may go mid-swipe (a mouse moved): its held line goes with it
            mode?.let { if (ended) gestures().end(it, zoom) else gestures().cancel(it) }
        }
    }
}

/** The swipe's seek, as the OSD writes it: "+1:20 · 12:34 / 1:20:00". */
private fun swipeSeekText(deltaMs: Long, targetMs: Long, lengthMs: Long): String {
    val sign = if (deltaMs < 0) "−" else "+"
    return sign + playerClock(abs(deltaMs)) + " · " + playerClock(targetMs) + " / " + playerClock(lengthMs)
}
