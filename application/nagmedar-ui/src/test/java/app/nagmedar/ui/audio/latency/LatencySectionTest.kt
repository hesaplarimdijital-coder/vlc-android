package app.nagmedar.ui.audio.latency

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.latency.AppliedLatency
import app.nagmedar.audio.latency.LatencyProfile
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.OutputDeviceKey
import app.nagmedar.audio.latency.TestSignal
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-613: Settings › Latency profiles: the output now, the stepper, the delete, and the measurement's window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class LatencySectionTest {

    @get:Rule
    val compose = createComposeRule()

    /** A measurement begun in one test does not open the window in the next. */
    @After
    fun end() = LatencyMeasuring.end()

    private val buds = OutputDeviceKey(LatencyProfiles.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")
    private val speaker = OutputDeviceKey(LatencyProfiles.TYPE_BUILTIN_SPEAKER, "SM-X936B")

    private fun show(store: MemoryLatencyStore, active: OutputDeviceKey? = buds, io: MeasureIo? = null) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                LatencySection(store = store, active = active, applied = flowOf(active?.let { AppliedLatency(it, 0, null) }), measureIo = io)
            }
        }
    }

    @Test
    fun theOutputNowAndItsDelay() {
        show(MemoryLatencyStore(listOf(LatencyProfile(speaker, 0, measured = true, spreadMs = 2, calibrationMs = 12), LatencyProfile(buds, 180, measured = true, spreadMs = 4))))
        compose.onNodeWithText("Şimdi: Galaxy Buds2 Pro · +180 ms").assertExists()
        compose.onNodeWithText("Dahili hoparlör").assertExists()
        compose.onNodeWithText("mikrofon yolu 12 ms · yayılım 2 ms").assertExists()
        compose.onNodeWithText("Bluetooth · ölçüldü · yayılım 4 ms").assertExists()
        compose.onNodeWithText("Kullanımda").assertExists()
        assertEquals(2, compose.onAllNodesWithTag("latency-profile").fetchSemanticsNodes().size)
    }

    @Test
    fun theStepperMovesByTenAndStaysInRange() {
        val store = MemoryLatencyStore(listOf(LatencyProfile(buds, 180, measured = true, spreadMs = 4)))
        show(store)
        // the up step
        compose.onAllNodesWithTag("latency-step")[1].performClick()
        compose.waitForIdle()
        assertEquals(190, store.profiles.value.single().delayMs)
        // set by hand: no longer the measurement's
        assertEquals(false, store.profiles.value.single().measured)
        compose.onAllNodesWithTag("latency-step")[0].performClick()
        compose.onAllNodesWithTag("latency-step")[0].performClick()
        compose.waitForIdle()
        assertEquals(170, store.profiles.value.single().delayMs)
        compose.onNodeWithText("+170 ms").assertExists()
    }

    @Test
    fun aProfileIsDeleted() {
        val store = MemoryLatencyStore(listOf(LatencyProfile(buds, 180)))
        show(store)
        compose.onNodeWithTag("latency-delete").performClick()
        compose.waitForIdle()
        assertTrue(store.profiles.value.isEmpty())
        compose.onNodeWithTag("latency-empty").assertExists()
    }

    @Test
    fun withoutAnOutputThereIsNothingToMeasure() {
        show(MemoryLatencyStore(), active = null)
        compose.onNodeWithText("Şu anda kullanımda bir çıkış cihazı yok").assertExists()
        compose.onNodeWithTag("latency-measure").assertIsNotEnabled()
    }

    @Test
    fun theMeasurementsWindowOpensWithItsExplanation() {
        show(MemoryLatencyStore(listOf(LatencyProfile(buds, 0))))
        compose.onNodeWithTag("latency-measure").performClick()
        compose.onNodeWithTag("latency-dialog").assertExists()
        compose.onNodeWithText("Galaxy Buds2 Pro ölçümü").assertExists()
        compose.onNodeWithTag("latency-start").assertExists()
    }

    @Test
    fun theResultIsSavedToTheProfile() {
        var saved: LatencyResult? = null
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                MeasureSurface(
                    device = buds,
                    state = LatencyMeasureSession.State.Done(LatencyResult(rawMs = 183, spreadMs = 4, valid = 5, total = 5, exact = true, referenceMs = 12)),
                    denied = false,
                    onStart = {},
                    onRetry = {},
                    onSave = { saved = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("+183 ms").assertExists()
        compose.onNodeWithText("5 test sesinden 5 tanesi · yayılım 4 ms").assertExists()
        compose.onNodeWithText("Dahili hoparlörün payı (12 ms) çıkarıldı: +170 ms kaydedilir").assertExists()
        compose.onNodeWithTag("latency-save").performClick()
        assertEquals(170, saved!!.delayMs)
    }

    @Test
    fun anUncertainResultWarns() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                MeasureSurface(
                    device = buds,
                    state = LatencyMeasureSession.State.Done(LatencyResult(rawMs = 240, spreadMs = 45, valid = 4, total = 5, exact = false, referenceMs = null)),
                    denied = false,
                    onStart = {},
                    onRetry = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("latency-uncertain").assertExists()
        // without the streams' clocks the figure is shown but not saved
        compose.onNodeWithTag("latency-inexact").assertExists()
        compose.onNodeWithTag("latency-save").assertDoesNotExist()
    }

    @Test
    fun theSpeakersMeasurementIsTheReferenceNotItsDelay() {
        val store = MemoryLatencyStore(listOf(LatencyProfile(speaker, 0)))
        val room = object : MeasureIo {
            override suspend fun playAndRecord(signal: TestSignal, tailMs: Int, onProgress: (Float) -> Unit): Recording {
                val origin = signal.rate / 4
                val out = FloatArray(origin + signal.samples.size + signal.rate * tailMs / 1000)
                for (start in signal.burstStarts) for (i in signal.burst.indices) out[origin + start + signal.rate * 12 / 1000 + i] += signal.burst[i] * 0.3f
                return Recording(out, signal.rate, origin.toDouble(), exact = true)
            }
        }
        // the microphone is allowed already, so the measurement starts at once
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        show(store, active = speaker, io = room)
        compose.onNodeWithTag("latency-measure").performClick()
        compose.onNodeWithTag("latency-start").performClick()
        // the correlation runs on a worker; its result comes back through the main looper
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("latency-save").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("latency-save").performClick()
        compose.waitForIdle()
        store.profiles.value.single().let {
            assertEquals(0, it.delayMs)
            assertEquals(12, it.calibrationMs)
            assertTrue(it.measured)
        }
    }

    @Test
    fun aRefusedMicrophoneSaysSo() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                MeasureSurface(device = buds, state = LatencyMeasureSession.State.Intro, denied = true, onStart = {}, onRetry = {}, onSave = {}, onDismiss = {})
            }
        }
        compose.onNodeWithTag("latency-denied").assertExists()
        compose.onNodeWithText("Mikrofona izin verilmedi").assertExists()
    }
}
