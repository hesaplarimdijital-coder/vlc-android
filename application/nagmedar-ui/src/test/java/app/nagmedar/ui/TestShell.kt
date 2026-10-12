package app.nagmedar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuBarState
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.input.menuBarSpecs
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.MiniPlayer
import app.nagmedar.ui.playback.PlayerBarActions
import app.nagmedar.ui.playback.PlayerBarInfo
import app.nagmedar.ui.playback.TransportBar
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.screens.PlaceholderScreen
import app.nagmedar.ui.shell.CaptionBar
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.NoTvLabActions
import app.nagmedar.ui.tvlab.TvLabContent
import app.nagmedar.ui.tvlab.sampleTvLabView

/** Sample rows for screenshots and layout tests (the medialibrary is native and not available in JVM tests). */
data class SampleMovie(val title: String, val resolution: String, val duration: String)

val sampleMovies = listOf(
    SampleMovie("Kuzey Işığı", "2160p DV P7", "2:11:45"),
    SampleMovie("Sessiz Liman", "2160p HDR10", "1:52:07"),
    SampleMovie("Demir Vadi", "2160p DV P8", "2:04:31"),
    SampleMovie("Mavi Kanal", "1080p", "1:38:12"),
    SampleMovie("Kırık Pusula", "2160p HDR10+", "2:20:03"),
    SampleMovie("Tuz ve Kül", "2160p DV P8", "1:47:55"),
    SampleMovie("Uzak Sinyal", "2160p HDR10", "2:02:40"),
    SampleMovie("Cam Şehir", "2160p DV P5", "1:59:19"),
)

/** The real shell with sample screens and a sample transport bar. */
@Composable
fun TestShell(
    widthClass: WidthClass,
    startDestination: Destination = Destination.Home,
    sidebarCollapsed: Boolean = false,
    menuBar: MenuBarState? = null,
    /** "Sıradaki" open (S-310): its button lit, the panel in [overlay]. */
    queueOpen: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    /** A desktop window's caption bar (S-306). */
    caption: CaptionBar? = null,
    screen: @Composable (Destination, ShellNavigator) -> Unit = { destination, _ -> SampleScreen(destination) },
) {
    val navController = rememberNavController()
    LaunchedEffect(startDestination) {
        if (startDestination != Destination.Home) ShellNavigator(navController).navigate(startDestination)
    }
    NagmedarShell(
        widthClass = widthClass,
        sidebarCollapsed = sidebarCollapsed,
        onSidebarCollapsedChange = {},
        // sample counts; the podcasts' "new" is the sample's own word (S-1501: the screens in English too)
        badges = mapOf(
            Destination.Movies to "128",
            Destination.Music to "4 812",
            Destination.Podcasts to if (currentLocale().language == "tr") "4 yeni" else "4 new",
        ),
        onSearch = {},
        menus = {
            // the app's menus, from the shortcut table (S-302)
            val res = LocalContext.current.resources
            val specs = remember(res) { menuBarSpecs(res, {}, { true }) }
            if (menuBar != null) MenuBarMenus(specs, menuBar) else MenuBarMenus(specs)
        },
        player = { kind -> SamplePlayerBar(kind, queueOpen = queueOpen) },
        navController = navController,
        overlay = overlay,
        caption = caption,
        screen = screen,
    )
}

@Composable
fun SampleScreen(destination: Destination) {
    when (destination) {
        Destination.Movies -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.nag_nav_movies), subtitle = pluralStringResource(R.plurals.nag_films_count, 128, 128)) {
                NagIconButton(NagIcons.Grid, stringResource(R.string.nag_view_grid), {})
                NagIconButton(NagIcons.ListView, stringResource(R.string.nag_view_list), {}, selected = true)
            }
            DenseTable(
                columns = listOf(
                    TableColumn<SampleMovie>(stringResource(R.string.nag_column_title), ColumnWidth.Weight(1f)) { it.title },
                    TableColumn<SampleMovie>(stringResource(R.string.nag_column_resolution), ColumnWidth.Fixed(140.dp), mono = true) { it.resolution },
                    TableColumn<SampleMovie>(stringResource(R.string.nag_column_duration), ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.duration },
                ),
                rows = sampleMovies,
                key = { it.title },
                selectedKey = "Kuzey Işığı",
                sort = TableSort(0, true),
                onSortChange = {},
                onRowClick = { _, _ -> },
            )
        }
        Destination.Home -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.nag_nav_home))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                sampleMovies.take(3).forEach { movie ->
                    Column(Modifier.width(200.dp)) {
                        Box(Modifier.fillMaxWidth().height(112.dp).background(NagTheme.colors.surfaceRaised, RoundedCornerShape(6.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(movie.title, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text)
                        Text(movie.duration, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
                    }
                }
            }
        }
        // S-1201: the TV Lab with the sample film analysed
        Destination.TvLab -> TvLabContent(sampleTvLabView(), NoTvLabActions)
        else -> PlaceholderScreen(destination, onOpenClassic = {})
    }
}

/** What the sample bars play. */
val samplePlayerInfo = PlayerBarInfo(
    title = "Sabaha Kadar",
    subtitle = "Ada Karan · Gece Mavisi",
    isVideo = false,
    playing = true,
    lengthMs = 258_000,
    format = "FLAC 24/96",
    volume = 70,
)

/** Player actions that do nothing: the shell's screenshots only show the bars. */
object NoPlayerActions : PlayerBarActions {
    override fun openPlayer() = Unit

    override fun togglePlayPause() = Unit

    override fun previous() = Unit

    override fun next() = Unit

    override fun seekTo(fraction: Float) = Unit

    override fun stop() = Unit

    override fun shuffle() = Unit

    override fun cycleRepeat() = Unit

    override fun setVolume(volume: Int) = Unit
}

/** The real player bars (S-304) with [samplePlayerInfo], 42 % played. */
@Composable
fun SamplePlayerBar(kind: NavKind, info: PlayerBarInfo = samplePlayerInfo, actions: PlayerBarActions = NoPlayerActions, queueOpen: Boolean = false) {
    val artwork: @Composable (Dp) -> Unit = { size -> Artwork(media = null, size = size, video = info.isVideo) }
    if (kind == NavKind.Sidebar) {
        TransportBar(info, { 0.42f }, actions, artwork, queueOpen = queueOpen)
    } else {
        MiniPlayer(info, { 0.42f }, actions, artwork, floating = kind == NavKind.BottomBar, queueOpen = queueOpen)
    }
}
