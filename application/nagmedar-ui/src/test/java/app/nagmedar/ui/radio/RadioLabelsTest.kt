package app.nagmedar.ui.radio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** S-1105: the directory's English names for countries and languages as the interface words them. */
class RadioLabelsTest {

    private val tr = Locale.forLanguageTag("tr")
    private val en = Locale.ENGLISH

    @Test
    fun aCountryInTheInterfacesLanguage() {
        assertEquals("Almanya", RadioLabels.country("DE", "Germany", tr))
        assertEquals("Germany", RadioLabels.country("de", "Germany", en))
        // a code nobody knows: the directory's own name, else the code
        assertEquals("Nowhere", RadioLabels.country("XX", "Nowhere", tr))
        assertEquals("XX", RadioLabels.country("XX", null, tr))
        assertEquals("Ülke", RadioLabels.country(null, "Ülke", tr))
        assertEquals("", RadioLabels.country(null, null, tr))
        assertEquals("Atlantis", RadioLabels.country("1", "Atlantis", en))
    }

    @Test
    fun aLanguageFromItsCodeOrItsEnglishName() {
        assertEquals("Türkçe", RadioLabels.language("turkish", "tr", tr))
        assertEquals("Almanca", RadioLabels.language("german", null, tr))
        assertEquals("German", RadioLabels.language("german", null, en))
        assertEquals("Fransızca", RadioLabels.language(" French ", null, tr))
        // not a language the platform knows: the directory's name, capitalised in the interface's alphabet
        assertEquals("Klingonca-yok", RadioLabels.language("klingonca-yok", "zz", tr))
        assertEquals("İstanbul lehçesi", RadioLabels.language("istanbul lehçesi", null, tr))
    }

    @Test
    fun theListsAreInTheInterfacesAlphabet() {
        val countries = listOf(
            DirectoryCountry("Turkey", "TR", 800),
            DirectoryCountry("Germany", "DE", 600),
            DirectoryCountry("Austria", "AT", 300),
            DirectoryCountry("Cyprus", "CY", 20),
        )
        val labels = RadioLabels.countries(countries, tr).map { it.second }
        assertEquals(labels.sortedWith(java.text.Collator.getInstance(tr)), labels)
        assertEquals("Almanya", labels.first())
    }

    @Test
    fun aStationsPlace() {
        assertEquals("Almanya · Almanca", RadioLabels.place(RadioSamples.classical, tr))
        assertEquals("Germany · German", RadioLabels.place(RadioSamples.classical, en))
        // no language: the country alone; nothing known: nothing
        assertEquals("Fransa", RadioLabels.place(RadioSamples.oneVote.copy(language = null), tr))
        assertEquals("", RadioLabels.place(Station(null, "Benim", "http://ev.example/s"), tr))
    }

    @Test
    fun theFiltersOfferTheCommonCodecsAndBitrates() {
        assertEquals(RadioLabels.BITRATES.sorted(), RadioLabels.BITRATES)
        assertEquals(RadioLabels.CODECS.distinct(), RadioLabels.CODECS)
    }
}
