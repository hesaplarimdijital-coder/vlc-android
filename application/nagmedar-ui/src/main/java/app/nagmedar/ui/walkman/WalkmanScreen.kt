/*
 * Nağmedar 1.5.0: after Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/{WalkmanSyncRoot,PairingScreen,SectionCard}.kt),
 * the owner's own app; drawn again with Nağmedar's parts.
 */
package app.nagmedar.ui.walkman

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nagmedar.ui.R
import app.nagmedar.ui.components.BrowserCommands
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.walkman.model.ScanProgress
import app.nagmedar.walkman.model.SyncState
import kotlinx.coroutines.delay

/** The page's three views, as Walkman Sync's three screens. */
enum class WalkmanView { Cards, Plan, Transfer }

private enum class Phase { Idle, Scanning, Plan, Running, Finished, ScanFailed }

private fun SyncState.phase(): Phase = when (this) {
    is SyncState.Idle, is SyncState.AwaitingVolumes -> Phase.Idle
    is SyncState.Scanning -> Phase.Scanning
    is SyncState.PlanReady -> Phase.Plan
    is SyncState.Transferring, is SyncState.Paused -> Phase.Running
    is SyncState.Completed -> Phase.Finished
    is SyncState.Failed -> if (summary != null) Phase.Finished else Phase.ScanFailed
}

/**
 * 1.5.0, Araçlar › "Walkman'e aktar" (Walkman Sync): new music from the tablet's card copied onto a Sony Walkman's card,
 * only added, nothing on the Walkman's card overwritten, renamed or deleted. The cards are chosen once (Kartlar), the two
 * compared (Tara), what is new listed to choose from (Aktarılacaklar), then copied in the background with its progress
 * (Aktarım); the page follows the work from one view to the next.
 */
@Composable
fun WalkmanScreen(viewModel: WalkmanViewModel = viewModel(factory = WalkmanViewModel.Factory)) {
    val state by viewModel.state.collectAsState()
    var view by rememberSaveable { mutableStateOf(WalkmanView.Cards) }
    var followed by rememberSaveable { mutableStateOf<Phase?>(null) }
    val phase = state.phase()
    // the page follows the work (a list made: its list; a copy: its progress; nothing: the cards) when the work moves on;
    // not when the page is only drawn again (the tablet turned, a DeX window made again): the view chosen stays
    LaunchedEffect(phase) {
        val before = followed
        followed = phase
        if (before == phase || (before == null && phase == Phase.Idle)) return@LaunchedEffect
        when (phase) {
            Phase.Plan -> view = WalkmanView.Plan
            Phase.Running, Phase.Finished -> view = WalkmanView.Transfer
            Phase.Idle, Phase.ScanFailed -> view = WalkmanView.Cards
            Phase.Scanning -> Unit
        }
    }
    // the cards read again whenever the page is opened: one may have been put in meanwhile
    LaunchedEffect(Unit) { viewModel.refreshVolumes() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onPickResult(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    // the window's refresh (F5) reads the cards again, as the button does
    BrowserCommands(onUp = { false }, onRefresh = {
        viewModel.refreshVolumes()
        true
    })
    Column(Modifier.fillMaxSize().testTag("walkman-page")) {
        // the sentence under the title where it has room; a phone's narrow line would cut it to a word
        val subtitle = stringResource(R.string.nag_walkman_subtitle).takeIf { currentWidthClass() != WidthClass.Compact }
        PageHeader(title = stringResource(R.string.nag_nav_walkman), subtitle = subtitle) {
            NagButton(stringResource(R.string.nag_walkman_action_refresh), viewModel::refreshVolumes, icon = NagIcons.Repeat, modifier = Modifier.testTag("walkman-refresh"))
        }
        SegmentedChoice(
            listOf(
                WalkmanView.Cards to stringResource(R.string.nag_walkman_nav_cards),
                WalkmanView.Plan to stringResource(R.string.nag_walkman_nav_new_music),
                WalkmanView.Transfer to stringResource(R.string.nag_walkman_nav_transfer),
            ),
            view,
            { view = it },
            modifier = Modifier.padding(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapS).testTag("walkman-views"),
        )
        HorizontalRule()
        when (view) {
            WalkmanView.Cards -> WalkmanCards(viewModel, onPickFolder = { intent -> runCatching { picker.launch(intent) } })
            WalkmanView.Plan -> WalkmanPlan(viewModel)
            WalkmanView.Transfer -> WalkmanTransfer(viewModel)
        }
    }
    val cancelling by viewModel.cancelDialog.collectAsState()
    if (cancelling) CancelDialog(viewModel)
}

/** A card of the page: a surface edged as Nağmedar's are. */
@Composable
internal fun WalkmanSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Column(
        modifier.background(colors.surface, shape).border(1.dp, colors.border, shape).padding(NagTheme.dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS),
        content = content,
    )
}

/** A word of state in the page: its tone's sign and colour, an action at its end when there is one. */
@Composable
internal fun WalkmanNote(tone: StateTone, text: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    val c = toneColors(tone)
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Row(
        modifier.fillMaxWidth().background(c.container, shape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (tone) {
                StateTone.Success -> NagIcons.SuccessCircle
                StateTone.Error -> NagIcons.ErrorCircle
                StateTone.Warning -> NagIcons.Warning
                StateTone.Neutral, StateTone.Info -> NagIcons.Info
            },
            contentDescription = null,
            tint = c.content,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = NagTheme.type.label, color = c.content, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            NagButton(action.first, action.second)
        }
    }
}

/** A name and its value on one line, the value in a mono face for identifiers and paths. */
@Composable
internal fun WalkmanFact(label: String, value: String, mono: Boolean = false, maxLines: Int = 1, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = if (mono) NagTheme.type.mono else NagTheme.type.body,
            color = valueColor ?: colors.text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** What a choice of card came to, or a command not taken; it goes by itself after a few seconds. */
@Composable
internal fun WalkmanMessage(viewModel: WalkmanViewModel) {
    val message by viewModel.message.collectAsState()
    val res = LocalContext.current.resources
    val current = message ?: return
    LaunchedEffect(current) {
        delay(MESSAGE_MILLIS)
        viewModel.clearMessage()
    }
    val (tone, text) = when (current) {
        is UiMessage.Bound -> StateTone.Success to res.getString(R.string.nag_walkman_bound_ok, res.roleName(current.role), current.label)
        is UiMessage.Rejected -> StateTone.Error to res.bindRejectionText(current.reason)
        UiMessage.NotAccepted -> StateTone.Warning to res.getString(R.string.nag_walkman_msg_not_accepted)
        UiMessage.Failure -> StateTone.Error to res.getString(R.string.nag_walkman_msg_failure)
    }
    WalkmanNote(tone, text, Modifier.testTag("walkman-message"), action = stringResource(R.string.nag_walkman_action_dismiss) to viewModel::clearMessage)
}

/** The scan as it goes: its step, its progress, the file it is at; Cancel stops it. */
@Composable
internal fun ScanPanel(progress: ScanProgress, onCancel: () -> Unit) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val title = stringResource(R.string.nag_walkman_scanning_title)
    WalkmanSection(Modifier.fillMaxWidth().testTag("walkman-scanning")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.weight(1f))
            NagButton(stringResource(R.string.nag_walkman_action_cancel), onCancel)
        }
        NagProgressBar(progress.fraction, title)
        Text(res.scanPhaseText(progress), style = NagTheme.type.body, color = colors.textSecondary)
        progress.currentPath?.let { path ->
            Text(path, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

/** "Aktarım iptal edilsin mi?": what is copied stays, the file being written goes, the rest are not copied. */
@Composable
private fun CancelDialog(viewModel: WalkmanViewModel) {
    app.nagmedar.ui.components.NagDialog(onDismissRequest = viewModel::dismissCancelDialog) {
        app.nagmedar.ui.components.DialogFrame(viewModel::dismissCancelDialog) {
            app.nagmedar.ui.components.DialogCard(stringResource(R.string.nag_walkman_cancel_title), Modifier.testTag("walkman-cancel-dialog")) {
                Text(stringResource(R.string.nag_walkman_cancel_text), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_walkman_cancel_keep), viewModel::dismissCancelDialog)
                    NagButton(stringResource(R.string.nag_walkman_cancel_confirm), viewModel::confirmCancel, danger = true, modifier = Modifier.testTag("walkman-cancel-confirm"))
                }
            }
        }
    }
}

private const val MESSAGE_MILLIS = 8_000L
