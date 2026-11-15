package app.nagmedar.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.MusicRead
import app.nagmedar.ui.library.MusicSnapshot
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.stats.statsKey
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** 1.1.2 (the design's tablet page): an album played once and not for a year since: its name, its artist, its tracks. */
@Immutable
internal class ForgottenAlbum(val title: String, val artist: String?, val tracks: List<MediaWrapper>)

/** How long an album rests before the sidebar remembers it: a year. */
private const val FORGOTTEN_AFTER_MS = 365L * 24 * 60 * 60 * 1000

/**
 * The albums played before and not for a year since, the most played of them first, three at most: from the library's
 * last read (the Music page's, or the list kept from the last start) and the play counts.
 */
@Composable
internal fun rememberForgottenAlbums(repository: MediaRepository): List<ForgottenAlbum> {
    val context = LocalContext.current
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    val read = repository.lastRead.get<MusicRead>("music") ?: MusicSnapshot.cached(repository.musicScope())
    val found by produceState(emptyList<ForgottenAlbum>(), read, stats) {
        val media = read?.media ?: return@produceState
        value = withContext(Dispatchers.Default) { forgottenAlbums(media, stats, System.currentTimeMillis()) }
    }
    return found
}

/** [media]'s albums played before [now] less a year and not since, the most played first, three at most. */
internal fun forgottenAlbums(media: List<MediaWrapper>, stats: Map<String, TrackStats>, now: Long): List<ForgottenAlbum> {
    val before = now - FORGOTTEN_AFTER_MS
    return media
        .filter { !it.albumName.isNullOrBlank() && it.type == MediaWrapper.TYPE_AUDIO }
        .groupBy { (it.albumName ?: "") + "\u0000" + (it.albumArtistName?.takeIf { a -> a.isNotBlank() } ?: it.artistName ?: "") }
        .values
        .mapNotNull { tracks ->
            val counts = tracks.mapNotNull { m -> statsKey(m)?.let { stats[it] } }
            val last = counts.maxOfOrNull { it.lastPlayedAt } ?: 0L
            if (last <= 0L || last > before) null else Triple(tracks, counts.sumOf { it.plays }, last)
        }
        .sortedWith(compareByDescending<Triple<List<MediaWrapper>, Int, Long>> { it.second }.thenBy { it.third })
        .take(3)
        .map { (tracks, _, _) ->
            val first = tracks.first()
            ForgottenAlbum(first.albumName.orEmpty(), first.albumArtistName?.takeIf { it.isNotBlank() } ?: first.artistName, tracks.sortedBy { it.trackNumber })
        }
}

/** The covers' turns and how far each lies over the one before (the design's). */
private val TURNS = listOf(-7f, 2f, 8f)

/**
 * The sidebar's "Bir yıldır açmadıkların" (the design's): the albums' covers askew, a tap plays one; their names under
 * them. Nothing while there are none.
 */
@Composable
internal fun ForgottenShelf(albums: List<ForgottenAlbum>, modifier: Modifier = Modifier) {
    if (albums.isEmpty()) return
    val colors = NagTheme.colors
    val context = LocalContext.current
    val names = when (albums.size) {
        1 -> albums[0].title
        2 -> stringResource(R.string.nag_list_two, albums[0].title, albums[1].title)
        else -> stringResource(R.string.nag_list_three, albums[0].title, albums[1].title, albums[2].title)
    }
    val play = stringResource(R.string.nag_play)
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("sidebar-forgotten"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nag_music_forgotten), style = designText(13f, 18f, WideTitleWeight), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.height(64.dp).padding(start = 4.dp)) {
            albums.forEachIndexed { i, album ->
                val label = listOfNotNull(album.title, album.artist).joinToString(", ") + ". " + play
                ForgottenCover(album, TURNS[i], label, Modifier.offset(x = (-16).dp * i)) {
                    PlaybackActions.playList(context, album.tracks, 0, PlaySource(PlayFrom.Album, album.title))
                }
            }
        }
        Text(names, style = designText(12f, 17f), color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ForgottenCover(album: ForgottenAlbum, turn: Float, label: String, modifier: Modifier, onPlay: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    val track = remember(album) { album.tracks.firstOrNull()?.let(CoverTrack::of) }
    val px = with(LocalDensity.current) { 72.dp.roundToPx() }
    val image = rememberCover(track, px)
    Box(
        modifier
            .size(60.dp)
            .rotate(turn)
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .nagClickable(interaction, onPlay, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(NagIcons.Albums, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(24.dp))
        }
    }
}
