package app.nagmedar.ui.audio.latency

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import androidx.core.content.ContextCompat
import app.nagmedar.audio.latency.AppliedLatency
import app.nagmedar.audio.latency.LatencyProfile
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.OutputDeviceKey
import app.nagmedar.audio.latency.OutputKind
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService

/** The kind of device as the list names it. */
internal fun kindLabel(res: Resources, kind: OutputKind): String = res.getString(
    when (kind) {
        OutputKind.Bluetooth -> R.string.nag_latency_kind_bluetooth
        OutputKind.Usb -> R.string.nag_latency_kind_usb
        OutputKind.Hdmi -> R.string.nag_latency_kind_hdmi
        OutputKind.Wired -> R.string.nag_latency_kind_wired
        OutputKind.Speaker -> R.string.nag_latency_kind_speaker
        OutputKind.Other -> R.string.nag_latency_kind_other
    },
)

/** [device] as the screens name it: its product name, the built-in speaker by its kind (its name is the phone's model). */
internal fun deviceLabel(res: Resources, device: OutputDeviceKey): String =
    if (device.kind == OutputKind.Speaker || device.name.isBlank()) kindLabel(res, device.kind) else device.name

/**
 * S-613: Settings › Latency profiles: the output in use and what it plays late, every device
 * seen with its delay (a stepper and a slider in 10 ms steps, a delete), and the measurement with
 * the microphone. [store], [active] and [applied] are the live ones; the screenshot tests give
 * their own, and [measureIo] a room of their making.
 */
@Composable
internal fun LatencySection(
    store: LatencyProfileStore? = null,
    active: OutputDeviceKey? = null,
    applied: Flow<AppliedLatency?>? = null,
    measureIo: MeasureIo? = null,
) {
    val context = LocalContext.current
    val res = context.resources
    val locale = currentLocale()
    val colors = NagTheme.colors
    val scope = rememberCoroutineScope()
    val profileStore = store ?: remember(context) { RoomLatencyStore(context) }
    val profiles by remember(profileStore) { profileStore.observe() }.collectAsState(emptyList())
    val appliedNow by (applied ?: LatencyCenter.applied).collectAsState(null)
    // without the service, Android's own order over the devices connected
    val connected = remember(context) { OutputDevices.active(context) }
    val output = active ?: appliedNow?.device ?: connected
    // a measurement under way outlives the screen: the holder says whose it is
    var measuring by remember { mutableStateOf(LatencyMeasuring.device) }

    SettingsSection(stringResource(R.string.nag_latency_settings)) {
        Text(stringResource(R.string.nag_latency_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.height(NagTheme.dimens.gapS))
        val now = when {
            output == null -> stringResource(R.string.nag_latency_now_none)
            else -> {
                val delay = LatencyProfiles.delayMs(profiles, output)
                stringResource(R.string.nag_latency_now, deviceLabel(res, output), stringResource(R.string.nag_sync_ms, signed(delay, locale)))
            }
        }
        SettingRow(stringResource(R.string.nag_latency_measure), subtitle = now) {
            NagButton(
                stringResource(R.string.nag_latency_measure_button),
                { output?.let { measuring = it } },
                enabled = output != null,
                primary = true,
                modifier = Modifier.testTag("latency-measure"),
            )
        }
        Spacer(Modifier.height(NagTheme.dimens.gapS))
        if (profiles.isEmpty()) {
            Text(stringResource(R.string.nag_latency_empty), style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.testTag("latency-empty"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                profiles.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        active = profile.device == output || (output != null && LatencyProfiles.find(profiles, output)?.device == profile.device),
                        onDelay = { ms ->
                            // set by hand: no longer the measurement's; the speaker's own measurement stays a reference
                            scope.launch { profileStore.save(profile.copy(delayMs = ms, measured = false, spreadMs = profile.spreadMs.takeIf { profile.calibrationMs != null }, updatedAt = System.currentTimeMillis())) }
                        },
                        onDelete = { scope.launch { profileStore.delete(profile.device) } },
                    )
                }
            }
        }
    }

    measuring?.let { device ->
        // the speaker's own round trip (the microphone's path), taken off another device's measurement
        val reference = profiles.firstOrNull { it.device.kind == OutputKind.Speaker }?.calibrationMs?.takeIf { device.kind != OutputKind.Speaker }
        LatencyMeasureDialog(
            device = device,
            io = measureIo ?: remember(context) { AndroidMeasureIo(context) },
            referenceMs = reference,
            onSave = { result ->
                // the row the device already has (perhaps without its address), not a second one
                val existing = LatencyProfiles.find(profiles, device)
                val now = System.currentTimeMillis()
                val saved = when {
                    // the speaker: its measurement is the reference, its delay stays the user's
                    device.kind == OutputKind.Speaker -> (existing ?: LatencyProfile(device, 0)).copy(measured = true, spreadMs = result.spreadMs, calibrationMs = result.rawMs, updatedAt = now)
                    else -> (existing ?: LatencyProfile(device, 0)).copy(delayMs = result.delayMs, measured = true, spreadMs = result.spreadMs, updatedAt = now)
                }
                scope.launch { profileStore.save(saved) }
                measuring = null
            },
            onDismiss = { measuring = null },
        )
    }
}

/**
 * S-613: the measurement under way, outliving the screen's composition (the list scrolled away,
 * a DeX window resized): one at a time, on the app's scope, until it is ended.
 */
internal object LatencyMeasuring {
    var device: OutputDeviceKey? = null
        private set
    var session: LatencyMeasureSession? = null
        private set

    /**
     * Playback was on when the measurement started, so it goes on again after: as soon as the
     * measurement ends, done or failed, whether its window (or the settings) is still open or not.
     */
    var resume: Boolean = false

    /** The session for [device]: the one under way, or a new one. */
    fun begin(device: OutputDeviceKey, io: MeasureIo, referenceMs: Int?): LatencyMeasureSession {
        session?.takeIf { this.device == device }?.let { return it }
        session?.reset()
        this.device = device
        // made outside any window's composition when ProcessState.warmUp ran first; else in the caller's snapshot
        return LatencyMeasureSession(io, AppScope, referenceMs, onEnded = { resumePlayback() }).also { session = it }
    }

    /** What was paused for the measurement plays again, once. Main thread. */
    fun resumePlayback() {
        if (!resume) return
        resume = false
        runCatching { PlaybackService.instance?.play() }
    }

    /** The measurement is over (saved, cancelled): one under way stops, and what was paused plays again. */
    fun end() {
        session?.reset()
        session = null
        device = null
        resumePlayback()
    }
}

/** One device: its name and kind, a measured badge, the delay with its stepper and slider, and the delete. */
@Composable
private fun ProfileRow(profile: LatencyProfile, active: Boolean, onDelay: (Int) -> Unit, onDelete: () -> Unit) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val name = deviceLabel(res, profile.device)
    val delayText = stringResource(R.string.nag_sync_ms, signed(profile.delayMs, locale))
    Column(Modifier.fillMaxWidth().testTag("latency-profile")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            Column(Modifier.weight(1f)) {
                Text(name, style = NagTheme.type.bodyStrong, color = colors.text)
                val kind = kindLabel(res, profile.device.kind)
                val detail = buildList {
                    if (profile.device.kind != OutputKind.Speaker) add(kind)
                    val calibration = profile.calibrationMs
                    when {
                        calibration != null -> add(stringResource(R.string.nag_latency_calibrated, calibration, profile.spreadMs ?: 0))
                        profile.measured -> add(stringResource(R.string.nag_latency_measured, profile.spreadMs ?: 0))
                    }
                }
                if (detail.isNotEmpty()) Text(detail.joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            }
            if (active) ToneBadge(stringResource(R.string.nag_latency_active), StateTone.Success)
            NagIconButton(NagIcons.Delete, stringResource(R.string.nag_latency_delete, name), onDelete, modifier = Modifier.testTag("latency-delete"))
        }
        // the slider's value while it is dragged; written when it is let go
        var pending by remember(profile.delayMs) { mutableStateOf(profile.delayMs) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            StepButton("−", stringResource(R.string.nag_latency_step_down, name)) { onDelay(LatencyProfiles.snap(profile.delayMs - LatencyProfiles.STEP_MS)) }
            NagSlider(
                value = (pending - LatencyProfiles.MIN_MS) / (LatencyProfiles.MAX_MS - LatencyProfiles.MIN_MS).toFloat(),
                onValueChange = { fraction ->
                    pending = LatencyProfiles.snap((LatencyProfiles.MIN_MS + fraction * (LatencyProfiles.MAX_MS - LatencyProfiles.MIN_MS)).toInt())
                },
                onValueChangeFinished = { if (pending != profile.delayMs) onDelay(pending) },
                keyStep = LatencyProfiles.STEP_MS / (LatencyProfiles.MAX_MS - LatencyProfiles.MIN_MS).toFloat(),
                wheelStep = LatencyProfiles.STEP_MS / (LatencyProfiles.MAX_MS - LatencyProfiles.MIN_MS).toFloat(),
                hoverLabel = { fraction -> res.getString(R.string.nag_sync_ms, signed(LatencyProfiles.snap((LatencyProfiles.MIN_MS + fraction * (LatencyProfiles.MAX_MS - LatencyProfiles.MIN_MS)).toInt()), locale)) },
                modifier = Modifier.weight(1f).widthIn(max = 320.dp).semantics { contentDescription = name },
            )
            StepButton("+", stringResource(R.string.nag_latency_step_up, name)) { onDelay(LatencyProfiles.snap(profile.delayMs + LatencyProfiles.STEP_MS)) }
            Text(
                if (pending == profile.delayMs) delayText else stringResource(R.string.nag_sync_ms, signed(pending, locale)),
                style = NagTheme.type.mono,
                color = if (profile.delayMs == 0) colors.textSecondary else colors.text,
                maxLines = 1,
                modifier = Modifier.width(76.dp).testTag("latency-value"),
            )
        }
    }
}

/** A step of 10 ms: a small square button with the sign, named for the reader. */
@Composable
private fun StepButton(text: String, label: String, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Box(
        Modifier
            .size(dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .border(1.dp, colors.borderStrong, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .semantics { contentDescription = label }
            .testTag("latency-step"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.mono, color = colors.text, maxLines = 1)
    }
}

/**
 * S-613: the measurement's window: the microphone asked for with its reason (the system's
 * dialog follows), the explanation, the chirps' progress, the result with its save, or why there
 * is none. Playback is paused first so that the chirps are all the room hears.
 */
@Composable
internal fun LatencyMeasureDialog(device: OutputDeviceKey, io: MeasureIo, referenceMs: Int?, onSave: (LatencyResult) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = remember(device, io) { LatencyMeasuring.begin(device, io, referenceMs) }
    var denied by remember { mutableStateOf(false) }
    val start = {
        denied = false
        // the chirps are all the room should hear; what played goes on after
        val service = runCatching { PlaybackService.instance }.getOrNull()
        if (service != null && runCatching { service.isPlaying }.getOrDefault(false)) {
            LatencyMeasuring.resume = true
            runCatching { service.pause() }
        }
        session.start()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) start() else denied = true }
    val state = session.state
    // done or failed, what played goes on by itself (LatencyMeasuring); cancelled under way, too
    val close = {
        LatencyMeasuring.end()
        onDismiss()
    }
    NagDialog(onDismissRequest = close) {
        DialogFrame(close) {
            MeasureSurface(
                device = device,
                state = state,
                denied = denied,
                onStart = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else ask.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRetry = { session.reset() },
                onSave = {
                    LatencyMeasuring.end()
                    onSave(it)
                },
                onDismiss = close,
            )
        }
    }
}

/** S-613: the measurement's card by its state; [denied]: the microphone was refused. */
@Composable
internal fun MeasureSurface(
    device: OutputDeviceKey,
    state: LatencyMeasureSession.State,
    denied: Boolean,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onSave: (LatencyResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val colors = NagTheme.colors
    val name = deviceLabel(res, device)
    DialogCard(stringResource(R.string.nag_latency_measure_title, name), Modifier.testTag("latency-dialog"), cardWidth = 440.dp) {
        when {
            denied -> {
                StatePanel(
                    tone = StateTone.Warning,
                    icon = NagIcons.Lock,
                    title = stringResource(R.string.nag_latency_mic_denied),
                    description = stringResource(R.string.nag_latency_mic_denied_hint),
                    primary = StateAction(stringResource(R.string.nag_latency_retry), onStart),
                    secondary = StateAction(stringResource(R.string.nag_sc_cancel), onDismiss),
                    dense = true,
                    modifier = Modifier.fillMaxWidth().testTag("latency-denied"),
                )
            }
            state is LatencyMeasureSession.State.Intro -> {
                Text(stringResource(R.string.nag_latency_intro), style = NagTheme.type.body, color = colors.text)
                Text(stringResource(R.string.nag_latency_intro_steps), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                Text(stringResource(R.string.nag_latency_intro_bluetooth), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                Text(stringResource(R.string.nag_latency_mic_reason), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(stringResource(R.string.nag_latency_start), onStart, primary = true, modifier = Modifier.testTag("latency-start"))
                }
            }
            state is LatencyMeasureSession.State.Measuring -> {
                Text(stringResource(R.string.nag_latency_measuring), style = NagTheme.type.body, color = colors.text)
                NagProgressBar(state.progress, stringResource(R.string.nag_latency_measuring), Modifier.testTag("latency-progress"))
                Text(stringResource(R.string.nag_latency_measuring_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                }
            }
            state is LatencyMeasureSession.State.Done -> {
                val result = state.result
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.nag_sync_ms, signed(result.rawMs, locale)),
                        style = NagTheme.type.display,
                        color = colors.text,
                        modifier = Modifier.testTag("latency-result"),
                    )
                    Text(
                        stringResource(R.string.nag_latency_result_detail, result.valid, result.total, result.spreadMs),
                        style = NagTheme.type.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (result.saveable && device.kind == OutputKind.Speaker) {
                    Text(stringResource(R.string.nag_latency_result_speaker), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                } else if (result.saveable && result.referenceMs != null) {
                    Text(
                        stringResource(R.string.nag_latency_result_reference, result.referenceMs, stringResource(R.string.nag_sync_ms, signed(result.delayMs, locale))),
                        style = NagTheme.type.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                if (!result.saveable) {
                    Text(stringResource(R.string.nag_latency_result_inexact), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("latency-inexact"))
                }
                if (result.uncertain) {
                    Text(
                        stringResource(R.string.nag_latency_result_uncertain, result.spreadMs),
                        style = NagTheme.type.labelSmall,
                        color = colors.warning,
                        modifier = Modifier.testTag("latency-uncertain"),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (result.saveable) {
                        NagButton(stringResource(R.string.nag_latency_again), onRetry)
                        NagButton(stringResource(R.string.nag_latency_save), { onSave(result) }, primary = true, modifier = Modifier.testTag("latency-save"))
                    } else {
                        NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                        NagButton(stringResource(R.string.nag_latency_again), onRetry, primary = true)
                    }
                }
            }
            state is LatencyMeasureSession.State.Failed -> {
                val (title, hint) = when (state.reason) {
                    MeasureFailure.NotHeard -> R.string.nag_latency_fail_quiet to R.string.nag_latency_fail_quiet_hint
                    MeasureFailure.Inconsistent -> R.string.nag_latency_fail_noisy to R.string.nag_latency_fail_noisy_hint
                    MeasureFailure.Device -> R.string.nag_latency_fail_device to R.string.nag_latency_fail_device_hint
                }
                StatePanel(
                    tone = StateTone.Warning,
                    icon = NagIcons.Warning,
                    title = stringResource(title),
                    description = stringResource(hint),
                    primary = StateAction(stringResource(R.string.nag_latency_retry), onRetry),
                    secondary = StateAction(stringResource(R.string.nag_sc_cancel), onDismiss),
                    dense = true,
                    modifier = Modifier.fillMaxWidth().testTag("latency-failed"),
                )
            }
        }
    }
}
