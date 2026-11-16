/*
 * Nağmedar 1.5.0: after Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/PairingScreen.kt), the owner's own app;
 * drawn again with Nağmedar's parts.
 */
package app.nagmedar.ui.walkman

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.walkman.model.RemovableVolume
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.VolumeBinding
import app.nagmedar.walkman.model.VolumeId
import app.nagmedar.walkman.model.VolumeKind
import app.nagmedar.walkman.model.VolumeRole
import app.nagmedar.walkman.model.VolumeStatus

/**
 * The cards: the tablet's card the music is read from (only read) and the Walkman's card it goes to, each with its
 * state, its identity, its folder and what to do; the cards put in. While a scan or a copy runs they cannot be changed.
 */
@Composable
internal fun WalkmanCards(viewModel: WalkmanViewModel, onPickFolder: (Intent) -> Unit) {
    val state by viewModel.state.collectAsState()
    val snapshot by viewModel.volumes.collectAsState()
    val mounted by viewModel.mountedVolumes.collectAsState()
    val targetInfo by viewModel.targetInfo.collectAsState()
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val current = state
    val busy = current.isBusy
    val wide = currentWidthClass() != WidthClass.Compact
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.pagePadding).testTag("walkman-cards"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (snapshot.pair != null) R.string.nag_walkman_cards_ready else R.string.nag_walkman_cards_choose),
                style = NagTheme.type.bodyStrong,
                color = NagTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            NagButton(
                stringResource(R.string.nag_walkman_action_scan),
                viewModel::scan,
                icon = NagIcons.Resume,
                primary = true,
                enabled = current.canScan(),
                modifier = Modifier.testTag("walkman-scan"),
            )
        }
        WalkmanMessage(viewModel)
        if (current is SyncState.Failed && current.summary == null) {
            val hint = if (current.recoverable) " " + stringResource(R.string.nag_walkman_failed_recoverable_hint) else ""
            WalkmanNote(StateTone.Error, res.errorText(current.error) + hint, action = stringResource(R.string.nag_walkman_action_dismiss) to viewModel::dismissResult)
        }
        if (current is SyncState.Scanning) ScanPanel(current.progress, viewModel::requestCancel)
        if (busy) WalkmanNote(StateTone.Info, stringResource(R.string.nag_walkman_cards_locked))
        val pick = { role: VolumeRole, hint: VolumeId?, expected: VolumeId? -> onPickFolder(viewModel.createPickerIntent(role, hint, expected)) }
        val source: @Composable (Modifier) -> Unit = { modifier ->
            VolumeCard(VolumeRole.SOURCE, snapshot.source, mounted, null, busy, { hint, expected -> pick(VolumeRole.SOURCE, hint, expected) }, { viewModel.unbind(VolumeRole.SOURCE) }, modifier)
        }
        val target: @Composable (Modifier) -> Unit = { modifier ->
            VolumeCard(VolumeRole.TARGET, snapshot.target, mounted, targetInfo, busy, { hint, expected -> pick(VolumeRole.TARGET, hint, expected) }, { viewModel.unbind(VolumeRole.TARGET) }, modifier)
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gapL)) {
                source(Modifier.weight(1f))
                target(Modifier.weight(1f))
            }
        } else {
            source(Modifier.fillMaxWidth())
            target(Modifier.fillMaxWidth())
        }
        DetectedVolumes(mounted, listOf(snapshot.source, snapshot.target))
    }
}

private fun VolumeStatus.bindingOrNull(): VolumeBinding? = when (this) {
    VolumeStatus.Unbound -> null
    is VolumeStatus.NotMounted -> binding
    is VolumeStatus.PermissionMissing -> binding
    is VolumeStatus.ReadOnly -> binding
    is VolumeStatus.Ready -> binding
    is VolumeStatus.Conflict -> binding
}

private fun VolumeStatus.tone(): StateTone = when (this) {
    VolumeStatus.Unbound -> StateTone.Neutral
    is VolumeStatus.NotMounted, is VolumeStatus.PermissionMissing -> StateTone.Warning
    is VolumeStatus.ReadOnly, is VolumeStatus.Conflict -> StateTone.Error
    is VolumeStatus.Ready -> StateTone.Success
}

private fun VolumeStatus.label(): Int = when (this) {
    VolumeStatus.Unbound -> R.string.nag_walkman_status_unbound
    is VolumeStatus.NotMounted -> R.string.nag_walkman_status_not_mounted
    is VolumeStatus.PermissionMissing -> R.string.nag_walkman_status_permission_missing
    is VolumeStatus.ReadOnly -> R.string.nag_walkman_status_read_only
    is VolumeStatus.Ready -> R.string.nag_walkman_status_ready
    is VolumeStatus.Conflict -> R.string.nag_walkman_status_conflict
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VolumeCard(
    role: VolumeRole,
    status: VolumeStatus,
    mounted: List<RemovableVolume>,
    targetInfo: TargetInfo?,
    busy: Boolean,
    onPick: (hint: VolumeId?, expected: VolumeId?) -> Unit,
    onUnbind: () -> Unit,
    modifier: Modifier,
) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val binding = status.bindingOrNull()
    val volume = binding?.let { b -> mounted.firstOrNull { it.id == b.volume } }
    val isTarget = role == VolumeRole.TARGET
    WalkmanSection(modifier.testTag(if (isTarget) "walkman-target" else "walkman-source")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isTarget) NagIcons.Headphones else NagIcons.ExternalDrive, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(NagTheme.dimens.gapM))
            Column(Modifier.weight(1f)) {
                Text(res.roleName(role), style = NagTheme.type.title, color = colors.text, maxLines = 1)
                Text(
                    stringResource(if (isTarget) R.string.nag_walkman_role_target_hint else R.string.nag_walkman_role_source_hint),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(NagTheme.dimens.gapS))
            ToneBadge(stringResource(status.label()), status.tone())
        }
        if (binding != null) {
            val info = targetInfo?.takeIf { isTarget && it.volume == binding.volume }
            Column {
                WalkmanFact(stringResource(R.string.nag_walkman_card_name), volume?.label ?: binding.label)
                WalkmanFact(stringResource(R.string.nag_walkman_card_uuid), binding.volume.toString(), mono = true)
                WalkmanFact(stringResource(R.string.nag_walkman_card_folder), binding.treeDocId, mono = true)
                if (volume != null) {
                    WalkmanFact(
                        stringResource(R.string.nag_walkman_card_reader),
                        stringResource(
                            when (volume.kind) {
                                VolumeKind.SD_CARD -> R.string.nag_walkman_kind_sd
                                VolumeKind.USB -> R.string.nag_walkman_kind_usb
                                VolumeKind.UNKNOWN -> R.string.nag_walkman_kind_unknown
                            },
                        ),
                    )
                    if (isTarget) {
                        WalkmanFact(
                            stringResource(R.string.nag_walkman_card_access),
                            stringResource(if (volume.isWritable) R.string.nag_walkman_access_rw else R.string.nag_walkman_access_ro),
                            valueColor = if (volume.isWritable) null else colors.danger,
                        )
                    }
                }
                if (isTarget) {
                    WalkmanFact(stringResource(R.string.nag_walkman_card_filesystem), info?.let { res.fileSystemLabel(it.fileSystem) } ?: DASH)
                    WalkmanFact(stringResource(R.string.nag_walkman_card_free), info?.freeBytes?.let { formatBytes(it) } ?: DASH)
                }
            }
            if (isTarget && info == null) {
                Text(stringResource(R.string.nag_walkman_card_fs_after_scan), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            }
        }
        when (status) {
            VolumeStatus.Unbound -> WalkmanNote(StateTone.Info, stringResource(R.string.nag_walkman_hint_unbound))
            is VolumeStatus.NotMounted -> WalkmanNote(StateTone.Warning, stringResource(R.string.nag_walkman_hint_not_mounted, status.binding.volume.toString()))
            is VolumeStatus.PermissionMissing -> WalkmanNote(StateTone.Warning, stringResource(R.string.nag_walkman_hint_permission_missing))
            is VolumeStatus.Conflict -> WalkmanNote(StateTone.Error, stringResource(R.string.nag_walkman_hint_conflict))
            is VolumeStatus.ReadOnly -> WalkmanNote(StateTone.Error, stringResource(R.string.nag_walkman_hint_read_only))
            is VolumeStatus.Ready -> Unit
        }
        // on a phone's narrow card the buttons go on to a second line rather than being squeezed
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS), verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
            if (status is VolumeStatus.PermissionMissing) {
                NagButton(
                    stringResource(R.string.nag_walkman_action_grant_again),
                    { onPick(status.binding.volume, status.binding.volume) },
                    icon = NagIcons.Folder,
                    primary = true,
                    enabled = !busy,
                )
            }
            NagButton(
                stringResource(R.string.nag_walkman_action_select_folder),
                { onPick(binding?.volume, null) },
                icon = NagIcons.Folder,
                primary = binding == null,
                enabled = !busy,
                modifier = Modifier.testTag(if (isTarget) "walkman-pick-target" else "walkman-pick-source"),
            )
            if (binding != null) {
                NagButton(stringResource(R.string.nag_walkman_action_remove), onUnbind, icon = NagIcons.Close, enabled = !busy)
            }
        }
    }
}

/** The cards put in now, which of them is which. */
@Composable
private fun DetectedVolumes(mounted: List<RemovableVolume>, statuses: List<VolumeStatus>) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val bound = statuses.mapNotNull { it.bindingOrNull() }
    WalkmanSection(Modifier.fillMaxWidth().testTag("walkman-detected")) {
        Text(stringResource(R.string.nag_walkman_detected_title), style = NagTheme.type.title, color = colors.text)
        if (mounted.isEmpty()) {
            Text(stringResource(R.string.nag_walkman_detected_none), style = NagTheme.type.body, color = colors.textSecondary)
        }
        for (volume in mounted) {
            val role = bound.firstOrNull { it.volume == volume.id }?.role
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (volume.kind == VolumeKind.USB) NagIcons.ExternalDrive else NagIcons.Device, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(NagTheme.dimens.gapM))
                Column(Modifier.weight(1f)) {
                    Text(volume.label, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(volume.id.toString(), style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
                }
                if (role != null) Text(res.roleName(role), style = NagTheme.type.labelSmall, color = colors.accentText, maxLines = 1)
            }
        }
    }
}
