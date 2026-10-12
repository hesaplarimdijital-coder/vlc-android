package app.nagmedar.ui.player.refresh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.frameRateLabel
import app.nagmedar.ui.library.refreshRateLabel
import app.nagmedar.ui.library.refreshRateNumber
import app.nagmedar.ui.library.refreshRatesLabel
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.player.panel.decimal
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.PlexMono
import java.util.Locale

/**
 * The sync tab's "Refresh rate matching" (S-612), after the mockups' "Panel · Sync": the film's
 * frame rate, the screen's refresh rate and which screen it is, how often a frame repeats there
 * (whether the rate is a whole multiple of the film's); whether the screen follows the film
 * (always, if seamless, off) and what that does on this screen.
 */
@Composable
fun RefreshSection(status: RefreshStatus, match: RefreshMatch, onMatch: (RefreshMatch) -> Unit) {
    val locale = currentLocale()
    val cadence = status.cadence
    PanelSection(spacing = 8.dp) {
        SectionTitle(stringResource(R.string.nag_refresh_title)) {
            when (cadence) {
                null -> Unit
                is Cadence.Even -> ToneBadge(stringResource(R.string.nag_refresh_exact), NagTheme.colors.success)
                else -> ToneBadge(stringResource(R.string.nag_refresh_not_exact), NagTheme.colors.warning)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FactRow(stringResource(R.string.nag_refresh_content), status.fps.takeIf { it > 0 }?.let { frameRateLabel(it, locale) }, mono = true, tag = "refresh-content")
            FactRow(stringResource(R.string.nag_refresh_screen), screenLabel(status), mono = true, tag = "refresh-screen")
            FactRow(cadenceLabel(cadence), cadenceText(cadence), mono = false, tag = "refresh-cadence")
        }
        SegmentedChoice(
            options = RefreshMatch.entries.map { it to stringResource(it.label) },
            selected = match,
            onSelect = onMatch,
            modifier = Modifier.fillMaxWidth().testTag("refresh-match"),
            fill = true,
        )
        HintCard(refreshNotes(status, match, locale))
    }
}

/** What the choice does here, and what keeps the screen from it: no rate of its that takes the film, Android's own setting. */
@Composable
private fun refreshNotes(status: RefreshStatus, match: RefreshMatch, locale: Locale): List<String> {
    val notes = mutableListOf(
        when (match) {
            RefreshMatch.Always -> if (status.fps > 0) {
                stringResource(R.string.nag_refresh_always_hint, refreshRateNumber(status.fps, locale))
            } else {
                stringResource(R.string.nag_refresh_always_hint_any)
            }
            RefreshMatch.Seamless -> stringResource(R.string.nag_refresh_seamless_hint)
            RefreshMatch.Off -> stringResource(R.string.nag_refresh_off_hint)
        },
    )
    if (match != RefreshMatch.Off) {
        if (!status.hasEvenRate) notes += stringResource(R.string.nag_refresh_no_multiple, refreshRatesLabel(status.rates, locale))
        when {
            status.system == SystemMatch.Never -> notes += stringResource(R.string.nag_refresh_system_never)
            status.system == SystemMatch.Seamless && match == RefreshMatch.Always -> notes += stringResource(R.string.nag_refresh_system_seamless)
        }
    }
    return notes
}

/** "120 Hz · tablet panel", "23,976 Hz · LG TV": the screen's rate now and which screen it is; null before it is read. */
@Composable
fun screenLabel(status: RefreshStatus): String? {
    if (!(status.hz > 0)) return null
    val name = when (status.screen) {
        ScreenKind.Phone -> stringResource(R.string.nag_refresh_phone)
        ScreenKind.Tablet -> stringResource(R.string.nag_refresh_tablet)
        ScreenKind.External -> status.screenName?.takeIf { it.isNotBlank() }
    }
    return listOfNotNull(refreshRateLabel(status.hz, currentLocale()), name).joinToString(" · ")
}

/** "Repeated frames", or "Dropped frames" where the screen is slower than the film. */
@Composable
fun cadenceLabel(cadence: Cadence?): String {
    val dropped = when (cadence) {
        is Cadence.Drift -> cadence.dropped
        is Cadence.Uneven -> cadence.dropped
        else -> false
    }
    return stringResource(if (dropped) R.string.nag_refresh_drops else R.string.nag_refresh_repeats)
}

/** "≈ 8 sn'de bir · göze zor çarpar", "saniyede ≈ 12 · kaydırmalarda takılır", "yok · …"; null while the rates are unknown. */
@Composable
fun cadenceText(cadence: Cadence?): String? {
    val locale = currentLocale()
    return when (cadence) {
        null -> null
        is Cadence.Even -> stringResource(R.string.nag_refresh_none)
        is Cadence.Drift -> {
            val often = if (cadence.seconds >= MINUTE_S) {
                stringResource(R.string.nag_refresh_every_minutes, approximately(cadence.seconds / MINUTE_S, locale))
            } else {
                stringResource(R.string.nag_refresh_every_seconds, approximately(cadence.seconds, locale))
            }
            often + " · " + stringResource(if (cadence.seconds >= NOTICEABLE_S) R.string.nag_refresh_subtle else R.string.nag_refresh_visible)
        }
        is Cadence.Uneven -> stringResource(R.string.nag_refresh_per_second, approximately(cadence.perSecond, locale)) + " · " +
            stringResource(if (cadence.dropped) R.string.nag_refresh_choppy else R.string.nag_refresh_stutter)
    }
}

/** A frame out of step once in this many seconds or more is hard to notice. */
private const val NOTICEABLE_S = 5.0

private const val MINUTE_S = 60.0

/** "8", "12", "1,5": a whole number, with a decimal below two. */
private fun approximately(value: Double, locale: Locale): String = decimal(value, if (value < 2) 1 else 0, locale)

/** A fact of the section: its name, its value ("—" before it is known). */
@Composable
private fun FactRow(label: String, value: String?, mono: Boolean, tag: String) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, modifier = Modifier.width(96.dp))
        Text(
            value ?: "—",
            style = if (mono) NagTheme.type.labelSmall.copy(fontFamily = PlexMono) else NagTheme.type.labelSmall,
            color = if (value != null) colors.text else colors.textTertiary,
            modifier = Modifier.weight(1f).testTag(tag),
        )
    }
}

/** "whole multiple" in green, "not a whole multiple" in amber. */
@Composable
private fun ToneBadge(text: String, tone: Color) {
    Text(
        text,
        style = NagTheme.type.labelSmall,
        color = tone,
        maxLines = 1,
        modifier = Modifier
            .background(tone.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .testTag("refresh-badge"),
    )
}

/** What the choice does, on a raised ground with a screen's icon, as the mockup has it. */
@Composable
private fun HintCard(lines: List<String>) {
    val colors = NagTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("refresh-hint"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(NagIcons.Display, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { Text(it, style = NagTheme.type.labelSmall, color = colors.textSecondary) }
        }
    }
}
