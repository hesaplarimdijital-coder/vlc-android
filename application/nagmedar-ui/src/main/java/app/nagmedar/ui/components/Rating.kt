package app.nagmedar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * S-808: a rating as five stars, [rating] of them filled (0: none). With [onRate] a click on a
 * star sets the rating to it, a click on the rating's own star takes it back, and a resting mouse
 * shows what a click would set. [focusable], it is one control for the keyboard (← and → a star
 * less or more, 0–5, Delete for none); a screen reader hears "Rating: 3 of 5" and can change it.
 * Each star is [starSize] in a [target] square that takes the clicks.
 */
@Composable
fun RatingStars(
    rating: Int,
    onRate: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    starSize: Dp = 14.dp,
    target: Dp = starSize + 4.dp,
    focusable: Boolean = false,
    filledTint: Color = NagTheme.colors.accent,
    emptyTint: Color = NagTheme.colors.textTertiary,
) {
    val stars = rating.coerceIn(0, 5)
    val description = if (stars == 0) stringResource(R.string.nag_rating_none) else stringResource(R.string.nag_rating_value, stars)
    val focus = remember { MutableInteractionSource() }
    val state = focus.collectState()
    val hovers = remember { List(5) { MutableInteractionSource() } }
    // the star a resting mouse is on: what a click would set
    val hovered = hovers.mapIndexedNotNull { i, source -> if (source.collectIsHoveredAsState().value) i + 1 else null }.lastOrNull()
    val keys = if (focusable && onRate != null) {
        Modifier
            .focusRing(state, RoundedCornerShape(4.dp))
            .focusable(interactionSource = focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val next = when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> stars - 1
                    Key.DirectionRight, Key.DirectionUp -> stars + 1
                    Key.Delete, Key.Backspace, Key.Zero, Key.NumPad0 -> 0
                    Key.One, Key.NumPad1 -> 1
                    Key.Two, Key.NumPad2 -> 2
                    Key.Three, Key.NumPad3 -> 3
                    Key.Four, Key.NumPad4 -> 4
                    Key.Five, Key.NumPad5 -> 5
                    else -> return@onPreviewKeyEvent false
                }.coerceIn(0, 5)
                if (next != stars) onRate(next)
                true
            }
    } else {
        Modifier
    }
    Row(
        modifier
            .then(keys)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (onRate != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(stars.toFloat(), 0f..5f, steps = 4)
                    setProgress { value ->
                        onRate(value.roundToInt().coerceIn(0, 5))
                        true
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (star in 1..5) {
            val shown = hovered ?: stars
            val filled = star <= shown
            val clicks = if (onRate != null) {
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(hovers[star - 1])
                    .clickable(interactionSource = hovers[star - 1], indication = null) { onRate(if (star == stars) 0 else star) }
            } else {
                Modifier
            }
            // the stars are the mouse's and the finger's; a screen reader sets the whole (above)
            Box(Modifier.size(target).then(clicks).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Icon(
                    if (filled) NagIcons.StarFilled else NagIcons.Star,
                    contentDescription = null,
                    tint = when {
                        !filled -> emptyTint
                        hovered != null && star > stars -> filledTint.copy(alpha = 0.55f)
                        else -> filledTint
                    },
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}
