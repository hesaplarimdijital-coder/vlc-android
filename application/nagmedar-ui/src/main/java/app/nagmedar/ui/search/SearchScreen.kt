package app.nagmedar.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.R
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.Episode
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.Sidecars
import app.nagmedar.ui.library.VideoItem
import app.nagmedar.ui.library.buildVideoLibrary
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.rememberTech
import app.nagmedar.ui.library.techSummary
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File

/** A track as the search page lists it. */
@Immutable
data class TrackItem(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val media: MediaWrapper? = null,
)

/** Everything the search page looks in: videos (films and episodes), tracks, and the videos' subtitle lines. */
@Immutable
class SearchData(
    val videos: List<VideoItem>,
    val tracks: List<TrackItem>,
    val subtitles: List<Triple<VideoItem, String, List<SubtitleCue>>> = emptyList(),
) {
    /** Each episode with its show by key, in the library's order (show, season, episode). */
    private val episodes: Map<String, Pair<String, Episode>> = buildVideoLibrary(videos).shows
        .flatMap { show -> show.episodes.map { it.item.key to (show.title to it) } }
        .toMap()

    private val episodeOrder: Map<String, Int> = episodes.keys.withIndex().associate { (index, key) -> key to index }

    /** "Kuzey Rüzgârı · S01E02 · Fener": an episode as the page shows it; null for a film. */
    fun episodeLabel(key: String): String? = episodes[key]?.let { (show, episode) ->
        listOfNotNull(show, episode.code, episode.info.title.takeIf { it.isNotEmpty() }).joinToString(" · ")
    }

    /** Where an episode comes in the library, for hits that are as good as each other. */
    internal fun episodeOrder(key: String): Int = episodeOrder[key] ?: Int.MAX_VALUE

    // an episode is searched with its show and number: "kuzey s01e02" finds it
    internal val videoIndex: List<Searchable<VideoItem>> = videos.map { video ->
        val episode = episodes[video.key]
        if (episode != null) {
            val (show, ep) = episode
            Searchable(video, SearchKind.Episode, listOf(show, ep.code, ep.info.title).filter { it.isNotEmpty() }.joinToString(" "))
        } else {
            Searchable(video, SearchKind.Film, video.title, video.folder.substringAfterLast('/'))
        }
    }
    internal val trackIndex: List<Searchable<TrackItem>> = tracks.map { Searchable(it, SearchKind.Music, it.title, "${it.artist} ${it.album}") }
}

/** What a query found, by kind; [hiddenOnNetwork] counts hits on network shares left out. */
@Immutable
class SearchResults(
    val query: SearchQuery,
    val films: List<VideoItem>,
    val episodes: List<VideoItem>,
    val tracks: List<TrackItem>,
    val cues: List<CueHit<VideoItem>>,
    val hiddenOnNetwork: Int,
    val millis: Long,
) {
    val count: Int get() = films.size + episodes.size + tracks.size + cues.size
}

/** A key on a network share (smb://, upnp://, nfs://, ftp…): searched only when asked for. */
fun isOnNetwork(key: String): Boolean = Uri.parse(key).scheme?.lowercase() !in setOf(null, "file", "content")

/** The clock a search is timed by; the screenshots' goes on in even steps, for the same picture each time. */
internal object SearchClock {
    var nanoTime: () -> Long = System::nanoTime
}

/** Searches [data] (S-311); files on network shares count only with [includeNetwork]. */
fun searchLibrary(data: SearchData, raw: String, includeNetwork: Boolean): SearchResults {
    val start = SearchClock.nanoTime()
    val query = SearchQuery.parse(raw)
    val videos = data.videoIndex.search(query)
    val tracks = data.trackIndex.search(query)
    val cues = if (query.kinds.isEmpty() || SearchKind.Subtitle in query.kinds) searchCues(data.subtitles, query) else emptyList()
    var hidden = 0
    fun <T> List<SearchHit<T>>.local(key: (T) -> String) = filter { hit ->
        val network = isOnNetwork(key(hit.value))
        if (network && !includeNetwork) hidden++
        includeNetwork || !network
    }
    val shownVideos = videos.local { it.key }
    val shownTracks = tracks.local { it.key }
    return SearchResults(
        query = query,
        films = shownVideos.filter { it.kind == SearchKind.Film }.map { it.value },
        // episodes as good as each other come in the show's order, not by the length of their names
        episodes = shownVideos.filter { it.kind == SearchKind.Episode }
            .sortedWith(compareByDescending<SearchHit<VideoItem>> { (it.score + 99) / 100 }.thenBy { data.episodeOrder(it.value.key) })
            .map { it.value },
        tracks = shownTracks.map { it.value },
        cues = cues,
        hiddenOnNetwork = hidden,
        millis = (SearchClock.nanoTime() - start) / 1_000_000,
    )
}

/** The scopes of the page's chips. */
enum class SearchScope { All, Films, Episodes, Music, Subtitles }

/**
 * The search page (S-311): the library's films, episodes and tracks and what their subtitles
 * say, grouped, with chips for each kind and for the files on network shares (off until asked).
 */
@Composable
fun SearchScreen(repository: MediaRepository, query: String, onQueryChange: (String) -> Unit) {
    val context = LocalContext.current
    val data by produceState<SearchData?>(null) {
        val videos = repository.videoItems()
        val tracks = repository.tracks().map { it.toTrackItem() }
        value = SearchData(videos, tracks)
        // what the subtitles say comes a moment later: the files are read now
        value = SearchData(videos, tracks, readSubtitles(videos))
    }
    val tech by rememberTech(data?.videos)
    SearchContent(
        data = data ?: SearchData(emptyList(), emptyList()),
        loading = data == null,
        tech = tech,
        query = query,
        onQueryChange = onQueryChange,
        onPlayVideo = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } },
        onPlayTrack = { item, list ->
            val medias = list.mapNotNull { it.media }
            item.media?.let { media -> PlaybackActions.playList(context, medias, medias.indexOf(media).coerceAtLeast(0)) }
        },
        onPlayCue = { item, ms -> item.media?.let { PlaybackActions.playVideoAt(context, it, ms) } },
    )
}

private fun MediaWrapper.toTrackItem() = TrackItem(
    key = uri.toString(),
    title = title ?: uri?.lastPathSegment.orEmpty(),
    artist = artistName.orEmpty(),
    album = albumName.orEmpty(),
    durationMs = length,
    media = this,
)

/** Reads the text subtitle files next to [videos]: at most 2 MB a file, 40 MB in all. */
internal suspend fun readSubtitles(videos: List<VideoItem>): List<Triple<VideoItem, String, List<SubtitleCue>>> = withContext(Dispatchers.IO) {
    val listings = HashMap<String, List<String>>()
    var budget = 40L * 1024 * 1024
    val out = mutableListOf<Triple<VideoItem, String, List<SubtitleCue>>>()
    for (video in videos) {
        if (video.sidecarSubtitles.isEmpty() || video.path.isEmpty()) continue
        val folder = video.folder
        val names = listings.getOrPut(folder) { File(folder).list()?.toList().orEmpty() }
        for (name in Sidecars.files(video.path.substringAfterLast('/'), names)) {
            if (name.substringAfterLast('.').lowercase() !in Sidecars.textExtensions) continue
            val file = File(folder, name)
            val size = file.length()
            if (size <= 0 || size > 2L * 1024 * 1024 || size > budget) continue
            budget -= size
            val cues = runCatching { SubtitleParser.parse(name, file.readBytes()) }.getOrDefault(emptyList())
            if (cues.isNotEmpty()) out += Triple(video, name, cues)
        }
    }
    out
}

/** The page for [data], fed by the screen or by tests. */
@Composable
fun SearchContent(
    data: SearchData,
    loading: Boolean,
    tech: Map<String, MediaTech>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onPlayCue: (VideoItem, Long) -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    var scope by rememberSaveable { mutableStateOf(SearchScope.All) }
    var includeNetwork by rememberSaveable { mutableStateOf(false) }
    val results = remember(data, query, includeNetwork) { searchLibrary(data, query, includeNetwork) }
    val field = remember { FocusRequester() }
    var fieldFocused by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("search"),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "field") {
            SearchField(query, onQueryChange, field)
            // the page opens with the field focused, once: an item is composed when the list is measured
            if (!fieldFocused) {
                LaunchedEffect(Unit) {
                    runCatching { field.requestFocus() }
                    fieldFocused = true
                }
            }
        }
        if (results.query.isEmpty) {
            item(key = "syntax") { SyntaxHelp(Modifier.padding(top = dimens.gapL)) }
            return@LazyColumn
        }
        item(key = "summary") {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(top = dimens.gapS)) {
                Text(
                    pluralStringResource(R.plurals.nag_search_count, results.count, query.trim(), results.count),
                    style = NagTheme.type.headline,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (data.subtitles.isEmpty() && !loading) R.string.nag_search_where_library else R.string.nag_search_where) + " · " +
                        stringResource(R.string.nag_search_millis, results.millis),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        item(key = "chips") {
            // a phone scrolls the chips sideways
            Row(Modifier.widthIn(max = ContentWidth).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val counts = mapOf(
                    SearchScope.All to results.count,
                    SearchScope.Films to results.films.size,
                    SearchScope.Episodes to results.episodes.size,
                    SearchScope.Music to results.tracks.size,
                    SearchScope.Subtitles to results.cues.size,
                )
                SearchScope.entries.forEach { s ->
                    if (s == SearchScope.All || counts.getValue(s) > 0) {
                        FilterChip("${scopeLabel(s)} ${counts.getValue(s)}", scope == s, { on -> scope = if (on) s else SearchScope.All })
                    }
                }
                FilterChip(stringResource(R.string.nag_search_network), includeNetwork, { includeNetwork = it })
            }
        }
        if (results.count == 0) {
            item(key = "none") {
                NoResultsState(
                    title = stringResource(R.string.nag_search_nothing, query.trim()),
                    suggestions = listOfNotNull(
                        StateAction(stringResource(R.string.nag_search_show_network)) { includeNetwork = true }.takeIf { results.hiddenOnNetwork > 0 },
                    ),
                )
            }
        }
        val films = if (scope == SearchScope.All || scope == SearchScope.Films) results.films else emptyList()
        val episodes = if (scope == SearchScope.All || scope == SearchScope.Episodes) results.episodes else emptyList()
        val tracks = if (scope == SearchScope.All || scope == SearchScope.Music) results.tracks else emptyList()
        val cues = if (scope == SearchScope.All || scope == SearchScope.Subtitles) results.cues else emptyList()
        val limit = if (scope == SearchScope.All) 5 else Int.MAX_VALUE
        section(R.string.nag_search_films, films, limit, { "film-${it.key}" }) { VideoHit(it, it.title, tech[it.key], results.query) { onPlayVideo(it) } }
        section(R.string.nag_search_episodes, episodes, limit, { "episode-${it.key}" }) {
            VideoHit(it, data.episodeLabel(it.key) ?: it.title, tech[it.key], results.query) { onPlayVideo(it) }
        }
        section(R.string.nag_search_music, tracks, limit, { "track-${it.key}" }) { TrackHit(it, results.query) { onPlayTrack(it, tracks) } }
        section(R.string.nag_search_said, cues, limit, { "cue-${it.video.key}-${it.file}-${it.cue.startMs}" }) {
            CueHitRow(it, results.query) { onPlayCue(it.video, it.cue.startMs) }
        }
        if (results.hiddenOnNetwork > 0 && !includeNetwork) {
            item(key = "network") { NetworkCard(results.hiddenOnNetwork) { includeNetwork = true } }
        }
        item(key = "syntax") { SyntaxHelp(Modifier.padding(top = dimens.gapL)) }
    }
}

private val ContentWidth = 960.dp

private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    hits: List<T>,
    limit: Int,
    key: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    if (hits.isEmpty()) return
    item(key = "header-$title") {
        Row(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
            Text(
                stringResource(title).uppercase(currentLocale()) + " · " + hits.size,
                style = NagTheme.type.caption,
                color = NagTheme.colors.textTertiary,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
    items(hits.take(limit), key = key) { hit -> Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { row(hit) } }
}

@Composable
private fun scopeLabel(scope: SearchScope): String = stringResource(
    when (scope) {
        SearchScope.All -> R.string.nag_palette_all
        SearchScope.Films -> R.string.nag_search_films
        SearchScope.Episodes -> R.string.nag_search_episodes
        SearchScope.Music -> R.string.nag_search_music
        SearchScope.Subtitles -> R.string.nag_search_subtitles
    },
)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focus: FocusRequester) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val placeholder = stringResource(R.string.nag_search_placeholder)
    Row(
        Modifier
            .widthIn(max = ContentWidth)
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) NagIconButton(NagIcons.Close, stringResource(R.string.nag_search_clear), { onQueryChange("") })
    }
}

/** The text with the query's words in full colour and weight. */
@Composable
private fun marked(text: String, query: SearchQuery): AnnotatedString {
    val strong = NagTheme.colors.text
    return buildAnnotatedString {
        append(text)
        highlightRanges(text, query).forEach { addStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
    }
}

@Composable
private fun HitRow(onClick: () -> Unit, label: String, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NagTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun VideoHit(item: VideoItem, title: String, tech: MediaTech?, query: SearchQuery, onClick: () -> Unit) {
    val colors = NagTheme.colors
    HitRow(onClick, title) {
        Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(NagTheme.dimens.radiusSmall))) {
            Artwork(item.media, size = 120.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(marked(title, query), style = NagTheme.type.bodyStrong, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatDuration(item.durationMs), tech?.let(::techSummary)?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TrackHit(item: TrackItem, query: SearchQuery, onClick: () -> Unit) {
    val colors = NagTheme.colors
    HitRow(onClick, item.title) {
        Artwork(item.media, size = 40.dp, video = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(marked(item.title, query), style = NagTheme.type.bodyStrong, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(marked(listOf(item.artist, item.album).filter { it.isNotEmpty() }.joinToString(" · "), query), style = NagTheme.type.labelSmall,
                color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(item.durationMs), style = NagTheme.type.mono, color = colors.textTertiary)
    }
}

@Composable
private fun CueHitRow(hit: CueHit<VideoItem>, query: SearchQuery, onClick: () -> Unit) {
    val colors = NagTheme.colors
    HitRow(onClick, hit.video.title) {
        Text(
            if (hit.cue.startMs < 1000) "0:00" else formatDuration(hit.cue.startMs),
            style = NagTheme.type.mono,
            color = colors.accentText,
            modifier = Modifier.width(72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(marked("“${hit.cue.text}”", query), style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${hit.video.title} · ${hit.file}", style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NetworkCard(count: Int, onShow: () -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Row(
        Modifier
            .widthIn(max = ContentWidth)
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, colors.borderStrong, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Network, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(NagTheme.dimens.icon))
        Spacer(Modifier.width(10.dp))
        Text(pluralStringResource(R.plurals.nag_search_on_network, count, count), style = NagTheme.type.body, color = colors.text,
            modifier = Modifier.weight(1f))
        NagButton(stringResource(R.string.nag_search_show_network), onShow)
    }
}

/** "tür:film kuzey", "tam ifade", -fragman (in English type:film north…), and the Turkish letters. */
@Composable
private fun SyntaxHelp(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Column(
        modifier
            .widthIn(max = ContentWidth)
            .fillMaxWidth()
            .background(colors.surface, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.nag_search_syntax), style = NagTheme.type.bodyStrong, color = colors.text)
        SyntaxLine(stringResource(R.string.nag_search_syntax_kind_example), stringResource(R.string.nag_search_syntax_kind))
        SyntaxLine(stringResource(R.string.nag_search_syntax_phrase_example), stringResource(R.string.nag_search_syntax_phrase))
        SyntaxLine(stringResource(R.string.nag_search_syntax_exclude_example), stringResource(R.string.nag_search_syntax_exclude))
        Text(stringResource(R.string.nag_search_syntax_letters), style = NagTheme.type.labelSmall, color = colors.textTertiary)
    }
}

@Composable
private fun SyntaxLine(example: String, meaning: String) {
    Row {
        Text(example, style = NagTheme.type.mono, color = NagTheme.colors.text, modifier = Modifier.width(180.dp))
        Text(meaning, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
    }
}
