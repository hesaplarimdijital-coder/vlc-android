package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 1): a count or a dot on the icons of the phone's bar, the rail and the
 * collapsed sidebar; "Daha fazla" has a dot when a page it lists has a badge; TalkBack reads the entry and the badge.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class NavIconBadgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val badges = mapOf(
        Destination.Podcasts to NavIconBadge(4, "4 yeni bölüm"),
        Destination.Downloads to NavIconBadge(),
    )

    private fun shell(widthClass: WidthClass, iconBadges: NavIconBadges = badges, sidebarCollapsed: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, sidebarCollapsed = sidebarCollapsed, iconBadges = iconBadges)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun thePhonesBarShowsTheCountAndADotOnMore() {
        shell(WidthClass.Compact)
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithContentDescription("Podcast, 4 yeni bölüm").assertExists()
        // the downloads are among "Daha fazla"'s pages: a dot there
        compose.onNodeWithContentDescription("Daha fazla, Yeni").assertExists()
        compose.onAllNodesWithTag("nav-badge", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun pastNinetyNineIsNinetyNinePlus() {
        shell(WidthClass.Compact, mapOf(Destination.Podcasts to NavIconBadge(120)))
        compose.onNodeWithText("99+").assertExists()
        compose.onNodeWithContentDescription("Podcast, 120 yeni").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun theRailShowsThemToo() {
        shell(WidthClass.Expanded)
        compose.onNodeWithText("4").assertExists()
    }

    /** The open sidebar keeps its text beside the entries; a collapsed one shows the marks on the icons. */
    @Test
    @Config(qualifiers = "tr-w1400dp-h900dp-mdpi")
    fun anOpenSidebarKeepsItsText() {
        shell(WidthClass.Large)
        compose.onAllNodesWithTag("nav-badge", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "tr-w1400dp-h900dp-mdpi")
    fun aCollapsedSidebarShowsTheMarks() {
        shell(WidthClass.Large, sidebarCollapsed = true)
        compose.onAllNodesWithTag("nav-badge", useUnmergedTree = true).assertCountEquals(2)
    }
}
