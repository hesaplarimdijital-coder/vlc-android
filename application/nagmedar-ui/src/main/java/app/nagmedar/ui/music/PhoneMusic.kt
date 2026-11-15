package app.nagmedar.ui.music

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.shell.LocalShellOverlayHost
import app.nagmedar.ui.theme.Bricolage
import app.nagmedar.ui.theme.BodoniHeadline
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.SemiBoldPlus
import app.nagmedar.ui.theme.rememberNagHaptics
import java.util.Locale
import kotlin.math.roundToInt

// 1.1.1: the phone's Music page as the design draws it (Main.dc.html and its list view): its band of marbled paper under
// the status bar, the brand's, the search's and the options' chips over the band, the masthead with the counts, the ways
// in as tabs, the toolbar (Karıştır, the order, covers or rows), the letters in the ink with how many each has, the covers
// two across with the playing one's ring, and the letters' wave down the list's end.

/** The band's height under the status bar: 164 dp under a 28 dp one, as the design has it. */
internal val PHONE_BAND = 136.dp

/** The album page's band, shorter: the chips and a strip of the paper under them. */
internal val PHONE_PAGE_BAND = 76.dp

/** The chips' row: its top under the status bar, a chip's height. */
private val CHIPS_TOP = 8.dp
private val CHIP = 48.dp

/** How far the list passes under the chips before their scrim is whole. */
private val CHIPS_FADE = 40.dp

/** What the chips keep clear above the list's first rows under them: the status bar, then this. */
internal val PHONE_CHIPS_ROOM = CHIPS_TOP + CHIP + 8.dp

/** The room a list keeps at its end for the letters' wave, and the wave's width. */
internal val WAVE_ROOM = 52.dp
private val WAVE_WIDTH = 48.dp

/** The design's sans text: [size] on [line] at [weight], growing with the window's text step as the theme's styles do. */
@Composable
internal fun designText(size: Float, line: Float, weight: FontWeight = FontWeight.Normal): TextStyle {
    val type = NagTheme.type
    return TextStyle(fontFamily = Bricolage, fontWeight = weight, fontSize = type.size(size), lineHeight = type.size(line))
}

/** A chip over the band: the page's colour, a little of the band through it. */
@Composable
private fun chipColor(): Color {
    val colors = NagTheme.colors
    return if (colors.isDark) colors.background.copy(alpha = 0.86f) else colors.surface.copy(alpha = 0.90f)
}

/** The design's off tick: the text's colour, faint. */
@Composable
internal fun tickOff(): Color = NagTheme.colors.text.copy(alpha = 0.14f)

// ------------------------------------------------------------------- where the band is

/**
 * One header's band on screen: its bottom in the window (px), as it was last laid out; 1.1.3: [gone] while it is folded
 * away under the chips (what is left of it lies under their scrim).
 */
@Stable
internal class BandPlace {
    var bottom by mutableFloatStateOf(Float.MAX_VALUE)
    var gone: () -> Boolean = { false }
}

/**
 * Where a phone's Music page's band is: each header composed with one tells it ([reportsBand]); the newest is the one on
 * screen (the album page over the list). The chips' scrim comes up as the band goes under them, and the status bar's
 * icons follow what lies beneath them.
 */
@Stable
internal class PhoneTop {
    internal val bands = mutableStateListOf<BandPlace>()
    var pageTop by mutableFloatStateOf(0f)

    /**
     * The band's bottom from the page's top (px); null while no band is composed (the list scrolled past its header) or
     * it has folded away.
     */
    fun bandBottom(): Float? = bands.lastOrNull()?.takeIf { !it.gone() }?.let { it.bottom - pageTop }
}

internal val LocalPhoneTop = staticCompositionLocalOf<PhoneTop?> { null }

/** This band tells the page's [PhoneTop] where it is while it is composed; [gone]: while it has folded away. */
@Composable
internal fun Modifier.reportsBand(gone: (() -> Boolean)? = null): Modifier {
    val top = LocalPhoneTop.current ?: return this
    val place = remember { BandPlace() }
    place.gone = gone ?: NEVER_GONE
    DisposableEffect(top, place) {
        top.bands.add(place)
        onDispose { top.bands.remove(place) }
    }
    return onGloballyPositioned { place.bottom = it.positionInWindow().y + it.size.height }
}

private val NEVER_GONE: () -> Boolean = { false }

/**
 * While [paper] (the band's light paper under the status bar) the status bar's icons are dark, whatever the theme; the
 * theme's again once the page's colour lies there, or the page goes.
 */
@Composable
private fun StatusIconsOverPaper(paper: Boolean) {
    val view = LocalView.current
    val dark = NagTheme.colors.isDark
    val window = remember(view) { view.context.activity()?.window } ?: return
    DisposableEffect(window, view, paper, dark) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = paper || !dark
        onDispose { controller.isAppearanceLightStatusBars = !dark }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

// ------------------------------------------------------------------- the chips

/**
 * The chips over the band, which stay where they are as the list scrolls: the brand's (a group's page: the way back), the
 * search's, the options' ([options] null: none). Once the list's rows pass beneath them the page's colour comes up behind
 * them and the status bar; 1.1.3: the brand's mark then names [section] (the tab the page is on: "Albümler",
 * "Favoriler"), in place of 1.1.1's "Nağmedar". [optionsAnchor] lies over the options' chip (the filter's panel opens
 * under it); [covered]: a page over the whole of this one (a show's on a phone), whose colour lies under the status bar.
 */
@Composable
internal fun PhoneMusicChips(
    statusTop: Dp,
    inks: EbruInks,
    onBack: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    options: (() -> List<MenuEntry>)?,
    marked: Boolean,
    modifier: Modifier = Modifier,
    section: String? = null,
    covered: () -> Boolean = NOT_COVERED,
    optionsAnchor: @Composable BoxScope.() -> Unit = {},
) {
    val top = LocalPhoneTop.current
    val colors = NagTheme.colors
    val density = LocalDensity.current
    val statusPx = with(density) { statusTop.toPx() }
    val rowBottom = with(density) { (statusTop + CHIPS_TOP + CHIP).toPx() }
    val fade = with(density) { CHIPS_FADE.toPx() }
    val host = LocalShellOverlayHost.current
    val paper by remember(top, statusPx) { derivedStateOf { (top?.bandBottom() ?: Float.NEGATIVE_INFINITY) > statusPx / 2f } }
    val latestCovered = rememberUpdatedState(covered)
    val over by remember(host) { derivedStateOf { latestCovered.value() || (host != null && (host.expandProgress() > 0.5f || host.windowCovered)) } }
    // the band gone from under the chips' row: the page's colour behind them, the tab's name beside the mark. A band says
    // where it is once laid out, after the chips' first frame: until then the chip names nothing (no name flashing as a
    // page opens)
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        withFrameNanos {}
        settled = true
    }
    val named by remember(top, rowBottom) { derivedStateOf { settled && (top?.bandBottom() ?: Float.NEGATIVE_INFINITY) < rowBottom } }
    StatusIconsOverPaper(paper && !over)
    val scrim = colors.background
    Box(modifier.fillMaxWidth().testTag("music-chips")) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    // how far the band has gone from under the chips: 0 while it lies there, 1 once the rows pass beneath
                    val away = top?.bandBottom()?.let { ((rowBottom + fade - it) / fade).coerceIn(0f, 1f) } ?: 1f
                    if (away <= 0f) return@drawBehind
                    val solid = ((statusPx + CHIPS_TOP.toPx() + CHIP.toPx() / 2f) / size.height).coerceIn(0.01f, 0.98f)
                    drawRect(
                        Brush.verticalGradient(
                            0f to scrim.copy(alpha = 0.96f * away),
                            solid to scrim.copy(alpha = 0.86f * away),
                            1f to scrim.copy(alpha = 0f),
                        ),
                    )
                },
        )
        Box(
            Modifier
                .padding(top = statusTop + CHIPS_TOP, start = 12.dp, end = 12.dp, bottom = 16.dp)
                .fillMaxWidth()
                .height(CHIP),
        ) {
            // the brand's chip opens towards the others, never under them: a long name is cut short
            val trailing = (if (onSearch != null) 1 else 0) + (if (options != null) 1 else 0)
            if (onBack != null) {
                ChipButton(NagIcons.ChevronLeft, stringResource(R.string.nag_music_back), onBack, Modifier.align(Alignment.CenterStart).testTag("music-back"))
            } else {
                BrandChip(inks, section, named, Modifier.align(Alignment.CenterStart).padding(end = (CHIP + 8.dp) * trailing))
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onSearch != null) ChipButton(NagIcons.Search, stringResource(R.string.nag_search), onSearch, Modifier.testTag("music-search"))
                if (options != null) {
                    var open by remember { mutableStateOf(false) }
                    var height by remember { mutableIntStateOf(0) }
                    Box(Modifier.onSizeChanged { height = it.height }) {
                        ChipButton(NagIcons.MoreVertical, stringResource(R.string.nag_music_options), { open = true }, Modifier.testTag("music-options"),
                            dot = marked, selected = open)
                        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) options() else emptyList(), offset = IntOffset(0, height))
                        optionsAnchor()
                    }
                }
            }
        }
    }
}

private val NOT_COVERED: () -> Boolean = { false }

/**
 * The brand's chip: the band's mark in the ink's ring, a round chip while the band lies under it; 1.1.3: once the band
 * has gone from under it ([named]) it opens and names [section] in Bodoni's italic, the tab the page is on.
 */
@Composable
private fun BrandChip(inks: EbruInks, section: String?, named: Boolean, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val type = NagTheme.type
    val instant = NagTheme.motion.reduced || LocalMotionTier.current == MotionTier.Reduced
    // the last name stays while the chip closes over it
    var last by remember { mutableStateOf(section) }
    if (section != null) last = section
    // the mark alone is still the brand to TalkBack ("Nağmedar"); open, the chip reads its tab's name
    val shown = named && section != null
    val brand = stringResource(R.string.nagmedar_shell_title)
    Row(
        modifier
            .height(CHIP)
            .clip(RoundedCornerShape(CHIP / 2))
            .background(chipColor())
            .padding(horizontal = 15.dp)
            .semantics(mergeDescendants = true) { if (!shown) contentDescription = brand }
            .testTag("music-brand"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandMark(inks, Modifier.size(18.dp))
        AnimatedVisibility(
            visible = shown,
            enter = if (instant) EnterTransition.None else {
                expandHorizontally(tween(NagMotion.MEDIUM, easing = NagMotion.Emphasized), expandFrom = Alignment.Start) +
                    fadeIn(tween(NagMotion.MEDIUM, delayMillis = NagMotion.SHORT / 2))
            },
            exit = if (instant) ExitTransition.None else {
                shrinkHorizontally(tween(NagMotion.MEDIUM, easing = NagMotion.Emphasized), shrinkTowards = Alignment.Start) + fadeOut(tween(NagMotion.SHORT))
            },
        ) {
            // another tab chosen while the chip is open: its name fades in over the last one's
            AnimatedContent(
                last.orEmpty(),
                transitionSpec = {
                    if (instant) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(NagMotion.MEDIUM, delayMillis = NagMotion.SHORT / 2)) togetherWith fadeOut(tween(NagMotion.SHORT))
                    } using SizeTransform(clip = false)
                },
                label = "chipSection",
            ) { name ->
                Text(
                    name,
                    style = TextStyle(fontFamily = BodoniHeadline, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = type.size(20f), lineHeight = type.size(22f)),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 1.dp).testTag("music-section"),
                )
            }
        }
    }
}

/** 1.1.1: the brand's mark: a crop of the band in a circle, the ink's ring around it. */
@Composable
internal fun BandMark(inks: EbruInks, modifier: Modifier = Modifier, ring: Dp = 1.5.dp) {
    val ink = NagTheme.colors.accent
    Box(
        modifier
            .drawBehind { drawCircle(ink, radius = size.minDimension / 2f + ring.toPx() / 2f, style = Stroke(ring.toPx())) }
            .clip(CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // (too small to be made before the eyes: it fades into the next album's)
        EbruBand(inks, 26.dp, Modifier.requiredSize(90.dp, 26.dp), marbled = false)
    }
}

/** A round chip over the band: [icon] in the text's colour; [dot]: something holds (a filter). */
@Composable
private fun ChipButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Boolean = false, selected: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    Box(
        modifier
            .size(CHIP)
            .pressScale(state.pressed, 0.92f)
            .focusRing(state, CircleShape)
            .clip(CircleShape)
            .background(chipColor())
            .background(stateBackground(state, selected))
            .nagClickable(interaction, onClick, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.onSelection else colors.text, modifier = Modifier.size(22.dp))
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 11.dp)
                    .size(8.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

// ------------------------------------------------------------------- the header

/**
 * The phone page's header, the first item of its list or grid, scrolling away with it: the band from the window's top
 * (under the status bar, [statusTop]), the masthead with [counts], the ways in as tabs, the toolbar ([shuffle] the shown
 * tracks), then what holds the list ([filtered], [onClearFilters]) and [below] (the converter's job).
 */
@Composable
internal fun PhoneMusicHeader(
    inks: EbruInks,
    statusTop: Dp,
    counts: String?,
    state: MusicBrowserState,
    shuffle: (() -> Unit)?,
    filtered: String?,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().testTag("music-header")) {
        EbruBand(inks, statusTop + PHONE_BAND, Modifier.reportsBand())
        Masthead(counts)
        Spacer(Modifier.height(8.dp))
        ViewTabs(state)
        Spacer(Modifier.height(8.dp))
        PhoneToolbar(state, shuffle)
        if (filtered != null) FilteredLine(filtered, onClearFilters)
        below()
        Spacer(Modifier.height(2.dp))
    }
}

/** The page's top while there is no list yet (loading, nothing found, no permission): the band and the masthead. */
@Composable
internal fun PhoneMusicTopOnly(inks: EbruInks, statusTop: Dp, counts: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        EbruBand(inks, statusTop + PHONE_BAND, Modifier.reportsBand())
        Masthead(counts)
    }
}

/** "Müzikler" (1.1.3: another page's [title]) in Bodoni's italic, the counts under it. */
@Composable
internal fun Masthead(counts: String?, title: String? = null) {
    val colors = NagTheme.colors
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title ?: stringResource(R.string.nag_music_masthead),
            style = NagTheme.type.masthead.copy(letterSpacing = (-0.015).em),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (counts != null) {
            Text(counts, style = designText(14f, 20f), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The ways in on a phone, in the order the design has them; the recently played last, past the fade. */
internal val PHONE_VIEWS = listOf(
    MusicView.Tracks, MusicView.Artists, MusicView.Albums, MusicView.Genres, MusicView.Composers, MusicView.Years, MusicView.Folders,
    MusicView.Recent,
)

/**
 * The ways in as a strip of tabs over a rule, the chosen one in the text's colour over the ink's line; it scrolls
 * sideways, the page's colour over the tabs past its end while there are some. A phone's runs from the window's edges
 * ([start] 4 dp: the first tab's words line up with the page's); 1.1.2: the tablet's inside the page's margins, its
 * first tab's words on the margin and the rule from there ([ruleStart]).
 */
@Composable
internal fun ViewTabs(state: MusicBrowserState, modifier: Modifier = Modifier, start: Dp = 4.dp, fade: Boolean = true, ruleStart: Dp = 0.dp) {
    val colors = NagTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rule = colors.border
    val page = colors.background
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                val inset = ruleStart.toPx()
                drawRect(rule, Offset(if (rtl) 0f else inset, size.height - 1.dp.toPx()), Size(size.width - inset, 1.dp.toPx()))
            },
    ) {
        Row(
            Modifier
                .height(47.dp)
                .horizontalScroll(scroll)
                .padding(start = start, end = if (fade) 44.dp else 0.dp)
                .selectableGroup()
                .testTag("music-views"),
        ) {
            PHONE_VIEWS.forEach { view -> ViewTab(stringResource(viewLabel(view)), view == state.view) { state.open(view) } }
        }
        // more tabs past the end: the page's colour comes over them
        if (fade && scroll.value < scroll.maxValue) Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(64.dp)
                .height(47.dp)
                .drawBehind {
                    val clear = page.copy(alpha = 0f)
                    drawRect(if (rtl) Brush.horizontalGradient(0f to page, 0.28f to page, 1f to clear) else Brush.horizontalGradient(0f to clear, 0.72f to page, 1f to page))
                },
        )
    }
}

@Composable
private fun ViewTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val ink = colors.accent
    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val requester = remember { BringIntoViewRequester() }
    // the chosen tab in sight (Klasörler past the fade, chosen from elsewhere)
    LaunchedEffect(selected) { if (selected) runCatching { requester.bringIntoView() } }
    Box(
        Modifier
            .fillMaxHeight()
            .bringIntoViewRequester(requester)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = designText(15f, 20f, if (selected) SemiBoldPlus else FontWeight.Medium),
            color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp)
                    .drawBehind {
                        // the ink's line along the tab's foot, its top corners round
                        val h = 3.dp.toPx()
                        val line = Path().apply {
                            addRoundRect(RoundRect(0f, size.height - h, size.width, size.height, topLeftCornerRadius = CornerRadius(h), topRightCornerRadius = CornerRadius(h)))
                        }
                        drawPath(line, ink)
                    },
            )
        }
    }
}

/** Karıştır in the ink; the order; the covers or the rows. */
@Composable
private fun PhoneToolbar(state: MusicBrowserState, shuffle: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .testTag("music-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShufflePill(shuffle)
        Spacer(Modifier.weight(1f))
        SortButton(state)
        if (state.view.grouped) LayoutSegment(state)
    }
}

/** "Karıştır": the shown tracks in a random order, an ink-filled pill. */
@Composable
internal fun ShufflePill(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(24.dp)
    val enabled = onClick != null
    val fill = if (enabled) colors.accent else colors.accent.copy(alpha = 0.38f)
    Row(
        modifier
            .height(48.dp)
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .clip(shape)
            .background(fill)
            .background(if (state.pressed) colors.onAccent.copy(alpha = 0.16f) else if (state.hovered) colors.onAccent.copy(alpha = 0.08f) else Color.Transparent)
            .nagClickable(interaction, { onClick?.invoke() }, enabled = enabled)
            .padding(start = 13.dp, end = 17.dp)
            .testTag("music-shuffle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(NagIcons.Shuffle, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.nag_shuffle), style = designText(15f, 20f, SemiBoldPlus), color = colors.onAccent, maxLines = 1)
    }
}

/** "Oynat": an outlined pill, for a group's page beside Karıştır. */
@Composable
internal fun PlayPill(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(24.dp)
    val enabled = onClick != null
    Row(
        modifier
            .height(48.dp)
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .clip(shape)
            .background(stateBackground(state))
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(colors.borderStrong, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(size.height / 2), style = Stroke(w))
            }
            .nagClickable(interaction, { onClick?.invoke() }, enabled = enabled)
            .padding(start = 14.dp, end = 18.dp)
            .testTag("music-play"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(NagIcons.Play, contentDescription = null, tint = if (enabled) colors.text else colors.textTertiary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.nag_play), style = designText(15f, 20f, FontWeight.SemiBold), color = if (enabled) colors.text else colors.textTertiary, maxLines = 1)
    }
}

/** What an order is to the button: its short label ("A–Z"), its name ("Ada göre"), its words for TalkBack. */
private class SortWords(val label: String, val said: String, val name: String)

/**
 * The order's button: "A–Z" and a chevron (a tablet's says the order's name: "Ada göre", [named]); its menu has the
 * orders and their two directions.
 */
@Composable
internal fun SortButton(state: MusicBrowserState, named: Boolean = false, height: Dp = 48.dp) {
    val colors = NagTheme.colors
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val st = interaction.collectState()
    val shape = RoundedCornerShape(24.dp)
    val words = sortWords(state)
    val entries = sortEntries(state)
    Box(Modifier.onSizeChanged { anchor = it.height }) {
        Row(
            Modifier
                .height(height)
                .focusRing(st, shape)
                .clip(shape)
                .background(stateBackground(st, open))
                .nagClickable(interaction, { open = true }, onClickLabel = words.said)
                .semantics { contentDescription = words.said }
                .padding(start = if (named) 14.dp else 10.dp, end = if (named) 10.dp else 6.dp)
                .testTag("music-sort"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (named) 4.dp else 2.dp),
        ) {
            Text(if (named) words.name else words.label, style = designText(14f, 20f, FontWeight.SemiBold), color = if (open) colors.onSelection else colors.textSecondary, maxLines = 1)
            Icon(NagIcons.ChevronDown, contentDescription = null, tint = if (open) colors.onSelection else colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        MenuPopup(visible = open, onDismiss = { open = false }, entries = if (open) entries else emptyList(), offset = IntOffset(0, anchor))
    }
}

/** The kinds of order the button tells apart: by words, by a date, by a length. */
private enum class SortKind { Words, Date, Length }

private fun kindOf(order: GroupOrder): SortKind = when (order) {
    GroupOrder.Name, GroupOrder.Artist -> SortKind.Words
    GroupOrder.Year, GroupOrder.Added -> SortKind.Date
}

private fun kindOf(column: MusicColumn): SortKind = when (column) {
    MusicColumn.Year, MusicColumn.Added, MusicColumn.LastPlayed -> SortKind.Date
    MusicColumn.Duration -> SortKind.Length
    else -> SortKind.Words
}

/** The orders a track list's button offers. */
private val TRACK_ORDERS = listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Album, MusicColumn.Year, MusicColumn.Added, MusicColumn.Duration)

@Composable
private fun orderName(order: GroupOrder): String = stringResource(
    when (order) {
        GroupOrder.Name -> R.string.nag_sort_name
        GroupOrder.Artist -> R.string.nag_sort_artist
        GroupOrder.Year -> R.string.nag_sort_year
        GroupOrder.Added -> R.string.nag_sort_added
    },
)

@Composable
private fun orderName(column: MusicColumn): String = stringResource(
    when (column) {
        MusicColumn.Artist, MusicColumn.AlbumArtist -> R.string.nag_sort_artist
        MusicColumn.Album -> R.string.nag_sort_album
        MusicColumn.Year -> R.string.nag_sort_year
        MusicColumn.Added -> R.string.nag_sort_added
        MusicColumn.Duration -> R.string.nag_sort_duration
        else -> R.string.nag_sort_name
    },
)

/** The two directions of a kind of order, the rising one first: "A'dan Z'ye", "Eskiden yeniye", "Kısadan uzuna". */
@Composable
private fun directions(kind: SortKind): Pair<String, String> = when (kind) {
    SortKind.Words -> stringResource(R.string.nag_sort_dir_az) to stringResource(R.string.nag_sort_dir_za)
    SortKind.Date -> stringResource(R.string.nag_sort_dir_old) to stringResource(R.string.nag_sort_dir_new)
    SortKind.Length -> stringResource(R.string.nag_sort_dir_short) to stringResource(R.string.nag_sort_dir_long)
}

/** The button's label: "A–Z" or "Z–A" for an order by words, the column's name ("Yıl", "Eklenme", "Süre") for another. */
@Composable
private fun sortLabel(kind: SortKind, ascending: Boolean, name: String): String = when (kind) {
    SortKind.Words -> stringResource(if (ascending) R.string.nag_sort_az else R.string.nag_sort_za)
    else -> name
}

@Composable
private fun sortWords(state: MusicBrowserState): SortWords {
    if (state.view.grouped) {
        val sort = state.groupSort ?: GroupSort.default(state.view)
        val kind = kindOf(sort.order)
        val (up, down) = directions(kind)
        val short = stringResource(if (sort.order == GroupOrder.Year) R.string.nag_column_year else R.string.nag_column_added)
        return SortWords(
            sortLabel(kind, sort.ascending, short),
            stringResource(R.string.nag_sort_described, orderName(sort.order).lowercase(currentLocaleOf()), if (sort.ascending) up.lowercase(currentLocaleOf()) else down.lowercase(currentLocaleOf())),
            orderName(sort.order),
        )
    }
    val sort = effectiveSort(state) ?: MusicSort(MusicColumn.Title, true)
    val kind = kindOf(sort.column)
    val (up, down) = directions(kind)
    return SortWords(
        sortLabel(kind, sort.ascending, stringResource(sort.column.label)),
        stringResource(R.string.nag_sort_described, orderName(sort.column).lowercase(currentLocaleOf()), if (sort.ascending) up.lowercase(currentLocaleOf()) else down.lowercase(currentLocaleOf())),
        orderName(sort.column),
    )
}

@Composable
private fun currentLocaleOf(): Locale = app.nagmedar.ui.library.currentLocale()

/** The menu of orders: each order, checked when it is the list's; then its two directions. */
@Composable
private fun sortEntries(state: MusicBrowserState): List<MenuEntry> {
    if (state.view.grouped) {
        val sort = state.groupSort ?: GroupSort.default(state.view)
        val (up, down) = directions(kindOf(sort.order))
        val orders = GroupSort.choices(state.view).map { order ->
            val name = orderName(order)
            MenuEntry.Item(name, {
                // a date's order starts with the newest, words with A
                state.groupSort = GroupSort(order, kindOf(order) == SortKind.Words).takeIf { it != GroupSort.default(state.view) }
            }, checked = order == sort.order)
        }
        return orders + MenuEntry.Separator + listOf(
            MenuEntry.Item(up, { state.groupSort = sort.copy(ascending = true).takeIf { it != GroupSort.default(state.view) } }, checked = sort.ascending),
            MenuEntry.Item(down, { state.groupSort = sort.copy(ascending = false).takeIf { it != GroupSort.default(state.view) } }, checked = !sort.ascending),
        )
    }
    val sort = effectiveSort(state) ?: MusicSort(MusicColumn.Title, true)
    val (up, down) = directions(kindOf(sort.column))
    val orders = TRACK_ORDERS.map { column ->
        val name = orderName(column)
        MenuEntry.Item(name, { state.sort = MusicSort(column, kindOf(column) == SortKind.Words) }, checked = column == sort.column)
    }
    return orders + MenuEntry.Separator + listOf(
        MenuEntry.Item(up, { state.sort = sort.copy(ascending = true) }, checked = sort.ascending),
        MenuEntry.Item(down, { state.sort = sort.copy(ascending = false) }, checked = !sort.ascending),
    )
}

/** The covers or the rows: two round buttons on the surface, the chosen one raised ([button]: a phone's 48 dp, a tablet's 44). */
@Composable
internal fun LayoutSegment(state: MusicBrowserState, button: Dp = 48.dp, inset: Dp = 0.dp) {
    val colors = NagTheme.colors
    val grid = state.layout == MusicLayout.Grid
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(inset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(inset),
    ) {
        SegmentButton(NagIcons.Grid, stringResource(R.string.nag_music_layout_grid), grid, "music-layout-grid", button) { state.changeLayout(MusicLayout.Grid) }
        SegmentButton(NagIcons.ListView, stringResource(R.string.nag_music_layout_list), !grid, "music-layout-list", button) { state.changeLayout(MusicLayout.List) }
    }
}

@Composable
private fun SegmentButton(icon: ImageVector, label: String, selected: Boolean, tag: String, side: Dp, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val haptics = rememberNagHaptics()
    Box(
        Modifier
            .size(side)
            .focusRing(state, CircleShape)
            .clip(CircleShape)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .background(stateBackground(state))
            .semantics { this.selected = selected }
            .nagClickable(interaction, {
                if (!selected) haptics.tick()
                onClick()
            }, role = Role.RadioButton, onClickLabel = label)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.text else colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/** What holds the list now ("Süzülen: Yarım kalanlar, 24 bit"), and the button that lets it all go. */
@Composable
private fun FilteredLine(text: String, onClear: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp)
            .testTag("music-filtered"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.nag_music_filtered, text), style = designText(13f, 18f), color = colors.textSecondary, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .focusRing(state, shape)
                .clip(shape)
                .background(stateBackground(state))
                .nagClickable(interaction, onClear)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.nag_clear_filters), style = designText(14f, 20f, FontWeight.SemiBold), color = colors.accentText, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------- the letters' wave

/** A letter of the wave: how many items it has (0: none in the list), the list's index of its heading (-1: none). */
@Immutable
internal class WaveLetter(val letter: String, val count: Int, val index: Int)

/** A heading of a lettered list: its letter, how many items it has, its index in the lazy list. */
@Immutable
internal class LetterRun(val letter: String, val count: Int, val index: Int)

/**
 * [alphabet] (the interface language's, [rememberAlphabet]: Turkish's 29 letters, English's 26) with the list's [runs]: the
 * letters it has with their counts, the others empty; a heading outside the alphabet ("#", another script's letter, the
 * nameless) before it when it comes first in the list, after it otherwise.
 */
internal fun waveLetters(runs: List<LetterRun>, alphabet: List<String>): List<WaveLetter> {
    val known = alphabet.toHashSet()
    val byLetter = HashMap<String, LetterRun>()
    runs.forEach { byLetter.putIfAbsent(it.letter, it) }
    val inside = alphabet.map { l -> byLetter[l]?.let { WaveLetter(l, it.count, it.index) } ?: WaveLetter(l, 0, -1) }
    val outside = runs.filter { it.letter !in known }.distinctBy { it.letter }
    val firstInside = runs.indexOfFirst { it.letter in known }
    val before = outside.filter { r -> firstInside < 0 || runs.indexOf(r) < firstInside }.map { WaveLetter(it.letter, it.count, it.index) }
    val after = outside.filter { r -> firstInside >= 0 && runs.indexOf(r) > firstInside }.map { WaveLetter(it.letter, it.count, it.index) }
    return before + inside + after
}

/**
 * The letters of a list down its end (the design's "Harfe atla"): a short dash for each letter of the alphabet, longer as
 * the letter has more, in the ink for the letter at the list's top; a finger on it (a tap, a slide) takes the list to a
 * letter, the letter shown beside the finger. TalkBack hears it as a slider over the letters. [top] and [bottom]: where
 * it starts and ends in the list's height (px, read while laying out: the header scrolling away gives it room).
 */
@Composable
internal fun LetterWave(
    letters: List<WaveLetter>,
    current: () -> Int,
    onJump: (WaveLetter) -> Unit,
    top: () -> Int,
    bottom: () -> Int,
    modifier: Modifier = Modifier,
    /**
     * 1.1.2 (the design's tablet page): [WaveStyle.Named] beside a tablet's covers, each letter written before its dash,
     * those in sight ([range]: the letters of the list's first and last items in sight) in the text's colour over a soft
     * band, their dashes in the ink; [WaveStyle.Thin] beside a tablet's rows, the dashes alone, thinner.
     */
    style: WaveStyle = WaveStyle.Phone,
    range: (() -> IntRange)? = null,
) {
    if (letters.count { it.count > 0 } < 2) return
    val colors = NagTheme.colors
    val density = LocalDensity.current
    val haptics = rememberNagHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val most = remember(letters) { letters.maxOf { it.count }.coerceAtLeast(1) }
    val currentLetter by remember(letters) { derivedStateOf { current() } }
    val inSight by remember(letters, range) { derivedStateOf { range?.invoke() } }
    var touched by remember { mutableStateOf<WaveLetter?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val latestJump by rememberUpdatedState(onJump)
    val ink = colors.accent
    val some = colors.textTertiary
    val none = tickOff()
    val label = stringResource(R.string.nag_music_jump)
    val n = letters.size
    val waveWidth = if (style == WaveStyle.Thin) 44.dp else WAVE_WIDTH
    val widthPx = with(density) { waveWidth.roundToPx() }
    // the named wave's letters, measured once: bold in sight, regular out of it
    val measurer = rememberTextMeasurer()
    val letterSize = NagTheme.type.size(12f)
    val named = remember(letters, measurer, letterSize, style) {
        if (style != WaveStyle.Named) emptyList() else letters.map { l ->
            val plain = TextStyle(fontSize = letterSize, lineHeight = letterSize, fontWeight = FontWeight.Medium)
            measurer.measure(l.letter, plain) to measurer.measure(l.letter, plain.copy(fontWeight = FontWeight.Bold))
        }
    }
    val text = colors.text
    /** The letter with items nearest to slot [slot]. */
    fun nearest(slot: Int): WaveLetter? {
        for (d in 0 until n) {
            letters.getOrNull(slot - d)?.takeIf { it.count > 0 }?.let { return it }
            letters.getOrNull(slot + d)?.takeIf { it.count > 0 }?.let { return it }
        }
        return null
    }
    Box(
        modifier
            .layout { measurable, constraints ->
                val from = top().coerceIn(0, constraints.maxHeight)
                val to = bottom().coerceIn(from, constraints.maxHeight)
                val placeable = measurable.measure(Constraints.fixed(widthPx, to - from))
                layout(widthPx, constraints.maxHeight) { placeable.place(0, from) }
            }
            .semantics {
                contentDescription = label
                stateDescription = letters.getOrNull(currentLetter)?.letter.orEmpty()
                progressBarRangeInfo = ProgressBarRangeInfo(currentLetter.coerceAtLeast(0).toFloat(), 0f..(n - 1).toFloat(), steps = (n - 2).coerceAtLeast(0))
                setProgress { target ->
                    val to = nearest(target.roundToInt().coerceIn(0, n - 1))
                    if (to != null) latestJump(to)
                    to != null
                }
            }
            .pointerInput(letters) {
                fun pick(y: Float) {
                    fingerY = y
                    val slot = (y / size.height * n).toInt().coerceIn(0, n - 1)
                    val to = nearest(slot) ?: return
                    if (to !== touched) {
                        touched = to
                        haptics.tick()
                        latestJump(to)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    touched = null
                }
            }
            .drawBehind {
                val slot = size.height / n
                val shown = touched?.let { letters.indexOf(it) } ?: currentLetter
                val sight = inSight?.takeIf { it.first >= 0 && it.last >= it.first }
                when (style) {
                    WaveStyle.Phone -> {
                        val bar = 4.dp.toPx()
                        letters.forEachIndexed { i, l ->
                            val w = if (l.count > 0) (6f + l.count.toFloat() / most * 22f).dp.toPx() else 4.dp.toPx()
                            val colour = when {
                                i == shown -> ink
                                l.count > 0 -> some
                                else -> none
                            }
                            drawRoundRect(colour, Offset((size.width - w) / 2f, slot * i + (slot - bar) / 2f), Size(w, bar), CornerRadius(bar / 2f))
                        }
                    }
                    WaveStyle.Named -> {
                        // the letters in sight on a soft band, the design's 6 % of the text's colour
                        if (sight != null) {
                            drawRoundRect(text.copy(alpha = 0.06f), Offset(0f, slot * sight.first), Size(size.width, slot * (sight.last - sight.first + 1)), CornerRadius(10.dp.toPx()))
                        }
                        val bar = 4.dp.toPx()
                        val letterBox = 12.dp.toPx()
                        val start = 6.dp.toPx()
                        letters.forEachIndexed { i, l ->
                            val lit = (sight?.contains(i) ?: false) || i == shown
                            val (plain, bold) = named[i]
                            val layout = if (lit) bold else plain
                            val letterColour = when {
                                lit -> text
                                l.count > 0 -> some
                                else -> text.copy(alpha = 0.28f)
                            }
                            val lx = if (rtl) size.width - start - letterBox + (letterBox - layout.size.width) / 2f else start + (letterBox - layout.size.width) / 2f
                            drawText(layout, color = letterColour, topLeft = Offset(lx, slot * i + (slot - layout.size.height) / 2f))
                            val w = if (l.count > 0) (4f + l.count.toFloat() / most * 20f).dp.toPx() else 3.dp.toPx()
                            val barColour = when {
                                lit -> ink
                                l.count > 0 -> text.copy(alpha = 0.34f)
                                else -> text.copy(alpha = 0.12f)
                            }
                            val bx = start + letterBox + 6.dp.toPx()
                            drawRoundRect(barColour, Offset(if (rtl) size.width - bx - w else bx, slot * i + (slot - bar) / 2f), Size(w, bar), CornerRadius(bar / 2f))
                        }
                    }
                    WaveStyle.Thin -> {
                        val bar = 3.dp.toPx()
                        letters.forEachIndexed { i, l ->
                            val lit = (sight?.contains(i) ?: false) || i == shown
                            val w = if (l.count > 0) (3f + l.count.toFloat() / most * 15f).dp.toPx() else 2.dp.toPx()
                            val colour = when {
                                lit -> ink
                                l.count > 0 -> text.copy(alpha = 0.34f)
                                else -> text.copy(alpha = 0.12f)
                            }
                            drawRoundRect(colour, Offset((size.width - w) / 2f, slot * i + (slot - bar) / 2f), Size(w, bar), CornerRadius(2.dp.toPx()))
                        }
                    }
                }
            }
            .testTag("music-wave"),
    ) {
        val shownLetter = touched
        if (shownLetter != null) {
            // the letter beside the finger, towards the list
            val bubble = 56.dp
            Box(
                Modifier
                    .offset {
                        val x = if (rtl) waveWidth.roundToPx() + 8.dp.roundToPx() else -(bubble + 8.dp).roundToPx()
                        IntOffset(x, (fingerY - bubble.toPx() / 2f).roundToInt())
                    }
                    .size(bubble)
                    .shadow(8.dp, CircleShape)
                    .background(colors.surfaceRaised, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(shownLetter.letter, style = TextStyle(fontFamily = BodoniHeadline, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = NagTheme.type.size(28f)), color = colors.accentText)
            }
        }
    }
}

/** 1.1.2: how a [LetterWave] is drawn: a phone's, a tablet's beside its covers (with the letters), beside its rows. */
internal enum class WaveStyle { Phone, Named, Thin }

/** The interface language's alphabet, its letters in its order (a string of the language's resources). */
@Composable
internal fun rememberAlphabet(): List<String> {
    val letters = stringResource(R.string.nag_music_alphabet)
    return remember(letters) { letters.codePoints().toArray().map { String(Character.toChars(it)) } }
}

/** The index in [letters] of the letter whose heading is at or above [firstVisible], the list's first item in sight. */
internal fun currentWaveLetter(letters: List<WaveLetter>, firstVisible: Int): Int {
    var best = -1
    var bestIndex = -1
    letters.forEachIndexed { i, l ->
        if (l.index in 0..firstVisible && l.index > bestIndex) {
            best = i
            bestIndex = l.index
        }
    }
    return if (best >= 0) best else letters.indexOfFirst { it.count > 0 }
}

// ------------------------------------------------------------------- rows

/** A track's cover, [corner] round, its album's sign on the raised surface while there is none. */
@Composable
internal fun TrackCover(track: Track, size: Dp, modifier: Modifier = Modifier, corner: Dp = 5.dp) {
    val colors = NagTheme.colors
    val cover = remember(track.uri) { CoverTrack.of(track) }
    val px = with(LocalDensity.current) { (size * 1.5f).roundToPx() }
    val image = rememberCover(cover, px)
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(NagIcons.Music, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** The playing cover's ring: [width] of the ink, [gap] outside the cover's edge, drawn round its [corner]. */
internal fun Modifier.coverRing(on: Boolean, color: Color, corner: Dp, gap: Dp = 3.dp, width: Dp = 2.dp): Modifier =
    if (!on) this else drawBehind {
        val g = gap.toPx()
        val w = width.toPx()
        val out = g + w / 2f
        drawRoundRect(color, Offset(-out, -out), Size(size.width + out * 2f, size.height + out * 2f), CornerRadius(corner.toPx() + out), style = Stroke(w))
    }

/** A group's sign in a row when it has no cover: an artist's or a composer's initial in a circle, a genre's or a folder's icon. */
@Composable
internal fun GroupSign(view: MusicView, title: String?, size: Dp = 48.dp) {
    val colors = NagTheme.colors
    when (view) {
        MusicView.Artists, MusicView.Composers -> {
            val letter = title?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
            Box(Modifier.size(size).background(colors.selection, CircleShape), contentAlignment = Alignment.Center) {
                Text(letter, style = TextStyle(fontFamily = BodoniHeadline, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = NagTheme.type.size(22f)),
                    color = colors.onSelection, maxLines = 1)
            }
        }
        else -> {
            val icon = when (view) {
                MusicView.Folders -> NagIcons.Folder
                MusicView.Years -> NagIcons.History
                MusicView.Recent -> NagIcons.History
                else -> NagIcons.Music
            }
            Box(Modifier.size(size).background(colors.surfaceRaised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** The style of a letter heading's letter: Bodoni's italic, 30 on 40, a weight heavier than the display's. */
@Composable
internal fun letterStyle(): TextStyle {
    val type = NagTheme.type
    return TextStyle(fontFamily = BodoniHeadline, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = type.size(30f), lineHeight = type.size(40f))
}

/** The first item of a lazy list or grid not hidden under [covered] px at its top (the chips), from its items in sight. */
internal fun firstUnder(items: List<Pair<Int, Int>>, offsets: (Int) -> Int, covered: Int): Int {
    for ((index, size) in items) if (offsets(index) + size > covered) return index
    return items.firstOrNull()?.first ?: 0
}
