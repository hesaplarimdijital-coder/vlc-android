package app.nagmedar.ui.backup

import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.videolan.tools.Notices

/** S-1502: where a restore is: the backup read and its parts to choose, restoring, done. */
sealed interface RestoreState {
    data class Choosing(val backup: Backup) : RestoreState

    data object Restoring : RestoreState

    data class Done(val report: RestoreReport) : RestoreState
}

/**
 * S-1502: Settings › Backup: a backup of the app's settings, shortcuts,
 * playlists, smart lists, statistics, the user's lyrics, the files' settings and the covers chosen
 * by hand, written to a file the system's picker names; and a restore of the parts chosen from
 * one. The screenshot tests give [lastBackup] (0: none) and the buttons' [onBackUp] and
 * [onRestore]; the app reads and writes the files itself.
 */
@Composable
internal fun BackupSection(
    lastBackup: Long? = null,
    onBackUp: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val app = remember { AppBackup(context) }
    var last by remember { mutableStateOf(lastBackup ?: app.lastBackup() ?: 0L) }
    var working by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf<RestoreState?>(null) }

    val backUpTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_ZIP)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val written = runCatching { app.write(app.make(), uri) }.getOrDefault(false)
            working = false
            if (written) last = app.lastBackup() ?: last
            Notices.show(context, if (written) R.string.nag_backup_done else R.string.nag_backup_failed)
        }
    }
    val restoreFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                restore = RestoreState.Choosing(app.read(uri))
            } catch (e: BackupException) {
                Notices.show(context, readFailure(e.reason), long = true)
            } catch (e: IOException) {
                Notices.show(context, R.string.nag_backup_unreadable, long = true)
            }
        }
    }

    SettingsSection(stringResource(R.string.nag_backup_settings)) {
        val made = if (last > 0L) stringResource(R.string.nag_backup_last, backupTime(last, locale)) else stringResource(R.string.nag_backup_never)
        SettingRow(stringResource(R.string.nag_backup_make), subtitle = stringResource(R.string.nag_backup_make_hint) + "\n" + made) {
            NagButton(
                stringResource(if (working) R.string.nag_backup_working else R.string.nag_backup_make_button),
                { onBackUp?.invoke() ?: backUpTo.launch(res.getString(R.string.nag_backup_file_name, fileDate(System.currentTimeMillis()))) },
                enabled = !working,
                modifier = Modifier.testTag("backup-make"),
            )
        }
        SettingRow(stringResource(R.string.nag_backup_restore), subtitle = stringResource(R.string.nag_backup_restore_hint)) {
            NagButton(
                stringResource(R.string.nag_backup_restore_button),
                { onRestore?.invoke() ?: restoreFrom.launch(OPEN_TYPES) },
                modifier = Modifier.testTag("backup-restore"),
            )
        }
    }

    restore?.let { state ->
        RestoreDialog(
            state = state,
            onRestore = { parts ->
                val backup = (state as? RestoreState.Choosing)?.backup ?: return@RestoreDialog
                restore = RestoreState.Restoring
                scope.launch { restore = RestoreState.Done(app.restore(backup.only(parts))) }
            },
            onDismiss = { if (restore !is RestoreState.Restoring) restore = null },
        )
    }
}

/** S-1502: the restore's window: the parts to choose, then what came back. */
@Composable
fun RestoreDialog(state: RestoreState, onRestore: (Set<BackupPart>) -> Unit, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { RestoreSurface(state, onRestore, onDismiss) }
    }
}

/**
 * S-1502: the restore's card. Choosing: when the backup was made and by what, each of its parts
 * with what it holds, ticked; restoring; done: what each part brought, the playlists' tracks this
 * library does not have, the parts that could not be written.
 */
@Composable
fun RestoreSurface(state: RestoreState, onRestore: (Set<BackupPart>) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val locale = currentLocale()
    when (state) {
        is RestoreState.Choosing -> {
            val backup = state.backup
            var chosen by remember(backup) { mutableStateOf(backup.parts.toSet()) }
            DialogCard(stringResource(R.string.nag_backup_restore), Modifier.testTag("restore-dialog"), cardWidth = 460.dp) {
                Text(
                    stringResource(R.string.nag_backup_made, backupTime(backup.createdAt, locale), backup.appVersion),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    backup.parts.forEach { part ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NagCheckbox(
                                checked = part in chosen,
                                onCheckedChange = { on -> chosen = if (on) chosen + part else chosen - part },
                                label = stringResource(partLabel(part)),
                                modifier = Modifier.weight(1f).testTag("restore-${part.name}"),
                            )
                            Text(partCount(res, part, backup.count(part), backup.playlistTracks), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(
                        stringResource(R.string.nag_backup_restore_go),
                        { onRestore(chosen) },
                        primary = true,
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.testTag("restore-go"),
                    )
                }
            }
        }
        RestoreState.Restoring -> DialogCard(stringResource(R.string.nag_backup_restore), Modifier.testTag("restore-dialog")) {
            Text(stringResource(R.string.nag_backup_restoring), style = NagTheme.type.body, color = colors.textSecondary)
        }
        is RestoreState.Done -> {
            val report = state.report
            DialogCard(stringResource(R.string.nag_backup_done_title), Modifier.testTag("restore-dialog"), cardWidth = 460.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    report.restored.forEach { (part, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(partLabel(part)), style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                            Text(partCount(res, part, count, null), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                        }
                    }
                }
                if (report.missingTracks > 0) {
                    Text(
                        res.getQuantityString(R.plurals.nag_backup_missing, report.missingTracks, report.missingTracks),
                        style = NagTheme.type.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag("restore-missing"),
                    )
                }
                if (report.failed.isNotEmpty()) {
                    Text(
                        stringResource(R.string.nag_backup_failed_parts, report.failed.joinToString(", ") { res.getString(partLabel(it)) }),
                        style = NagTheme.type.labelSmall,
                        color = colors.danger,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_sc_close), onDismiss, primary = true, modifier = Modifier.testTag("restore-done"))
                }
            }
        }
    }
}

/** A part's name. */
internal fun partLabel(part: BackupPart): Int = when (part) {
    BackupPart.Settings -> R.string.nag_backup_part_settings
    BackupPart.Shortcuts -> R.string.nag_backup_part_shortcuts
    BackupPart.Playlists -> R.string.nag_backup_part_playlists
    BackupPart.SmartLists -> R.string.nag_backup_part_smart_lists
    BackupPart.Stats -> R.string.nag_backup_part_stats
    BackupPart.Lyrics -> R.string.nag_backup_part_lyrics
    BackupPart.FileSettings -> R.string.nag_backup_part_file_settings
    BackupPart.Covers -> R.string.nag_backup_part_covers
    BackupPart.Latency -> R.string.nag_backup_part_latency
    BackupPart.RadioSchedules -> R.string.nag_backup_part_radio_schedules
}

/** What [count] of [part] is: "214 settings", "5 lists · 312 tracks" ([tracks]: the playlists' tracks, when known). */
internal fun partCount(res: Resources, part: BackupPart, count: Int, tracks: Int?): String = when (part) {
    BackupPart.Settings -> res.getQuantityString(R.plurals.nag_backup_settings_count, count, count)
    BackupPart.Shortcuts -> res.getQuantityString(R.plurals.nag_backup_changes_count, count, count)
    BackupPart.Playlists -> res.getQuantityString(R.plurals.nag_backup_lists_count, count, count) +
        (tracks?.let { SEPARATOR + res.getQuantityString(R.plurals.nag_tracks_count, it, it) } ?: "")
    BackupPart.SmartLists -> res.getQuantityString(R.plurals.nag_backup_lists_count, count, count)
    BackupPart.Stats, BackupPart.Lyrics -> res.getQuantityString(R.plurals.nag_tracks_count, count, count)
    BackupPart.FileSettings -> res.getQuantityString(R.plurals.nag_backup_files_count, count, count)
    BackupPart.Covers -> res.getQuantityString(R.plurals.nag_music_albums, count, count)
    BackupPart.Latency -> res.getQuantityString(R.plurals.nag_backup_latency_count, count, count)
    BackupPart.RadioSchedules -> res.getQuantityString(R.plurals.nag_backup_schedules_count, count, count)
}

/** Why a file did not read as a backup, as said to the user. */
private fun readFailure(reason: BackupException.Reason): Int = when (reason) {
    BackupException.Reason.NotABackup -> R.string.nag_backup_not_backup
    BackupException.Reason.Newer -> R.string.nag_backup_newer
    BackupException.Reason.Damaged -> R.string.nag_backup_damaged
}

/** When a backup was made, as the language writes a date and a time. */
internal fun backupTime(at: Long, locale: Locale): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(at))

/** The day in a backup's file name: 2026-10-12. */
private fun fileDate(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(at))

private const val MIME_ZIP = "application/zip"

/** What the picker offers to restore from: the backup's ZIP, its manifest alone, or a file it does not know the type of. */
private val OPEN_TYPES = arrayOf(MIME_ZIP, "application/json", "application/octet-stream")

/** The separator of a part's counts. */
private const val SEPARATOR = " · "
