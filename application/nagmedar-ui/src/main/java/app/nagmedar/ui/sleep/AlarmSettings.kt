package app.nagmedar.ui.sleep

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.music.MediaLibraryPlaylists
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.provider.Settings as SystemSettings

/**
 * S-1007: Settings › Alarm: on or off, its time and days (none: once), what it plays (the music
 * played last, a playlist, a stream), how long its volume takes to rise and how loud the device
 * is set; the next ring is said under the time. The screenshot tests give [initial], [exact],
 * [playlists], [now] and [onChange]; the app reads and keeps the alarm itself.
 */
@Composable
internal fun AlarmSection(
    initial: Alarm? = null,
    exact: Boolean? = null,
    playlists: List<PlaylistSummary>? = null,
    now: Long? = null,
    onChange: ((Alarm) -> Unit)? = null,
) {
    val context = LocalContext.current
    var alarm by remember { mutableStateOf(initial ?: Alarms.load(context)) }
    // a change made to the alarm as it is now, not as the last frame drew it: keys repeat faster
    val edit: ((Alarm) -> Alarm) -> Unit = { change ->
        val changed = change(alarm)
        if (changed != alarm) {
            alarm = changed
            if (onChange != null) onChange(changed) else Alarms.save(context.applicationContext, changed)
        }
    }
    SettingsSection(stringResource(R.string.nag_alarm_title)) {
        SettingRow(stringResource(R.string.nag_alarm_wake), subtitle = stringResource(R.string.nag_alarm_wake_hint)) {
            NagSwitch(
                checked = alarm.enabled,
                onCheckedChange = { on -> edit { it.copy(enabled = on) } },
                label = stringResource(if (alarm.enabled) R.string.nag_alarm_on else R.string.nag_alarm_off),
                modifier = Modifier.testTag("alarm-switch"),
            )
        }
        if (alarm.enabled) AlarmRows(alarm, edit, exact, playlists, now)
    }
}

@Composable
private fun AlarmRows(alarm: Alarm, edit: ((Alarm) -> Alarm) -> Unit, exact: Boolean?, playlists: List<PlaylistSummary>?, now: Long?) {
    val context = LocalContext.current
    val locale = context.resources.locale()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    // the clock "next" is told by, turned each half minute
    val clock = now ?: produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }.value
    SettingRow(stringResource(R.string.nag_alarm_time)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimePart(
                alarm.hour, max = 23, label = stringResource(R.string.nag_alarm_hour), modifier = Modifier.testTag("alarm-hour"),
                onSet = { hour -> edit { it.copy(hour = hour) } },
                onStep = { up -> edit { it.copy(hour = Alarm.step(it.hour, 1, 23, up)) } },
            )
            Text(":", style = NagTheme.type.title, color = colors.text, modifier = Modifier.padding(horizontal = 4.dp))
            TimePart(
                alarm.minute, max = 59, label = stringResource(R.string.nag_alarm_minute), modifier = Modifier.testTag("alarm-minute"),
                onSet = { minute -> edit { it.copy(minute = minute) } },
                // by five, to the next multiple
                onStep = { up -> edit { it.copy(minute = Alarm.step(it.minute, 5, 59, up)) } },
            )
        }
    }
    alarm.next(clock)?.let { at ->
        Text(nextRing(at, clock, locale), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("alarm-next"))
    }
    SettingRow(stringResource(R.string.nag_alarm_days), subtitle = if (alarm.days.isEmpty()) stringResource(R.string.nag_alarm_once) else null) {
        DayChips(alarm.days, locale) { day, on -> edit { it.copy(days = if (on) it.days + day else it.days - day) } }
    }
    SourceRows(alarm.source, playlists) { source -> edit { it.copy(source = source) } }
    SettingRow(stringResource(R.string.nag_alarm_ramp), subtitle = stringResource(R.string.nag_alarm_ramp_hint)) {
        SegmentedChoice(
            options = Alarm.RAMPS.map { seconds ->
                seconds to if (seconds < 60) stringResource(R.string.nag_duration_seconds, seconds) else stringResource(R.string.nag_duration_minutes, seconds / 60)
            },
            selected = Alarm.RAMPS.minByOrNull { abs(it - alarm.rampSeconds) } ?: Alarm.RAMPS[1],
            onSelect = { seconds -> edit { it.copy(rampSeconds = seconds) } },
        )
    }
    SettingRow(stringResource(R.string.nag_alarm_loudness), subtitle = stringResource(R.string.nag_alarm_loudness_hint)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagSlider(
                value = alarm.loudness / 100f,
                onValueChange = { fraction ->
                    // by fives
                    val percent = (fraction * 20).roundToInt().coerceIn(0, 20) * 5
                    edit { it.copy(loudness = percent) }
                },
                keyStep = 0.05f,
                wheelStep = 0.05f,
                modifier = Modifier.width(200.dp).testTag("alarm-loudness"),
            )
            Spacer(Modifier.width(dimens.gapS))
            Text(
                NumberFormat.getPercentInstance(locale).format(alarm.loudness / 100.0),
                style = NagTheme.type.mono,
                color = colors.textSecondary,
                modifier = Modifier.width(56.dp),
            )
        }
    }
    ExactRow(exact)
    Spacer(Modifier.height(dimens.gapS))
    NagButton(stringResource(R.string.nag_alarm_try), { Alarms.play(context.applicationContext, alarm) }, icon = NagIcons.Alarm, modifier = Modifier.testTag("alarm-try"))
}

/** "Next: tomorrow at 07:00, in 9 h 12 min"; past a day, the day and the time alone. */
@Composable
private fun nextRing(at: Long, now: Long, locale: Locale): String {
    val day = when (val days = Alarm.daysUntil(at, now)) {
        0 -> stringResource(R.string.nag_alarm_today)
        1 -> stringResource(R.string.nag_alarm_tomorrow)
        else -> SimpleDateFormat("EEEE", locale).format(Date(at)).takeIf { days < 7 } ?: SimpleDateFormat("EEEE d MMMM", locale).format(Date(at))
    }
    val ring = Calendar.getInstance().apply { timeInMillis = at }
    val time = String.format(Locale.ROOT, "%02d:%02d", ring.get(Calendar.HOUR_OF_DAY), ring.get(Calendar.MINUTE))
    val minutes = ((at - now + 59_999) / 60_000).toInt()
    if (minutes >= 24 * 60) return stringResource(R.string.nag_alarm_next, day, time)
    val within = when {
        minutes < 60 -> stringResource(R.string.nag_duration_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.nag_duration_hours, minutes / 60)
        else -> stringResource(R.string.nag_duration_hours_minutes, minutes / 60, minutes % 60)
    }
    return stringResource(R.string.nag_alarm_next_in, day, time, within)
}

/** The hour or the minute of "07 : 30": typed (the digits come in from the right, as on a clock) or stepped with ↑ and ↓. */
@Composable
private fun TimePart(value: Int, max: Int, label: String, modifier: Modifier, onSet: (Int) -> Unit, onStep: (up: Boolean) -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val text = String.format(Locale.ROOT, "%02d", value)
    val shape = RoundedCornerShape(dimens.radiusControl)
    BasicTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { typed ->
            val digits = typed.text.filter { it in '0'..'9' }
            // "07" and a 3 typed: "73" is no hour, so "03"
            val two = digits.takeLast(2).toIntOrNull() ?: 0
            val chosen = if (two <= max) two else digits.takeLast(1).toIntOrNull() ?: 0
            if (chosen != value) onSet(chosen)
        },
        singleLine = true,
        textStyle = NagTheme.type.title.copy(color = colors.text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        interactionSource = interaction,
        modifier = modifier
            .width(56.dp)
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.borderStrong, shape)
            .semantics { contentDescription = label }
            .tabMovesFocus(focusManager)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onStep(true)
                        true
                    }
                    Key.DirectionDown -> {
                        onStep(false)
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) { inner() } },
    )
}

/** Monday to Sunday, each on or off: "Pzt Sal Çar …" as the language shortens them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: Set<Int>, locale: Locale, onToggle: (day: Int, on: Boolean) -> Unit) {
    val names = remember(locale) { DateFormatSymbols.getInstance(locale).shortWeekdays }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(names[Alarm.calendarDay(day)], day in days, { on -> onToggle(day, on) })
        }
    }
}

/** What the alarm plays: the music played last, a playlist, or a stream whose address is typed under it. */
@Composable
private fun SourceRows(source: AlarmSource, given: List<PlaylistSummary>?, onChange: (AlarmSource) -> Unit) {
    val context = LocalContext.current
    val playlists = given ?: produceState(emptyList<PlaylistSummary>()) {
        value = runCatching { MediaLibraryPlaylists(context.applicationContext).playlists() }.getOrDefault(emptyList())
    }.value
    val last = stringResource(R.string.nag_alarm_source_last)
    val stream = stringResource(R.string.nag_alarm_source_stream)
    SettingRow(stringResource(R.string.nag_alarm_source)) {
        MenuButton(
            text = when (source) {
                AlarmSource.Last -> last
                is AlarmSource.Playlist -> source.name
                is AlarmSource.Stream -> stream
            },
            entries = {
                buildList {
                    add(MenuEntry.Item(last, { onChange(AlarmSource.Last) }, checked = source == AlarmSource.Last))
                    if (playlists.isNotEmpty()) add(MenuEntry.Separator)
                    playlists.forEach { playlist ->
                        add(
                            MenuEntry.Item(
                                playlist.name,
                                { onChange(AlarmSource.Playlist(playlist.id, playlist.name)) },
                                checked = (source as? AlarmSource.Playlist)?.id == playlist.id,
                            ),
                        )
                    }
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item(stream, { onChange(AlarmSource.Stream((source as? AlarmSource.Stream)?.url.orEmpty())) }, checked = source is AlarmSource.Stream))
                }
            },
            modifier = Modifier.widthIn(max = 280.dp).testTag("alarm-source"),
        )
    }
    if (source is AlarmSource.Stream) StreamAddress(source.url) { url -> onChange(AlarmSource.Stream(url)) }
}

@Composable
private fun StreamAddress(url: String, onChange: (String) -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(url) }
    val label = stringResource(R.string.nag_alarm_stream_address)
    val placeholder = stringResource(R.string.nag_alarm_stream_placeholder)
    Column(Modifier.fillMaxWidth().padding(bottom = dimens.gapS)) {
        Text(label, style = NagTheme.type.label, color = colors.textSecondary)
        Spacer(Modifier.height(dimens.gapXs))
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                onChange(typed.trim())
            },
            singleLine = true,
            textStyle = NagTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { contentDescription = label }
                .testTag("alarm-stream")
                .tabMovesFocus(focusManager),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary)
                    inner()
                }
            },
        )
        Text(stringResource(R.string.nag_alarm_stream_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
    }
}

/** Android 12 and later ask whether an app may ring at the minute; without it, the alarm may come late. */
@Composable
private fun ExactRow(given: Boolean?) {
    val context = LocalContext.current
    val exact = given ?: produceState(Alarms.exact(context)) {
        // back from the system's page, it is known within moments
        while (true) {
            delay(2_000)
            value = Alarms.exact(context)
        }
    }.value
    if (exact) return
    val dimens = NagTheme.dimens
    Row(
        Modifier.padding(vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Text(stringResource(R.string.nag_alarm_exact_off), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning, modifier = Modifier.weight(1f))
        NagButton(stringResource(R.string.nag_alarm_exact_allow), {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    context.startActivity(
                        Intent(SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        })
    }
}
