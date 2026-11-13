package app.nagmedar.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.MoreButton
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.lazyKeys
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.components.toActionGroups
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.mediaMenu
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.rememberRead
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicLink
import app.nagmedar.ui.music.MusicLinks
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.toTrack
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.LocalShellTopPadding
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.Locale

/** A track as the search page lists it. */
@Immutable
data class TrackItem(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val media: MediaWrapper? = null,
    /** 1.0.7: who its album is filed under. */
    val albumArtist: String = "",
) {
    /**
     * What a track is found by besides its title, for the search page and the palette alike: its artist, its
     * album, and (1.0.7) who its album is filed under when that is someone else.
     */
    val words: String
        get() = listOf(artist, albumArtist.takeUnless { it.equals(artist, ignoreCase = true) }.orEmpty(), album).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Everything the search looks in: the tracks and (1.0.7) the music library's artists and albums ([groups]).
 * Equal to another that holds the same: a library read again that has not changed changes nothing on screen.
 */
@Immutable
class SearchData(val tracks: List<TrackItem>, val groups: List<GroupItem> = emptyList()) {
    internal val trackIndex: List<Searchable<TrackItem>> = tracks.map { Searchable(it, SearchKind.Music, it.title, it.words) }
    internal val groupIndex: List<Searchable<GroupItem>> = groups.map { Searchable(it, SearchKind.Music, it.title, it.artist) }

    override fun equals(other: Any?): Boolean = other is SearchData && other.tracks == tracks && other.groups == groups

    override fun hashCode(): Int = 31 * tracks.size + groups.size
}

/** What a query found, by kind; [hiddenOnNetwork] counts hits on network shares left out. */
@Immutable
class SearchResults(
    val query: SearchQuery,
    val tracks: List<TrackItem>,
    val hiddenOnNetwork: Int,
    val millis: Long,
    /** 1.0.7: the artists and the albums found, each best first. */
    val artists: List<GroupItem> = emptyList(),
    val albums: List<GroupItem> = emptyList(),
) {
    val count: Int get() = artists.size + albums.size + tracks.size
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
    val tracks = data.trackIndex.search(query)
    var hidden = 0
    fun <T> List<SearchHit<T>>.local(key: (T) -> String) = filter { hit ->
        val network = isOnNetwork(key(hit.value))
        if (network && !includeNetwork) hidden++
        includeNetwork || !network
    }
    val shownTracks = tracks.local { it.key }
    // an artist or an album with nothing on the device goes with its tracks (they are what is counted)
    val groups = data.groupIndex.search(query).map { it.value }.filter { includeNetwork || !it.onNetwork }
    return SearchResults(
        query = query,
        tracks = shownTracks.map { it.value },
        hiddenOnNetwork = hidden,
        millis = (SearchClock.nanoTime() - start) / 1_000_000,
        artists = groups.filter { it.view == MusicView.Artists },
        albums = groups.filter { it.view == MusicView.Albums },
    )
}

/**
 * 1.0.7: the library as the search looks at it, for the search page and the palette alike: kept while the
 * window lives ([MediaRepository.lastRead]), so whichever opens next has it in its first frame and reads it
 * again behind what it shows (the palette read the library at each opening and had no media until then).
 * The very first time the Music's list kept on the device stands in until the library is read; null before.
 */
@Composable
fun rememberSearchData(repository: MediaRepository): State<SearchData?> {
    val locale = currentLocale()
    return rememberRead<SearchData>(repository.lastRead, "search", locale) {
        if (value == null) {
            repository.keptMusic(repository.musicScope())?.media?.let { kept -> value = withContext(Dispatchers.Default) { searchData(kept, kept, locale) } }
        }
        val all = repository.tracks()
        // the artists and albums are the Music view's, which opens them: of its folders when it has some of its own
        val music = if (repository.sources().of(LibrarySection.Music).isEmpty()) all else repository.tracks(LibrarySection.Music)
        value = withContext(Dispatchers.Default) { searchData(all, music, locale) }
    }
}

/** Every track of [all], and the artists and albums of [music] as the Music view groups them. */
internal fun searchData(all: List<MediaWrapper>, music: List<MediaWrapper>, locale: Locale): SearchData {
    val byUri = music.associateBy { it.uri?.toString().orEmpty() }
    val library = MusicLibrary(music.map { it.toTrack(null) }, locale)
    return SearchData(all.map { it.toTrackItem() }, searchGroups(library) { byUri[it.uri] })
}

/**
 * The search page (S-311): the library's tracks and (1.0.7) its artists and albums, with a chip for the
 * files on network shares (off until asked).
 */
@Composable
fun SearchScreen(repository: MediaRepository, query: String, onQueryChange: (String) -> Unit) {
    val context = LocalContext.current
    val data by rememberSearchData(repository)
    SearchContent(
        data = data ?: SearchData(emptyList()),
        query = query,
        onQueryChange = onQueryChange,
        onPlayTrack = { item, list ->
            val medias = list.mapNotNull { it.media }
            item.media?.let { media -> PlaybackActions.playList(context, medias, medias.indexOf(media).coerceAtLeast(0)) }
        },
        // as the players' artist and album links: the Music view opens the group
        onOpenGroup = { group -> MusicLinks.open(MusicLink(group.view, listOf(group.group))) },
    )
}

internal fun MediaWrapper.toTrackItem() = TrackItem(
    key = uri.toString(),
    title = title ?: uri?.lastPathSegment.orEmpty(),
    artist = artistName.orEmpty(),
    album = albumName.orEmpty(),
    durationMs = length,
    media = this,
    albumArtist = albumArtistName.orEmpty(),
)

/** The page for [data], fed by the screen or by tests. */
@Composable
fun SearchContent(
    data: SearchData,
    query: String,
    onQueryChange: (String) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onOpenGroup: (GroupItem) -> Unit = {},
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    var includeNetwork by rememberSaveable { mutableStateOf(false) }
    val results = remember(data, query, includeNetwork) { searchLibrary(data, query, includeNetwork) }
    val field = remember { FocusRequester() }
    var fieldFocused by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("search"),
        // the page scrolls under the phone's bars (Desktop Kit 1.0)
        contentPadding = LocalShellTopPadding.current + PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapM) + LocalShellBottomPadding.current,
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
                    stringResource(R.string.nag_search_where_library) + " · " + stringResource(R.string.nag_search_millis, results.millis),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        item(key = "chips") {
            // a phone keeps them to one line that scrolls sideways, its ends fading; wider windows wrap them
            ChipRow(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
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
        // 1.0.7: the artists and the albums found, then the tracks
        section(R.string.nag_music_view_artists, results.artists, { it.key }) { GroupHit(it, results.query) { onOpenGroup(it) } }
        section(R.string.nag_music_view_albums, results.albums, { it.key }) { GroupHit(it, results.query) { onOpenGroup(it) } }
        val tracks = results.tracks
        section(R.string.nag_music_view_tracks, tracks, { "track-${it.key}" }) { TrackHit(it, results.query) { onPlayTrack(it, tracks) } }
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
    // 1.0.1: one key each, though the library lists a file twice (a key used twice would end the app)
    val keys = lazyKeys(hits.map(key))
    itemsIndexed(hits, key = { index, _ -> keys[index] }) { _, hit -> Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { row(hit) } }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focus: FocusRequester) {
    val focusManager = LocalFocusManager.current
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
            if (query.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).tabMovesFocus(focusManager).semantics { contentDescription = placeholder },
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
private fun HitRow(onClick: () -> Unit, label: String, media: MediaWrapper?, content: @Composable RowScope.() -> Unit) {
    val context = LocalContext.current
    // the same menu as in the lists; a hit without an item of the medialibrary has none
    val menu = { media?.let { mediaMenu(context, it, onClick) }.orEmpty() }
    // a phone's hit ends with "⋯": the same menu as the long press, as a sheet (Desktop Kit 1.0)
    val more = media != null && currentWidthClass() == WidthClass.Compact
    ContextMenuArea(
        entries = menu,
        enabled = media != null,
        accessibilityLabel = stringResource(R.string.nag_more_actions),
    ) {
        HitRowBody(onClick, label) {
            content()
            if (more) MoreButton(groups = { menu().toActionGroups() }, title = label)
        }
    }
}

@Composable
private fun HitRowBody(onClick: () -> Unit, label: String, content: @Composable RowScope.() -> Unit) {
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
private fun TrackHit(item: TrackItem, query: SearchQuery, onClick: () -> Unit) {
    val colors = NagTheme.colors
    HitRow(onClick, item.title, item.media) {
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

/** 1.0.7: an artist ("4 albüm · 38 parça") or an album (its cover, "Ada Karan · 9 parça"); a click opens it in the Music view. */
@Composable
private fun GroupHit(item: GroupItem, query: SearchQuery, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val tracks = pluralStringResource(R.plurals.nag_tracks_count, item.tracks, item.tracks)
    val artists = item.view == MusicView.Artists
    val albums = pluralStringResource(R.plurals.nag_music_albums, item.albums, item.albums)
    // an album is found by its artist too: the name is marked, the count beside it is not
    val artist = marked(item.artist, query)
    val line = buildAnnotatedString {
        when {
            artists -> append("$albums · $tracks")
            item.artist.isEmpty() -> append(tracks)
            else -> {
                append(artist)
                append(" · $tracks")
            }
        }
    }
    HitRow(onClick, item.title, null) {
        if (artists) {
            Box(Modifier.size(40.dp).background(colors.surface, RoundedCornerShape(NagTheme.dimens.radiusControl)), contentAlignment = Alignment.Center) {
                Icon(NagIcons.Artists, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(NagTheme.dimens.icon))
            }
        } else {
            Artwork(item.media, size = 40.dp, video = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(marked(item.title, query), style = NagTheme.type.bodyStrong, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(NagTheme.dimens.icon))
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

/** "tam ifade", -canlı (in English "exact phrase", -live), and the Turkish letters. */
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
