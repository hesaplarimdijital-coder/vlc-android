package app.nagmedar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.screens.PlaceholderScreen
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.MiniPlayer
import app.nagmedar.ui.playback.PlayerBarActions
import app.nagmedar.ui.playback.PlayerBarInfo
import app.nagmedar.ui.playback.TransportBar
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.NagTheme

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
        badges = mapOf(Destination.Movies to "128", Destination.Music to "4 812", Destination.Podcasts to "4 yeni"),
        onSearch = {},
        menus = {
            MenuBarMenus(
                listOf("Medya", "Oynatma", "Ses", "Video", "Altyazı", "Araçlar", "Görünüm", "Yardım").map { title ->
                    MenuSpec(title) { listOf(MenuEntry.Item("$title…", {})) }
                },
            )
        },
        player = { kind -> SamplePlayerBar(kind) },
        navController = navController,
        screen = screen,
    )
}

@Composable
fun SampleScreen(destination: Destination) {
    when (destination) {
        Destination.Movies -> Column(Modifier.fillMaxSize()) {
            PageHeader(title = "Filmler", subtitle = "128 video") {
                NagIconButton(NagIcons.Grid, "Izgara görünümü", {})
                NagIconButton(NagIcons.ListView, "Liste görünümü", {}, selected = true)
            }
            DenseTable(
                columns = listOf(
                    TableColumn<SampleMovie>("Ad", ColumnWidth.Weight(1f)) { it.title },
                    TableColumn<SampleMovie>("Çözünürlük", ColumnWidth.Fixed(140.dp), mono = true) { it.resolution },
                    TableColumn<SampleMovie>("Süre", ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { it.duration },
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
            PageHeader(title = "Ana sayfa")
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
fun SamplePlayerBar(kind: NavKind, info: PlayerBarInfo = samplePlayerInfo, actions: PlayerBarActions = NoPlayerActions) {
    val artwork: @Composable (Dp) -> Unit = { size -> Artwork(media = null, size = size, video = info.isVideo) }
    if (kind == NavKind.Sidebar) {
        TransportBar(info, { 0.42f }, actions, artwork)
    } else {
        MiniPlayer(info, { 0.42f }, actions, artwork, floating = kind == NavKind.BottomBar)
    }
}
