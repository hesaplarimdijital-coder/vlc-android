package app.nagmedar.ui.prefs

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT

/** The window [controller]'s dialog asks for, over "All settings"; nothing when none is asked. */
@Composable
fun PreferenceDialogHost(controller: AllSettingsController) {
    val dialog = controller.dialog ?: return
    val close = { controller.dialog = null }
    val context = LocalContext.current
    val values = controller.values
    when (dialog) {
        is PrefDialog.Choice -> {
            val (entries, entryValues) = PreferenceCatalog.entries(dialog.item, context)
            // a value that is not kept (the subtitles' presets) shows none chosen
            val current = if (dialog.item.persistent) values.text(dialog.item) else null
            ChoiceDialog(dialog.item.title, entries, entryValues, current, onSelect = { controller.choose(dialog.item, it) }, onDismiss = close)
        }
        is PrefDialog.MultiChoice -> MultiChoiceDialog(
            dialog.item.title,
            dialog.item.entries,
            dialog.item.entryValues,
            values.selection(dialog.item),
            onSave = { controller.chooseSet(dialog.item, it) },
            onDismiss = close,
        )
        is PrefDialog.Text -> TextDialog(dialog.item.title, values.text(dialog.item), dialog.item.multiLine, onSave = { controller.type(dialog.item, it) }, onDismiss = close)
        is PrefDialog.Number -> NumberDialog(dialog.item.title, values.number(dialog.item), dialog.item.min, dialog.item.max, onSave = { controller.setNumber(dialog.item, it) }, onDismiss = close)
        is PrefDialog.Colour -> ColourDialog(dialog.item.title, values.number(dialog.item), onSave = { controller.setColour(dialog.item, it) }, onDismiss = close)
        is PrefDialog.Confirm -> ConfirmDialog(dialog.title, dialog.message, dialog.button, onConfirm = dialog.onConfirm, onDismiss = close)
        PrefDialog.Restart -> ConfirmDialog(
            stringResource(R.string.restart_vlc),
            stringResource(R.string.restart_message),
            stringResource(R.string.nag_prefs_restart_now),
            onConfirm = controller::restartApp,
            onDismiss = close,
            dismiss = stringResource(R.string.nag_prefs_later),
        )
        PrefDialog.SleepDefault -> SleepDefaultDialog(
            minutes = values.long(SLEEP_TIMER_DEFAULT_INTERVAL, -1L).takeIf { it > 0 }?.let { (it / 60_000L).toInt() },
            wait = values.bool(SLEEP_TIMER_DEFAULT_WAIT, false),
            reset = values.bool(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false),
            onSave = controller::setSleepDefault,
            onDismiss = close,
        )
        PrefDialog.Permissions -> PermissionsDialog(onDismiss = close)
        is PrefDialog.Pin -> PinDialog(dialog.mode, controller, onDone = {
            close()
            dialog.onDone()
        }, onCancel = {
            close()
            dialog.onCancel()
        })
    }
}

/** A ListPreference's entries, one to pick; the window closes on the pick. */
@Composable
internal fun ChoiceDialog(title: String, entries: List<String>, values: List<String>, selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title, cardWidth = 420.dp) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    entries.zip(values).forEach { (entry, value) ->
                        ChoiceRow(entry, value == selected) {
                            onSelect(value)
                            onDismiss()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .nagClickable(interaction, onClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
        if (selected) Icon(NagIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimens.icon))
    }
}

/** A MultiSelectListPreference's entries to tick; Save keeps them. */
@Composable
internal fun MultiChoiceDialog(title: String, entries: List<String>, values: List<String>, selected: Set<String>, onSave: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var ticked by remember { mutableStateOf(selected) }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title, cardWidth = 420.dp) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    entries.zip(values).forEach { (entry, value) ->
                        NagCheckbox(value in ticked, { on -> ticked = if (on) ticked + value else ticked - value }, entry, Modifier.fillMaxWidth())
                    }
                }
                DialogButtons(onDismiss, onSave = {
                    onSave(ticked)
                    onDismiss()
                })
            }
        }
    }
}

/** An EditTextPreference's text; Save or Enter keeps it. */
@Composable
internal fun TextDialog(title: String, initial: String, multiLine: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val save = {
        onSave(text.trim())
        onDismiss()
    }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title, cardWidth = 460.dp) {
                NagTextField(text, { text = it }, title, onDone = save, lines = if (multiLine) 3 else 1)
                DialogButtons(onDismiss, save)
            }
        }
    }
}

/** VLC's number picker (1…100 seconds): typed, or a step at a time with − and +. */
@Composable
internal fun NumberDialog(title: String, initial: Int, min: Int, max: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.coerceIn(min, max).toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in min..max }
    val save = {
        if (value != null) {
            onSave(value)
            onDismiss()
        }
    }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                    NagButton(stringResource(R.string.nag_prefs_minus), { text = ((value ?: initial) - 1).coerceIn(min, max).toString() })
                    Box(Modifier.weight(1f)) {
                        NagTextField(text, { text = it.filter(Char::isDigit).take(3) }, stringResource(R.string.nag_prefs_range, min, max), onDone = save)
                    }
                    NagButton(stringResource(R.string.nag_prefs_plus), { text = ((value ?: initial) + 1).coerceIn(min, max).toString() })
                }
                DialogButtons(onDismiss, save, enabled = value != null)
            }
        }
    }
}

/** The colours offered for the subtitles' text, background, shadow and outline; a code of six hex digits is taken too. */
private val swatches = listOf(
    0xFFFFFFFF, 0xFFFFFF00, 0xFFFFE45C, 0xFF7FE8FF, 0xFF00FF00, 0xFFFF8C00,
    0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFFD0D4DA, 0xFF808080, 0xFF000000,
).map { it.toInt() }

/** A ColorPreferenceCompat's colour: a swatch or a hex code; the opacity is a setting of its own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColourDialog(title: String, initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var colour by remember { mutableIntStateOf(initial or OPAQUE) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    val dimens = NagTheme.dimens
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    swatches.forEach { argb ->
                        Swatch(argb, selected = argb == colour) {
                            colour = argb
                            hex = hexOf(argb)
                        }
                    }
                }
                NagTextField(hex, { typed ->
                    hex = typed.take(7)
                    parseHex(typed)?.let { colour = it }
                }, stringResource(R.string.nag_prefs_colour_code))
                DialogButtons(onDismiss, {
                    onSave(colour)
                    onDismiss()
                }, enabled = parseHex(hex) != null)
            }
        }
    }
}

/** A colour to pick: its swatch, ringed when it is the chosen one; read out by its code. */
@Composable
internal fun Swatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val target = NagTheme.dimens.minTarget.coerceAtLeast(32.dp)
    val code = hexOf(argb)
    Box(
        Modifier
            .size(target)
            .focusRing(state, CircleShape)
            .nagClickable(interaction, onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = code
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(Color(argb), CircleShape)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.borderStrong, CircleShape),
        )
    }
}

private const val OPAQUE = 0xFF000000.toInt()

internal fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or OPAQUE }
}

/** A question before a change: [button] makes it, [dismiss] (Cancel) leaves it. */
@Composable
internal fun ConfirmDialog(title: String, message: String, button: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String? = null) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(title, cardWidth = 460.dp) {
                Text(
                    message,
                    style = NagTheme.type.body,
                    color = NagTheme.colors.textSecondary,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS, Alignment.End)) {
                    NagButton(dismiss ?: stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(button, {
                        onDismiss()
                        onConfirm()
                    }, primary = true)
                }
            }
        }
    }
}

@Composable
private fun DialogButtons(onDismiss: () -> Unit, onSave: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS, Alignment.End)) {
        NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
        NagButton(stringResource(R.string.nag_prefs_save), onSave, primary = true, enabled = enabled)
    }
}

/**
 * VLC's default sleep timer (PlaylistManager starts every playback with it): off, or a number of
 * minutes, waiting for the end of what plays and starting again on a touch if asked.
 */
@Composable
internal fun SleepDefaultDialog(minutes: Int?, wait: Boolean, reset: Boolean, onSave: (Int?, Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(minutes?.toString().orEmpty()) }
    var waits by remember { mutableStateOf(wait) }
    var resets by remember { mutableStateOf(reset) }
    val typed = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_SLEEP_MINUTES }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.sleep_title), cardWidth = 420.dp) {
                NagTextField(text, { text = it.filter(Char::isDigit).take(4) }, stringResource(R.string.nag_prefs_sleep_minutes), placeholder = stringResource(R.string.disabled))
                NagCheckbox(waits, { waits = it }, stringResource(R.string.wait_before_sleep), Modifier.fillMaxWidth())
                NagCheckbox(resets, { resets = it }, stringResource(R.string.reset_on_interaction), Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_prefs_sleep_off), {
                        onSave(null, waits, resets)
                        onDismiss()
                    })
                    Spacer(Modifier.weight(1f))
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(stringResource(R.string.nag_prefs_save), {
                        onSave(typed, waits, resets)
                        onDismiss()
                    }, primary = true, enabled = typed != null)
                }
            }
        }
    }
}

private const val MAX_SLEEP_MINUTES = 1440

/**
 * The permissions the app has (VLC's permission list): media files, all files, notifications;
 * each says whether it is given, and asks for it with the system's prompt or opens the system's
 * page for it. It reads them again when the window comes back from there.
 */
@Composable
internal fun PermissionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { round++ } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { round++ }
    val media = remember(round) { MediaAccess.hasMediaAccess(context) }
    val allFiles = remember(round) { MediaAccess.hasAllFiles(context) }
    val notifications = remember(round) { MediaAccess.hasNotifications(context) }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.permissions), cardWidth = 460.dp) {
                PermissionRow(stringResource(R.string.nag_state_permission_title), stringResource(R.string.nag_state_permission_desc), media) {
                    if (media) open(context, MediaAccess.appSettings(context)) else runCatching { ask.launch(MediaAccess.mediaPermissions) }
                }
                if (MediaAccess.canAskAllFiles) {
                    PermissionRow(stringResource(R.string.nag_fr_all_files), stringResource(R.string.nag_fr_all_files_desc), allFiles) {
                        open(context, MediaAccess.allFilesSettings(context))
                    }
                }
                PermissionRow(stringResource(R.string.nag_fr_notif_item), stringResource(R.string.nag_fr_notif_item_desc), notifications) {
                    val permission = MediaAccess.notificationPermission
                    if (notifications || permission == null) open(context, MediaAccess.appSettings(context)) else runCatching { ask.launch(arrayOf(permission)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_sc_close), onDismiss, primary = true)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onChange: () -> Unit) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text)
            Text(description, style = NagTheme.type.labelSmall, color = colors.textTertiary)
            Text(
                stringResource(if (granted) R.string.nag_prefs_granted else R.string.nag_prefs_not_granted),
                style = NagTheme.type.labelSmall,
                color = if (granted) colors.textSecondary else colors.text,
            )
        }
        Spacer(Modifier.width(NagTheme.dimens.gapS))
        NagButton(stringResource(if (granted) R.string.nag_prefs_change else R.string.nag_prefs_allow), onChange)
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The parental control's PIN of four digits (VLC's PinCodeActivity): made twice over the first
 * time, asked for once to get in, the old one then twice the new one to change it. It is kept as
 * VLC kept it (its SHA-256), so the classic player's safe mode reads the same PIN.
 */
@Composable
internal fun PinDialog(mode: PinMode, controller: AllSettingsController, onDone: () -> Unit, onCancel: () -> Unit) {
    var step by remember { mutableStateOf(if (mode == PinMode.Create) PinStep.New else PinStep.Existing) }
    var first by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val next = next@{
        if (pin.length != PIN_LENGTH) return@next
        when (step) {
            PinStep.Existing -> if (!controller.pinMatches(pin)) {
                error = R.string.safe_mode_invalid_pin
            } else if (mode == PinMode.Check) {
                onDone()
            } else {
                step = PinStep.New
                error = null
            }
            PinStep.New -> {
                first = pin
                step = PinStep.Again
                error = null
            }
            PinStep.Again -> if (pin != first) {
                error = R.string.safe_mode_no_match
                step = PinStep.New
            } else {
                controller.savePin(pin)
                onDone()
            }
        }
        pin = ""
    }
    val title = when (mode) {
        PinMode.Create -> R.string.pin_code_reason_create
        PinMode.Check -> R.string.pin_code_reason_check
        PinMode.Modify -> R.string.pin_code_reason_modify
    }
    NagDialog(onDismissRequest = onCancel) {
        DialogFrame(onCancel) {
            DialogCard(stringResource(title)) {
                val label = when (step) {
                    PinStep.Existing -> R.string.safe_mode_pin
                    PinStep.New -> R.string.safe_mode_new_pin
                    PinStep.Again -> R.string.safe_mode_re_pin
                }
                NagTextField(pin, { pin = it.filter(Char::isDigit).take(PIN_LENGTH) }, stringResource(label), secret = true, onDone = next)
                error?.let { Text(stringResource(it), style = NagTheme.type.labelSmall, color = NagTheme.colors.text) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
                    NagButton(stringResource(R.string.nag_prefs_next), next, primary = true, enabled = pin.length == PIN_LENGTH)
                }
            }
        }
    }
}

private enum class PinStep { Existing, New, Again }

private const val PIN_LENGTH = 4
