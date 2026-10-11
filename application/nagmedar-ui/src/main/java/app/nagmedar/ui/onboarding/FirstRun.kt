package app.nagmedar.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.AlertBanner
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.shell.BrandMark
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.mixSrgb

/** The four steps of the first-run flow (S-309); every one of them can be skipped. */
enum class FirstRunStep(@StringRes val label: Int) {
    Access(R.string.nag_fr_access_label),
    Sources(R.string.nag_fr_sources_label),
    Audio(R.string.nag_fr_audio_label),
    Notifications(R.string.nag_fr_notif_label),
}

/** What the flow knows and what the user chose; the host refreshes the permission fields on resume. */
@Stable
class FirstRunState(start: FirstRunStep = FirstRunStep.Access) {
    var step by mutableStateOf(start)
    var mediaAccess by mutableStateOf(false)
    var allFiles by mutableStateOf(false)
    /** The media permission request came back refused: the flow offers folders instead. */
    var accessDenied by mutableStateOf(false)
    var deviceSelected by mutableStateOf(true)
    /** Removable drives by path; a drive is selected unless the user unticks it. */
    val drivesSelected = mutableStateMapOf<String, Boolean>()
    var networkShares by mutableStateOf(false)
    var onlineSources by mutableStateOf(true)
    var audioRoute by mutableStateOf<AudioRoute?>(null)
    var audioPlaying by mutableStateOf(false)
    var audioHeard by mutableStateOf<Boolean?>(null)
    var notifications by mutableStateOf(false)

    val isLast: Boolean get() = step == FirstRunStep.entries.last()

    fun next() {
        step = FirstRunStep.entries.getOrElse(step.ordinal + 1) { step }
    }

    /** False on the first step, where back leaves nothing to go back to. */
    fun back(): Boolean {
        if (step.ordinal == 0) return false
        step = FirstRunStep.entries[step.ordinal - 1]
        return true
    }

    /** The removable drives left ticked; the device's own storage is [deviceSelected]. */
    private fun chosenDrives(drives: List<StorageSource>) = drives.filter { it.removable && drivesSelected[it.path] != false }

    /** Paths the medialibrary should scan: this device when it can, and the drives left ticked. */
    fun scanPaths(devicePath: String, drives: List<StorageSource>): List<String> = buildList {
        if (deviceSelected && mediaAccess) add(devicePath)
        if (mediaAccess) chosenDrives(drives).forEach { add(it.path) }
    }

    /** Sources counted on the Sources step's button. */
    fun sourceCount(drives: List<StorageSource>, folders: Set<String>): Int =
        (if (deviceSelected && mediaAccess) 1 else 0) +
            (if (mediaAccess) chosenDrives(drives).size else 0) +
            folders.size + (if (networkShares) 1 else 0) + (if (onlineSources) 1 else 0)
}

/** What the flow asks of the app; the host wires these to permission requests and pickers. */
class FirstRunActions(
    val requestMediaAccess: () -> Unit,
    val requestAllFiles: () -> Unit,
    val openAppSettings: () -> Unit,
    val pickFolder: () -> Unit,
    val removeFolder: (String) -> Unit,
    val playTestSound: () -> Unit,
    val requestNotifications: () -> Unit,
    /** Leaving the Sources step: start the scan on what was chosen. */
    val sourcesChosen: () -> Unit,
    val finish: () -> Unit,
)

/**
 * The first-run flow: four steps under a progress line, each with "Atla". Full screen on a
 * phone; a centred column on tablets and DeX windows.
 */
@Composable
fun FirstRunFlow(
    state: FirstRunState,
    drives: List<StorageSource>,
    folders: List<Pair<String, String>>,
    actions: FirstRunActions,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val folderUris = remember(folders) { folders.map { it.first }.toSet() }
    val advance = {
        when {
            state.isLast -> actions.finish()
            state.step == FirstRunStep.Sources -> {
                actions.sourcesChosen()
                state.next()
            }
            else -> state.next()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier
                .widthIn(max = if (wide) 560.dp else maxWidth)
                .fillMaxSize()
                .padding(top = if (wide) 32.dp else 0.dp, bottom = if (wide) 32.dp else 0.dp),
        ) {
            FlowTopBar(state, onSkip = advance)
            val motion = NagTheme.motion
            val slide = with(LocalDensity.current) { NagMotion.STEP_SLIDE_DP.dp.roundToPx() }
            // S-203: the next step comes in from the end, the previous one from the start
            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = { motion.step(forward = targetState.ordinal > initialState.ordinal, slidePx = slide) },
                label = "step",
            ) { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    StepHeader(step)
                    when (step) {
                        FirstRunStep.Access -> AccessStep(state, folders, actions)
                        FirstRunStep.Sources -> SourcesStep(state, drives, folders)
                        FirstRunStep.Audio -> AudioStep(state, actions)
                        FirstRunStep.Notifications -> NotificationStep(state, actions)
                    }
                    if (step == FirstRunStep.Access || step == FirstRunStep.Sources) PrivacyNote()
                    Spacer(Modifier.height(16.dp))
                }
            }
            FlowFooter(
                label = when (state.step) {
                    FirstRunStep.Sources -> pluralStringResource(
                        R.plurals.nag_fr_continue_sources,
                        state.sourceCount(drives, folderUris),
                        state.sourceCount(drives, folderUris),
                    )
                    FirstRunStep.Notifications -> stringResource(R.string.nag_fr_finish)
                    else -> stringResource(R.string.nag_fr_continue)
                },
                hint = when (state.step) {
                    FirstRunStep.Sources -> stringResource(R.string.nag_fr_next_hint_sources)
                    FirstRunStep.Notifications -> stringResource(R.string.nag_fr_later_hint)
                    else -> null
                },
                onClick = advance,
            )
        }
    }
}

@Composable
private fun FlowTopBar(state: FirstRunState, onSkip: () -> Unit) {
    val colors = NagTheme.colors
    val total = FirstRunStep.entries.size
    val current = state.step.ordinal + 1
    val progressLabel = stringResource(R.string.nag_fr_progress, current, total)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(NagTheme.dimens.minTarget.coerceAtLeast(44.dp)), contentAlignment = Alignment.Center) {
            if (state.step.ordinal > 0) {
                NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_fr_back), { state.back() },
                    iconSize = NagTheme.dimens.iconLarge)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .semantics {
                    contentDescription = progressLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 1f..total.toFloat())
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total) { index ->
                val fill by animateColorAsState(
                    if (index < current) colors.accent else colors.borderStrong,
                    animationSpec = NagTheme.motion.standard(),
                    label = "segment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(fill, RoundedCornerShape(2.dp)),
                )
            }
        }
        TextButton(stringResource(R.string.nag_fr_skip), onSkip)
    }
}

@Composable
private fun StepHeader(step: FirstRunStep) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.chrome)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(Modifier.size(34.dp))
        }
        Text(
            stringResource(R.string.nag_fr_step, step.ordinal + 1, FirstRunStep.entries.size, stringResource(step.label)),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            stringResource(
                when (step) {
                    FirstRunStep.Access -> R.string.nag_fr_access_title
                    FirstRunStep.Sources -> R.string.nag_fr_sources_title
                    FirstRunStep.Audio -> R.string.nag_fr_audio_title
                    FirstRunStep.Notifications -> R.string.nag_fr_notif_title
                },
            ),
            style = NagTheme.type.display.copy(fontSize = 25.sp, lineHeight = 30.sp),
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                when (step) {
                    FirstRunStep.Access -> R.string.nag_fr_access_desc
                    FirstRunStep.Sources -> R.string.nag_fr_sources_desc
                    FirstRunStep.Audio -> R.string.nag_fr_audio_desc
                    FirstRunStep.Notifications -> R.string.nag_fr_notif_desc
                },
            ),
            style = NagTheme.type.body,
            color = colors.textSecondary,
        )
    }
}

// ---------------------------------------------------------------- steps

@Composable
private fun StepItems(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun AccessStep(state: FirstRunState, folders: List<Pair<String, String>>, actions: FirstRunActions) {
    StepItems {
        OptionCard(
            icon = NagIcons.Movies,
            title = stringResource(R.string.nag_fr_media_files),
            description = stringResource(R.string.nag_fr_media_files_desc),
            status = if (state.mediaAccess) stringResource(R.string.nag_fr_granted) else null,
            action = if (state.mediaAccess) null else StateAction(stringResource(R.string.nag_state_grant), actions.requestMediaAccess),
        )
        if (MediaAccess.canAskAllFiles) {
            OptionCard(
                icon = NagIcons.Folder,
                title = stringResource(R.string.nag_fr_all_files),
                description = stringResource(R.string.nag_fr_all_files_desc),
                status = if (state.allFiles) stringResource(R.string.nag_fr_granted) else null,
                action = if (state.allFiles) null else StateAction(stringResource(R.string.nag_fr_open_settings), actions.requestAllFiles),
            )
        }
        if (state.accessDenied && !state.mediaAccess) {
            AlertBanner(
                tone = StateTone.Warning,
                title = stringResource(R.string.nag_fr_denied_title),
                message = stringResource(R.string.nag_fr_denied_desc),
                actions = listOf(
                    StateAction(stringResource(R.string.nag_fr_open_settings), actions.openAppSettings),
                    StateAction(stringResource(R.string.nag_fr_pick_folder), actions.pickFolder),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!state.mediaAccess || folders.isNotEmpty()) FolderList(folders, actions)
    }
}

/** Folders chosen with the system picker, and the button that adds one. */
@Composable
private fun FolderList(folders: List<Pair<String, String>>, actions: FirstRunActions) {
    folders.forEach { (uri, name) ->
        OptionCard(
            icon = NagIcons.Folder,
            title = name,
            description = stringResource(R.string.nag_fr_source_folder),
            status = stringResource(R.string.nag_fr_granted),
            trailing = {
                NagIconButton(NagIcons.Close, stringResource(R.string.nag_fr_folder_remove, name), { actions.removeFolder(uri) })
            },
        )
    }
    NagButton(
        text = stringResource(R.string.nag_fr_pick_folder),
        onClick = actions.pickFolder,
        icon = NagIcons.FolderAdd,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SourcesStep(state: FirstRunState, drives: List<StorageSource>, folders: List<Pair<String, String>>) {
    val context = LocalContext.current
    StepItems {
        CheckCard(
            title = stringResource(R.string.nag_fr_source_device),
            description = stringResource(R.string.nag_fr_media_files_desc),
            checked = state.deviceSelected && state.mediaAccess,
            enabled = state.mediaAccess,
            status = if (state.mediaAccess) stringResource(R.string.nag_fr_granted) else stringResource(R.string.nag_fr_source_needs_access),
            statusTone = if (state.mediaAccess) StateTone.Success else StateTone.Warning,
            onCheckedChange = { state.deviceSelected = it },
        )
        drives.filter { it.removable }.forEach { drive ->
            CheckCard(
                title = drive.label,
                description = stringResource(R.string.nag_fr_source_drive_desc, android.text.format.Formatter.formatShortFileSize(context, drive.totalBytes)),
                checked = state.mediaAccess && state.drivesSelected[drive.path] != false,
                enabled = state.mediaAccess,
                status = if (state.mediaAccess) null else stringResource(R.string.nag_fr_source_needs_access),
                statusTone = StateTone.Warning,
                onCheckedChange = { state.drivesSelected[drive.path] = it },
            )
        }
        folders.forEach { (_, name) ->
            CheckCard(
                title = name,
                description = stringResource(R.string.nag_fr_source_folder),
                checked = true,
                enabled = false,
                onCheckedChange = {},
            )
        }
        CheckCard(
            title = stringResource(R.string.nag_fr_source_network),
            description = stringResource(R.string.nag_fr_source_network_desc),
            checked = state.networkShares,
            onCheckedChange = { state.networkShares = it },
        )
        CheckCard(
            title = stringResource(R.string.nag_fr_source_online),
            description = stringResource(R.string.nag_fr_source_online_desc),
            checked = state.onlineSources,
            onCheckedChange = { state.onlineSources = it },
        )
    }
}

@Composable
private fun AudioStep(state: FirstRunState, actions: FirstRunActions) {
    val colors = NagTheme.colors
    StepItems {
        NagButton(
            text = stringResource(R.string.nag_fr_audio_play),
            onClick = actions.playTestSound,
            icon = NagIcons.Volume,
            primary = state.audioRoute == null,
            enabled = !state.audioPlaying,
            modifier = Modifier.fillMaxWidth(),
        )
        val route = state.audioRoute
        if (route != null) {
            OptionCard(
                icon = NagIcons.Waveform,
                title = stringResource(R.string.nag_fr_audio_route, route.name),
                description = stringResource(R.string.nag_fr_audio_rate, route.sampleRate / 1000f),
            )
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_fr_audio_heard), false to stringResource(R.string.nag_fr_audio_not_heard)),
                selected = state.audioHeard,
                onSelect = { state.audioHeard = it },
            )
            when (state.audioHeard) {
                true -> Text(stringResource(R.string.nag_fr_audio_ok), style = NagTheme.type.body, color = toneColors(StateTone.Success).content)
                false -> Text(stringResource(R.string.nag_fr_audio_help), style = NagTheme.type.body, color = colors.textSecondary)
                null -> Unit
            }
        }
    }
}

@Composable
private fun NotificationStep(state: FirstRunState, actions: FirstRunActions) {
    StepItems {
        OptionCard(
            icon = NagIcons.Info,
            title = stringResource(R.string.nag_fr_notif_item),
            description = stringResource(R.string.nag_fr_notif_item_desc),
            status = if (state.notifications) stringResource(R.string.nag_fr_granted) else null,
            action = if (state.notifications) null else StateAction(stringResource(R.string.nag_state_grant), actions.requestNotifications),
        )
    }
}

@Composable
private fun PrivacyNote() {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(NagIcons.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(stringResource(R.string.nag_fr_privacy), style = NagTheme.type.labelSmall, color = colors.textSecondary)
    }
}

@Composable
private fun FlowFooter(label: String, hint: String?, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .focusRing(state, shape)
                .background(
                    when {
                        state.pressed -> mixSrgb(colors.accent, colors.onAccent, 0.76f)
                        state.hovered -> mixSrgb(colors.accent, colors.onAccent, 0.88f)
                        else -> colors.accent
                    },
                    shape,
                )
                .nagClickable(interaction, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = NagTheme.type.title.copy(fontWeight = FontWeight.SemiBold), color = colors.onAccent)
        }
        if (hint != null) {
            Text(hint, style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .heightIn(min = NagTheme.dimens.minTarget.coerceAtLeast(44.dp))
            .focusRing(state, shape)
            .background(if (state.hovered || state.pressed) colors.hover else colors.background, shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NagTheme.type.body, color = colors.textSecondary)
    }
}

/** A line of the Access and Notifications steps: what it is, its state, and the button that changes it. */
@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: String? = null,
    action: StateAction? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text)
            Text(description, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            if (status != null) StatusLine(status, StateTone.Success)
        }
        if (action != null) NagButton(action.label, action.onClick)
        trailing?.invoke()
    }
}

@Composable
private fun StatusLine(text: String, tone: StateTone) {
    val c = toneColors(tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(if (tone == StateTone.Success) NagIcons.Check else NagIcons.Warning, contentDescription = null, tint = c.content,
            modifier = Modifier.size(12.dp))
        Text(text, style = NagTheme.type.labelSmall, color = c.content)
    }
}

/** A selectable source of the Sources step; the whole card toggles it. */
@Composable
private fun CheckCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    status: String? = null,
    statusTone: StateTone = StateTone.Success,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(14.dp)
    val on = checked && enabled
    val stateText = stringResource(if (checked) R.string.nag_fr_selected else R.string.nag_fr_not_selected)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .focusRing(state, shape)
            .background(
                when {
                    on -> mixSrgb(colors.accent, colors.background, 0.09f)
                    state.hovered && enabled -> colors.hover
                    else -> colors.surface
                },
                shape,
            )
            .border(1.dp, if (on) mixSrgb(colors.accent, colors.background, 0.55f) else colors.borderStrong, shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox,
                onValueChange = onCheckedChange)
            .semantics { stateDescription = stateText }
            .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(if (on) colors.accent else colors.background, RoundedCornerShape(6.dp))
                    .border(if (on) 0.dp else 2.dp, if (enabled) colors.textTertiary else colors.borderStrong, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(NagIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NagTheme.type.bodyStrong, color = if (enabled) colors.text else colors.textSecondary)
            Text(description, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            if (status != null) StatusLine(status, statusTone)
        }
    }
}
