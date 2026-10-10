package app.nagmedar.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons

enum class NavSection(@StringRes val title: Int?) {
    Library(R.string.nag_section_library),
    Sources(R.string.nag_section_sources),
    Tools(R.string.nag_section_tools),
    Footer(null),
}

/**
 * Top-level destinations of the navigation graph, in sidebar order. [comingIn] marks screens
 * that are placeholders in this build, with the MVP that delivers them.
 */
enum class Destination(
    val route: String,
    @StringRes val label: Int,
    val section: NavSection,
    @StringRes val description: Int? = null,
    val comingIn: String? = null,
) {
    Home("home", R.string.nag_nav_home, NavSection.Library),
    Movies("movies", R.string.nag_nav_movies, NavSection.Library),
    Series("series", R.string.nag_nav_series, NavSection.Library, R.string.nag_coming_series, "MVP1"),
    Music("music", R.string.nag_nav_music, NavSection.Library),
    Podcasts("podcasts", R.string.nag_nav_podcasts, NavSection.Library, R.string.nag_coming_podcasts, "MVP4"),
    Radio("radio", R.string.nag_nav_radio, NavSection.Library, R.string.nag_coming_radio, "MVP4"),
    Playlists("playlists", R.string.nag_nav_playlists, NavSection.Library, R.string.nag_coming_playlists, "MVP2"),
    Device("device", R.string.nag_nav_device, NavSection.Sources, R.string.nag_coming_device, "MVP1"),
    Network("network", R.string.nag_nav_network, NavSection.Sources, R.string.nag_coming_network, "MVP1"),
    Streams("streams", R.string.nag_nav_streams, NavSection.Sources, R.string.nag_coming_streams, "MVP1"),
    TvLab("tvlab", R.string.nag_nav_tv_lab, NavSection.Tools, R.string.nag_coming_tv_lab, "MVP1"),
    Convert("convert", R.string.nag_nav_convert, NavSection.Tools, R.string.nag_coming_convert, "MVP3"),
    Downloads("downloads", R.string.nag_nav_downloads, NavSection.Tools, R.string.nag_coming_downloads, "MVP4"),
    Settings("settings", R.string.nag_nav_settings, NavSection.Footer),
    More("more", R.string.nag_nav_more, NavSection.Footer);

    val icon: ImageVector
        get() = when (this) {
            Home -> NagIcons.Home
            Movies -> NagIcons.Movies
            Series -> NagIcons.Series
            Music -> NagIcons.Music
            Podcasts -> NagIcons.Podcasts
            Radio -> NagIcons.Radio
            Playlists -> NagIcons.PlaylistAdd
            Device -> NagIcons.Device
            Network -> NagIcons.Network
            Streams -> NagIcons.Streams
            TvLab -> NagIcons.TvLab
            Convert -> NagIcons.Convert
            Downloads -> NagIcons.Downloads
            Settings -> NagIcons.Settings
            More -> NagIcons.Menu
        }

    /** Shorter label for the compact bottom bar. */
    @get:StringRes
    val shortLabel: Int
        get() = when (this) {
            Movies -> R.string.nag_nav_video
            Podcasts -> R.string.nag_nav_podcasts_short
            else -> label
        }

    companion object {
        val bottomBar = listOf(Home, Movies, Music, Podcasts, More)
        val rail = listOf(Home, Movies, Series, Music, Podcasts, Radio, TvLab)
        val railFooter = listOf(More, Settings)
        val sidebar = entries.filter { it != More }

        /** Destinations reachable through "More" when [shown] are already in the navigation chrome. */
        fun overflow(shown: Collection<Destination>) = entries.filter { it != More && it !in shown }

        fun fromRoute(route: String?): Destination = entries.firstOrNull { it.route == route } ?: Home
    }
}
