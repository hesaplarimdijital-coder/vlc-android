package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.walkman.FakeWalkmanController
import app.nagmedar.ui.walkman.WalkmanScreen
import app.nagmedar.ui.walkman.WalkmanViewModel
import app.nagmedar.ui.walkman.sampleSource
import app.nagmedar.ui.walkman.sampleTarget
import app.nagmedar.ui.walkman.sampleWalkmanPlan
import app.nagmedar.walkman.model.RemovableVolume
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.VolumeBinding
import app.nagmedar.walkman.model.VolumeKind
import app.nagmedar.walkman.model.VolumeRole
import app.nagmedar.walkman.model.VolumeStatus
import app.nagmedar.walkman.workflow.VolumeSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.5.0, for approval: "Walkman'e aktar": the cards on a phone, the new music to copy in DeX. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkmanScreenshotTest {

    @get:org.junit.Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** [navKind]: on a phone the shell's bar above carries the title, the page's header only its actions. */
    private fun capture(name: String, theme: ThemeMode, input: InputMode, controller: FakeWalkmanController, navKind: NavKind = NavKind.Sidebar) {
        val model = WalkmanViewModel(controller, SavedStateHandle())
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                CompositionLocalProvider(LocalNavKind provides navKind) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { WalkmanScreen(model) }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun cardsPhone() {
        val controller = FakeWalkmanController()
        val sd = RemovableVolume(sampleSource, "SD kart", VolumeKind.SD_CARD, isWritable = true)
        val walkman = RemovableVolume(sampleTarget, "USB depolama", VolumeKind.USB, isWritable = true)
        val source = VolumeBinding(VolumeRole.SOURCE, sampleSource, "content://tree/$sampleSource%3AMusic", "$sampleSource:Music", "SD kart", 0)
        controller.mountedVolumes.value = listOf(sd, walkman)
        controller.volumes.value = VolumeSnapshot(VolumeStatus.Ready(source, sd), VolumeStatus.Unbound)
        controller.state.value = SyncState.AwaitingVolumes(VolumeStatus.Ready(source, sd), VolumeStatus.Unbound)
        capture("walkman-cards-phone-dark", ThemeMode.Dark, InputMode.Touch, controller, NavKind.BottomBar)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun planDex() {
        val controller = FakeWalkmanController()
        controller.state.value = SyncState.PlanReady(sampleWalkmanPlan())
        capture("walkman-plan-dex-light", ThemeMode.Light, InputMode.Pointer, controller)
    }
}
