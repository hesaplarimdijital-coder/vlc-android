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
    Series("series", R.string.nag_nav_series, NavSection.Library),
    Music("music", R.string.nag_nav_music, NavSection.Library),
    Podcasts("podcasts", R.string.nag_nav_podcasts, NavSection.Library, R.string.nag_podcasts_description),
    Radio("radio", R.string.nag_nav_radio, NavSection.Library),
    Playlists("playlists", R.string.nag_nav_playlists, NavSection.Library),
    Device("device", R.string.nag_nav_device, NavSection.Sources, R.string.nag_coming_device, "MVP4"),
    Network("network", R.string.nag_nav_network, NavSection.Sources),
    Streams("streams", R.string.nag_nav_streams, NavSection.Sources, R.string.nag_coming_streams, "MVP4"),
    Convert("convert", R.string.nag_nav_convert, NavSection.Tools, R.string.nag_coming_convert),
    Downloads("downloads", R.string.nag_nav_downloads, NavSection.Tools, R.string.nag_coming_downloads, "MVP4"),
    Settings("settings", R.string.nag_nav_settings, NavSection.Footer),
    More("more", R.string.nag_nav_more, NavSection.Footer),

    /** S-311: the search page, opened from the search button and the palette, never listed in the navigation. */
    Search("search", R.string.nag_nav_search, NavSection.Footer),

    /** S-209: about and licences, opened from Settings, never listed in the navigation. */
    About("about", R.string.nag_settings_about, NavSection.Footer),

    /** S-906: the music engine's equaliser, opened from Settings › Music and Audio › Equaliser…, never listed. */
    Equalizer("equalizer", R.string.nag_eq_title, NavSection.Footer);

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
            Convert -> NagIcons.Convert
            Downloads -> NagIcons.Downloads
            Settings -> NagIcons.Settings
            More -> NagIcons.Menu
            Search -> NagIcons.Search
            About -> NagIcons.Info
            Equalizer -> NagIcons.Equalizer
        }

    /**
     * Shorter label for the bottom bar and the rail, where words are narrow; the same word as the
     * sidebar's and the page's title, only shorter (S-1501).
     */
    @get:StringRes
    val shortLabel: Int
        get() = when (this) {
            Podcasts -> R.string.nag_nav_podcasts_short
            else -> label
        }

    companion object {
        val bottomBar = listOf(Home, Movies, Music, Podcasts, More)
        val rail = listOf(Home, Movies, Series, Music, Podcasts, Radio)
        val railFooter = listOf(More, Settings)
        /** Pages opened from others, never listed in the navigation. */
        val unlisted = setOf(Search, About, Equalizer)

        val sidebar = entries.filter { it != More && it !in unlisted }

        /** Destinations reachable through "More" when [shown] are already in the navigation chrome. */
        fun overflow(shown: Collection<Destination>) = entries.filter { it != More && it !in unlisted && it !in shown }

        fun fromRoute(route: String?): Destination = entries.firstOrNull { it.route == route } ?: Home
    }
}
