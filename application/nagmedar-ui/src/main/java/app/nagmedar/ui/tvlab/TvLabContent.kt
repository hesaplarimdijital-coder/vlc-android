package app.nagmedar.ui.tvlab

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.audioLine
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.frameRateLabel
import app.nagmedar.ui.library.rangeLabel
import app.nagmedar.ui.library.rangeLine
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.library.subtitleLine
import app.nagmedar.ui.library.videoCodecLabel
import app.nagmedar.ui.library.videoLine
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.PlexMono
import kotlinx.coroutines.delay
import java.util.Locale

/** The resources in the interface's language; read in composition, a change of language recomposes. */
@Composable
@ReadOnlyComposable
internal fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

/** How a finding's level shows. */
internal fun FindingLevel.tone(): StateTone = when (this) {
    FindingLevel.Critical -> StateTone.Error
    FindingLevel.Warning -> StateTone.Warning
    FindingLevel.Ok -> StateTone.Success
}

/**
 * The TV Lab (S-1201 … S-1204), as in the DeX mockup: the target device over the page; the film
 * with its drive and how long the analysis took; four figures (the result, whether the video is
 * kept, the bitrate, the profile's threshold); then the findings and the streams beside the
 * bitrate chart and the drive. One column on a phone. [notice] is a passing message ("Grafik
 * kaydedildi.").
 */
@Composable
fun TvLabContent(
    view: TvLabView,
    actions: TvLabActions,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val res = resources()
    val locale = currentLocale()
    val names = remember(res, locale) { LocalizedStreamNames(res, locale) }
    val report = remember(view.tech, view.profile, view.bitrate, names) { checkOf(view, names) }
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxSize().testTag("tvlab")) {
        val wide = maxWidth >= 840.dp
        val narrow = maxWidth < 600.dp
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.nag_nav_tv_lab), subtitle = stringResource(R.string.nag_tvlab_subtitle)) {
                if (!narrow) TargetPicker(view, actions, compact = false)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapL),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (narrow) TargetPicker(view, actions, compact = true, modifier = Modifier.fillMaxWidth())
                notice?.let { Notice(it, onDismissNotice) }
                when {
                    view.file == null && !view.available -> NoTools()
                    view.file == null -> Empty(view.profile, actions)
                    else -> Analysis(view, report, actions, wide = wide, narrow = narrow, locale = locale)
                }
            }
        }
    }
}

@Composable
private fun Analysis(view: TvLabView, report: CheckReport?, actions: TvLabActions, wide: Boolean, narrow: Boolean, locale: Locale) {
    FileCard(view, actions, narrow)
    Summary(view, report, narrow, locale)
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Findings(view, report, actions)
                view.tech?.let { Streams(it, locale, labelWidth = 110.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard(view, actions, narrow = false, locale = locale)
                DiskCard(view, actions, locale)
            }
        }
    } else {
        Findings(view, report, actions)
        ChartCard(view, actions, narrow = narrow, locale = locale)
        DiskCard(view, actions, locale)
        view.tech?.let { Streams(it, locale, labelWidth = if (narrow) 88.dp else 110.dp) }
    }
}

/** "Hedef [LG OLED B4 · webOS 24 · USB ▾] [Profili düzenle]"; on a phone the button joins the menu. */
@Composable
private fun TargetPicker(view: TvLabView, actions: TvLabActions, compact: Boolean, modifier: Modifier = Modifier) {
    val res = resources()
    val colors = NagTheme.colors
    val entries = {
        view.profiles.map { profile ->
            MenuEntry.Item(profile.label(), { actions.chooseProfile(profile.id) }, checked = profile.id == view.profile.id)
        } + listOfNotNull(
            MenuEntry.Separator,
            if (compact) MenuEntry.Item(res.getString(R.string.nag_tvlab_edit_profile), actions::editProfile) else null,
            MenuEntry.Item(res.getString(R.string.nag_tvlab_new_profile), actions::newProfile, icon = NagIcons.Plus),
            MenuEntry.Item(res.getString(R.string.nag_tvlab_import_profile), actions::importProfile, icon = NagIcons.Folder),
        )
    }
    Row(modifier.testTag("tvlab-target"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nag_tvlab_target), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        MenuButton(
            text = view.profile.label(),
            entries = entries,
            icon = if (view.profile.source == SourceLink.Local) NagIcons.Device else NagIcons.Series,
            modifier = if (compact) Modifier.weight(1f) else Modifier,
        )
        if (!compact) NagButton(stringResource(R.string.nag_tvlab_edit_profile), actions::editProfile)
    }
}

/** A passing message, gone after a few seconds or when closed. */
@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(text) {
        delay(NOTICE_MS)
        dismiss()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(toneColors(StateTone.Info).container, RoundedCornerShape(NagTheme.dimens.radiusControl))
            .padding(start = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("tvlab-notice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Info, contentDescription = null, tint = toneColors(StateTone.Info).icon, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = NagTheme.type.label, color = colors.text, modifier = Modifier.weight(1f))
        NagIconButton(NagIcons.Close, stringResource(R.string.nag_lab_close), onDismiss)
    }
}

@Composable
private fun NoTools() {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        StatePanel(
            tone = StateTone.Warning,
            icon = NagIcons.Warning,
            title = stringResource(R.string.nag_tvlab_no_tools),
            description = stringResource(R.string.nag_tvlab_no_tools_text),
        )
    }
}

@Composable
private fun Empty(profile: DeviceProfile, actions: TvLabActions) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        StatePanel(
            tone = StateTone.Info,
            icon = NagIcons.TvLab,
            title = stringResource(R.string.nag_tvlab_empty_title),
            description = stringResource(R.string.nag_tvlab_empty_text, profile.name),
            primary = StateAction(stringResource(R.string.nag_tvlab_choose_file), actions::chooseFile),
        )
    }
}

/** A card of the page. */
@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Column(
        modifier
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** A card's heading: "Bit hızı", "Disk". */
@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NagTheme.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
        color = NagTheme.colors.text,
        maxLines = 1,
        modifier = modifier.semantics { heading() },
    )
}

/** The film: its name, size, drive and how long the analysis took; another file, the report, a stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileCard(view: TvLabView, actions: TvLabActions, narrow: Boolean) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    val file = view.file ?: return
    val drive = view.drive
    val detail = listOfNotNull(
        file.sizeBytes.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
        drive?.let { d -> d.name?.let { name -> d.filesystem?.let { "$name ($it)" } ?: name } },
        when {
            view.running -> res.getString(R.string.nag_tvlab_analysing)
            view.elapsedMs != null -> res.getString(R.string.nag_tvlab_took, elapsedLabel(res, view.elapsedMs))
            else -> null
        },
    ).joinToString(" · ")
    val buttons: @Composable () -> Unit = {
        if (view.running) NagButton(stringResource(R.string.nag_tvlab_stop), actions::stop, icon = NagIcons.Close)
        NagButton(stringResource(R.string.nag_tvlab_report), actions::showReport, enabled = view.tech != null)
        NagButton(stringResource(R.string.nag_tvlab_other_file), actions::chooseFile)
    }
    Card(Modifier.fillMaxWidth().testTag("tvlab-file")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NagIcons.Movies, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = NagTheme.type.mono.copy(fontSize = 13.sp), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) Text(detail, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2)
            }
            if (!narrow) {
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            }
        }
        if (narrow) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
        }
    }
}

/** The four figures over the findings. */
@Composable
private fun Summary(view: TvLabView, report: CheckReport?, narrow: Boolean, locale: Locale) {
    val res = resources()
    val colors = NagTheme.colors
    val error = toneColors(StateTone.Error).content
    val warning = toneColors(StateTone.Warning).content
    val success = toneColors(StateTone.Success).content
    val result: @Composable (Modifier) -> Unit = { modifier ->
        val (value, color, detail) = when {
            view.probe is Step.Running -> Triple(res.getString(R.string.nag_tvlab_analysing), colors.textSecondary, "")
            report == null -> Triple(res.getString(R.string.nag_tvlab_result_unreadable), error, "")
            else -> Triple(
                resultCounts(res, report),
                when {
                    report.critical > 0 -> error
                    report.warnings > 0 -> warning
                    else -> success
                },
                if (view.bitrate?.complete != true && view.profile.limitKbps > 0) {
                    res.getString(R.string.nag_tvlab_result_partial)
                } else {
                    resultVerdict(res, report)
                },
            )
        }
        Figure(stringResource(R.string.nag_tvlab_result), value, detail, color, mono = false, modifier = modifier.testTag("tvlab-result"))
    }
    val video: @Composable (Modifier) -> Unit = { modifier ->
        val stream = view.tech?.video
        val (value, color) = when {
            report == null -> "–" to colors.textTertiary
            stream == null -> res.getString(R.string.nag_tvlab_no_video) to colors.textSecondary
            report.videoKept -> res.getString(R.string.nag_tvlab_video_kept) to success
            else -> res.getString(R.string.nag_tvlab_video_reencode) to error
        }
        val detail = stream?.let { listOf(videoCodecLabel(it) + if (it.profile.isNotEmpty()) " ${it.profile}" else "", rangeLabel(it, withEnhancement = true)).joinToString(" · ") }
        Figure(stringResource(R.string.nag_tvlab_video_stream), value, detail.orEmpty(), color, mono = false, modifier = modifier)
    }
    val bitrate: @Composable (Modifier) -> Unit = { modifier ->
        val analysis = view.bitrate?.takeIf { it.complete }
        val step = view.bitrateStep
        val value = when {
            analysis != null -> kbpsLabel(analysis.peakSecondKbps.toLong(), locale)
            step is Step.Running -> percentLabel(step.fraction ?: 0f, locale)
            else -> "–"
        }
        val over = analysis != null && view.profile.limitKbps > 0 && analysis.peakSecondKbps > view.profile.limitKbps
        val detail = analysis?.let { stringResource(R.string.nag_tvlab_bitrate_detail, kbpsLabel(it.averageKbps.toLong(), locale)) }
            ?: if (step is Step.Running) stringResource(R.string.nag_tvlab_reading_packets, percentLabel(step.fraction ?: 0f, locale)) else ""
        Figure(stringResource(R.string.nag_tvlab_bitrate), value, detail, if (over) warning else colors.text, mono = true, modifier = modifier.testTag("tvlab-bitrate"))
    }
    val threshold: @Composable (Modifier) -> Unit = { modifier ->
        val limit = view.profile.limitKbps
        Figure(
            stringResource(R.string.nag_tvlab_threshold),
            if (limit > 0) kbpsLabel(limit.toLong(), locale) else stringResource(R.string.nag_tvlab_no_limit),
            stringResource(R.string.nag_tvlab_threshold_detail, sourceLabel(res, view.profile.source)),
            colors.text,
            mono = limit > 0,
            modifier = modifier,
        )
    }
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                result(Modifier.weight(1f).fillMaxHeight())
                video(Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bitrate(Modifier.weight(1f).fillMaxHeight())
                threshold(Modifier.weight(1f).fillMaxHeight())
            }
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            result(Modifier.weight(1f).fillMaxHeight())
            video(Modifier.weight(1f).fillMaxHeight())
            bitrate(Modifier.weight(1f).fillMaxHeight())
            threshold(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** A figure: its caption in capitals, its value, a line under it. */
@Composable
private fun Figure(caption: String, value: String, detail: String, color: Color, mono: Boolean, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    val locale = currentLocale()
    Column(
        modifier
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            caption.uppercase(locale),
            style = NagTheme.type.caption.copy(letterSpacing = 0.06.em),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = NagTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = if (mono) PlexMono else NagTheme.type.title.fontFamily),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) Text(detail, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The findings, the critical first; while the streams are read, a line saying so. */
@Composable
private fun Findings(view: TvLabView, report: CheckReport?, actions: TvLabActions) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth().testTag("tvlab-findings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.nag_tvlab_findings).uppercase(currentLocale()),
            style = NagTheme.type.caption.copy(letterSpacing = 0.08.em),
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        val probe = view.probe
        when {
            probe is Step.Running -> Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nag_tvlab_reading_streams), style = NagTheme.type.label, color = colors.textSecondary)
                NagProgressBar(null, stringResource(R.string.nag_tvlab_reading_streams))
            }
            report == null -> Card(Modifier.fillMaxWidth()) {
                val stopped = (probe as? Step.Failed)?.reason == TvLabController.CANCELLED
                StatePanel(
                    tone = if (stopped) StateTone.Neutral else StateTone.Error,
                    icon = if (stopped) NagIcons.Close else NagIcons.ErrorCircle,
                    title = stringResource(if (stopped) R.string.nag_tvlab_stopped else R.string.nag_tvlab_unreadable_title),
                    description = if (stopped) null else stringResource(R.string.nag_tvlab_unreadable_text),
                    primary = StateAction(stringResource(R.string.nag_tvlab_other_file), actions::chooseFile),
                    dense = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            else -> report.findings.forEach { finding ->
                FindingCard(findingText(res, finding, view.profile, locale), finding.level)
            }
        }
    }
}

/** One finding: its mark and level, what, why, and what to do. */
@Composable
private fun FindingCard(text: FindingText, level: FindingLevel) {
    val res = resources()
    val colors = NagTheme.colors
    val tone = toneColors(level.tone())
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("tvlab-finding"),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .background(tone.iconContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (level == FindingLevel.Ok) {
                Icon(NagIcons.Check, contentDescription = null, tint = tone.content, modifier = Modifier.size(12.dp))
            } else {
                Text("!", style = NagTheme.type.caption.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold), color = tone.content)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.title,
                    style = NagTheme.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                ToneBadge(levelLabel(res, level), level.tone())
            }
            Text(text.why, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            Text(text.fix, style = NagTheme.type.labelSmall, color = colors.text)
        }
    }
}

/** What the film holds (S-1201): its container, video, dynamic range and Dolby Vision, every track. */
@Composable
private fun Streams(tech: MediaTech, locale: Locale, labelWidth: Dp) {
    val res = resources()
    val commentary = stringResource(R.string.nag_tech_commentary)
    val rows = remember(tech, res, locale) { streamRows(res, tech, locale, commentary) }
    Card(Modifier.fillMaxWidth().testTag("tvlab-streams")) {
        CardTitle(stringResource(R.string.nag_tvlab_streams))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 26.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, modifier = Modifier.width(labelWidth))
                Text(value, style = NagTheme.type.mono.copy(fontSize = 12.sp), color = NagTheme.colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The streams' rows: a label and the facts, as MediaInfo would list them. */
fun streamRows(res: Resources, tech: MediaTech, locale: Locale, commentary: String): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    rows += res.getString(R.string.nag_tvlab_container) to listOfNotNull(
        tech.container.takeIf { it.isNotEmpty() },
        tech.sizeBytes.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
        tech.durationMs.takeIf { it > 0 }?.let(::formatDuration),
        tech.chapters.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_tvlab_chapters, it, it) },
    ).joinToString(" · ")
    tech.video?.let { video ->
        rows += res.getString(R.string.nag_tech_video) to listOfNotNull(
            videoLine(video),
            video.fps.takeIf { it > 0 }?.let { frameRateLabel(it, locale) },
            video.bitDepth.takeIf { it > 0 }?.let { "$it bit" },
        ).joinToString(" · ")
        val light = listOfNotNull(
            video.maxCll.takeIf { it > 0 }?.let { "MaxCLL $it" },
            video.maxFall.takeIf { it > 0 }?.let { "MaxFALL $it" },
        )
        rows += res.getString(R.string.nag_tvlab_dynamic_range) to (listOf(rangeLine(video, locale)) + light).joinToString(" · ")
        if (video.dvProfile > 0) {
            val layers = listOfNotNull(BASE_LAYER, ENHANCEMENT_LAYER.takeIf { video.dvEnhancementLayer }, RPU.takeIf { video.dvRpu }).joinToString(" + ") +
                if (video.dvEnhancement.isNotEmpty()) " · ${video.dvEnhancement}" else ""
            rows += DOLBY_VISION to res.getString(R.string.nag_tvlab_dv_detail, video.dvProfile, video.dvLevel, layers)
        }
    }
    tech.audio.forEachIndexed { index, track ->
        val flags = listOfNotNull(
            track.sampleRate.takeIf { it > 0 }?.let { kiloHertz(it, locale) },
            track.bits.takeIf { it > 0 }?.let { "$it bit" },
            res.getString(R.string.nag_tvlab_default).takeIf { track.isDefault },
            res.getString(R.string.nag_tvlab_forced).takeIf { track.forced },
        )
        rows += res.getString(R.string.nag_tech_audio, index + 1) to (listOf(audioLine(track, locale, commentary)) + flags).joinToString(" · ")
    }
    if (tech.subtitles.isNotEmpty()) rows += res.getString(R.string.nag_tech_subtitles) to subtitleLine(tech.subtitles, locale)
    return rows
}

/** "48 kHz", "44,1 kHz". */
private fun kiloHertz(rate: Int, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(rate / 1000.0) + " kHz"

/** The bitrate chart (S-1202) with its legend and export; while the packets are read, how far. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(view: TvLabView, actions: TvLabActions, narrow: Boolean, locale: Locale) {
    val res = resources()
    val colors = NagTheme.colors
    val palette = chartPalette()
    val analysis = view.bitrate?.takeIf { it.complete }
    Card(Modifier.fillMaxWidth().testTag("tvlab-chart-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.nag_tvlab_bitrate))
            Spacer(Modifier.weight(1f))
            if (analysis != null) {
                MenuButton(
                    text = stringResource(R.string.nag_tvlab_export),
                    entries = {
                        listOf(
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_export_png), { actions.exportChart(ChartFormat.Png) }),
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_export_csv), { actions.exportChart(ChartFormat.Csv) }),
                        )
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(stringResource(R.string.nag_tvlab_legend_average), palette.average, dashed = false, thick = true)
            Legend(stringResource(R.string.nag_tvlab_legend_peak), palette.peak, dashed = false, thick = false)
            if (view.profile.limitKbps > 0) Legend(stringResource(R.string.nag_tvlab_threshold), palette.threshold, dashed = true, thick = true)
        }
        val step = view.bitrateStep
        when {
            analysis != null -> {
                val model = remember(analysis, view.profile) { ChartModel.of(analysis, view.profile.limitKbps, view.profile.source) }
                val measurer = rememberTextMeasurer()
                BitrateChart(
                    model,
                    chartDescription(res, analysis, model, locale),
                    measurer,
                    Modifier.fillMaxWidth().height(if (narrow) 170.dp else 200.dp),
                )
            }
            step is Step.Running -> {
                val label = stringResource(R.string.nag_tvlab_reading_packets, percentLabel(step.fraction ?: 0f, locale))
                NagProgressBar(step.fraction, label, Modifier.padding(top = 6.dp))
                Text(label, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            step is Step.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                val stopped = step.reason == TvLabController.CANCELLED
                Text(
                    stringResource(if (stopped) R.string.nag_tvlab_stopped else R.string.nag_tvlab_bitrate_failed),
                    style = NagTheme.type.label,
                    color = if (stopped) colors.textSecondary else toneColors(StateTone.Error).content,
                    modifier = Modifier.weight(1f),
                )
                if (view.tech != null) NagButton(stringResource(R.string.nag_tvlab_restart), actions::analyseBitrate)
            }
            else -> Text(stringResource(R.string.nag_tvlab_result_partial), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
}

/** What a screen reader hears of the chart. */
private fun chartDescription(res: Resources, analysis: BitrateAnalysis, model: ChartModel, locale: Locale): String {
    val over = if (model.threshold > 0) analysis.secondsOver(model.threshold) else 0
    return res.getString(
        R.string.nag_tvlab_chart_description,
        kbpsLabel(analysis.averageKbps.toLong(), locale),
        kbpsLabel(analysis.peakSecondKbps.toLong(), locale),
        tickLabel(model.highest, model.hours),
        res.getQuantityString(R.plurals.nag_tvlab_seconds, over, over),
    )
}

/** A line of the legend: its stroke and its name. */
@Composable
private fun Legend(text: String, color: Color, dashed: Boolean, thick: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 4.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                (if (thick) 2.dp else 1.5.dp).toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}

/** The drive (S-1204): its name, file system and room, its sequential read, set beside the film's peak. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiskCard(view: TvLabView, actions: TvLabActions, locale: Locale) {
    val res = resources()
    val colors = NagTheme.colors
    val drive = view.drive
    val step = view.diskStep
    Card(Modifier.fillMaxWidth().testTag("tvlab-disk")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.nag_tvlab_drive), Modifier.weight(1f))
            NagButton(
                stringResource(R.string.nag_tvlab_measure_again),
                actions::measureDiskAgain,
                enabled = view.file != null && !view.running,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (drive?.removable == true) NagIcons.ExternalDrive else NagIcons.Device,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                drive?.name ?: stringResource(R.string.nag_tvlab_unknown_drive),
                style = NagTheme.type.bodyStrong,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (drive != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                drive.filesystem?.let { ToneBadge(it, StateTone.Neutral) }
                ToneBadge(stringResource(if (drive.removable) R.string.nag_tvlab_external_drive else R.string.nag_tvlab_internal_storage), StateTone.Neutral)
                drive.usbVersion?.let { ToneBadge("USB $it", StateTone.Neutral) }
            }
            val room = listOfNotNull(
                drive.totalBytes?.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
                drive.freeBytes?.takeIf { it >= 0 }?.let { stringResource(R.string.nag_tvlab_free, sizeLabel(it, locale)) },
            ).joinToString(" · ")
            if (room.isNotEmpty()) Text(room, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        }
        val disk = view.disk
        when {
            step is Step.Running -> {
                val rate = step.rate?.let { megabytesLabel(res, it, locale) }
                val label = stringResource(R.string.nag_tvlab_measuring, rate ?: percentLabel(step.fraction ?: 0f, locale))
                NagProgressBar(step.fraction, label, Modifier.padding(top = 4.dp))
                Text(label, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            disk != null -> {
                Text(
                    megabytesLabel(res, disk.megabytesPerSecond, locale),
                    style = NagTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono),
                    color = colors.text,
                    modifier = Modifier.testTag("tvlab-disk-speed"),
                )
                Text(
                    stringResource(R.string.nag_tvlab_sequential, res.getString(R.string.nag_tvlab_ms, disk.firstMs.toInt())),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
                view.bitrate?.takeIf { it.complete && it.peakSecondKbps > 0 }?.let { analysis ->
                    val ratio = disk.kbps.toDouble() / analysis.peakSecondKbps
                    if (ratio < 1) {
                        Text(stringResource(R.string.nag_tvlab_too_slow), style = NagTheme.type.label, color = toneColors(StateTone.Warning).content)
                    } else {
                        val times = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (ratio < 10) 1 else 0 }.format(ratio)
                        Text(stringResource(R.string.nag_tvlab_times_peak, times), style = NagTheme.type.label, color = toneColors(StateTone.Success).content)
                    }
                }
                if (view.profile.source == SourceLink.Usb2 && disk.megabytesPerSecond > USB2_MEGABYTES) {
                    Text(stringResource(R.string.nag_tvlab_usb2_note), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                }
            }
            step is Step.Failed -> Text(
                stringResource(if (step.reason == TvLabController.CANCELLED) R.string.nag_tvlab_stopped else R.string.nag_tvlab_measure_failed),
                style = NagTheme.type.label,
                color = colors.textSecondary,
            )
            else -> Unit
        }
    }
}

/** How long a passing message shows. */
private const val NOTICE_MS = 4_000L

/** What a TV's USB 2.0 port reads in practice, MB/s. */
private const val USB2_MEGABYTES = 30.0

private const val BASE_LAYER = "BL"
private const val ENHANCEMENT_LAYER = "EL"
private const val RPU = "RPU"
private const val DOLBY_VISION = "Dolby Vision"
