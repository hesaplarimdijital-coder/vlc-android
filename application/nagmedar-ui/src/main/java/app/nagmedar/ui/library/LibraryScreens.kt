package app.nagmedar.ui.library

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.cast.CastPayload
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaUtils

/**
 * Context menu of a media item; the same entries serve right click, long press and the Menu key.
 * "Play on …" opens the device picker for it (S-1302).
 * [info] shows the details beside the list; without it the information window opens.
 * [select] starts selecting by touch (S-310): the menu's "Seç".
 * A file of this device is shared ("Paylaş…") or shown in its folder ("Dosyada göster").
 */
fun mediaMenu(context: Context, media: MediaWrapper, play: () -> Unit, info: (() -> Unit)? = null, select: (() -> Unit)? = null): List<MenuEntry> {
    val res = context.resources
    return listOfNotNull(
        MenuEntry.Item(res.getString(R.string.nag_play), play, shortcut = "Enter", icon = NagIcons.Play),
        MenuEntry.Item(res.getString(R.string.nag_play_next), { MediaUtils.insertNext(context, media) }, icon = NagIcons.Next),
        MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { MediaUtils.appendMedia(context, media) }, icon = NagIcons.PlaylistAdd),
        // S-1302: on a Chromecast or a DLNA renderer, chosen in the device picker; a tracker module plays here alone
        if (Casting.enabled(context) && Casting.offered(media.uri)) {
            MenuEntry.Item(res.getString(R.string.nag_cast_play_on), { Casting.openPicker(CastPayload(listOf(media), 0)) }, icon = NagIcons.Cast)
        } else {
            null
        },
        select?.let { MenuEntry.Item(res.getString(R.string.nag_select), it, icon = NagIcons.Check) },
        MenuEntry.Separator,
        MenuEntry.Item(res.getString(R.string.nag_sc_media_info), info ?: { MediaInfoRequests.open(media) }, icon = NagIcons.Info),
        // the file itself: to another app, or where it is
        *fileMenuEntries(context, MediaFile.of(media)).toTypedArray(),
    )
}

// ---------------------------------------------------------------- shelves

/** While the medialibrary scans, the screens reload this often so new media appear. */
private const val RELOAD_WHILE_SCANNING_MS = 4_000L

/** Loads with [load] now and, while a scan runs, again every few seconds. */
internal suspend fun <T> ProduceStateScope<T?>.loadWhileScanning(scanning: Boolean, load: suspend () -> T) {
    while (true) {
        value = load()
        if (!scanning) break
        delay(RELOAD_WHILE_SCANNING_MS)
    }
}

@Composable
internal fun ShelfTitle(text: String) {
    Text(
        text,
        style = NagTheme.type.title,
        color = NagTheme.colors.text,
        modifier = Modifier
            .padding(start = NagTheme.dimens.pagePadding, top = NagTheme.dimens.gapL, bottom = NagTheme.dimens.gapS)
            .semantics { heading() },
    )
}

@Composable
internal fun TrackCard(media: MediaWrapper, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier = modifier
            .focusRing(state, shape)
            .background(if (state.hovered || state.pressed) stateBackground(state) else colors.background, shape)
            .nagClickable(interaction, onClick, onClickLabel = media.title)
            .padding(6.dp),
    ) {
        Artwork(media, size = 138.dp, corner = dimens.radiusControl)
        Spacer(Modifier.height(8.dp))
        Text(media.title ?: "", style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(media.artistName ?: stringResource(R.string.nag_unknown_artist), style = NagTheme.type.labelSmall,
            color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
