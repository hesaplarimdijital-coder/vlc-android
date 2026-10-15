package app.nagmedar.ui.radio.record

import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.AlertBanner
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.music.PlaylistNameDialog
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationLogo
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.time.ZoneId

/** The widest the recordings' content grows. */
private val ContentWidth = 960.dp

/**
 * S-207: the Recordings tab of the radio screen. From the top: warnings (room, the folder, the
 * alarm permission), the recordings going on (track, time, size, stop), the scheduled ones (next
 * run, edit, delete), then what was recorded: grouped by station and recording, each open to its
 * tracks, with the room they take. A track plays through the normal player (Enter, a click), and
 * has a menu (also on right click and the Menu key): play, share, rename, delete; a recording's
 * menu plays it all, keeps only its complete tracks or deletes it. A deletion can be taken back
 * through [undo]. [stations] are the ones a schedule can be made for; [autoRefresh] turns off the
 * clocks that keep the screen current (the screenshot tests).
 */
@Composable
fun RecordingsTab(
    controller: RecordingsController,
    modifier: Modifier = Modifier,
    stations: List<Station> = emptyList(),
    undo: UndoBarState? = null,
    autoRefresh: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
    onShare: (RecordedTrack) -> Unit = {},
    onChooseFolder: () -> Unit = {},
    onUseDefaultFolder: () -> Unit = {},
    onAllowExact: () -> Unit = {},
    onSearchStations: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    LaunchedEffect(controller) { controller.opened() }
    if (autoRefresh) {
        LaunchedEffect(controller) {
            while (true) {
                delay(if (controller.live.isEmpty()) IDLE_REFRESH_MS else LIVE_REFRESH_MS)
                controller.refresh()
            }
        }
    }
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        val text = RecordingText.notice(res, notice)
        if (RecordingText.undoable(notice) && undo != null) {
            undo.show(text, res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) { controller.undo(notice) }
        } else {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
        controller.noticed()
    }
    val now = if (autoRefresh) rememberClock(controller.now) else controller.now
    val tracks = controller.tracks
    val dimens = NagTheme.dimens
    val empty = tracks != null && tracks.isEmpty() && controller.live.isEmpty() && controller.schedules.isEmpty()
    Box(modifier.fillMaxSize()) {
        if (empty) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Banners(controller, onChooseFolder, onUseDefaultFolder, onAllowExact)
                EmptyRecordings(controller, onSearchStations)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().testTag("recordings-list"),
                contentPadding = PaddingValues(horizontal = dimens.pagePadding - 8.dp, vertical = dimens.gapXs),
            ) {
                item(key = "banners") { Banners(controller, onChooseFolder, onUseDefaultFolder, onAllowExact) }
                if (controller.live.isNotEmpty()) {
                    item(key = "live-caption") { Caption(stringResource(R.string.nag_radio_rec_live)) }
                    controller.live.forEach { live ->
                        item(key = "live-${live.sessionId}") { Wide { LiveCard(live, now, controller) } }
                    }
                }
                item(key = "sched-caption") {
                    Caption(stringResource(R.string.nag_radio_rec_scheduled)) {
                        NagButton(
                            stringResource(R.string.nag_radio_rec_schedule_add),
                            { controller.schedule() },
                            icon = NagIcons.Alarm,
                            modifier = Modifier.testTag("recordings-schedule-add"),
                        )
                    }
                }
                if (controller.schedules.isEmpty()) {
                    item(key = "sched-empty") {
                        Wide {
                            Text(
                                stringResource(R.string.nag_radio_rec_sched_none),
                                style = NagTheme.type.labelSmall,
                                color = NagTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("recordings-sched-none"),
                            )
                        }
                    }
                }
                controller.schedules.forEach { row ->
                    item(key = "sched-${row.schedule.id}") { Wide { ScheduleRowView(row, now, zone, controller) } }
                }
                item(key = "library-caption") { Caption(stringResource(R.string.nag_radio_rec_library)) }
                item(key = "storage") { Wide { StorageBlock(controller) } }
                if (tracks == null) {
                    // the database has not answered yet
                } else if (tracks.isEmpty()) {
                    item(key = "library-empty") {
                        Wide {
                            Text(
                                stringResource(R.string.nag_radio_rec_library_none),
                                style = NagTheme.type.labelSmall,
                                color = NagTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("recordings-library-none"),
                            )
                        }
                    }
                } else {
                    controller.groups.forEach { group ->
                        item(key = "station-${group.key}") { Wide { StationHeader(group, controller) } }
                        if (RecordingsController.stationKey(group) in controller.open) {
                            group.sessions.forEach { session ->
                                item(key = "session-${session.sessionId}") { Wide { SessionHeader(session, now, zone, controller) } }
                                if (RecordingsController.sessionKey(session) in controller.open) {
                                    session.tracks.forEach { track ->
                                        item(key = "track-${track.id}") { Wide { TrackRow(session, track, controller, onShare) } }
                                    }
                                }
                            }
                        }
                    }
                    if (controller.missing.isNotEmpty()) {
                        item(key = "missing") {
                            Wide {
                                NagButton(
                                    stringResource(R.string.nag_radio_rec_remove_missing),
                                    controller::forgetMissing,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).testTag("recordings-forget-missing"),
                                )
                            }
                        }
                    }
                }
            }
        }
        controller.editor?.let { draft ->
            ScheduleEditor(controller, draft, stations, zone)
        }
        controller.renaming?.let { track ->
            val extension = track.fileName.substringAfterLast('.', "")
            PlaylistNameDialog(
                title = stringResource(R.string.nag_radio_rec_rename_title),
                initial = if (extension.isEmpty()) track.fileName else track.fileName.removeSuffix(".$extension"),
                confirm = stringResource(R.string.nag_radio_rec_rename),
                onConfirm = { name -> controller.rename(track, name) },
                onDismiss = { controller.askRename(null) },
            )
        }
    }
}

/** The time, turned each second, for the clocks of the recordings going on. */
@Composable
private fun rememberClock(initial: Long): Long = produceState(initial) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1_000)
    }
}.value

private const val LIVE_REFRESH_MS = 3_000L
private const val IDLE_REFRESH_MS = 30_000L

@Composable
private fun Wide(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { content() }
}

@Composable
private fun Caption(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = NagTheme.dimens.gapM, bottom = NagTheme.dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = NagTheme.type.bodyStrong,
            color = NagTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

// ---------------------------------------------------------------- warnings

/** What is wrong with where recordings go, how much room is left, and the alarms: each a banner with the way to mend it. */
@Composable
private fun Banners(controller: RecordingsController, onChooseFolder: () -> Unit, onUseDefaultFolder: () -> Unit, onAllowExact: () -> Unit) {
    val res = LocalContext.current.resources
    val folder = controller.folder
    val storage = controller.storage
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (folder) {
            is FolderState.Missing -> AlertBanner(
                tone = StateTone.Error,
                title = stringResource(R.string.nag_radio_rec_folder_missing_title),
                message = stringResource(R.string.nag_radio_rec_folder_missing_desc, folder.label),
                actions = listOf(
                    StateAction(stringResource(R.string.nag_radio_rec_folder_default_use), onUseDefaultFolder),
                    StateAction(stringResource(R.string.nag_radio_rec_folder_choose), onChooseFolder),
                ),
                modifier = Modifier.testTag("recordings-folder-missing"),
            )
            is FolderState.NoPermission -> AlertBanner(
                tone = StateTone.Error,
                title = stringResource(R.string.nag_radio_rec_folder_denied_title),
                message = stringResource(R.string.nag_radio_rec_folder_denied_desc, folder.label),
                actions = listOf(
                    StateAction(stringResource(R.string.nag_radio_rec_folder_default_use), onUseDefaultFolder),
                    StateAction(stringResource(R.string.nag_radio_rec_folder_choose), onChooseFolder),
                ),
                modifier = Modifier.testTag("recordings-folder-denied"),
            )
            else -> Unit
        }
        if (storage != null) SpaceBanner(storage, res)
        if (!controller.exactAlarms && controller.schedules.any { it.schedule.enabled }) {
            AlertBanner(
                tone = StateTone.Warning,
                title = stringResource(R.string.nag_radio_rec_exact_title),
                message = stringResource(R.string.nag_radio_rec_exact_desc),
                icon = NagIcons.Alarm,
                actions = listOf(StateAction(stringResource(R.string.nag_alarm_exact_allow), onAllowExact)),
                modifier = Modifier.testTag("recordings-exact"),
            )
        }
    }
}

/** The warning before the limit, and the word at it. */
@Composable
private fun SpaceBanner(storage: StorageUsage, res: Resources) {
    val locale = currentLocale()
    val size = { bytes: Long -> RecordingText.size(bytes, locale) }
    val prune = storage.limits.action == LimitAction.PruneOldest
    when (val verdict = storage.verdict) {
        StorageVerdict.Ok -> Unit
        is StorageVerdict.Warn -> when (verdict.issue) {
            SpaceIssue.LowSpace -> AlertBanner(
                tone = StateTone.Warning,
                title = stringResource(R.string.nag_radio_rec_low_banner_title),
                message = stringResource(
                    if (prune) R.string.nag_radio_rec_low_banner_prune else R.string.nag_radio_rec_low_banner_stop,
                    size(storage.freeBytes.coerceAtLeast(0)), size(storage.limits.minFreeBytes),
                ),
                modifier = Modifier.testTag("recordings-low-space"),
            )
            SpaceIssue.LimitReached -> AlertBanner(
                tone = StateTone.Warning,
                title = stringResource(R.string.nag_radio_rec_limit_banner_title),
                message = stringResource(
                    if (prune) R.string.nag_radio_rec_limit_banner_prune else R.string.nag_radio_rec_limit_banner_stop,
                    size(storage.usedBytes), size(storage.limits.maxTotalBytes),
                ),
                modifier = Modifier.testTag("recordings-near-limit"),
            )
        }
        is StorageVerdict.Stop, is StorageVerdict.Prune -> {
            val issue = (verdict as? StorageVerdict.Stop)?.issue ?: (verdict as StorageVerdict.Prune).issue
            AlertBanner(
                tone = StateTone.Error,
                title = stringResource(R.string.nag_radio_rec_at_limit_title),
                message = stringResource(
                    if (issue == SpaceIssue.LowSpace) R.string.nag_radio_rec_at_limit_space else R.string.nag_radio_rec_at_limit_total,
                    size(if (issue == SpaceIssue.LowSpace) storage.limits.minFreeBytes else storage.limits.maxTotalBytes),
                ),
                modifier = Modifier.testTag("recordings-at-limit"),
            )
        }
    }
}

// ---------------------------------------------------------------- live

/** A recording going on: the station, the track being recorded, how long, how big, and stop. */
@Composable
private fun LiveCard(live: LiveRecording, now: Long, controller: RecordingsController) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val compact = currentWidthClass() == WidthClass.Compact
    val state = live.state
    val shape = RoundedCornerShape(dimens.radiusControl)
    val station = live.request.station
    val elapsed = RecordingText.elapsed(res, if (state.startedAt > 0) now - state.startedAt else 0)
    val track = when {
        state.phase == Phase.Reconnecting -> stringResource(R.string.nag_radio_rec_live_reconnecting)
        state.phase == Phase.Connecting -> stringResource(R.string.nag_radio_rec_live_connecting)
        state.splits == false -> stringResource(R.string.nag_radio_rec_live_one_file)
        state.track != null -> stringResource(R.string.nag_radio_rec_live_track, with(RecordingNotifications) { state.track.label() })
        else -> stringResource(R.string.nag_radio_rec_live_waiting)
    }
    val until = live.request.endAt?.let { stringResource(R.string.nag_radio_rec_live_until, RecordingText.time(it, ZoneId.systemDefault())) }
    val stats = listOfNotNull(
        elapsed,
        RecordingText.size(state.bytes, locale),
        stringResource(R.string.nag_radio_rec_live_saved, state.tracksSaved),
        until,
    ).joinToString(" · ")
    val stop = stringResource(R.string.nag_radio_rec_stop)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(10.dp)
            .testTag("recording-live-${live.sessionId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(station.asStation(), 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(0xFFE5483B), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(station.name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // a new title is said to a screen reader when it changes
            Text(
                track,
                style = NagTheme.type.body,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("recording-live-track"),
            )
            Text(stats, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("recording-live-stats"))
            state.warning?.let {
                Spacer(Modifier.size(4.dp))
                ToneBadge(
                    stringResource(if (it == SpaceIssue.LowSpace) R.string.nag_radio_rec_live_low else R.string.nag_radio_rec_live_near_limit),
                    StateTone.Warning,
                )
            }
            if (compact) {
                Spacer(Modifier.size(6.dp))
                NagButton(stop, { controller.stop(live.sessionId) }, icon = NagIcons.Stop, modifier = Modifier.testTag("recording-stop-${live.sessionId}"))
            }
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            NagButton(stop, { controller.stop(live.sessionId) }, icon = NagIcons.Stop, modifier = Modifier.testTag("recording-stop-${live.sessionId}"))
        }
    }
}

// ---------------------------------------------------------------- scheduled

@Composable
private fun ScheduleRowView(row: ScheduleRow, now: Long, zone: ZoneId, controller: RecordingsController) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val schedule = row.schedule
    val shape = RoundedCornerShape(dimens.radiusControl)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val more = stringResource(R.string.nag_more_actions)
    val edit = stringResource(R.string.nag_radio_rec_edit)
    val menu = { scheduleMenu(res, schedule, controller) }
    val line = listOf(
        RecordingText.repeat(res, schedule, locale),
        RecordingText.range(schedule.hour, schedule.minute, schedule.durationMinutes),
        RecordingText.length(res, schedule.durationMinutes),
    ).joinToString(" · ")
    ContextMenuArea(entries = menu, accessibilityLabel = more, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = dimens.minTarget.coerceAtLeast(60.dp))
                    .focusRing(state, shape)
                    .background(stateBackground(state), shape)
                    .nagClickable(interaction, { controller.edit(schedule) }, onClickLabel = edit)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("schedule-${schedule.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationLogo(schedule.station.asStation(), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(schedule.station.name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        RecordingText.next(res, row, now, zone, locale),
                        style = NagTheme.type.labelSmall,
                        color = if (row.running) toneColors(StateTone.Success).content else colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("schedule-next-${schedule.id}"),
                    )
                    if (row.conflicts > 0) {
                        Spacer(Modifier.size(2.dp))
                        ToneBadge(stringResource(R.string.nag_radio_rec_sched_overlap), StateTone.Warning)
                    }
                }
            }
            NagSwitch(
                checked = schedule.enabled,
                onCheckedChange = { controller.enable(schedule, it) },
                label = stringResource(if (schedule.enabled) R.string.nag_alarm_on else R.string.nag_alarm_off),
                modifier = Modifier.testTag("schedule-switch-${schedule.id}"),
            )
            IconMenuButton(NagIcons.MoreHorizontal, more, menu)
        }
    }
}

private fun scheduleMenu(res: Resources, schedule: RecordingSchedule, controller: RecordingsController): List<MenuEntry> = listOf(
    MenuEntry.Item(res.getString(R.string.nag_radio_rec_edit), { controller.edit(schedule) }, icon = NagIcons.Alarm),
    MenuEntry.Separator,
    MenuEntry.Item(res.getString(R.string.nag_radio_rec_delete_schedule), { controller.deleteSchedule(schedule) }, icon = NagIcons.Delete, destructive = true),
)

// ---------------------------------------------------------------- the room

/** How much room recordings take: a bar, in numbers, and where the files go. */
@Composable
private fun StorageBlock(controller: RecordingsController) {
    val storage = controller.storage ?: return
    val locale = currentLocale()
    val colors = NagTheme.colors
    val size = { bytes: Long -> RecordingText.size(bytes, locale) }
    val tone = when (storage.verdict) {
        StorageVerdict.Ok -> null
        is StorageVerdict.Warn -> StateTone.Warning
        else -> StateTone.Error
    }
    val used = when {
        storage.limits.maxTotalBytes > 0 -> stringResource(R.string.nag_radio_rec_storage, size(storage.usedBytes), size(storage.limits.maxTotalBytes))
        else -> stringResource(R.string.nag_radio_rec_storage_nolimit, size(storage.usedBytes))
    }
    val free = if (storage.freeBytes >= 0) stringResource(R.string.nag_radio_rec_storage_free, size(storage.freeBytes)) else null
    val folder = when (val place = controller.folder) {
        FolderState.Default -> stringResource(R.string.nag_radio_rec_folder_default)
        is FolderState.Chosen -> place.label
        is FolderState.Missing -> place.label
        is FolderState.NoPermission -> place.label
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("recordings-storage")) {
        NagProgressBar(
            storage.fraction,
            used,
            color = tone?.let { toneColors(it).base } ?: colors.accent,
        )
        Spacer(Modifier.size(6.dp))
        Text(listOfNotNull(used, free).joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        Text(stringResource(R.string.nag_radio_rec_folder_in, folder), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- the library

@Composable
private fun StationHeader(group: StationGroup, controller: RecordingsController) {
    val key = RecordingsController.stationKey(group)
    val expanded = key in controller.open
    Disclosure(
        expanded = expanded,
        onToggle = { controller.toggle(key) },
        tag = "recordings-station-${group.key}",
        leading = { StationLogo(group.station.asStation(), 36.dp) },
        title = group.station.name,
        titleStyle = NagTheme.type.bodyStrong,
        line = listOf(
            pluralStringResource(R.plurals.nag_radio_rec_tracks, group.tracks, group.tracks),
            RecordingText.size(group.bytes, currentLocale()),
        ).joinToString(" · "),
        indent = 0.dp,
    )
}

@Composable
private fun SessionHeader(session: SessionGroup, now: Long, zone: ZoneId, controller: RecordingsController) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val key = RecordingsController.sessionKey(session)
    val expanded = key in controller.open
    val menu = { sessionMenu(res, session, controller) }
    val more = stringResource(R.string.nag_more_actions)
    val line = listOfNotNull(
        pluralStringResource(R.plurals.nag_radio_rec_tracks, session.tracks.size, session.tracks.size),
        RecordingText.size(session.bytes, locale),
        formatDuration(session.durationMs).takeIf { session.durationMs > 0 },
        pluralStringResource(R.plurals.nag_radio_rec_partial_count, session.partial, session.partial).takeIf { session.partial > 0 },
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Disclosure(
                expanded = expanded,
                onToggle = { controller.toggle(key) },
                tag = "recordings-session-${session.sessionId}",
                leading = null,
                title = RecordingText.began(res, session.startedAt, now, zone, locale),
                titleStyle = NagTheme.type.body,
                line = line,
                indent = 28.dp,
                menu = menu,
            )
        }
        IconMenuButton(NagIcons.MoreHorizontal, more, menu)
    }
}

private fun sessionMenu(res: Resources, session: SessionGroup, controller: RecordingsController): List<MenuEntry> = listOf(
    MenuEntry.Item(res.getString(R.string.nag_radio_rec_play_all), { controller.playAll(session) }, icon = NagIcons.Play),
    MenuEntry.Item(
        res.getString(R.string.nag_radio_rec_keep_complete),
        { controller.keepComplete(session) },
        enabled = session.partial > 0,
        icon = NagIcons.Check,
    ),
    MenuEntry.Separator,
    MenuEntry.Item(res.getString(R.string.nag_radio_rec_delete_all), { controller.delete(session.tracks) }, icon = NagIcons.Delete, destructive = true),
)

/** A line that opens and closes what is under it: a station, a recording. */
@Composable
private fun Disclosure(
    expanded: Boolean,
    onToggle: () -> Unit,
    tag: String,
    leading: (@Composable () -> Unit)?,
    title: String,
    titleStyle: TextStyle,
    line: String,
    indent: Dp,
    menu: (() -> List<MenuEntry>)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val toggleLabel = stringResource(if (expanded) R.string.nag_radio_rec_collapse else R.string.nag_radio_rec_expand)
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
                .padding(start = indent)
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .semantics { role = Role.Button }
                .nagClickable(interaction, onToggle, onClickLabel = toggleLabel)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) NagIcons.ChevronDown else NagIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = titleStyle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (menu != null) {
        ContextMenuArea(entries = menu, modifier = Modifier.fillMaxWidth()) { row() }
    } else {
        row()
    }
}

/** A track: its title and artist, how long, how big, whether it is whole; play, and its menu. */
@Composable
private fun TrackRow(session: SessionGroup, track: RecordedTrack, controller: RecordingsController, onShare: (RecordedTrack) -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val gone = track.id in controller.missing
    val more = stringResource(R.string.nag_more_actions)
    val play = stringResource(R.string.nag_play)
    val menu = { trackMenu(res, session, track, gone, controller, onShare) }
    val line = listOfNotNull(
        track.artist?.takeIf { it.isNotBlank() },
        formatDuration(track.durationMs).takeIf { track.durationMs > 0 },
        RecordingText.size(track.sizeBytes, locale),
    ).joinToString(" · ")
    ContextMenuArea(entries = menu, accessibilityLabel = more, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = dimens.minTarget.coerceAtLeast(52.dp))
                    .focusRing(state, shape)
                    .background(stateBackground(state), shape)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed) return@onKeyEvent false
                        when (event.key) {
                            Key.Delete -> controller.delete(listOf(track))
                            Key.F2 -> if (gone) return@onKeyEvent false else controller.askRename(track)
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    .nagClickable(interaction, { controller.play(session, track) }, enabled = !gone, onClickLabel = play)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("track-${track.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = NagTheme.type.body,
                        color = if (gone) colors.textTertiary else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (gone) {
                    Spacer(Modifier.width(8.dp))
                    ToneBadge(stringResource(R.string.nag_radio_rec_missing), StateTone.Error)
                } else if (!track.complete) {
                    Spacer(Modifier.width(8.dp))
                    ToneBadge(stringResource(R.string.nag_radio_rec_partial), StateTone.Info, Modifier.testTag("track-partial-${track.id}"))
                }
            }
            NagIconButton(NagIcons.Play, play, { controller.play(session, track) }, enabled = !gone, modifier = Modifier.testTag("track-play-${track.id}"))
            IconMenuButton(NagIcons.MoreHorizontal, more, menu)
        }
    }
}

private fun trackMenu(
    res: Resources,
    session: SessionGroup,
    track: RecordedTrack,
    gone: Boolean,
    controller: RecordingsController,
    onShare: (RecordedTrack) -> Unit,
): List<MenuEntry> = buildList {
    add(MenuEntry.Item(res.getString(R.string.nag_play), { controller.play(session, track) }, shortcut = "Enter", icon = NagIcons.Play, enabled = !gone))
    add(MenuEntry.Item(res.getString(R.string.nag_radio_rec_share), { onShare(track) }, enabled = !gone))
    add(MenuEntry.Item(res.getString(R.string.nag_radio_rec_rename), { controller.askRename(track) }, shortcut = "F2", enabled = !gone))
    add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_radio_rec_delete), { controller.delete(listOf(track)) }, shortcut = "Del", icon = NagIcons.Delete, destructive = true))
}

// ---------------------------------------------------------------- empty

/** No recording, no schedule: what recording is for and the two ways to begin. */
@Composable
private fun EmptyRecordings(controller: RecordingsController, onSearchStations: () -> Unit) {
    val res = LocalContext.current.resources
    val keys = ShortcutRegistry.chordLabel(ShortcutId.RadioRecord, res)
    StatePanel(
        tone = StateTone.Neutral,
        icon = NagIcons.Record,
        title = stringResource(R.string.nag_radio_rec_empty_title),
        description = if (keys != null) stringResource(R.string.nag_radio_rec_empty_desc, keys) else stringResource(R.string.nag_radio_rec_empty_desc_menu),
        primary = StateAction(stringResource(R.string.nag_radio_rec_schedule_add)) { controller.schedule() },
        secondary = StateAction(stringResource(R.string.nag_radio_search_stations), onSearchStations),
        modifier = Modifier.testTag("recordings-empty"),
    )
}
