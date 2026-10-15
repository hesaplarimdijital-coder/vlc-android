package app.nagmedar.ui.radio.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** S-1107: the names of recorded files are the track's, safe for FAT, exFAT and the storage access framework. */
class TrackNamesTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    // 2026-10-15 14:30:05 in Istanbul (UTC+3)
    private val at = 1_792_063_805_000L

    @Test
    fun aTrackIsArtistDashTitle() {
        assertEquals("Sezen Aksu - Hadi Bakalım", TrackNames.baseName(TrackTag("Sezen Aksu", "Hadi Bakalım"), "Radyo", at, zone))
    }

    @Test
    fun aTitleWithoutAnArtistTakesTheStationInFront() {
        assertEquals("Haber Hattı - Sabah Bülteni", TrackNames.baseName(TrackTag(null, "Sabah Bülteni"), "Haber Hattı", at, zone))
    }

    @Test
    fun aTrackWithoutATitleIsNamedByTheTime() {
        assertEquals("Haber Hattı - 2026-10-15 14.30.05", TrackNames.baseName(null, "Haber Hattı", at, zone))
        assertEquals("Haber Hattı - 2026-10-15 14.30.05", TrackNames.baseName(TrackTag("Biri", null), "Haber Hattı", at, zone))
    }

    @Test
    fun charactersFileSystemsRefuseAreReplacedOrDropped() {
        assertEquals("AC-DC - Who Made Who", TrackNames.sanitize("AC/DC - Who Made Who"))
        assertEquals("a-b-c-d", TrackNames.sanitize("a\\b:c|d"))
        assertEquals("What' is this", TrackNames.sanitize("What\" is this?"))
        assertEquals("ab", TrackNames.sanitize("a*b<>"))
        assertEquals("ab", TrackNames.sanitize("a\u0000\u001Fb\u007F"))
    }

    @Test
    fun spacesAreTidiedAndATrailingDotOrASpaceIsDropped() {
        assertEquals("a b", TrackNames.sanitize("  a \t  b  "))
        assertEquals("Mr. Brightside", TrackNames.sanitize("Mr. Brightside..."))
        assertEquals("hidden", TrackNames.sanitize(".hidden"))
        assertEquals("", TrackNames.sanitize(" . . "))
        assertEquals("", TrackNames.sanitize("???"))
    }

    @Test
    fun reservedDeviceNamesAreNotUsedAsTheyAre() {
        assertEquals("_CON", TrackNames.sanitize("CON"))
        assertEquals("_nul", TrackNames.sanitize("nul"))
        assertEquals("_COM1", TrackNames.sanitize("COM1"))
        assertEquals("CONSOLE", TrackNames.sanitize("CONSOLE"))
        assertEquals("COM10", TrackNames.sanitize("COM10"))
    }

    @Test
    fun turkishLettersAndEmojiStayAndAreComposed() {
        assertEquals("İstanbul Çıkışlı Şarkı 🎵", TrackNames.sanitize("İstanbul Çıkışlı Şarkı 🎵"))
        // "e" and a combining accent become one letter
        assertEquals("café", TrackNames.sanitize("café"))
    }

    @Test
    fun aLongNameIsCutToTheByteLimitWithoutBreakingALetter() {
        val long = "Ş".repeat(300)
        val fitted = TrackNames.fit(long, TrackNames.MAX_BYTES)
        assertTrue(fitted.toByteArray().size <= TrackNames.MAX_BYTES)
        assertTrue(fitted.all { it == 'Ş' })
        val emoji = "🎵".repeat(100)
        val cut = TrackNames.fit(emoji, 50)
        assertTrue(cut.toByteArray().size <= 50)
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertEquals("kısa", TrackNames.fit("kısa", 50))
        val name = TrackNames.baseName(TrackTag("A".repeat(150), "B".repeat(150)), "Radyo", at, zone)
        assertTrue(name.toByteArray().size + ".opus".length <= 255)
    }

    @Test
    fun aNameTakenGetsANumberBeforeTheExtensionAndCaseDoesNotMatter() {
        assertEquals("A - B.mp3", TrackNames.unique("A - B", "mp3", emptySet()))
        assertEquals("A - B (2).mp3", TrackNames.unique("A - B", "mp3", setOf("a - b.mp3")))
        assertEquals("A - B (3).mp3", TrackNames.unique("A - B", "mp3", setOf("a - b.mp3", "a - b (2).mp3")))
        assertEquals("A - B (2).aac", TrackNames.unique("A - B", "aac", setOf("a - b.aac", "a - b.mp3")))
    }

    @Test
    fun theStationsFolderIsNamedLikeAFile() {
        assertEquals("Radyo-D", TrackNames.folderName("Radyo/D"))
        assertEquals("Radio", TrackNames.folderName("???"))
        assertFalse(TrackNames.folderName("Haber: Hattı.").endsWith("."))
    }
}
