package app.nagmedar.ui.tvlab.remux

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.rememberReorderState
import app.nagmedar.ui.components.reorderHandle
import app.nagmedar.ui.components.reorderItem
import app.nagmedar.ui.components.reorderList
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.sleep.Alarm
import app.nagmedar.ui.sleep.Alarms
import app.nagmedar.ui.sleep.TimePart
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.resources
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the queue's page asks for (S-1210). */
interface RemuxQueueActions {
    fun cancel(id: Long)

    fun remove(id: Long)

    /** The waiting jobs in this order, first to run first. */
    fun reorder(ids: List<Long>)

    fun hold(on: Boolean)

    /** The night run set for [at]; [charging], once the device charges. */
    fun night(at: Long, charging: Boolean)

    /** The night run taken off: the queue goes on now. */
    fun cancelNight()
}

/** The jobs not finished, as the queue runs them: those running first, then the waiting ones in their order. */
fun activeJobs(jobs: List<RemuxJobView>): List<RemuxJobView> =
    jobs.filter { it.active }.sortedWith(compareBy<RemuxJobView> { it.state == RemuxJobState.QUEUED }.thenBy { it.position }.thenBy { it.id })

/**
 * The remux queue's page (S-1210): the queue paused or resumed as a whole, its night run set or
 * taken off, and every job not finished, the running one first; the waiting ones in the order
 * they run, moved by their handle, their arrows or Alt+↑ and Alt+↓.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemuxQueuePage(jobs: List<RemuxJobView>, control: QueueControl, now: Long, actions: RemuxQueueActions, modifier: Modifier = Modifier) {
    val res = resources()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = currentLocale()
    val active = activeJobs(jobs)
    val waiting = active.filter { it.state == RemuxJobState.QUEUED }
    var nightDialog by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().testTag("tvlab-queue")) {
        Column(Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // S-1210: a night run set, or one whose time has come and that waits for the charger
            val night = control.waitsForNight(now) || control.waitsForCharger(now)
            val status = when {
                control.held -> stringResource(R.string.nag_tvlab_queue_held_text)
                control.waitsForNight(now) -> nightLine(control, locale)
                control.waitsForCharger(now) -> stringResource(R.string.nag_tvlab_queue_charger)
                waiting.isEmpty() && active.isEmpty() -> stringResource(R.string.nag_tvlab_queue_idle)
                else -> pluralStringResource(R.plurals.nag_tvlab_queue_waiting, waiting.size, waiting.size)
            }
            Text(
                status,
                style = NagTheme.type.label,
                color = if (control.held || night) toneColors(StateTone.Warning).content else colors.textSecondary,
                modifier = Modifier.testTag("tvlab-queue-status"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (control.held) {
                    NagButton(stringResource(R.string.nag_tvlab_queue_resume), { actions.hold(false) }, icon = NagIcons.Play, primary = true)
                } else {
                    NagButton(stringResource(R.string.nag_tvlab_queue_pause), { actions.hold(true) }, icon = NagIcons.Pause)
                }
                if (night) {
                    NagButton(stringResource(R.string.nag_tvlab_queue_start_now), actions::cancelNight)
                    NagButton(stringResource(R.string.nag_tvlab_queue_night_change), { nightDialog = true }, icon = NagIcons.Alarm)
                } else {
                    NagButton(stringResource(R.string.nag_tvlab_queue_night), { nightDialog = true }, icon = NagIcons.Alarm, modifier = Modifier.testTag("tvlab-queue-night"))
                }
            }
            Text(stringResource(R.string.nag_tvlab_queue_note), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        if (active.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                StatePanel(
                    tone = StateTone.Info,
                    icon = NagIcons.Queue,
                    title = stringResource(R.string.nag_tvlab_queue_empty),
                    description = stringResource(R.string.nag_tvlab_queue_empty_text),
                )
            }
        } else {
            val list = rememberLazyListState()
            val first = active.indexOfFirst { it.state == RemuxJobState.QUEUED }
            val move: (Int, Int) -> Unit = { from, to ->
                // among the waiting jobs only: the running one stays where it is
                if (first >= 0 && from >= first) {
                    val ids = waiting.map { it.id }.toMutableList()
                    val target = (to - first).coerceIn(0, ids.lastIndex)
                    ids.add(target, ids.removeAt(from - first))
                    actions.reorder(ids)
                }
            }
            val reorder = rememberReorderState(list, move)
            LazyColumn(
                Modifier.fillMaxSize().reorderList(reorder),
                state = list,
                contentPadding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapL),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(active, key = { _, job -> job.id }) { index, job ->
                    val queued = job.state == RemuxJobState.QUEUED
                    val up = res.getString(R.string.nag_queue_move_up)
                    val down = res.getString(R.string.nag_queue_move_down)
                    val canUp = queued && index > first
                    val canDown = queued && index < active.lastIndex
                    val shape = RoundedCornerShape(dimens.radiusCard)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .reorderItem(reorder, job.id, index)
                            .background(colors.surface, shape)
                            .border(1.dp, colors.border, shape)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(up) { move(index, index - 1); true }.takeIf { canUp },
                                    CustomAccessibilityAction(down) { move(index, index + 1); true }.takeIf { canDown },
                                )
                            }
                            .onKeyEvent { event ->
                                if (!queued || event.type != KeyEventType.KeyDown || !event.isAltPressed || event.isCtrlPressed) return@onKeyEvent false
                                when {
                                    event.key == Key.DirectionUp && canUp -> move(index, index - 1)
                                    event.key == Key.DirectionDown && canDown -> move(index, index + 1)
                                    else -> return@onKeyEvent false
                                }
                                true
                            }
                            .testTag("tvlab-queue-job"),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (queued) {
                            val handle = res.getString(R.string.nag_queue_handle, job.outputName)
                            Box(
                                Modifier
                                    .size(dimens.minTarget.coerceAtLeast(36.dp))
                                    .reorderHandle(reorder, job.id)
                                    .semantics { contentDescription = handle },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(NagIcons.Menu, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        RemuxJobRow(job, onCancel = { actions.cancel(job.id) }, onRemove = { actions.remove(job.id) }, modifier = Modifier.weight(1f))
                        if (queued) {
                            Column {
                                NagIconButton(NagIcons.ChevronUp, up, { move(index, index - 1) }, enabled = canUp)
                                NagIconButton(NagIcons.ChevronDown, down, { move(index, index + 1) }, enabled = canDown)
                            }
                        }
                    }
                }
            }
        }
    }
    if (nightDialog) {
        NightRunDialog(control, now, onSet = { at, charging -> actions.night(at, charging); nightDialog = false }, onDismiss = { nightDialog = false })
    }
}

/** "Gece çalıştırma: 23.10.2026 02:00 başlar · yalnızca şarjdayken". */
@Composable
private fun nightLine(control: QueueControl, locale: Locale): String {
    val at = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(control.nightAt))
    val line = stringResource(R.string.nag_tvlab_queue_night_at, at)
    return if (control.nightCharging) line + " · " + stringResource(R.string.nag_tvlab_queue_night_charging_short) else line
}

/**
 * The night run's window (S-1210): the hour and minute it starts at (02:00 at first), whether it
 * waits for the charger, when that is; without the exact-alarm permission, a word that it may
 * start a little late and the way to allow it.
 */
@Composable
private fun NightRunDialog(control: QueueControl, now: Long, onSet: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val colors = NagTheme.colors
    val start = remember { control.nightAt.takeIf { it > now }?.let { java.util.Calendar.getInstance().apply { timeInMillis = it } } }
    var hour by remember { mutableIntStateOf(start?.get(java.util.Calendar.HOUR_OF_DAY) ?: DEFAULT_HOUR) }
    var minute by remember { mutableIntStateOf(start?.get(java.util.Calendar.MINUTE) ?: 0) }
    var charging by remember { mutableStateOf(control.nightCharging) }
    val at = RemuxNight.next(hour, minute, now)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_tvlab_queue_night_title), Modifier.testTag("tvlab-night-dialog"), cardWidth = 420.dp) {
                Text(stringResource(R.string.nag_tvlab_queue_night_text), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                SettingRow(stringResource(R.string.nag_tvlab_queue_night_start)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimePart(hour, max = 23, label = stringResource(R.string.nag_alarm_hour), modifier = Modifier.testTag("tvlab-night-hour"), onSet = { hour = it }, onStep = { up -> hour = Alarm.step(hour, 1, 23, up) })
                        Text(":", style = NagTheme.type.title, color = colors.text, modifier = Modifier.padding(horizontal = 4.dp))
                        TimePart(minute, max = 59, label = stringResource(R.string.nag_alarm_minute), modifier = Modifier.testTag("tvlab-night-minute"), onSet = { minute = it }, onStep = { up -> minute = Alarm.step(minute, 5, 59, up) })
                    }
                }
                NagCheckbox(charging, { charging = it }, stringResource(R.string.nag_tvlab_queue_night_charging))
                Text(
                    stringResource(R.string.nag_tvlab_queue_night_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(at))),
                    style = NagTheme.type.label,
                    color = colors.text,
                )
                if (!Alarms.exact(context)) {
                    Text(stringResource(R.string.nag_tvlab_queue_night_inexact), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
                    NagButton(stringResource(R.string.nag_alarm_exact_allow), { Alarms.requestExact(context) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(stringResource(R.string.nag_tvlab_queue_night_set), { onSet(at, charging) }, primary = true, modifier = Modifier.testTag("tvlab-night-set"))
                }
            }
        }
    }
}

/** When a night run starts unless set otherwise: two at night. */
private const val DEFAULT_HOUR = 2
