package app.nagmedar.ui.library.metadata

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.data.FilmKind
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SelectionMark
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.BadgeEdge
import app.nagmedar.ui.library.BadgeGlass
import app.nagmedar.ui.library.BadgeText
import app.nagmedar.ui.library.Show
import app.nagmedar.ui.library.TechBadge
import app.nagmedar.ui.library.VideoItem
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.durationWords
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.flow.flowOf
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.text.NumberFormat
import java.util.Locale

// ---------------------------------------------------------------- what is looked up

/** The film [item] is, from its file name (or its title when the name says nothing); null when nothing names it. */
fun VideoItem.filmQuery(): FilmQuery? =
    (FilmNameParser.parse(path.ifEmpty { title }) ?: FilmNameParser.parseName(title))?.let(FilmQuery::movie)

/**
 * The film information key ([FilmQuery.key]) of each of [items], by the item's key: the names are read once for the
 * list, not again on each change of the film information (every background save) or recomposition.
 */
@Composable
fun rememberFilmKeys(items: List<VideoItem>): Map<String, String?> = remember(items) { items.associate { it.key to it.filmQuery()?.key } }

/** The series [Show] is, with the seasons the library has of it. */
fun Show.filmQuery(): FilmQuery = FilmQuery.show(title, seasons.map { it.number }.toSet())

/**
 * The film information found for the library, looking up in the background what [queries] has
 * not found yet; again when a setting changes. Empty in previews and tests without a database.
 */
@Composable
fun rememberFilmInfo(queries: List<FilmQuery>?): State<Map<String, FilmInfo>> {
    val context = LocalContext.current
    val settings by FilmInfoCenter.settingsChanges.collectAsState()
    LaunchedEffect(queries, settings) {
        if (queries != null) runCatching { FilmInfoCenter.request(context, queries) }
    }
    val flow = remember(context) { runCatching { FilmInfoCenter.all(context) }.getOrNull() ?: flowOf(emptyMap()) }
    return flow.collectAsState(initial = emptyMap())
}

/** A series' episodes as TMDB names them; empty until found. */
@Composable
fun rememberEpisodeMeta(showId: Long?): Map<Pair<Int, Int>, EpisodeMeta> {
    val context = LocalContext.current
    val flow = remember(context, showId) {
        showId?.let { id -> runCatching { FilmInfoCenter.episodes(context, id) }.getOrNull() } ?: flowOf(emptyMap())
    }
    return flow.collectAsState(initial = emptyMap()).value
}

// ---------------------------------------------------------------- numbers

/** "7,8" in Turkish, "7.8" in English: a rating out of ten with one decimal. */
fun ratingNumber(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

/** The film's ratings in a line: "IMDb 7,8 · Rotten Tomatoes %87 · TMDB 7,4", the ones there are. */
@Composable
fun ratingsLine(info: FilmInfo, short: Boolean = false): String {
    val locale = currentLocale()
    val tomatoes = info.rottenTomatoes?.let { stringResource(R.string.nag_film_percent, it) }
    return listOfNotNull(
        info.imdbRating?.let { stringResource(R.string.nag_film_imdb, ratingNumber(it, locale)) },
        tomatoes?.let { stringResource(if (short) R.string.nag_film_rt_short else R.string.nag_film_rt, it) },
        info.tmdbRating?.takeIf { !short || info.imdbRating == null }?.let { stringResource(R.string.nag_film_tmdb, ratingNumber(it, locale)) },
    ).joinToString(" · ")
}

/** "1999 · Bilim kurgu, Aksiyon · 2 sa 16 dk": the year, the genres and the length the film information gives. */
@Composable
fun factsLine(info: FilmInfo, genres: Int = 2): String = listOfNotNull(
    info.year?.toString(),
    info.genres.take(genres).joinToString(", ").takeIf { it.isNotEmpty() },
    info.runtimeMinutes?.takeIf { info.kind == FilmKind.MOVIE }?.let { durationWords(it * 60_000L) },
    info.seasonCount?.let { pluralStringResource(R.plurals.nag_seasons_count, it, it) },
).joinToString(" · ")

// ---------------------------------------------------------------- posters

/**
 * A poster card (the "Afiş" view): the 2:3 poster (the video's own picture until there is one), the
 * technical badge, the watched part, the title and a line under it. Hovered or focused, it rises a
 * little. Selected, it takes the accent's edge; [selecting] by touch shows the mark.
 */
@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    posterPath: String?,
    media: MediaWrapper?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    selecting: Boolean = false,
    badge: String? = null,
    progress: Float = 0f,
    watched: Boolean = false,
    /** A series' unwatched episodes, shown in the corner. */
    unwatched: Int = 0,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    val posterShape = RoundedCornerShape(dimens.radiusControl)
    val lifted = state.hovered || state.focused
    val scale by animateFloatAsState(if (lifted && !state.pressed) 1.03f else 1f, label = "poster-lift")
    val elevation by animateDpAsState(if (lifted) 10.dp else 0.dp, label = "poster-shadow")
    val px = with(LocalDensity.current) { 240.dp.roundToPx() }
    val stateText = when {
        unwatched > 0 -> pluralStringResource(R.plurals.nag_unwatched_episodes, unwatched, unwatched)
        progress > 0f -> stringResource(R.string.nag_partly_watched, (progress * 100).toInt().coerceIn(1, 99))
        watched -> stringResource(R.string.nag_watched)
        else -> null
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRing(state, shape)
            .background(if (state.pressed || selected) stateBackground(state, selected) else colors.background, shape)
            .then(if (selected) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .semantics {
                this.selected = selected
                if (stateText != null) stateDescription = stateText
            }
            .nagClickable(interaction, onClick, onClickLabel = title, onDoubleClick = onDoubleClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(elevation, posterShape, clip = false)
                .clip(posterShape)
                .background(colors.surfaceRaised),
        ) {
            FilmImage(posterPath, FilmImageSize.Poster, px, Modifier.fillMaxSize()) {
                Artwork(media, size = 240.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            }
            if (progress > 0f) WatchedBar(progress, Modifier.align(Alignment.BottomStart))
            if (badge != null) TechBadge(badge, Modifier.align(Alignment.TopStart).padding(6.dp))
            when {
                selecting -> SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                unwatched > 0 -> CountBadge(unwatched, Modifier.align(Alignment.TopEnd).padding(6.dp))
                watched && progress <= 0f -> WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The watched part of a video along the bottom of its picture. */
@Composable
fun WatchedBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Box(modifier.fillMaxWidth().height(3.dp).background(colors.scrim)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
    }
}

/** A tick on dark glass in a picture's corner: watched to the end. */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).background(BadgeGlass, CircleShape).border(1.dp, BadgeEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NagIcons.Check, contentDescription = null, tint = BadgeText, modifier = Modifier.size(14.dp))
    }
}

/** A series' unwatched episodes in a picture's corner: the number in the accent's pill. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Text(
        count.toString(),
        style = NagTheme.type.labelSmall,
        color = colors.onAccent,
        maxLines = 1,
        modifier = modifier
            .background(colors.accent, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** A small 2:3 poster for a list row, [width] wide. */
@Composable
fun PosterThumb(posterPath: String?, media: MediaWrapper?, width: Dp, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val px = with(LocalDensity.current) { (width * 2).roundToPx() }
    Box(
        modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(NagTheme.dimens.radiusSmall))
            .background(colors.surfaceRaised),
    ) {
        FilmImage(posterPath, FilmImageSize.Poster, px, Modifier.fillMaxSize()) {
            Artwork(media, size = width * 2, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
        }
    }
}

/**
 * An episode's mark at the end of its row: a tick when watched, a ring filled as far as it was
 * watched, an empty ring when not yet; a screen reader hears the state.
 */
@Composable
fun WatchMark(state: WatchState, progress: Float, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = NagTheme.colors
    val label = when (state) {
        WatchState.Watched -> stringResource(R.string.nag_watched)
        WatchState.Partly -> stringResource(R.string.nag_partly_watched, (progress * 100).toInt().coerceIn(1, 99))
        WatchState.Unwatched -> stringResource(R.string.nag_filter_unwatched)
    }
    val ring = if (selected) colors.onSelection else colors.textTertiary
    val fill = if (selected) colors.onSelection else colors.accent
    Box(modifier.size(20.dp).semantics { stateDescription = label }, contentAlignment = Alignment.Center) {
        when (state) {
            WatchState.Watched -> Box(Modifier.size(18.dp).background(if (selected) colors.onSelection else colors.success, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NagIcons.Check, contentDescription = label, tint = if (selected) colors.selection else colors.background, modifier = Modifier.size(12.dp))
            }
            WatchState.Partly, WatchState.Unwatched -> Box(
                Modifier.size(16.dp).drawBehind {
                    val stroke = 1.6.dp.toPx()
                    val inset = stroke / 2
                    drawArc(ring, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    if (state == WatchState.Partly) {
                        drawArc(fill, -90f, 360f * progress.coerceIn(0f, 1f), true, Offset(inset * 2, inset * 2),
                            Size(size.width - stroke * 2, size.height - stroke * 2))
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- the hero

/**
 * The hero above the posters: the film's backdrop with its title, facts, ratings and overview, and
 * play and details. Wide, the words stand on the backdrop's left over a fade to the page's colour;
 * narrow, under it. The fade is the page's own colour, so the words keep their contrast on any image.
 */
@Composable
fun FilmHero(info: FilmInfo, item: VideoItem, onPlay: () -> Unit, onDetails: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 640.dp
        val px = with(LocalDensity.current) { maxWidth.roundToPx() }
        val backdrop = @Composable { m: Modifier ->
            FilmImage(info.backdropPath, FilmImageSize.Backdrop, px, m) {
                Artwork(item.media, size = 480.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            }
        }
        val words = @Composable { m: Modifier ->
            Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.title, style = NagTheme.type.display, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                val facts = factsLine(info, genres = 3)
                if (facts.isNotEmpty()) Text(facts, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val ratings = ratingsLine(info)
                if (ratings.isNotEmpty()) Text(ratings, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                info.overview?.let { Text(it, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    NagButton(stringResource(if (item.progress > 0f) R.string.nag_details_resume else R.string.nag_play), onPlay, icon = NagIcons.Play, primary = true)
                    NagButton(stringResource(R.string.nag_details_title), onDetails, icon = NagIcons.Info)
                }
            }
        }
        if (wide) {
            val background = colors.background
            Box(Modifier.fillMaxWidth().height(if (maxWidth >= 1000.dp) 340.dp else 300.dp).clip(shape)) {
                backdrop(Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to background,
                            0.5f to background.copy(alpha = 0.92f),
                            0.8f to background.copy(alpha = 0.3f),
                            1f to Color.Transparent,
                        ),
                    ),
                )
                words(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.48f).padding(horizontal = 24.dp, vertical = 20.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                backdrop(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape))
                words(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The film the hero shows: the one in progress most recently added, else the newest; each with a backdrop. [keys]
 * are the items' film information keys ([rememberFilmKeys]).
 */
fun heroOf(items: List<VideoItem>, keys: Map<String, String?>, info: Map<String, FilmInfo>): Pair<VideoItem, FilmInfo>? {
    val withBackdrop = items.mapNotNull { item ->
        keys[item.key]?.let { info[it] }?.takeIf { it.backdropPath != null }?.let { item to it }
    }
    return withBackdrop.filter { it.first.progress > 0f }.maxByOrNull { it.first.addedAt } ?: withBackdrop.maxByOrNull { it.first.addedAt }
}
