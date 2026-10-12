package app.nagmedar.ui.tvlab

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * S-1201 … S-1204: the TV Lab screen with the sample film: the empty page asks for a file; the
 * results show the figures, the coloured findings, the chart and the drive; the target is chosen
 * from its menu; the chart exported; the report says it all; the editor edits a profile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// tall enough for the whole page: what is clicked is on the screen
@Config(qualifiers = "tr-w1280dp-h2400dp-mdpi")
class TvLabScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : TvLabActions {
        override fun chooseFile() {
            calls += "file"
        }

        override fun chooseProfile(id: String) {
            calls += "profile:$id"
        }

        override fun editProfile() {
            calls += "edit"
        }

        override fun newProfile() {
            calls += "new"
        }

        override fun importProfile() {
            calls += "import"
        }

        override fun measureDiskAgain() {
            calls += "disk"
        }

        override fun analyseBitrate() {
            calls += "bitrate"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun showReport() {
            calls += "report"
        }

        override fun exportChart(format: ChartFormat) {
            calls += "chart:$format"
        }
    }

    private val res get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun show(view: TvLabView) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                TvLabContent(view, actions)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theEmptyPageAsksForAFile() {
        show(TvLabView.empty(BuiltInProfiles.lgB4Usb, BuiltInProfiles.all))
        compose.onNodeWithText("Analiz edilecek filmi seçin").assertExists()
        compose.onNode(hasText("LG OLED B4", substring = true) and hasText("nasıl oynatacağını", substring = true)).assertExists()
        compose.onNode(hasText("Dosya seçin") and hasClickAction()).performClick()
        assertEquals(listOf("file"), calls)
    }

    @Test
    fun aBuildWithoutTheToolsSaysSo() {
        show(TvLabView.empty(BuiltInProfiles.lgB4Usb, available = false))
        compose.onNodeWithText("Bu sürümde analiz araçları yok").assertExists()
        compose.onNodeWithText("Dosya seçin").assertDoesNotExist()
    }

    @Test
    fun theResults() {
        show(sampleTvLabView())
        compose.onNodeWithText("Kuzey_Isigi_2160p_UHD_Remux.mkv").assertExists()
        compose.onNodeWithText("58,4 GB · SAMSUNG T7 (exFAT) · analiz 42 sn sürdü").assertExists()
        // the four figures
        compose.onNodeWithText("2 kritik · 3 uyarı").assertExists()
        compose.onNodeWithText("Bu hâliyle düzgün oynamaz").assertExists()
        compose.onNodeWithText("Yeniden kodlanmaz").assertExists()
        compose.onNodeWithText("HEVC Main 10 · DV P7 FEL").assertExists()
        compose.onNodeWithText("118 Mbps").assertExists()
        compose.onNodeWithText("100 Mbps").assertExists()
        compose.onNodeWithText("1 sn tepe · USB 2.0").assertExists()
        // the findings, the critical first
        assertEquals(6, compose.onAllNodesWithTag("tvlab-finding").fetchSemanticsNodes().size)
        compose.onNodeWithText("Dolby Vision P7 FEL · çift katman").assertExists()
        compose.onNodeWithText("DTS-HD MA 5.1 · Tür · varsayılan iz").assertExists()
        compose.onNodeWithText("LG OLED B4 bu codec'i oynatmaz; film sessiz başlar.").assertExists()
        compose.onNodeWithText("Bit hızı tepesi 118 Mbps (1 sn)").assertExists()
        compose.onNodeWithText("Eşik 100 Mbps; 3 saniye bunun üstünde, tampon çoğu zaman karşılar.").assertExists()
        assertEquals(2, compose.onAllNodesWithText("Kritik").fetchSemanticsNodes().size)
        // the chart, read out
        compose.onNode(hasContentDescription("Bit hızı grafiği", substring = true) and hasContentDescription("3 saniye", substring = true)).assertExists()
        // the drive
        compose.onNodeWithText("412 MB/s").assertExists()
        compose.onNodeWithText("sıralı okuma · ilk blok 11 ms").assertExists()
        compose.onNodeWithText("Bu cihazda ölçüldü; TV'nin USB 2.0 girişi yaklaşık 30 MB/s okur.").assertExists()
        // the streams
        compose.onNodeWithText("Profil 7 · seviye 6 · BL + EL + RPU · FEL").assertExists()
    }

    @Test
    fun theTargetIsChosenFromItsMenu() {
        show(sampleTvLabView())
        compose.onNode(hasText("LG OLED B4 · webOS 24 · USB") and hasClickAction()).performClick()
        compose.onNodeWithText("Tab S11 Ultra · One UI 8 · HDMI").performClick()
        compose.onNode(hasText("Profili düzenle") and hasClickAction()).performClick()
        assertEquals(listOf("profile:tab-s11-ultra", "edit"), calls)
    }

    @Test
    fun onTheTabletTheSameFilmHasOneWarning() {
        show(sampleTvLabView(BuiltInProfiles.tabS11Ultra))
        compose.onNodeWithText("1 uyarı").assertExists()
        compose.onNodeWithText("Sınır yok").assertExists()
        compose.onNodeWithText("Tab S11 Ultra bu profili Dolby Vision olarak oynatmaz; HDR10 temel katmanı görünür.").assertExists()
    }

    @Test
    fun theChartIsExported() {
        show(sampleTvLabView())
        compose.onNode(hasText("Dışa aktar") and hasClickAction()).performClick()
        compose.onNodeWithText("Grafik (PNG)…").performClick()
        compose.onNode(hasText("Dışa aktar") and hasClickAction()).performClick()
        compose.onNodeWithText("Veriler (CSV)…").performClick()
        assertEquals(listOf("chart:Png", "chart:Csv"), calls)
    }

    @Test
    fun whileItRuns() {
        show(sampleRunningView())
        // under the bitrate's figure and in the chart's card
        assertEquals(2, compose.onAllNodesWithText("Paketler okunuyor · %42").fetchSemanticsNodes().size)
        compose.onNodeWithText("Bit hızı henüz okunmadı").assertExists()
        compose.onNode(hasText("Durdur") and hasClickAction()).performClick()
        // the drive cannot be measured while the packets are read
        compose.onNode(hasText("Yeniden ölç") and hasClickAction()).assertIsNotEnabled()
        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun aStoppedReadingStartsAgain() {
        show(sampleTvLabView().copy(bitrate = null, bitrateStep = Step.Failed(TvLabController.CANCELLED)))
        compose.onNodeWithText("Durduruldu").assertExists()
        compose.onNode(hasText("Yeniden başlat") and hasClickAction()).performClick()
        compose.onNode(hasText("Yeniden ölç") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf("bitrate", "disk"), calls)
    }

    @Test
    fun anUnreadableFile() {
        show(sampleTvLabView().copy(tech = null, probe = Step.Failed(TvLabController.UNREADABLE), bitrate = null, bitrateStep = Step.Idle))
        compose.onNodeWithText("Dosya okunamadı").assertExists()
        compose.onNodeWithText("Okunamadı").assertExists()
        compose.onNode(hasText("Rapor") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun theReportSaysItAll() {
        val view = sampleTvLabView()
        val turkish = Locale.forLanguageTag("tr")
        val report = tvLabReport(res, view, checkOf(view, LocalizedStreamNames(res, turkish)), turkish, "Yorum")
        val lines = report.lines()
        assertEquals("TV Lab raporu", lines[0])
        assertTrue(report, "Hedef: LG OLED B4 · webOS 24 · USB" in lines)
        assertTrue(report, "Sonuç: 2 kritik · 3 uyarı · Bu hâliyle düzgün oynamaz" in lines)
        assertTrue(report, "[Kritik] Dolby Vision P7 FEL · çift katman" in lines)
        assertTrue(report, "  → Profil 8.1'e dönüştürülünce oynar: temel katman korunur, geliştirme katmanı atılır (Ayarlar › Laboratuvar)." in lines)
        assertTrue(report, "Ses 3: AC-3 2.0 · Tür · Yorum · 48 kHz" in lines)
        assertTrue(report, "100 Mbps eşiğinin üstünde 3 saniye" in lines)
        // S-1204: the drive in the report
        assertTrue(report, "SAMSUNG T7 · exFAT · Harici disk · USB 3.20 · 2 TB · 1,2 TB boş" in lines)
        assertTrue(report, "412 MB/s · sıralı okuma · ilk blok 11 ms" in lines)
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h2400dp-mdpi")
    fun everyFindingHasItsWordsInEnglishToo() {
        val english = Locale.ENGLISH
        FindingKind.entries.forEach { kind ->
            FindingLevel.entries.forEach { level ->
                val text = findingText(res, Finding(kind, level, "X", listOf(118_000, 100_000, 3)), BuiltInProfiles.lgB4Usb, english)
                assertTrue("$kind", text.title.isNotBlank() && text.why.isNotBlank() && text.fix.isNotBlank())
                assertTrue("$kind: ${text.why}", "%" !in text.why && "%" !in text.title)
            }
        }
        assertEquals(
            "Bitrate peak 118 Mbps (1 s)",
            findingText(res, Finding(FindingKind.BitratePeak, FindingLevel.Warning, values = listOf(118_000, 100_000, 3)), BuiltInProfiles.lgB4Usb, english).title,
        )
    }

    @Test
    fun theEditorBuildsTheProfile() {
        val draft = ProfileDraft(BuiltInProfiles.lgB4Usb.copy(builtIn = false), isNew = true)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ProfileEditorSurface(draft, saved = false, onSave = { calls += "save" }, onCancel = {}, onShare = {}, onExport = {}, onRemove = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Yeni profil").assertExists()
        // without a name it cannot be saved
        compose.onNode(hasText("Kaydet") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithTag("tvlab-editor").assertExists()
        compose.onNode(hasContentDescription("Ad")).performTextInput("Sony Bravia 8")
        compose.onNode(hasContentDescription("Bit hızı eşiği (Mbps, 1 sn tepe)")).performTextClearance()
        compose.onNode(hasContentDescription("Bit hızı eşiği (Mbps, 1 sn tepe)")).performTextInput("80,5")
        compose.onNode(hasText("Ağ") and hasClickAction()).performClick()
        compose.onNode(hasText("DTS") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("Kaydet") and hasClickAction()).assertIsEnabled().performClick()
        val built = draft.build("sony-bravia-8")
        assertEquals("Sony Bravia 8", built.name)
        assertEquals(80_500, built.peakKbps)
        assertEquals(SourceLink.Network, built.source)
        assertTrue(built.allowsAudio("dts"))
        assertEquals(BuiltInProfiles.lgB4Usb.dvProfiles, built.dvProfiles)
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun theEditorsChoices() {
        val all = listOf("a", "b", "c")
        // "All" off leaves the others when one goes
        assertEquals(setOf("a", "c"), ProfileDraft.toggle(setOf(DeviceProfile.ANY), "b", false, all))
        assertEquals(setOf("a", "b"), ProfileDraft.toggle(setOf("a"), "b", true, all))
        // the last one stays
        assertEquals(setOf("a"), ProfileDraft.toggle(setOf("a"), "a", false, all))
        assertEquals("62.5", ProfileDraft.Mbps.format(62_500))
        assertEquals(100_000, ProfileDraft.Mbps.parse("100"))
        assertEquals(62_500, ProfileDraft.Mbps.parse("62,5"))
        assertEquals(null, ProfileDraft.Mbps.parse("çok"))
        // Dolby Vision's profiles go with it
        val draft = ProfileDraft(BuiltInProfiles.lgB4Usb, isNew = false)
        draft.hdr = setOf(HdrFormat.HDR10)
        assertEquals(emptySet<Int>(), draft.build(BuiltInProfiles.lgB4Usb.id).dvProfiles)
        assertTrue(draft.build(BuiltInProfiles.lgB4Usb.id).builtIn)
        assertEquals(res.getString(R.string.nag_tvlab_save), "Kaydet")
    }
}
