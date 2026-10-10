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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.screens.PlaceholderScreen
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

@Composable
fun SamplePlayerBar(kind: NavKind) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().height(if (kind == NavKind.Sidebar) 72.dp else NagTheme.dimens.miniPlayerHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(colors.surfaceRaised, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sabaha kadar", style = NagTheme.type.bodyStrong, color = colors.text)
                Text("Ada Karan · Gece Mavisi", style = NagTheme.type.labelSmall, color = colors.textTertiary)
            }
            if (kind == NavKind.Sidebar) {
                NagSlider(value = 0.42f, onValueChange = {}, modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(12.dp))
            }
            NagIconButton(NagIcons.Previous, "Önceki parça", {})
            NagIconButton(NagIcons.Pause, "Duraklat", {})
            NagIconButton(NagIcons.Next, "Sonraki parça", {})
        }
    }
}
