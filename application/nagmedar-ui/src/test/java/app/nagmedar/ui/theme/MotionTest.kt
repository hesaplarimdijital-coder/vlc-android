package app.nagmedar.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.LoadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-203: the motion tokens follow the system's "Remove animations" setting; with it on,
 * transitions are instant and loops stand still.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w600dp-h420dp-mdpi")
class MotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setAnimatorScale(value: Float) {
        val resolver = compose.activity.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)
        // what the system does when the setting changes
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    @Test
    fun followsRemoveAnimations() {
        var motion: NagMotion? = null
        compose.setContent { NagmedarTheme { motion = NagTheme.motion } }
        compose.waitForIdle()
        assertEquals(NagMotion.Full, motion)

        compose.runOnUiThread { setAnimatorScale(0f) }
        compose.waitForIdle()
        assertEquals("Remove animations on", NagMotion.Reduced, motion)

        compose.runOnUiThread { setAnimatorScale(0.5f) }
        compose.waitForIdle()
        assertEquals("a slower scale still animates", NagMotion.Full, motion)
    }

    @Test
    fun theAppMayReduceMotionItself() {
        var motion: NagMotion? = null
        compose.setContent { NagmedarTheme(reduceMotion = true) { motion = NagTheme.motion } }
        compose.waitForIdle()
        assertEquals(NagMotion.Reduced, motion)
    }

    @Test
    fun reducedSpecsSnap() {
        val reduced = NagMotion.Reduced
        assertTrue(reduced.enter<Float>() is SnapSpec<*>)
        assertTrue(reduced.exit<Float>() is SnapSpec<*>)
        assertTrue(reduced.standard<Float>() is SnapSpec<*>)
        assertTrue(reduced.color<Float>() is SnapSpec<*>)
        assertTrue(reduced.emphasized<Float>() is SnapSpec<*>)
        assertTrue(reduced.emphasizedExit<Float>() is SnapSpec<*>)
        assertTrue(reduced.spatial<Float>() is SnapSpec<*>)
        assertTrue(reduced.fastSpatial<Float>() is SnapSpec<*>)
        assertTrue(reduced.effects<Float>() is SnapSpec<*>)
        assertNull(reduced.loop(NagMotion.SWEEP))
        for (desktop in listOf(false, true)) {
            assertEquals(EnterTransition.None, reduced.screenEnter(desktop))
            assertEquals(ExitTransition.None, reduced.screenExit(desktop))
        }
        val step = reduced.step(forward = true, slidePx = 24)
        assertEquals(EnterTransition.None, step.targetContentEnter)
        assertEquals(ExitTransition.None, step.initialContentExit)
    }

    @Test
    fun fullSpecsFollowTheTokens() {
        val full = NagMotion.Full
        assertEquals(NagMotion.MEDIUM, (full.enter<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(NagMotion.SHORT, (full.exit<Float>() as TweenSpec<Float>).durationMillis)
        assertEquals(NagMotion.LONG, (full.standard<Float>(NagMotion.LONG) as TweenSpec<Float>).durationMillis)
        assertEquals(NagMotion.LONG, (full.emphasized<Float>() as TweenSpec<Float>).durationMillis)
        // Desktop Kit 0.2.0: hover, press and selection ease over 150 ms
        assertEquals(150, (full.color<Float>() as TweenSpec<Float>).durationMillis)
        // leaving is quicker than arriving, so the new content is not kept waiting
        assertTrue(NagMotion.SHORT < NagMotion.MEDIUM && NagMotion.MEDIUM < NagMotion.LONG)
        assertNotNull(full.loop(NagMotion.SHIMMER))
        assertNotEquals(EnterTransition.None, full.screenEnter(desktop = false, initialScale = NagMotion.PHONE_SCALE))
        assertNotEquals(EnterTransition.None, full.screenEnter(desktop = true))
        assertNotEquals(EnterTransition.None, full.step(forward = false, slidePx = 24).targetContentEnter)
    }

    /** Desktop Kit 0.2.0: Android's fade-through takes 90 + 210 ms; nothing but the loops is longer than 300 ms. */
    @Test
    fun theTransitionsKeepToTheirTimes() {
        assertEquals(90, NagMotion.FADE_OUT)
        assertEquals(210, NagMotion.FADE_IN)
        assertEquals(NagMotion.LONG, NagMotion.FADE_OUT + NagMotion.FADE_IN)
        assertEquals(150, NagMotion.CROSSFADE)
        for (duration in listOf(NagMotion.SHORT, NagMotion.COLOR, NagMotion.MEDIUM, NagMotion.LONG, NagMotion.CROSSFADE)) {
            assertTrue("$duration ms", duration <= 300)
        }
        assertTrue(NagMotion.PHONE_SCALE < NagMotion.TABLET_SCALE && NagMotion.TABLET_SCALE < 1f)
    }

    /** The curves are Material 3's: arriving ones start fast, leaving ones end fast, all run from 0 to 1. */
    @Test
    fun theCurvesAreAndroids() {
        val curves = listOf(
            NagMotion.Emphasized, NagMotion.EmphasizedDecelerate, NagMotion.EmphasizedAccelerate,
            NagMotion.Standard, NagMotion.StandardDecelerate, NagMotion.StandardAccelerate,
        )
        for (curve in curves) {
            assertEquals(0f, curve.transform(0f), 0.001f)
            assertEquals(1f, curve.transform(1f), 0.001f)
        }
        assertTrue(NagMotion.EmphasizedDecelerate.transform(0.2f) > 0.5f)
        assertTrue(NagMotion.EmphasizedAccelerate.transform(0.5f) < 0.2f)
        assertTrue(NagMotion.StandardDecelerate.transform(0.5f) > NagMotion.StandardAccelerate.transform(0.5f))
    }

    /** Two frames of a loading state 480 ms apart, with the frame clock driven by hand. */
    private fun twoFrames(reduced: Boolean): Pair<IntArray, IntArray> {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer, reduceMotion = reduced) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    LoadingState(title = "Kütüphane taranıyor", posters = 3)
                }
            }
        }
        compose.mainClock.advanceTimeBy(160)
        val first = pixels()
        compose.mainClock.advanceTimeBy(480)
        val second = pixels()
        return first to second
    }

    private fun pixels(): IntArray {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    @Test
    fun loopsMoveWithFullMotion() {
        val (first, second) = twoFrames(reduced = false)
        assertFalse("the shimmer and the progress sweep should move", first.contentEquals(second))
    }

    @Test
    fun loopsStandStillWithAnimationsRemoved() {
        val (first, second) = twoFrames(reduced = true)
        assertTrue("nothing may move with animations removed", first.contentEquals(second))
    }
}
