package app.nagmedar.ui.cast

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import app.nagmedar.playback.RendererKind
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StateIconTile
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import org.videolan.tools.NetworkMonitor
import org.videolan.vlc.RendererDelegate

/**
 * S-1302: the device picker, over the window: "This device" first, then the devices found as they
 * answer (Chromecasts, DLNA renderers), each with its kind; what cannot be cast as it is, said
 * above them; while nothing is found, a search under way, then what to check. The devices are
 * looked for only while it is open (and while casting). [request] says what for: what plays now,
 * or what a menu chose to play.
 */
@Composable
fun CastPickerDialog(request: CastRequest, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CastPickerHost(request, onDismiss)
    }
}

/** The picker on the app's casting: the devices found, the one cast to, the network, the setting. */
@Composable
private fun CastPickerHost(request: CastRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (found, discovering) = rememberDiscovery()
    val active = rememberCastDevice()
    var enabled by remember { mutableStateOf(Casting.enabled(context)) }
    val connection by remember(context) { NetworkMonitor.getInstance(context).connectionFlow }.collectAsState()
    val lan = connection.connected && (!connection.mobile || connection.vpn)
    // how long it has looked: what to check after a while, and again after "Search again"
    var searchFrom by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val searchedMs by produceState(0L, searchFrom) {
        while (true) {
            value = SystemClock.elapsedRealtime() - searchFrom
            if (value >= CastPicker.EMPTY_AFTER_MS) break
            delay(500)
        }
    }
    val nowPlaying = rememberNowPlaying()
    val payload = request.payload
    val subject = payload?.first ?: nowPlaying.media
    val view = CastPicker.view(enabled, lan, found, active, discovering.value, searchedMs)
    CastPickerSurface(
        view = view,
        active = active,
        notices = remember(subject) { Casting.notices(subject) },
        playing = payload?.first?.title,
        onChoose = { device ->
            if (payload != null) Casting.play(context, device, payload) else Casting.cast(device)
            onDismiss()
        },
        onThisDevice = {
            if (payload != null) Casting.play(context, null, payload) else Casting.stop()
            onDismiss()
        },
        onClose = onDismiss,
        onTurnOn = {
            Casting.turnOn(context)
            enabled = true
        },
        onWifiSettings = {
            val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AndroidSettings.Panel.ACTION_WIFI else AndroidSettings.ACTION_WIFI_SETTINGS
            runCatching { context.startActivity(Intent(panel)) }
        },
        onSearchAgain = {
            RendererDelegate.restart()
            searchFrom = SystemClock.elapsedRealtime()
        },
    )
}

/**
 * S-1302: the picker over [activity] (the video player's cast button and Ctrl+Shift+C there), in
 * the player's dark theme; it goes when it is closed.
 */
fun showCastPicker(activity: FragmentActivity) {
    val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    // open already
    if (root.findViewWithTag<View>(PICKER_TAG) != null) return
    val view = ComposeView(activity)
    view.tag = PICKER_TAG
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    view.setContent {
        val prefs = rememberNagmedarPreferences()
        val mode = if (hasPrecisePointer(activity)) InputMode.Pointer else InputMode.Touch
        NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = mode) {
            // the view goes after the click that closed it
            CastPickerDialog(CastRequest()) { root.post { root.removeView(view) } }
        }
    }
    root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

private const val PICKER_TAG = "nagmedar-cast-picker"

/**
 * The picker as it looks (S-1302), for the app and the screenshots: a card in the middle of a
 * wide window, a sheet along the bottom of a phone's. Up and Down move between the devices,
 * Enter casts, Esc closes; a right click or a long press gives a device's menu.
 */
@Composable
fun CastPickerSurface(
    view: CastPickerView,
    active: CastDevice?,
    notices: List<CastNotice>,
    onChoose: (CastDevice) -> Unit,
    onThisDevice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** What a menu chose to play on a device; null: what plays now is cast. */
    playing: String? = null,
    onTurnOn: () -> Unit = {},
    onWifiSettings: () -> Unit = {},
    onSearchAgain: () -> Unit = {},
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        val sheet = maxWidth < SHEET_BELOW
        val shape = if (sheet) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(12.dp)
        Column(
            modifier
                .align(if (sheet) Alignment.BottomCenter else Alignment.Center)
                .then(if (sheet) Modifier.fillMaxWidth() else Modifier.width(CARD_WIDTH))
                .heightIn(max = maxHeight * 0.9f)
                .shadow(24.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (sheet) Modifier.navigationBarsPadding() else Modifier)
                .padding(dimens.gapL)
                .verticalScroll(rememberScrollState())
                .testTag("cast-picker"),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (playing != null) R.string.nag_cast_title_play else R.string.nag_cast_title),
                        style = NagTheme.type.title,
                        color = colors.text,
                    )
                    if (playing != null) Text(playing, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NagIconButton(NagIcons.Close, stringResource(R.string.nag_cast_close), onClose)
            }
            notices.forEach { NoticeLine(it) }
            PickerBody(view, active, onChoose, onThisDevice, onTurnOn, onWifiSettings, onSearchAgain)
        }
    }
}

/** Below this width the picker is a sheet along the bottom. */
private val SHEET_BELOW = 600.dp

private val CARD_WIDTH = 420.dp

@Composable
private fun ColumnScope.PickerBody(
    view: CastPickerView,
    active: CastDevice?,
    onChoose: (CastDevice) -> Unit,
    onThisDevice: () -> Unit,
    onTurnOn: () -> Unit,
    onWifiSettings: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    val colors = NagTheme.colors
    val first = remember { FocusRequester() }
    val keyboard = NagTheme.inputMode == InputMode.Pointer
    // the keyboard starts on the device cast to, or on this device
    val focusOn = active?.takeIf { view is CastPickerView.Devices }?.id
    DeviceRow(
        icon = NagIcons.Device,
        name = stringResource(R.string.nag_cast_this_device),
        detail = stringResource(if (active != null) R.string.nag_cast_here_stop else R.string.nag_cast_here),
        selected = active == null,
        onClick = onThisDevice,
        menu = { listOf(MenuEntry.Item(it.getString(R.string.nag_cast_menu_here), onThisDevice, icon = NagIcons.Device)) },
        modifier = if (focusOn == null) Modifier.focusRequester(first).testTag("cast-this-device") else Modifier.testTag("cast-this-device"),
    )
    when (view) {
        CastPickerView.Off -> StatePanel(
            StateTone.Info,
            NagIcons.Cast,
            stringResource(R.string.nag_cast_off_title),
            Modifier.align(Alignment.CenterHorizontally),
            description = stringResource(R.string.nag_cast_off_desc),
            primary = StateAction(stringResource(R.string.nag_cast_turn_on), onTurnOn),
            dense = true,
        )
        CastPickerView.NoNetwork -> StatePanel(
            StateTone.Warning,
            NagIcons.NetworkOff,
            stringResource(R.string.nag_cast_no_lan_title),
            Modifier.align(Alignment.CenterHorizontally),
            description = stringResource(R.string.nag_cast_no_lan_desc),
            primary = StateAction(stringResource(R.string.nag_net_wifi_settings), onWifiSettings),
            dense = true,
        )
        CastPickerView.Searching -> Searching()
        CastPickerView.Empty -> StatePanel(
            StateTone.Neutral,
            NagIcons.SearchOff,
            stringResource(R.string.nag_cast_empty_title),
            Modifier.align(Alignment.CenterHorizontally),
            description = stringResource(R.string.nag_cast_empty_desc),
            primary = StateAction(stringResource(R.string.nag_cast_search_again), onSearchAgain),
            dense = true,
        ) {
            listOf(R.string.nag_cast_tip_wifi, R.string.nag_cast_tip_multicast, R.string.nag_cast_tip_tv, R.string.nag_cast_tip_vpn).forEach { tip ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(BULLET, style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.width(14.dp))
                    Text(stringResource(tip), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                }
            }
        }
        is CastPickerView.Devices -> {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                view.devices.forEach { device ->
                    val on = device.id == active?.id
                    DeviceRow(
                        icon = kindIcon(device.kind),
                        name = device.name,
                        detail = if (on) stringResource(R.string.nag_cast_now, kindLabel(device.kind)) else kindLabel(device.kind),
                        selected = on,
                        onClick = { onChoose(device) },
                        menu = { res ->
                            if (on) {
                                listOf(MenuEntry.Item(res.getString(R.string.nag_cast_stop), onThisDevice, icon = NagIcons.Stop))
                            } else {
                                listOf(MenuEntry.Item(res.getString(R.string.nag_cast_menu_cast, device.name), { onChoose(device) }, icon = NagIcons.Cast))
                            }
                        },
                        modifier = if (on) Modifier.focusRequester(first).testTag("cast-device") else Modifier.testTag("cast-device"),
                    )
                }
            }
            if (view.searching) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val text = stringResource(R.string.nag_cast_still_searching)
                    Text(text, style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
                    NagProgressBar(null, label = text, modifier = Modifier.width(64.dp))
                }
            }
        }
    }
    if (keyboard) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Looking, nothing found yet: the search under way, said once. */
@Composable
private fun Searching() {
    val colors = NagTheme.colors
    val text = stringResource(R.string.nag_cast_searching)
    Column(
        Modifier.fillMaxWidth().padding(vertical = NagTheme.dimens.gapM).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS),
    ) {
        StateIconTile(NagIcons.Cast, StateTone.Info)
        Text(text, style = NagTheme.type.body, color = colors.textSecondary)
        NagProgressBar(null, label = text, modifier = Modifier.width(160.dp))
    }
}

/** A device to choose: its kind's icon, its name, what it is, a check on the one that plays. */
@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (android.content.res.Resources) -> List<MenuEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    ContextMenuArea({ menu(res) }, accessibilityLabel = name) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(52.dp))
                .focusRing(state, shape)
                .background(stateBackground(state, selected), shape)
                .nagClickable(interaction, onClick)
                .semantics(mergeDescendants = true) { this.selected = selected }
                .padding(horizontal = dimens.gapS, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.accentText, modifier = Modifier.size(dimens.iconLarge))
            Spacer(Modifier.width(dimens.gapM))
            Column(Modifier.weight(1f)) {
                Text(name, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(NagIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(dimens.icon))
        }
    }
}

/** What cannot be cast as it is, in a line of its tone. */
@Composable
private fun NoticeLine(notice: CastNotice) {
    val tone = if (notice == CastNotice.TrackerHere) StateTone.Warning else StateTone.Info
    val c = toneColors(tone)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.container, RoundedCornerShape(NagTheme.dimens.radiusControl))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("cast-notice"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (tone == StateTone.Warning) NagIcons.Warning else NagIcons.Info, contentDescription = null, tint = c.content, modifier = Modifier.size(NagTheme.dimens.icon))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                when (notice) {
                    CastNotice.TrackerHere -> R.string.nag_cast_notice_tracker
                    CastNotice.DsdAsPcm -> R.string.nag_cast_notice_dsd
                    CastNotice.EngineSettings -> R.string.nag_cast_notice_engine
                },
            ),
            style = NagTheme.type.labelSmall,
            color = c.content,
        )
    }
}

/** A device's kind as an icon: the cast mark for a Chromecast, a screen for a DLNA renderer. */
fun kindIcon(kind: RendererKind): ImageVector = when (kind) {
    RendererKind.Chromecast -> NagIcons.Cast
    // the series' icon is a television
    RendererKind.Dlna -> NagIcons.Series
    RendererKind.Other -> NagIcons.Network
}

@Composable
fun kindLabel(kind: RendererKind): String = stringResource(
    when (kind) {
        RendererKind.Chromecast -> R.string.nag_cast_kind_chromecast
        RendererKind.Dlna -> R.string.nag_cast_kind_dlna
        RendererKind.Other -> R.string.nag_cast_kind_other
    },
)

private const val BULLET = "•"
