package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 1.0.3 (Desktop Kit 2.3): the sidebar's footer entry shows its badge, as the entries above it do. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class SidebarBadgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFooterEntryShowsItsBadge() {
        compose.setContent {
            NagmedarTheme(inputMode = InputMode.Pointer) {
                Sidebar(
                    selected = Destination.Music,
                    onSelect = {},
                    collapsed = false,
                    onToggleCollapsed = {},
                    badges = mapOf(Destination.Music to "4 812", Destination.Settings to "1 yeni"),
                )
            }
        }
        compose.onNodeWithText("4 812").assertIsDisplayed()
        compose.onNodeWithText("1 yeni").assertIsDisplayed()
    }
}
