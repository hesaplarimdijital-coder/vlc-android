package app.nagmedar.ui.tvlab

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * S-1203: a device profile as a JSON file (schema 1) and where the profiles are kept: the app's,
 * changed or not, and the user's own. Robolectric for org.json.
 */
@RunWith(AndroidJUnit4::class)
class DeviceProfileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tvlab-test", Context.MODE_PRIVATE)

    @Test
    fun theAppsProfilesComeBackAsTheyWent() {
        BuiltInProfiles.all.forEach { profile ->
            assertEquals(profile, DeviceProfileCodec.decode(DeviceProfileCodec.encode(profile), builtIn = true))
        }
        val json = JSONObject(DeviceProfileCodec.encode(BuiltInProfiles.lgB4Usb))
        assertEquals(1, json.getInt("schema"))
        assertEquals(100.0, json.getDouble("peak_mbps"), 0.0)
        assertEquals("usb2", json.getString("source"))
        val video = json.getJSONObject("video")
        assertEquals(listOf("hdr10", "hlg", "dv"), (0 until video.getJSONArray("hdr").length()).map { video.getJSONArray("hdr").getString(it) })
        assertEquals(listOf(5, 8), (0 until video.getJSONArray("dv_profiles").length()).map { video.getJSONArray("dv_profiles").getInt(it) })
    }

    @Test
    fun aHandWrittenProfileIsRead() {
        val profile = DeviceProfileCodec.decode(
            """{"id":"sony-bravia-8","name":"Sony Bravia 8","source":"network","peak_mbps":80.5,
               "containers":["mkv","mp4"],"video":{"codecs":["HEVC","h264"],"hdr":["hdr10","hlg","dv","hdr10plus","xyz"],"dv_profiles":[5,8]},
               "audio":{"codecs":["eac3","dts"]},"later":{"unknown":true}}""",
        )!!
        assertEquals("Sony Bravia 8", profile.name)
        assertEquals(SourceLink.Network, profile.source)
        assertEquals(80_500, profile.peakKbps)
        assertEquals(setOf("MKV", "MP4"), profile.containers)
        assertEquals(setOf("hevc", "h264"), profile.videoCodecs)
        assertEquals(setOf(HdrFormat.HDR10, HdrFormat.HLG, HdrFormat.DolbyVision, HdrFormat.HDR10Plus), profile.hdr)
        // what it does not say has no limit
        assertEquals(Int.MAX_VALUE, profile.maxWidth)
        assertTrue(profile.allowsSubtitle("pgs"))
        assertTrue(profile.allowsAudio("dts"))
        assertFalse(profile.allowsAudio("truehd"))
        assertFalse(profile.builtIn)
        // not a profile, or one of a later schema
        assertNull(DeviceProfileCodec.decode("""{"schema":2,"id":"x"}"""))
        assertNull(DeviceProfileCodec.decode("""{"name":"no id"}"""))
        assertNull(DeviceProfileCodec.decode("not json"))
    }

    @Test
    fun newIdsAndFileNames() {
        assertEquals("sony-bravia-8", FileProfileStore.newId("Sony Bravia 8", emptyList()))
        assertEquals("sony-bravia-8-2", FileProfileStore.newId("Sony Bravia 8", listOf("sony-bravia-8")))
        assertEquals("salon-tv-si-guclu", FileProfileStore.newId("Salon TV'si (güçlü)", emptyList()))
        assertEquals("profile", FileProfileStore.newId("  ", emptyList()))
        assertEquals("izmir-salon", FileProfileStore.newId("İzmir Salon", emptyList()))
        assertEquals("a_b.json", FileProfileStore.fileName("a/b"))
    }

    @Test
    fun theStoreKeepsTheUsersProfilesAndChanges() {
        prefs.edit().clear().commit()
        val store = FileProfileStore(folder.newFolder("profiles"), prefs)
        assertEquals(BuiltInProfiles.all, store.all())
        assertFalse(store.saved(BuiltInProfiles.lgB4Usb.id))

        // the user's own, after the app's
        val bravia = BuiltInProfiles.lgB4Usb.copy(id = "sony-bravia-8", name = "Sony Bravia 8", builtIn = false)
        store.save(bravia)
        assertEquals(BuiltInProfiles.all.map { it.id } + "sony-bravia-8", store.all().map { it.id })
        assertTrue(store.saved("sony-bravia-8"))

        // an app's profile changed stays one, in its place
        val faster = BuiltInProfiles.lgB4Usb.copy(peakKbps = 120_000)
        store.save(faster)
        assertEquals(faster, store.all().first())
        assertTrue(store.all().first().builtIn)
        assertTrue(store.saved(BuiltInProfiles.lgB4Usb.id))

        // the chosen one is remembered; a user's profile removed is forgotten
        store.selected = "sony-bravia-8"
        assertEquals("sony-bravia-8", FileProfileStore(folder.root.resolve("profiles"), prefs).selected)
        store.remove("sony-bravia-8")
        assertNull(store.selected)
        // an app's goes back to as it came
        store.remove(BuiltInProfiles.lgB4Usb.id)
        assertEquals(BuiltInProfiles.all, store.all())
    }

    @Test
    fun aBrokenFileIsLeftOut() {
        val dir = folder.newFolder("broken")
        dir.resolve("x.json").writeText("{")
        assertEquals(BuiltInProfiles.all, FileProfileStore(dir, prefs).all())
    }
}
