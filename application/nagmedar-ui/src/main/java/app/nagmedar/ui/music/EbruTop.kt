package app.nagmedar.ui.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.CollapsingTop
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.rememberCollapsingTopState
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.shell.HeightClass
import app.nagmedar.ui.shell.LocalHeightClass
import app.nagmedar.ui.shell.LocalWindowRootInsets
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme

/**
 * 1.1.3: a page with the Music page's ebru top (Radio's, Podcasts'): the band in the inks of what plays and the masthead
 * ([title], [subtitle]) with [header] under it, which fold away as the page's lists scroll towards their end and come
 * back at their top ([CollapsingTop]); [pinned] (the page's tabs, its search field) stays, the lists ([content]) under it.
 *
 * On a phone the band lies under the status bar with the chips over it: the brand's mark, which names [section] (the tab
 * the page is on) once the band has gone from under it, [onSearch]'s chip and [options]' chip; [covered]: a page of this
 * one over all of it (a show's), whose colour then lies under the status bar. On a tablet's or a DeX window the band
 * across the top (the design's tall one on a tall window), [onSearch]'s field over it by touch, and the masthead with
 * [actions] beside it.
 */
@Composable
internal fun EbruPage(
    title: String,
    subtitle: String?,
    section: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    options: (() -> List<MenuEntry>)? = null,
    covered: () -> Boolean = { false },
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
    pinned: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // 1.1.5: what plays of any kind (a station's logo, an episode's picture too)
    val inks = rememberEbruInks(rememberNowPlaying().media)
    val top = rememberCollapsingTopState()
    val compact = currentWidthClass() == WidthClass.Compact
    val phoneTop = remember { PhoneTop() }
    val density = LocalDensity.current
    val statusTop = if (compact) with(density) { (LocalWindowRootInsets.current?.top?.getTop(this) ?: 0).toDp() } else 0.dp
    val short = LocalHeightClass.current == HeightClass.Compact
    val band = wideBandHeight(LocalConfiguration.current.screenHeightDp, short)
    // with a pointer the menu bar has its own search field
    val bandSearch = onSearch.takeIf { NagTheme.inputMode != InputMode.Pointer }
    // folded more than halfway, what has faded under a phone's chips is neither touched nor read out (the station's
    // button among it)
    val faded by remember(top) { derivedStateOf { top.fraction > 0.5f } }
    // (the lists in the same place in every width: a phone turned, a DeX window resized keeps them where they were)
    Box(
        modifier
            .fillMaxSize()
            .then(if (compact) Modifier.onGloballyPositioned { phoneTop.pageTop = it.positionInWindow().y } else Modifier)
            .testTag("ebru-page"),
    ) {
        CompositionLocalProvider(LocalPhoneTop provides phoneTop.takeIf { compact }) {
            CollapsingTop(
                top,
                header = {
                    Column(Modifier.fillMaxWidth().testTag("ebru-header")) {
                        if (compact) {
                            // a phone's band under the status bar; folded away, what is left of it lies under the chips'
                            // scrim, not under the status bar's icons
                            EbruBand(inks, statusTop + PHONE_BAND, Modifier.reportsBand(gone = { top.fraction > 0.98f }))
                            // what stays under the chips as the top folds fades beneath them
                            Column(
                                Modifier
                                    .graphicsLayer { alpha = 1f - top.fraction }
                                    .then(if (faded) Modifier.clearAndSetSemantics {}.noTouches() else Modifier),
                            ) {
                                Masthead(subtitle, title)
                                header()
                                Spacer(Modifier.height(8.dp))
                            }
                        } else {
                            if (band > 0.dp) WideBand(inks, band, bandSearch)
                            WideMasthead(subtitle, null, null, large = band >= TABLET_BAND, title = title, pills = false) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // a window too short for the band (a phone on its side): the band's search field's place
                                    if (band == 0.dp && bandSearch != null) {
                                        NagIconButton(NagIcons.Search, stringResource(R.string.nag_search), bandSearch, modifier = Modifier.testTag("music-search"))
                                    }
                                    actions()
                                }
                            }
                            header()
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                },
                reserve = if (compact) statusTop + PHONE_CHIPS_ROOM else 0.dp,
                pinned = pinned,
                content = content,
            )
            if (compact) {
                PhoneMusicChips(
                    statusTop = statusTop,
                    inks = inks,
                    onBack = null,
                    onSearch = onSearch,
                    options = options,
                    marked = false,
                    section = section,
                    covered = covered,
                )
            }
        }
    }
}

/** Nothing in it takes a touch: what has faded away under the chips. */
private fun Modifier.noTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
