package app.nagmedar.ui.player.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.subtitles.providers.ProviderReport
import app.nagmedar.ui.player.subtitles.providers.SubtitleResult
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelChip
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.decimal
import app.nagmedar.ui.theme.NagTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** "Search for subtitles" (S-706, S-707) in its own window, over the video. */
@Composable
fun OnlineSubtitlesDialog(
    state: OnlineSearchState,
    quota: OnlineQuota?,
    nowMs: Long,
    onName: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onByHash: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { OnlineSubtitlesSurface(state, quota, nowMs, onName, onLanguage, onByHash, onSearch, onDownload, onDismiss) }
    }
}

/**
 * The search itself: the film's name, the languages, the file's fingerprint; the subtitles found,
 * the fingerprint's matches first, each downloaded with a click; the day's quota. Enter searches,
 * Escape closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineSubtitlesSurface(
    state: OnlineSearchState,
    quota: OnlineQuota?,
    nowMs: Long,
    onName: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onByHash: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    val focusManager = LocalFocusManager.current
    val usable = state.problem != OnlineProblem.NoKey && state.problem != OnlineProblem.NoProvider && state.problem != OnlineProblem.NoFolder
    DialogCard(
        stringResource(R.string.nag_online_title),
        Modifier
            .testTag("online-dialog")
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        val label = stringResource(R.string.nag_online_name)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = state.name,
                onValueChange = onName,
                singleLine = true,
                enabled = usable,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (usable) 1f else PanelDisabledAlpha)
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = label }
                    .testTag("online-name")
                    .tabMovesFocus(focusManager)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            onSearch()
                            true
                        } else {
                            false
                        }
                    },
            )
            NagButton(stringResource(R.string.nag_online_search_short), onSearch, icon = NagIcons.Search, primary = true, enabled = usable && !state.searching, modifier = Modifier.testTag("online-search"))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.offered.forEach { code ->
                PanelChip(languageName(code, locale), selected = code in state.languages, enabled = usable) { onLanguage(code) }
            }
        }
        NagCheckbox(
            state.byHash,
            onByHash,
            stringResource(R.string.nag_online_by_hash),
            Modifier.alpha(if (usable) 1f else PanelDisabledAlpha).testTag("online-by-hash"),
            enabled = usable,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .testTag("online-results"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.results.orEmpty().forEach { subtitle ->
                ResultRow(subtitle, downloading = state.downloading == subtitle.key, enabled = state.downloading == null, locale = locale) { onDownload(subtitle) }
            }
        }
        if (state.reports.size > 1) ReportLines(state.reports)
        val status = when {
            state.problem != null -> problemText(state.problem, quota, locale)
            state.searching -> stringResource(R.string.nag_online_searching)
            state.results?.isEmpty() == true -> stringResource(R.string.nag_online_none)
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = NagTheme.type.labelSmall,
                color = if (state.problem != null) colors.warning else colors.textTertiary,
                modifier = Modifier.testTag("online-status"),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                quota?.let { stringResource(R.string.nag_online_quota, it.remainingAt(nowMs), it.allowed) }.orEmpty(),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f).testTag("online-quota"),
            )
            NagButton(stringResource(R.string.nag_online_close), onDismiss)
        }
    }
}

/**
 * A subtitle found: its language, its release; whether it was timed for this very file, how
 * often it was downloaded, its frame rate; the whole row downloads it.
 */
@Composable
private fun ResultRow(subtitle: SubtitleResult, downloading: Boolean, enabled: Boolean, locale: Locale, onDownload: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    val release = subtitle.release.ifBlank { subtitle.fileName }
    val details = listOfNotNull(
        stringResource(R.string.nag_online_hash_match).takeIf { subtitle.hashMatch },
        pluralStringResource(R.plurals.nag_online_downloads, subtitle.downloads.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), NumberFormat.getIntegerInstance(locale).format(subtitle.downloads)),
        subtitle.fps?.let { stringResource(R.string.nag_online_fps, decimal(it, FPS_DIGITS, locale)) },
        stringResource(R.string.nag_online_hearing_impaired).takeIf { subtitle.hearingImpaired },
        stringResource(R.string.nag_online_machine).takeIf { subtitle.machineTranslated },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onDownload, enabled = enabled, onClickLabel = stringResource(R.string.nag_online_download))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("online-result-${subtitle.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            subtitle.language.uppercase(Locale.ROOT),
            style = NagTheme.type.mono,
            color = if (subtitle.hashMatch) colors.success else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.width(36.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(release, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(if (downloading) R.string.nag_online_downloading else R.string.nag_online_download),
                style = NagTheme.type.label,
                color = if (enabled || downloading) colors.accentText else colors.textTertiary,
                maxLines = 1,
            )
            // which provider has it (S-707)
            Text(subtitle.sources, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp).testTag("online-source-${subtitle.key}"))
        }
    }
}

/** Each provider asked, and what it answered: how many it found, or what went wrong with it, while the others' results stay. */
@Composable
private fun ReportLines(reports: List<ProviderReport>) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth().testTag("online-reports"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        reports.forEach { report ->
            val problem = report.problem
            Text(
                if (problem != null) stringResource(R.string.nag_online_report_problem, report.name, stringResource(reportReason(problem))) else pluralStringResource(R.plurals.nag_online_report_found, report.found, report.name, report.found),
                style = NagTheme.type.labelSmall,
                color = if (problem != null) colors.warning else colors.textTertiary,
                modifier = Modifier.testTag("online-report-${report.provider}"),
            )
        }
    }
}

private fun reportReason(problem: OnlineProblem): Int = when (problem) {
    OnlineProblem.NoKey -> R.string.nag_online_r_no_key
    OnlineProblem.NoFolder -> R.string.nag_online_r_no_folder
    OnlineProblem.Offline -> R.string.nag_online_r_offline
    OnlineProblem.QuotaOver -> R.string.nag_online_r_quota
    OnlineProblem.Timeout -> R.string.nag_online_r_timeout
    else -> R.string.nag_online_r_server
}

private const val FPS_DIGITS = 3

/** What went wrong, and what to do. */
@Composable
internal fun problemText(problem: OnlineProblem, quota: OnlineQuota?, locale: Locale): String = when (problem) {
    OnlineProblem.NoKey -> stringResource(R.string.nag_online_no_key)
    OnlineProblem.Offline -> stringResource(R.string.nag_online_offline)
    OnlineProblem.QuotaOver -> quota?.resetAtMs?.let {
        stringResource(R.string.nag_online_quota_over_at, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(it)))
    } ?: stringResource(R.string.nag_online_quota_over)
    OnlineProblem.Server -> stringResource(R.string.nag_online_server)
    OnlineProblem.Unsaved -> stringResource(R.string.nag_online_unsaved)
    OnlineProblem.Timeout -> stringResource(R.string.nag_online_timeout)
    OnlineProblem.NoFolder -> stringResource(R.string.nag_online_no_folder)
    OnlineProblem.NoProvider -> stringResource(R.string.nag_online_no_provider)
}

/** "Türkçe", "İngilizce": a language's name in the interface's language. */
fun languageName(code: String, locale: Locale): String =
    Locale.forLanguageTag(code).getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }.ifEmpty { code }

/**
 * The languages offered for [locale]: its own, English and Turkish, as OpenSubtitles writes them
 * ("pt-BR", "zh-CN" where it tells them apart).
 */
fun onlineLanguages(locale: Locale): List<String> {
    val own = when (locale.language) {
        "pt" -> if (locale.country == "BR") "pt-BR" else "pt-PT"
        "zh" -> if (locale.country == "TW" || locale.country == "HK" || locale.script == "Hant") "zh-TW" else "zh-CN"
        "" -> null
        else -> locale.language
    }
    return listOfNotNull(own, "en", "tr").distinct()
}

/**
 * The subtitles' tab's row (S-706, S-707), after the mockups' player: "Search for subtitles", and
 * under it the day's downloads left, as OpenSubtitles last told them; or why nothing can be
 * searched (this build has no key, no provider is turned on, no folder is chosen).
 */
@Composable
fun OnlineSection(available: Boolean, quota: OnlineQuota?, nowMs: Long, reason: OnlineProblem = OnlineProblem.NoKey, onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("subtitles-online"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NagButton(
            stringResource(R.string.nag_online_search),
            onSearch,
            modifier = Modifier.fillMaxWidth().testTag("online-open"),
            icon = NagIcons.Search,
            enabled = available,
        )
        val caption = when {
            !available -> problemText(reason, quota, currentLocale())
            quota != null -> stringResource(R.string.nag_online_quota, quota.remainingAt(nowMs), quota.allowed)
            else -> null
        }
        if (caption != null) PanelCaption(caption, Modifier.testTag("online-caption"))
    }
}
