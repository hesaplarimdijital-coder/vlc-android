package app.nagmedar.ui.music

import app.nagmedar.ui.music.MusicColumn.Album
import app.nagmedar.ui.music.MusicColumn.Artist
import app.nagmedar.ui.music.MusicColumn.Duration
import app.nagmedar.ui.music.MusicColumn.Format
import app.nagmedar.ui.music.MusicColumn.Number
import app.nagmedar.ui.music.MusicColumn.Title
import app.nagmedar.ui.music.MusicColumn.Year
import org.junit.Assert.assertEquals
import org.junit.Test

/** Desktop Kit 1.0: the column chooser's rules and each way in's own columns. */
class MusicColumnsTest {

    @Test
    fun aColumnShownAgainGoesBesideTheOneItFollows() {
        val columns = listOf(Title, Artist, Duration)
        assertEquals(listOf(Title, Artist, Album, Duration), MusicColumns.toggle(columns, Album))
        // moved columns keep their places; the new one follows its neighbour in the table's own order
        assertEquals(listOf(Duration, Title, Artist, Year), MusicColumns.toggle(listOf(Duration, Title, Artist), Year))
        assertEquals(listOf(Title, Duration), MusicColumns.toggle(columns, Artist))
        // the title always stays
        assertEquals(columns, MusicColumns.toggle(columns, Title))
    }

    @Test
    fun aColumnMovesWithinTheTable() {
        val columns = listOf(Title, Number, Format, Duration)
        assertEquals(listOf(Number, Title, Format, Duration), MusicColumns.move(columns, Number, -1))
        assertEquals(listOf(Title, Number, Duration, Format), MusicColumns.move(columns, Format, 1))
        assertEquals(columns, MusicColumns.move(columns, Title, -1))
        assertEquals(columns, MusicColumns.move(columns, Duration, 1))
        assertEquals(columns, MusicColumns.move(columns, Artist, 1))
    }

    @Test
    fun eachWayInKeepsItsOwnColumns() {
        val kept = mutableMapOf<MusicView, List<MusicColumn>>()
        val stored = mapOf(MusicView.Albums to listOf(Number, Title, Duration))
        val state = MusicBrowserState(
            view = MusicView.Tracks,
            keep = { view, columns -> kept[view] = columns },
            columnsOf = { view -> stored[view] ?: MusicColumn.DEFAULT },
        )
        state.move(Artist, -1)
        assertEquals(listOf(Artist, Title, Album, Year, Format, Duration, MusicColumn.Rating), state.columns)
        assertEquals(state.columns, kept[MusicView.Tracks])
        state.open(MusicView.Albums)
        assertEquals(listOf(Number, Title, Duration), state.columns)
        state.resetColumns()
        assertEquals(MusicColumn.DEFAULT, state.columns)
        assertEquals(MusicColumn.DEFAULT, kept[MusicView.Albums])
    }
}
