package app.nagmedar.ui.library.metadata

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.data.FilmKind
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import org.videolan.tools.Notices
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** The words of a lookup's [outcome], said in a notice. */
fun outcomeText(context: Context, outcome: FilmOutcome): String = context.resources.getString(
    when (outcome) {
        FilmOutcome.Done -> R.string.nag_film_outcome_done
        FilmOutcome.NotFound -> R.string.nag_film_outcome_not_found
        FilmOutcome.Off -> R.string.nag_film_outcome_off
        FilmOutcome.NoKey -> R.string.nag_film_outcome_no_key
        FilmOutcome.Offline, FilmOutcome.WaitingForWifi -> R.string.nag_film_outcome_offline
        FilmOutcome.KeyRefused -> R.string.nag_film_outcome_refused
        FilmOutcome.Limited -> R.string.nag_film_outcome_limited
        FilmOutcome.Failed -> R.string.nag_film_outcome_failed
    },
)

/** Says in the app's notices how a lookup the user asked for ended; one that could not be made as an error. */
fun sayOutcome(context: Context, outcome: FilmOutcome) {
    val failed = outcome in setOf(FilmOutcome.Offline, FilmOutcome.WaitingForWifi, FilmOutcome.KeyRefused, FilmOutcome.Limited, FilmOutcome.Failed)
    Notices.show(context, outcomeText(context, outcome), error = failed)
}

/** What the film information can do for one film or series: refresh it, or search for the right one. */
class FilmActions(val refresh: () -> Unit, val fix: () -> Unit)

/**
 * "Bilgileri yenile" and "Yanlış film mi? Ara…" for [query], with the search dialog this opens
 * and the notice that says how a lookup ended; null when nothing names the film.
 */
@Composable
fun rememberFilmActions(query: FilmQuery?): FilmActions? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember(query?.key) { mutableStateOf(false) }
    if (query == null) return null
    if (searching) FilmSearchDialog(query) { searching = false }
    return FilmActions(
        refresh = {
            scope.launch {
                sayOutcome(context, FilmInfoCenter.refresh(context, query))
            }
        },
        fix = { searching = true },
    )
}

/**
 * The film information in a film's or a series' details: the poster beside the title and facts, the
 * ratings, the overview, the cast (the first six), refresh and search, and where it all comes from.
 * Without [info] it says there is none yet and why it may be.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmAbout(info: FilmInfo?, kind: String, actions: FilmActions?, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (info != null) {
            Row(verticalAlignment = Alignment.Top) {
                PosterThumb(info.posterPath, null, 84.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showTitle) {
                        Text(info.title, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    info.originalTitle?.let { Text(it, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    val facts = factsLine(info, genres = 4)
                    if (facts.isNotEmpty()) Text(facts, style = NagTheme.type.body, color = colors.textSecondary)
                    val ratings = ratingsLine(info)
                    if (ratings.isNotEmpty()) Text(ratings, style = NagTheme.type.mono, color = colors.text)
                }
            }
            info.overview?.let { Text(it, style = NagTheme.type.body, color = colors.textSecondary) }
            val cast = info.cast.take(6)
            if (cast.isNotEmpty()) {
                Text(
                    stringResource(R.string.nag_film_cast).uppercase(currentLocale()),
                    style = NagTheme.type.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.semantics { heading() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cast.forEach { member ->
                        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = listOf(member.name, member.character).filter { it.isNotEmpty() }.joinToString(", ") }) {
                            Text(member.name, style = NagTheme.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            if (member.character.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(member.character, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.nag_film_no_info), style = NagTheme.type.bodyStrong, color = colors.text)
            Text(noInfoReason(LocalContext.current), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        }
        if (actions != null && FilmInfoCenter.tmdbAvailable) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                NagButton(stringResource(R.string.nag_film_refresh), actions.refresh, icon = NagIcons.Resume, modifier = Modifier.testTag("film-refresh"))
                NagButton(
                    stringResource(if (kind == FilmKind.TV) R.string.nag_series_fix else R.string.nag_film_fix),
                    actions.fix,
                    icon = NagIcons.Search,
                    modifier = Modifier.testTag("film-fix"),
                )
            }
        }
        if (info != null) {
            Text(
                stringResource(if (FilmInfoCenter.omdbAvailable) R.string.nag_film_attribution else R.string.nag_film_attribution_tmdb),
                style = NagTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}

/** Why there is no film information: no key in this build, the setting off, or not found (yet). */
private fun noInfoReason(context: Context): String = context.resources.getString(
    when {
        !FilmInfoCenter.tmdbAvailable -> R.string.nag_film_outcome_no_key
        !FilmInfoCenter.enabled(context) -> R.string.nag_film_outcome_off
        else -> R.string.nag_film_no_info_desc
    },
)

/** An episode's own words from TMDB in its details: when it was first shown and what happens in it. */
@Composable
fun EpisodeAbout(meta: EpisodeMeta, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        airDateLabel(meta.airDate, locale)?.let { Text(stringResource(R.string.nag_episode_aired, it), style = NagTheme.type.labelSmall, color = colors.textTertiary) }
        meta.overview?.let { Text(it, style = NagTheme.type.body, color = colors.textSecondary) }
    }
}

/** "12 Mart 2019" from TMDB's "2019-03-12", in the interface's language. */
fun airDateLabel(date: String?, locale: Locale): String? {
    if (date.isNullOrBlank()) return null
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date) ?: return null
        DateFormat.getDateInstance(DateFormat.LONG, locale).format(parsed)
    }.getOrNull()
}

/**
 * "Yanlış film mi? Ara…": TMDB searched with the title (and year) as the user writes them, and the
 * one chosen kept for this film or series; a refresh keeps it too. Enter searches, Escape leaves.
 */
@Composable
fun FilmSearchDialog(query: FilmQuery, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { FilmSearchSurface(query, onDismiss) }
    }
}

@Composable
private fun FilmSearchSurface(query: FilmQuery, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = NagTheme.colors
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(query.title) }
    var year by remember { mutableStateOf(query.year?.toString().orEmpty()) }
    var hits by remember { mutableStateOf<List<TmdbHit>?>(null) }
    var problem by remember { mutableStateOf<FilmOutcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    val series = query.kind == FilmKind.TV
    val search = {
        if (text.isNotBlank() && !busy) {
            busy = true
            scope.launch {
                val (outcome, found) = FilmInfoCenter.search(context, query.kind, text.trim(), year.trim().toIntOrNull())
                problem = outcome.takeIf { it != FilmOutcome.Done }
                hits = found
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { search() }
    DialogCard(stringResource(if (series) R.string.nag_series_search_title else R.string.nag_film_search_title), Modifier.testTag("film-search"), cardWidth = 520.dp) {
        Text(stringResource(R.string.nag_film_search_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                NagTextField(text, { text = it }, stringResource(R.string.nag_film_search_field), onDone = { search() }, modifier = Modifier.testTag("film-search-field"))
            }
            if (!series) {
                Box(Modifier.width(88.dp)) {
                    NagTextField(year, { value -> year = value.filter { it.isDigit() }.take(4) }, stringResource(R.string.nag_column_year), onDone = { search() })
                }
            }
            NagButton(stringResource(R.string.nag_film_search_button), { search() }, primary = true, enabled = text.isNotBlank() && !busy)
        }
        val found = hits
        when {
            busy -> Text(stringResource(R.string.nag_film_searching), style = NagTheme.type.body, color = colors.textSecondary)
            problem != null -> Text(outcomeText(context, problem!!), style = NagTheme.type.body, color = colors.textSecondary)
            found != null && found.isEmpty() -> Text(stringResource(R.string.nag_film_search_none), style = NagTheme.type.body, color = colors.textSecondary)
            found != null -> Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                found.forEach { hit ->
                    HitRow(hit) {
                        scope.launch {
                            val outcome = FilmInfoCenter.choose(context, query, hit)
                            if (outcome == FilmOutcome.Done) {
                                Notices.show(context, context.getString(R.string.nag_film_chosen, hit.title))
                                onDismiss()
                            } else {
                                sayOutcome(context, outcome)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
        }
    }
}

@Composable
private fun HitRow(hit: TmdbHit, onChoose: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onChoose, onClickLabel = hit.title)
            .padding(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PosterThumb(hit.posterPath, null, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(hit.title, hit.year?.toString()).joinToString(" · "),
                style = NagTheme.type.bodyStrong,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            hit.originalTitle?.takeIf { !it.equals(hit.title, ignoreCase = true) }?.let {
                Text(it, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            hit.overview?.let { Text(it, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
