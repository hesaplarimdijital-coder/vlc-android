package app.nagmedar.ui.radio.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.sleep.Alarm
import app.nagmedar.ui.sleep.TimePart
import app.nagmedar.ui.theme.NagTheme
import java.time.ZoneId

/** S-1107: the window that schedules a recording, or changes a scheduled one. */
@Composable
internal fun ScheduleEditor(controller: RecordingsController, draft: ScheduleDraft, stations: List<Station>, zone: ZoneId) {
    NagDialog(onDismissRequest = controller::closeEditor) {
        DialogFrame(controller::closeEditor) { ScheduleSurface(controller, draft, stations, zone) }
    }
}

/**
 * The editor's card: the station, when it starts, how long it goes on or when it ends, and how it
 * repeats; under them when it runs next and what it meets among the other schedules.
 */
@Composable
internal fun ScheduleSurface(controller: RecordingsController, draft: ScheduleDraft, stations: List<Station>, zone: ZoneId) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val locale = currentLocale()
    val editing = draft.id != 0L
    val check = controller.check(draft)
    val next = draft.toSchedule(controller.now, zone)?.let { made ->
        ScheduleMath.next(made, controller.now, zone)?.let { window ->
            RecordingText.next(res, ScheduleRow(made, window, running = false, conflicts = 0), controller.now, zone, locale)
        }
    }
    DialogCard(
        stringResource(if (editing) R.string.nag_radio_rec_ed_title_edit else R.string.nag_radio_rec_ed_title_new),
        Modifier.testTag("schedule-dialog"),
        cardWidth = 460.dp,
    ) {
        SettingRow(stringResource(R.string.nag_radio_rec_ed_station)) {
            val chosen = draft.station
            MenuButton(
                chosen?.name ?: stringResource(R.string.nag_radio_rec_ed_pick),
                entries = {
                    if (stations.isEmpty()) {
                        listOf(MenuEntry.Item(res.getString(R.string.nag_radio_rec_ed_no_stations), {}, enabled = false))
                    } else {
                        stations.map { station ->
                            MenuEntry.Item(
                                station.name,
                                { controller.change(draft.copy(station = RecordingStation(station.name, station.streamUrl, station.uuid, station.logoUrl))) },
                                checked = chosen?.key == station.key,
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("schedule-station"),
            )
        }
        SettingRow(stringResource(R.string.nag_radio_rec_ed_start)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimePart(
                    draft.hour, max = 23, label = stringResource(R.string.nag_alarm_hour), modifier = Modifier.testTag("schedule-hour"),
                    onSet = { controller.change(draft.copy(hour = it)) },
                    onStep = { up -> controller.change(draft.copy(hour = Alarm.step(draft.hour, 1, 23, up))) },
                )
                Text(":", style = NagTheme.type.title, color = colors.text, modifier = Modifier.padding(horizontal = 4.dp))
                TimePart(
                    draft.minute, max = 59, label = stringResource(R.string.nag_alarm_minute), modifier = Modifier.testTag("schedule-minute"),
                    onSet = { controller.change(draft.copy(minute = it)) },
                    onStep = { up -> controller.change(draft.copy(minute = Alarm.step(draft.minute, 5, 59, up))) },
                )
            }
        }
        SettingRow(stringResource(R.string.nag_radio_rec_ed_duration)) {
            val options = (RecordingSchedule.DURATIONS + draft.durationMinutes).distinct().sorted()
            MenuButton(
                RecordingText.length(res, draft.durationMinutes),
                entries = {
                    options.map { minutes ->
                        MenuEntry.Item(RecordingText.length(res, minutes), { controller.change(draft.copy(durationMinutes = minutes)) }, checked = minutes == draft.durationMinutes)
                    }
                },
                modifier = Modifier.testTag("schedule-duration"),
            )
        }
        SettingRow(stringResource(R.string.nag_radio_rec_ed_end)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimePart(
                    draft.endHour, max = 23, label = stringResource(R.string.nag_alarm_hour), modifier = Modifier.testTag("schedule-end-hour"),
                    onSet = { controller.change(draft.endingAt(it, draft.endMinute)) },
                    onStep = { up -> controller.change(draft.endingAt(Alarm.step(draft.endHour, 1, 23, up), draft.endMinute)) },
                )
                Text(":", style = NagTheme.type.title, color = colors.text, modifier = Modifier.padding(horizontal = 4.dp))
                TimePart(
                    draft.endMinute, max = 59, label = stringResource(R.string.nag_alarm_minute), modifier = Modifier.testTag("schedule-end-minute"),
                    onSet = { controller.change(draft.endingAt(draft.endHour, it)) },
                    onStep = { up -> controller.change(draft.endingAt(draft.endHour, Alarm.step(draft.endMinute, 5, 59, up))) },
                )
            }
        }
        SettingRow(stringResource(R.string.nag_radio_rec_ed_repeat)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuButton(
                    repeatName(draft.repeat),
                    entries = {
                        Repeat.entries.map { repeat ->
                            MenuEntry.Item(repeatLabel(res, repeat), { controller.change(draft.copy(repeat = repeat)) }, checked = repeat == draft.repeat)
                        }
                    },
                    modifier = Modifier.testTag("schedule-repeat"),
                )
                if (draft.repeat == Repeat.Weekly) {
                    MenuButton(
                        RecordingText.weekday(draft.weekday, locale),
                        entries = {
                            (1..7).map { day ->
                                MenuEntry.Item(RecordingText.weekday(day, locale), { controller.change(draft.copy(weekday = day)) }, checked = day == draft.weekday)
                            }
                        },
                        modifier = Modifier.testTag("schedule-weekday"),
                    )
                }
            }
        }
        if (next != null) {
            Text(next, style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("schedule-next"))
        }
        val warning = toneColors(StateTone.Warning).content
        if (check.conflicts.isNotEmpty()) {
            Text(
                stringResource(R.string.nag_radio_rec_ed_conflict, check.conflicts.joinToString(", ") { it.station.name }),
                style = NagTheme.type.labelSmall,
                color = warning,
                modifier = Modifier.testTag("schedule-conflict"),
            )
        }
        if (check.tooMany) {
            Text(
                stringResource(R.string.nag_radio_rec_ed_too_many, controller.maxRecordings),
                style = NagTheme.type.labelSmall,
                color = warning,
                modifier = Modifier.testTag("schedule-too-many"),
            )
        }
        Spacer(Modifier.size(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                NagButton(
                    stringResource(R.string.nag_radio_rec_delete_schedule),
                    {
                        controller.schedules.firstOrNull { it.schedule.id == draft.id }?.let { controller.deleteSchedule(it.schedule) }
                        controller.closeEditor()
                    },
                    modifier = Modifier.testTag("schedule-delete"),
                )
            }
            Spacer(Modifier.weight(1f))
            NagButton(stringResource(R.string.nag_sc_cancel), controller::closeEditor, modifier = Modifier.testTag("schedule-cancel"))
            Spacer(Modifier.width(8.dp))
            NagButton(
                stringResource(if (editing) R.string.nag_radio_rec_ed_update else R.string.nag_radio_rec_ed_schedule),
                controller::saveSchedule,
                primary = true,
                enabled = draft.ready,
                modifier = Modifier.testTag("schedule-save"),
            )
        }
    }
}

@Composable
private fun repeatName(repeat: Repeat): String = repeatLabel(LocalContext.current.resources, repeat)

private fun repeatLabel(res: android.content.res.Resources, repeat: Repeat): String = res.getString(
    when (repeat) {
        Repeat.Once -> R.string.nag_radio_rec_repeat_once
        Repeat.Daily -> R.string.nag_radio_rec_repeat_daily
        Repeat.Weekdays -> R.string.nag_radio_rec_repeat_weekdays
        Repeat.Weekly -> R.string.nag_radio_rec_repeat_weekly_menu
    },
)
