package app.nagmedar.ui.music

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.SmartPlaylistEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S-805: where the smart lists are kept: Nağmedar's database in the app, a list in the tests. */
interface SmartListStore {
    /** The lists, in the order they were made, again after each change. */
    val lists: Flow<List<SmartList>>

    /** [list] kept: a new one (id 0) made, an old one changed; its id. */
    suspend fun save(list: SmartList): Long

    suspend fun delete(id: Long)
}

/** S-805: the smart lists of Nağmedar's database. */
class DatabaseSmartLists(context: Context) : SmartListStore {

    private val dao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { NagmedarDatabase.get(context).smartPlaylists() }

    override val lists: Flow<List<SmartList>>
        get() = dao.observe().map { rows -> rows.map { it.toSmartList() } }.flowOn(Dispatchers.IO)

    override suspend fun save(list: SmartList): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val made = if (list.id == 0L) now else dao.get(list.id)?.createdAt ?: now
        val entry = SmartPlaylistEntry(
            id = list.id,
            name = list.name,
            rules = list.encodeRules(),
            matchAll = list.matchAll,
            sort = list.encodeSort(),
            limitCount = list.limit,
            createdAt = made,
            updatedAt = now,
        )
        if (list.id == 0L) dao.insert(entry) else list.id.also { dao.update(entry) }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    private fun SmartPlaylistEntry.toSmartList() = SmartList(
        id = id,
        name = name,
        rules = SmartList.decodeRules(rules),
        matchAll = matchAll,
        sort = SmartList.decodeSort(sort),
        limit = limitCount,
    )
}

/** S-805: the smart lists as the playlists screen shows them; a change shows at once, the store follows. */
@Stable
class SmartListsState(private val store: SmartListStore, private val scope: CoroutineScope) {

    /** Null until read. */
    var lists: List<SmartList>? by mutableStateOf(null)
        private set

    /** Follows the store for as long as the caller's coroutine runs. */
    suspend fun follow() {
        store.lists.collect { lists = it }
    }

    /** [list] kept; [done] hears its id (a new list's is known only then). */
    fun save(list: SmartList, done: (Long) -> Unit = {}) {
        if (list.id != 0L) lists = lists?.map { if (it.id == list.id) list else it }
        scope.launch { done(store.save(list)) }
    }

    /** [list] deleted; [restored] tells the screen how to bring it back. */
    fun delete(list: SmartList, restored: (restore: () -> Unit) -> Unit) {
        lists = lists?.filter { it.id != list.id }
        scope.launch {
            store.delete(list.id)
            restored { save(list.copy(id = 0)) }
        }
    }
}
