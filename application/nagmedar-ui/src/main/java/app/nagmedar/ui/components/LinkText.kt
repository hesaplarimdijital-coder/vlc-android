package app.nagmedar.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.nagmedar.ui.theme.NagTheme

/**
 * Words that open something (a player's artist and album): underlined under the pointer and with the focus, which
 * draws the ring around them; reached by Tab, opened by Enter or Space; [clickLabel] says what a click does. Without
 * [onClick], plain text.
 */
@Composable
fun LinkText(
    text: String,
    onClick: (() -> Unit)?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    clickLabel: String? = null,
    maxLines: Int = 1,
) {
    if (onClick == null) {
        Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val lit = state.hovered || state.focused
    // a target's least size (S-1407): 24 dp with a pointer, 48 dp for a finger, more than the line's own height
    val target = NagTheme.dimens.minTarget
    Box(
        modifier
            .sizeIn(minWidth = target, minHeight = target)
            .focusRing(state, RoundedCornerShape(NagTheme.dimens.radiusSmall))
            .nagClickable(interaction, onClick, onClickLabel = clickLabel)
            .testTag("link"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = if (lit) style.copy(textDecoration = TextDecoration.Underline) else style,
            color = if (lit || state.pressed) colors.text else color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Artist · Album" with each a [LinkText] (S-1001: the players' line under the title): the two share the width by
 * their length, each cut short when it does not fit. Either may be missing.
 */
@Composable
fun ArtistAlbumLine(
    artist: String?,
    album: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onArtist: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    artistLabel: String? = null,
    albumLabel: String? = null,
) {
    val shownArtist = artist?.takeIf { it.isNotBlank() }
    val shownAlbum = album?.takeIf { it.isNotBlank() }
    if (shownArtist == null && shownAlbum == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (shownArtist != null) {
            LinkText(shownArtist, onArtist, style, color, Modifier.weight(shownArtist.length.coerceAtLeast(4).toFloat(), fill = false), clickLabel = artistLabel)
        }
        if (shownArtist != null && shownAlbum != null) Text(" · ", style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (shownAlbum != null) {
            LinkText(shownAlbum, onAlbum, style, color, Modifier.weight(shownAlbum.length.coerceAtLeast(4).toFloat(), fill = false), clickLabel = albumLabel)
        }
    }
}
