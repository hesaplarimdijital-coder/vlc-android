package app.nagmedar.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import org.videolan.tools.NAGMEDAR_DESTINATION_ALL_SETTINGS

enum class NavSection(@StringRes val title: Int?) {
    Library(R.string.nag_section_library),
    Sources(R.string.nag_section_sources),
    Tools(R.string.nag_section_tools),
    Footer(null),
}

/** Top-level destinations of the navigation graph, in sidebar order. */
enum class Destination(
    val route: String,
    @StringRes val label: Int,
    val section: NavSection,
    @StringRes val description: Int? = null,
) {
    Music("music", R.string.nag_nav_music, NavSection.Library),
    Podcasts("podcasts", R.string.nag_nav_podcasts, NavSection.Library, R.string.nag_podcasts_description),
    Radio("radio", R.string.nag_nav_radio, NavSection.Library),
    Playlists("playlists", R.string.nag_nav_playlists, NavSection.Library),
    Device("device", R.string.nag_nav_device, NavSection.Sources, R.string.nag_coming_device),
    Network("network", R.string.nag_nav_network, NavSection.Sources),
    Streams("streams", R.string.nag_nav_streams, NavSection.Sources, R.string.nag_coming_streams),
    Downloads("downloads", R.string.nag_nav_downloads, NavSection.Tools, R.string.nag_coming_downloads),
    Settings("settings", R.string.nag_nav_settings, NavSection.Footer),
    More("more", R.string.nag_nav_more, NavSection.Footer),

    /** S-311: the search page, opened from the search button and the palette, never listed in the navigation. */
    Search("search", R.string.nag_nav_search, NavSection.Footer),

    /** S-209: about and licences, opened from Settings, never listed in the navigation. */
    About("about", R.string.nag_settings_about, NavSection.Footer),

    /** S-906: the music engine's equaliser, opened from Settings › Music and Audio › Equaliser…, never listed. */
    Equalizer("equalizer", R.string.nag_eq_title, NavSection.Footer),

    /** S-1504: the user guide, opened from Settings, the Help menu (F1) and the palette, never listed in the navigation. */
    Guide("guide", R.string.nag_guide_title, NavSection.Footer),

    /** S-1504: every keyboard shortcut as it is now (Ctrl+/), never listed in the navigation. */
    ShortcutCard("shortcut-card", R.string.nag_shortcut_card_title, NavSection.Footer),

    /** VLC's preference pages drawn by Nağmedar, opened from Settings, never listed in the navigation. */
    AllSettings(NAGMEDAR_DESTINATION_ALL_SETTINGS, R.string.nag_prefs_title, NavSection.Footer);

    val icon: ImageVector
        get() = when (this) {
            Music -> NagIcons.Music
            Podcasts -> NagIcons.Podcasts
            Radio -> NagIcons.Radio
            Playlists -> NagIcons.PlaylistAdd
            Device -> NagIcons.Device
            Network -> NagIcons.Network
            Streams -> NagIcons.Streams
            Downloads -> NagIcons.Downloads
            Settings -> NagIcons.Settings
            More -> NagIcons.Menu
            Search -> NagIcons.Search
            About -> NagIcons.Info
            Equalizer -> NagIcons.Equalizer
            Guide -> NagIcons.Info
            ShortcutCard -> NagIcons.Keyboard
            AllSettings -> NagIcons.Settings
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
        /** The phone's bar (1.0): Müzik · Podcast · Radyo · ⋯, the rest in "Daha fazla". */
        val bottomBar = listOf(Music, Podcasts, Radio, More)
        val rail = listOf(Music, Podcasts, Radio)
        val railFooter = listOf(More, Settings)
        /** Pages opened from others, never listed in the navigation. */
        val unlisted = setOf(Search, About, Equalizer, Guide, ShortcutCard, AllSettings)

        val sidebar = entries.filter { it != More && it !in unlisted }

        /** Destinations reachable through "More" when [shown] are already in the navigation chrome. */
        fun overflow(shown: Collection<Destination>) = entries.filter { it != More && it !in unlisted && it !in shown }

        /** The page of [route]; Music, the start page, for none (or a page of an earlier version). */
        fun fromRoute(route: String?): Destination = entries.firstOrNull { it.route == route } ?: Music
    }
}
