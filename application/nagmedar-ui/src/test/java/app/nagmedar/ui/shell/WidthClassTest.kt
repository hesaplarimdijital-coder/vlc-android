package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class WidthClassTest {

    @Test
    fun thresholds() {
        assertEquals(WidthClass.Compact, WidthClass.fromWidth(411f))
        assertEquals(WidthClass.Compact, WidthClass.fromWidth(599.9f))
        assertEquals(WidthClass.Medium, WidthClass.fromWidth(600f))
        assertEquals(WidthClass.Expanded, WidthClass.fromWidth(840f))
        assertEquals(WidthClass.Large, WidthClass.fromWidth(1200f))
        assertEquals(WidthClass.ExtraLarge, WidthClass.fromWidth(1600f))
        assertEquals(WidthClass.ExtraLarge, WidthClass.fromWidth(2960f))
    }

    @Test
    fun navigationChrome() {
        assertEquals(NavKind.BottomBar, NavKind.forWidth(WidthClass.Compact))
        assertEquals(NavKind.Rail, NavKind.forWidth(WidthClass.Medium))
        assertEquals(NavKind.Rail, NavKind.forWidth(WidthClass.Expanded))
        assertEquals(NavKind.Sidebar, NavKind.forWidth(WidthClass.Large))
        assertEquals(NavKind.Sidebar, NavKind.forWidth(WidthClass.ExtraLarge))
    }

    @Test
    fun everyDestinationIsReachableFromEveryChrome() {
        val chromes = mapOf(
            NavKind.BottomBar to Destination.bottomBar,
            NavKind.Rail to Destination.rail + Destination.railFooter,
            NavKind.Sidebar to Destination.sidebar,
        )
        for ((kind, shown) in chromes) {
            val reachable = shown.toSet() + if (Destination.More in shown) Destination.overflow(shown).toSet() else emptySet()
            val missing = Destination.entries.filter { it != Destination.More && it !in reachable }
            assertEquals("$kind misses $missing", emptyList<Destination>(), missing)
        }
    }
}
