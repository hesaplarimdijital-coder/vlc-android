package app.nagmedar.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
data class SampleTrack(val title: String, val artist: String, val duration: String)

val sampleTracks = listOf(
    SampleTrack("Sabaha Kadar", "Ada Karan", "4:18"),
    SampleTrack("Gece Yolcusu", "Cemre Kaya", "4:14"),
    SampleTrack("Neva Kâr", "Kadri Erdem", "8:18"),
    SampleTrack("Yağmur", "Cemre Kaya", "3:51"),
    SampleTrack("Liman Işıkları", "Deniz Aral", "5:02"),
    SampleTrack("Tuz ve Kül", "Ada Karan", "3:47"),
    SampleTrack("Uzak Sinyal", "Kuzey Ekibi", "6:20"),
    SampleTrack("Cam Şehir", "Deniz Aral", "4:39"),
)

/** The real shell with sample screens and a sample transport bar. */
@Composable
fun TestShell(
    widthClass: WidthClass,
    startDestination: Destination = Destination.Music,
    sidebarCollapsed: Boolean = false,
    menuBar: MenuBarState? = null,
    /** "Sıradaki" open (S-310): its button lit, the panel in [overlay]. */
    queueOpen: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    /** A desktop window's caption bar (S-306). */
    caption: CaptionBar? = null,
    /** What the bars show (S-1302: casting). */
    playerInfo: PlayerBarInfo = samplePlayerInfo,
    screen: @Composable (Destination, ShellNavigator) -> Unit = { destination, _ -> SampleScreen(destination) },
) {
    val navController = rememberNavController()
    LaunchedEffect(startDestination) {
        if (startDestination != Destination.Music) ShellNavigator(navController).navigate(startDestination)
    }
    NagmedarShell(
        widthClass = widthClass,
        sidebarCollapsed = sidebarCollapsed,
        onSidebarCollapsedChange = {},
        // sample counts; the podcasts' "new" is the sample's own word (S-1501: the screens in English too)
        badges = mapOf(
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
        player = { kind -> SamplePlayerBar(kind, info = playerInfo, queueOpen = queueOpen) },
        navController = navController,
        overlay = overlay,
        caption = caption,
        screen = screen,
    )
}

@Composable
fun SampleScreen(destination: Destination) {
    when (destination) {
        Destination.Music -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.nag_nav_music), subtitle = pluralStringResource(R.plurals.nag_tracks_count, 4812, 4812)) {
                NagIconButton(NagIcons.Grid, stringResource(R.string.nag_view_grid), {})
                NagIconButton(NagIcons.ListView, stringResource(R.string.nag_view_list), {}, selected = true)
            }
            DenseTable(
                columns = listOf(
                    TableColumn<SampleTrack>(stringResource(R.string.nag_column_title), ColumnWidth.Weight(1f)) { it.title },
                    TableColumn<SampleTrack>(stringResource(R.string.nag_column_artist), ColumnWidth.Fixed(140.dp)) { it.artist },
                    TableColumn<SampleTrack>(stringResource(R.string.nag_column_duration), ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.duration },
                ),
                rows = sampleTracks,
                key = { it.title },
                selectedKey = "Sabaha Kadar",
                sort = TableSort(0, true),
                onSortChange = {},
                onRowClick = { _, _ -> },
            )
        }
        // S-1201: the TV Lab with the sample film analysed
        Destination.TvLab -> TvLabContent(sampleTvLabView(), NoTvLabActions)
        else -> Column(Modifier.fillMaxSize()) { PageHeader(title = stringResource(destination.label)) }
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
