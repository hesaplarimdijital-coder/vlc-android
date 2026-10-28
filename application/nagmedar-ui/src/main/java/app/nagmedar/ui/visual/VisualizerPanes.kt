package app.nagmedar.ui.visual

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * S-611: a visualiser in a pane: the drawing, and under it the kind to draw and the full-screen
 * button. When it cannot draw it says why, with the way out: the permission the system's
 * visualiser needs, Battery Saver's pause.
 */
@Composable
fun VisualizerPane(
    kind: VisualizerKind,
    state: VisualizerState,
    onKind: (VisualizerKind) -> Unit,
    modifier: Modifier = Modifier,
    onFullScreen: (() -> Unit)? = null,
    onAllow: (() -> Unit)? = null,
    onRunOnBatterySaver: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(dimens.gapS)
            .testTag("visualizer-pane"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            VisualizerCanvas(kind, state, Modifier.fillMaxSize())
            VisualizerNotice(state, onAllow, onRunOnBatterySaver)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(
                options = VisualizerKind.entries.map { it to stringResource(it.label) },
                selected = kind,
                onSelect = onKind,
                modifier = Modifier.weight(1f),
                fill = true,
            )
            if (onFullScreen != null) {
                NagIconButton(NagIcons.Fullscreen, stringResource(R.string.nag_vis_full_screen), onFullScreen, shortcut = ShortcutId.Visualizer, modifier = Modifier.testTag("visualizer-full-screen"))
            }
        }
    }
}

/** Why the visualiser does not draw, over it: a permission to give, Battery Saver, a device that refuses. */
@Composable
private fun VisualizerNotice(state: VisualizerState, onAllow: (() -> Unit)?, onRunOnBatterySaver: (() -> Unit)?) {
    val (text, action, onAction) = when {
        state.problem == ScopeProblem.NeedsPermission -> Triple(R.string.nag_vis_needs_permission, R.string.nag_vis_allow, onAllow)
        state.problem == ScopeProblem.Unavailable -> Triple(R.string.nag_vis_unavailable, null, null)
        state.pause == VisualizerPause.BatterySaver -> Triple(R.string.nag_vis_battery_saver, R.string.nag_vis_run_anyway, onRunOnBatterySaver)
        else -> return
    }
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .background(colors.surfaceRaised.copy(alpha = 0.94f), shape)
            .border(1.dp, colors.border, shape)
            // a touch on the notice is its own: it goes no further (the video player's gestures under it)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { if (it.pressed || it.previousPressed) it.consume() }
                }
            }
            .padding(NagTheme.dimens.gapS)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapXs),
    ) {
        Text(stringResource(text), style = NagTheme.type.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) NagButton(stringResource(action), onAction)
    }
}

/**
 * S-611: the battery's share an hour the visualiser takes at its frame rate, as the settings and
 * the full-screen view show it: "≈ %0,7 pil / saat · 30 fps".
 */
@Composable
fun visualizerCostLabel(kind: VisualizerKind, fps: VisualizerFps, batteryWh: Double): String {
    val percent = VisualizerCost.percentPerHour(kind, fps.fps, batteryWh)
    return stringResource(R.string.nag_vis_cost, percentText(percent, currentLocale()), fps.fps)
}

/** "%0,7" in Turkish, "0.7%" in English: one decimal below 10 %. */
internal fun percentText(percent: Double, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = if (percent < 10) 1 else 0 }.format(percent / 100.0)

// --------------------------------------------------------------- on the playback service

/**
 * S-611: the visualiser of what plays: the engine's tap for the music engine, the system's
 * visualiser on libVLC's output otherwise; drawn while the battery rules let it.
 */
@Composable
fun PlayingVisualizer(modifier: Modifier = Modifier, onFullScreen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = rememberNagmedarPreferences()
    val settings = prefs.visualizer
    val nowPlaying = rememberNowPlaying()
    val source = remember(context) { PlayingScopeSource(context) }
    val conditions = rememberVisualizerConditions(nowPlaying.playing)
    // a new track, or the permission just given: the source opens again
    var permissions by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions++ }
    val state = rememberVisualizer(source, settings, conditions, key = listOf(nowPlaying.media?.uri, settings, permissions))
    VisualizerPane(
        kind = settings.kind,
        state = state,
        onKind = { prefs.updateVisualizer(settings.copy(kind = it)) },
        modifier = modifier,
        onFullScreen = onFullScreen,
        onAllow = { runCatching { permission.launch(Manifest.permission.RECORD_AUDIO) } },
        onRunOnBatterySaver = { prefs.updateVisualizer(settings.copy(onBatterySaver = true)) },
    )
}

/**
 * S-611: a visualiser over the whole window (Ctrl+Shift+V): what plays, the drawing with its
 * scales, the kind, and what it costs the battery. Esc or Back closes it.
 */
@Composable
fun VisualizerFullScreen(
    title: String?,
    subtitle: String?,
    kind: VisualizerKind,
    state: VisualizerState,
    cost: String,
    onKind: (VisualizerKind) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onAllow: (() -> Unit)? = null,
    onRunOnBatterySaver: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BackHandler(onBack = onClose)
    Column(
        modifier
            .fillMaxSize()
            // over the window: nothing under it takes a click
            .pointerInput(Unit) { detectTapGestures { } }
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = dimens.gapL, vertical = dimens.gapM)
            .testTag("visualizer-full-screen-view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title ?: stringResource(R.string.nag_vis_title), style = NagTheme.type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_vis_close), onClose, shortcut = ShortcutId.Visualizer, modifier = Modifier.testTag("visualizer-close"))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            VisualizerCanvas(kind, state, Modifier.fillMaxSize().padding(vertical = dimens.gapS), large = true)
            VisualizerNotice(state, onAllow, onRunOnBatterySaver)
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
            SegmentedChoice(
                options = VisualizerKind.entries.map { it to stringResource(it.label) },
                selected = kind,
                onSelect = onKind,
                modifier = Modifier.fillMaxWidth(),
                fill = true,
            )
            Text(cost, style = NagTheme.type.labelSmall, color = colors.textTertiary, textAlign = TextAlign.Center)
        }
    }
}

/** S-611: the playing media's visualiser over the whole window. */
@Composable
fun PlayingVisualizerFullScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = rememberNagmedarPreferences()
    val settings = prefs.visualizer
    val nowPlaying = rememberNowPlaying()
    val media = nowPlaying.media
    val source = remember(context) { PlayingScopeSource(context) }
    val conditions = rememberVisualizerConditions(nowPlaying.playing)
    var permissions by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions++ }
    val state = rememberVisualizer(source, settings, conditions, key = listOf(media?.uri, settings, permissions))
    val batteryWh = remember(context) { VisualizerCost.batteryWh(context) }
    VisualizerFullScreen(
        title = media?.let { it.nowPlaying ?: it.title },
        subtitle = media?.let { listOfNotNull(it.artistName?.takeIf(String::isNotBlank), it.albumName?.takeIf(String::isNotBlank)).joinToString(" · ") },
        kind = settings.kind,
        state = state,
        cost = visualizerCostLabel(settings.kind, settings.fps, batteryWh),
        onKind = { prefs.updateVisualizer(settings.copy(kind = it)) },
        onClose = onClose,
        modifier = modifier,
        onAllow = { runCatching { permission.launch(Manifest.permission.RECORD_AUDIO) } },
        onRunOnBatterySaver = { prefs.updateVisualizer(settings.copy(onBatterySaver = true)) },
    )
}

// --------------------------------------------------------------- settings

/**
 * S-611: Settings › Visualiser: shown with music and in the video player, the kind, the frame
 * rate's cap, Battery Saver, and the battery warning with the estimate of what it costs.
 */
@Composable
internal fun VisualizerSection(prefs: NagmedarPreferences, batteryWh: Double? = null) {
    val context = LocalContext.current
    val settings = prefs.visualizer
    val wh = batteryWh ?: remember(context) { VisualizerCost.batteryWh(context) }
    val on = stringResource(R.string.nag_vis_on)
    val off = stringResource(R.string.nag_vis_off)
    SettingsSection(stringResource(R.string.nag_vis_title)) {
        Text(stringResource(R.string.nag_vis_settings_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        SettingRow(stringResource(R.string.nag_vis_music), subtitle = stringResource(R.string.nag_vis_music_hint)) {
            SegmentedChoice(options = listOf(true to on, false to off), selected = settings.music, onSelect = { prefs.updateVisualizer(settings.copy(music = it)) })
        }
        SettingRow(stringResource(R.string.nag_vis_kind), subtitle = stringResource(R.string.nag_vis_projectm)) {
            SegmentedChoice(
                options = VisualizerKind.entries.map { it to stringResource(it.label) },
                selected = settings.kind,
                onSelect = { prefs.updateVisualizer(settings.copy(kind = it)) },
            )
        }
        SettingRow(stringResource(R.string.nag_vis_fps), subtitle = visualizerCostLabel(settings.kind, settings.fps, wh)) {
            SegmentedChoice(
                options = VisualizerFps.entries.map { it to stringResource(R.string.nag_vis_fps_value, it.fps) },
                selected = settings.fps,
                onSelect = { prefs.updateVisualizer(settings.copy(fps = it)) },
            )
        }
        SettingRow(stringResource(R.string.nag_vis_battery_saver_title), subtitle = stringResource(R.string.nag_vis_battery_saver_hint)) {
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.nag_vis_pause), true to stringResource(R.string.nag_vis_keep_running)),
                selected = settings.onBatterySaver,
                onSelect = { prefs.updateVisualizer(settings.copy(onBatterySaver = it)) },
            )
        }
        // the battery warning
        Row(
            Modifier.fillMaxWidth().padding(top = NagTheme.dimens.gapXs).testTag("visualizer-battery-warning"),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS),
        ) {
            Icon(NagIcons.Warning, contentDescription = null, tint = NagTheme.colors.warning, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Text(stringResource(R.string.nag_vis_battery_warning), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        }
    }
}
