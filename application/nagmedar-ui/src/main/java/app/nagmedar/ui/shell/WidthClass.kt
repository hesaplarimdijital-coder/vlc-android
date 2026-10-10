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
