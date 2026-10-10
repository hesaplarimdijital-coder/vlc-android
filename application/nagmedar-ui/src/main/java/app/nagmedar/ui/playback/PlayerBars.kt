package app.nagmedar.ui.playback

import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ThumbnailsProvider

/** Cover or video thumbnail of [media], loaded through the classic thumbnail cache. */
@Composable
fun Artwork(media: MediaWrapper?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val colors = NagTheme.colors
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, media?.id, media?.artworkMrl, px) {
        value = media?.let { ThumbnailsProvider.obtainBitmap(it, px)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (media?.type == MediaWrapper.TYPE_VIDEO) NagIcons.Movies else NagIcons.Music,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(size / 2.5f),
            )
        }
    }
}

/** Compact and medium windows: a strip above the navigation with the current track. */
@Composable
fun MiniPlayer(nowPlaying: NowPlaying, modifier: Modifier = Modifier) {
    val media = nowPlaying.media ?: return
    val context = LocalContext.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val progress by rememberPlaybackProgress(nowPlaying)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val openLabel = stringResource(R.string.nag_open_player)
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.accent))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.miniPlayerHeight)
                .padding(horizontal = dimens.gapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (state.hovered) colors.hover else colors.chrome, RoundedCornerShape(dimens.radiusControl))
                    .nagClickable(interaction, { PlaybackActions.openPlayer(context) }, onClickLabel = openLabel),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(media, size = dimens.miniPlayerHeight - 20.dp)
                Spacer(Modifier.width(dimens.gapM))
                TitleBlock(media, Modifier.weight(1f))
            }
            NagIconButton(NagIcons.Previous, stringResource(R.string.nag_previous), PlaybackActions::previous)
            PlayPauseButton(nowPlaying.playing)
            NagIconButton(NagIcons.Next, stringResource(R.string.nag_next), PlaybackActions::next)
        }
    }
}

/** Large windows: the full-width transport bar of the mockups. */
@Composable
fun TransportBar(nowPlaying: NowPlaying, modifier: Modifier = Modifier) {
    val media = nowPlaying.media ?: return
    val context = LocalContext.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val progress by rememberPlaybackProgress(nowPlaying)
    val shuffling by PlaylistManager.shuffling.collectAsState()
    val repeating by PlaylistManager.repeating.collectAsState()
    val positionLabel = stringResource(R.string.nag_position)
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = dimens.gapL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .nagClickable(interaction, { PlaybackActions.openPlayer(context) }, onClickLabel = stringResource(R.string.nag_open_player)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(media, size = 48.dp)
                Spacer(Modifier.width(dimens.gapM))
                TitleBlock(media, Modifier.weight(1f))
            }
            Column(Modifier.widthIn(min = 360.dp, max = 640.dp).weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NagIconButton(NagIcons.Shuffle, stringResource(R.string.nag_shuffle), { PlaybackService.instance?.shuffle() },
                        selected = shuffling)
                    NagIconButton(NagIcons.Previous, stringResource(R.string.nag_previous), PlaybackActions::previous)
                    PlayPauseButton(nowPlaying.playing)
                    NagIconButton(NagIcons.Next, stringResource(R.string.nag_next), PlaybackActions::next)
                    NagIconButton(NagIcons.Repeat, stringResource(R.string.nag_repeat), { cycleRepeat() },
                        selected = repeating != PlaybackStateCompat.REPEAT_MODE_NONE)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val length = media.length
                    Text(formatDuration((length * progress).toLong()), style = NagTheme.type.mono, color = colors.textTertiary)
                    Spacer(Modifier.width(dimens.gapS))
                    NagSlider(
                        value = progress,
                        onValueChange = PlaybackActions::seekTo,
                        modifier = Modifier.weight(1f).semantics { contentDescription = positionLabel },
                    )
                    Spacer(Modifier.width(dimens.gapS))
                    Text(formatDuration(length), style = NagTheme.type.mono, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TitleBlock(media: MediaWrapper, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Column(modifier) {
        Text(media.nowPlaying ?: media.title ?: "", style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        val artist = media.artistName
        Text(
            if (artist.isNullOrBlank()) stringResource(R.string.nag_now_playing) else artist,
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean) {
    val colors = NagTheme.colors
    val size = NagTheme.dimens.minTarget.coerceAtLeast(36.dp)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size - 4.dp).clip(CircleShape).background(colors.accent))
        NagIconButton(
            icon = if (playing) NagIcons.Pause else NagIcons.Play,
            contentDescription = stringResource(if (playing) R.string.nag_pause else R.string.nag_play),
            onClick = PlaybackActions::togglePlayPause,
            size = size,
            tint = colors.onAccent,
        )
    }
}

private fun cycleRepeat() {
    val service = PlaybackService.instance ?: return
    service.repeatType = when (service.repeatType) {
        PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
        PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
        else -> PlaybackStateCompat.REPEAT_MODE_NONE
    }
}
