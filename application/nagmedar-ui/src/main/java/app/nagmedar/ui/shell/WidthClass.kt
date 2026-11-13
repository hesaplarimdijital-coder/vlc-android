package app.nagmedar.ui.shell

/**
 * Window width classes (Material 3, including the large and extra-large classes added for
 * desktop windowing). Layout decisions follow the window, not the device: the same tablet is
 * Compact in a narrow DeX window and Extra-large on an external monitor.
 */
enum class WidthClass(val minWidthDp: Int) {
    Compact(0),
    Medium(600),
    Expanded(840),
    Large(1200),
    ExtraLarge(1600);

    companion object {
        fun fromWidth(widthDp: Float): WidthClass = when {
            widthDp >= ExtraLarge.minWidthDp -> ExtraLarge
            widthDp >= Large.minWidthDp -> Large
            widthDp >= Expanded.minWidthDp -> Expanded
            widthDp >= Medium.minWidthDp -> Medium
            else -> Compact
        }
    }
}

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's finding 10): window height classes (Material 3). A phone on its side is
 * Compact in height (under 480 dp) while its width makes it Medium or Expanded: it gets the rail as a tablet does, but
 * keeps the phone's text, sizes and motion, a top bar 8 dp lower and the player under the content.
 */
enum class HeightClass(val minHeightDp: Int) {
    Compact(0),
    Medium(480),
    Expanded(900);

    companion object {
        fun fromHeight(heightDp: Float): HeightClass = when {
            heightDp >= Expanded.minHeightDp -> Expanded
            heightDp >= Medium.minHeightDp -> Medium
            else -> Compact
        }
    }
}

/**
 * The window's [HeightClass], from its configuration's height ([app.nagmedar.ui.theme.NagmedarTheme]): the on-screen
 * keyboard does not make a phone short. Outside the theme [HeightClass.Expanded], so nothing changes there.
 */
val LocalHeightClass = androidx.compose.runtime.compositionLocalOf { HeightClass.Expanded }

/** Navigation chrome used for a width class. */
enum class NavKind {
    /** Compact: bottom bar, mini player above it. */
    BottomBar,

    /** Medium and Expanded: navigation rail on the start edge. */
    Rail,

    /** Large and Extra-large: menu bar on top, collapsible sidebar, transport bar at the bottom. */
    Sidebar;

    companion object {
        fun forWidth(widthClass: WidthClass): NavKind = when (widthClass) {
            WidthClass.Compact -> BottomBar
            WidthClass.Medium, WidthClass.Expanded -> Rail
            WidthClass.Large, WidthClass.ExtraLarge -> Sidebar
        }
    }
}
