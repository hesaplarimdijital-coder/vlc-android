package app.nagmedar.ui.podcast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.pickedFolderLabel
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.playback.PlaybackRules
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.transcribe.WhisperSettingsRows
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S-1103: what the downloads take: how many episodes, how many bytes, and what the storage rules would do about it now. */
@Immutable
data class StorageUsage(val count: Int, val bytes: Long, val plan: CleanupPlan)

/** The usage of the downloads as they are now. */
suspend fun PodcastCenter.storageUsage(context: Context): StorageUsage = withContext(Dispatchers.IO) {
    val downloaded = NagmedarDatabase.get(context.applicationContext).podcasts().episodesDownload(DownloadState.DONE)
    StorageUsage(downloaded.size, downloaded.sumOf { it.downloadBytes }, cleaner(context).plan(playingEpisodes()))
}

/**
 * S-1103, S-1104: Settings › Podcasts: how often and on what network the feeds are read, the notification of new
 * episodes, the storage the downloads may take (a limit, played episodes deleted after some days, a folder), the
 * seconds the skip buttons go, and the transcripts Whisper makes on the device: new downloads by themselves or not, the
 * model and where it runs. The choices are kept as they are made; the refresh job is set again when they change
 * it. The screenshot tests give [settings], [usage] and [notificationsBlocked]; the app reads them.
 */
@Composable
internal fun PodcastSection(
    settings: PodcastSettings? = null,
    usage: StorageUsage? = null,
    notificationsBlocked: Boolean? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PodcastCenter.settingsStore(context) }
    var current by remember { mutableStateOf(settings ?: store.read()) }
    var held by remember { mutableStateOf(usage) }
    var blocked by remember { mutableStateOf(notificationsBlocked ?: !PodcastNotifier(context).canPost()) }
    if (usage == null) {
        LaunchedEffect(current.storageLimitGb, current.deletePlayedAfterDays) { held = runCatching { PodcastCenter.storageUsage(context) }.getOrNull() }
    }
    fun change(next: PodcastSettings) {
        val before = current
        current = next
        if (settings != null) return
        store.write(next)
        if (before.refreshHours != next.refreshHours || before.refreshUnmeteredOnly != next.refreshUnmeteredOnly) PodcastCenter.schedule(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> blocked = !granted }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // kept for good (read and write); the one before lets go as far as nothing uses it, the episodes downloaded there included
            val before = current.downloadFolder
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            change(current.copy(downloadFolder = uri.toString()))
            if (before.isNotEmpty() && before != uri.toString()) FolderGrants.release(context, before)
        }
    }
    PodcastSectionContent(
        settings = current,
        usage = held,
        notificationsBlocked = blocked,
        onChange = { next ->
            if (next.notifications && !current.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blocked) {
                runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            change(next)
        },
        onPickFolder = { runCatching { folder.launch(null) } },
        onDefaultFolder = {
            val before = current.downloadFolder
            change(current.copy(downloadFolder = ""))
            if (before.isNotEmpty()) FolderGrants.release(context, before)
        },
        onClean = {
            scope.launch {
                runCatching { PodcastCenter.cleaner(context).clean(PodcastCenter.playingEpisodes()) }
                held = runCatching { PodcastCenter.storageUsage(context) }.getOrNull()
            }
        },
        transcripts = { WhisperSettingsRows() },
    )
}

/** The section's lines, over the values it is given: what the app and the screenshot tests both draw. */
@Composable
internal fun PodcastSectionContent(
    settings: PodcastSettings,
    usage: StorageUsage?,
    notificationsBlocked: Boolean,
    onChange: (PodcastSettings) -> Unit,
    onPickFolder: () -> Unit,
    onDefaultFolder: () -> Unit,
    onClean: () -> Unit,
    /** The model and its acceleration ([WhisperSettingsRows]), under the transcripts' caption; nothing in the screenshots. */
    transcripts: @Composable () -> Unit = {},
) {
    val res = LocalContext.current.resources
    val locale = res.locale()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    SettingsSection(stringResource(R.string.nag_pod_settings)) {
        Text(stringResource(R.string.nag_pod_settings_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        SettingRow(stringResource(R.string.nag_pod_settings_refresh), subtitle = stringResource(R.string.nag_pod_settings_refresh_hint)) {
            MenuButton(
                res.getQuantityString(R.plurals.nag_pod_settings_every_hours, settings.refreshHours, settings.refreshHours),
                entries = {
                    PodcastSettings.REFRESH_HOURS.map { hours ->
                        MenuEntry.Item(
                            res.getQuantityString(R.plurals.nag_pod_settings_every_hours, hours, hours),
                            { onChange(settings.copy(refreshHours = hours)) },
                            checked = hours == settings.refreshHours,
                        )
                    }
                },
                modifier = Modifier.testTag("podcast-settings-refresh"),
            )
        }
        SettingRow(stringResource(R.string.nag_pod_settings_wifi), subtitle = stringResource(R.string.nag_pod_settings_wifi_hint)) {
            NagSwitch(
                checked = settings.refreshUnmeteredOnly,
                onCheckedChange = { onChange(settings.copy(refreshUnmeteredOnly = it)) },
                label = stringResource(if (settings.refreshUnmeteredOnly) R.string.nag_pod_set_on else R.string.nag_pod_set_off),
                modifier = Modifier.testTag("podcast-settings-wifi"),
            )
        }
        SettingRow(
            stringResource(R.string.nag_pod_settings_notify),
            subtitle = stringResource(if (settings.notifications && notificationsBlocked) R.string.nag_pod_settings_notify_blocked else R.string.nag_pod_settings_notify_hint),
        ) {
            NagSwitch(
                checked = settings.notifications,
                onCheckedChange = { onChange(settings.copy(notifications = it)) },
                label = stringResource(if (settings.notifications) R.string.nag_pod_set_on else R.string.nag_pod_set_off),
                modifier = Modifier.testTag("podcast-settings-notify"),
            )
        }

        Spacer(Modifier.height(dimens.gapM))
        SectionCaption(stringResource(R.string.nag_pod_settings_downloads))
        SettingRow(stringResource(R.string.nag_pod_settings_limit), subtitle = stringResource(R.string.nag_pod_settings_limit_hint)) {
            MenuButton(
                gigabytes(res, settings.storageLimitGb),
                entries = {
                    PodcastSettings.STORAGE_GB.map { gb -> MenuEntry.Item(gigabytes(res, gb), { onChange(settings.copy(storageLimitGb = gb)) }, checked = gb == settings.storageLimitGb) }
                },
                modifier = Modifier.testTag("podcast-settings-limit"),
            )
        }
        SettingRow(stringResource(R.string.nag_pod_settings_played), subtitle = stringResource(R.string.nag_pod_settings_played_hint)) {
            MenuButton(
                playedAfter(res, settings.deletePlayedAfterDays),
                entries = {
                    PodcastSettings.PLAYED_DAYS.map { days ->
                        MenuEntry.Item(playedAfter(res, days), { onChange(settings.copy(deletePlayedAfterDays = days)) }, checked = days == settings.deletePlayedAfterDays)
                    }
                },
                modifier = Modifier.testTag("podcast-settings-played"),
            )
        }
        val label = pickedFolderLabel(settings.downloadFolder.ifEmpty { null }, stringResource(R.string.nag_pod_settings_folder_default))
        SettingRow(stringResource(R.string.nag_pod_settings_folder), subtitle = label) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                if (settings.downloadFolder.isNotEmpty()) NagButton(stringResource(R.string.nag_snap_default_folder), onDefaultFolder, modifier = Modifier.testTag("podcast-settings-folder-default"))
                NagButton(stringResource(R.string.nag_snap_change_folder), onPickFolder, modifier = Modifier.testTag("podcast-settings-folder"))
            }
        }
        if (usage != null) Usage(settings, usage, onClean, locale)

        Spacer(Modifier.height(dimens.gapM))
        SectionCaption(stringResource(R.string.nag_pod_settings_playback))
        SettingRow(stringResource(R.string.nag_pod_settings_skip_back), subtitle = stringResource(R.string.nag_pod_settings_skip_hint)) {
            MenuButton(
                stringResource(R.string.nag_pod_set_seconds, settings.skipBackSeconds),
                entries = {
                    PlaybackRules.BACK_SECONDS.map { s ->
                        MenuEntry.Item(res.getString(R.string.nag_pod_set_seconds, s), { onChange(settings.copy(skipBackSeconds = s)) }, checked = s == settings.skipBackSeconds)
                    }
                },
                modifier = Modifier.testTag("podcast-settings-skip-back"),
            )
        }
        SettingRow(stringResource(R.string.nag_pod_settings_skip_forward)) {
            MenuButton(
                stringResource(R.string.nag_pod_set_seconds, settings.skipForwardSeconds),
                entries = {
                    PlaybackRules.FORWARD_SECONDS.map { s ->
                        MenuEntry.Item(res.getString(R.string.nag_pod_set_seconds, s), { onChange(settings.copy(skipForwardSeconds = s)) }, checked = s == settings.skipForwardSeconds)
                    }
                },
                modifier = Modifier.testTag("podcast-settings-skip-forward"),
            )
        }

        Spacer(Modifier.height(dimens.gapM))
        SectionCaption(stringResource(R.string.nag_pod_settings_transcripts))
        Text(stringResource(R.string.nag_pod_settings_transcripts_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        SettingRow(stringResource(R.string.nag_pod_settings_auto_transcribe), subtitle = stringResource(R.string.nag_pod_settings_auto_transcribe_hint)) {
            NagSwitch(
                checked = settings.autoTranscribe,
                onCheckedChange = { onChange(settings.copy(autoTranscribe = it)) },
                label = stringResource(if (settings.autoTranscribe) R.string.nag_pod_set_on else R.string.nag_pod_set_off),
                modifier = Modifier.testTag("podcast-settings-auto-transcribe"),
            )
        }
        transcripts()
    }
}

/** The downloads' size against the limit, the warning when it is near, and the cleanup that would be done. */
@Composable
private fun Usage(settings: PodcastSettings, usage: StorageUsage, onClean: () -> Unit, locale: Locale) {
    val colors = NagTheme.colors
    val gap = NagTheme.dimens.gapS
    val plan = usage.plan
    val used = sizeLabel(usage.bytes, locale)
    val summary = when {
        usage.count == 0 -> stringResource(R.string.nag_pod_settings_usage_none)
        settings.storageLimitGb > 0 -> stringResource(R.string.nag_pod_settings_usage_of, used, sizeLabel(settings.storageLimitBytes, locale))
        else -> pluralStringResource(R.plurals.nag_pod_settings_usage, usage.count, used, usage.count)
    }
    Column(Modifier.padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(summary, style = NagTheme.type.body, color = colors.text, modifier = Modifier.testTag("podcast-settings-usage"))
        if (settings.storageLimitGb > 0) NagProgressBar((usage.bytes.toFloat() / settings.storageLimitBytes).coerceIn(0f, 1f), summary)
        if (plan.removals.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.nag_pod_settings_cleanup, plan.removals.size, plan.removals.size, sizeLabel(plan.freedBytes, locale)),
                style = NagTheme.type.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.testTag("podcast-settings-cleanup-text"),
            )
            NagButton(stringResource(R.string.nag_pod_settings_cleanup_now), onClean, modifier = Modifier.testTag("podcast-settings-cleanup"))
        }
        if (plan.nearLimit || plan.overLimit) {
            Text(
                stringResource(if (plan.overLimit) R.string.nag_pod_settings_limit_over else R.string.nag_pod_settings_limit_near),
                style = NagTheme.type.labelSmall,
                color = toneColors(StateTone.Warning).content,
                modifier = Modifier.testTag("podcast-settings-warning"),
            )
        }
    }
}

private fun gigabytes(res: Resources, gb: Int): String =
    if (gb == 0) res.getString(R.string.nag_pod_settings_no_limit) else res.getString(R.string.nag_pod_settings_gb, gb)

private fun playedAfter(res: Resources, days: Int): String =
    if (days == 0) res.getString(R.string.nag_pod_settings_keep_played) else res.getQuantityString(R.plurals.nag_pod_settings_after_days, days, days)
