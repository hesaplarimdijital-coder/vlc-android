package app.nagmedar.ui.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** S-805: smart lists in a list, for the tests. */
class FakeSmartListStore(vararg lists: SmartList) : SmartListStore {

    private var nextId = 1L
    private val all = MutableStateFlow(lists.map { it.copy(id = nextId++) })

    /** The lists as kept. */
    val kept: List<SmartList> get() = all.value

    override val lists: Flow<List<SmartList>> get() = all

    override suspend fun save(list: SmartList): Long {
        if (list.id == 0L) {
            val made = list.copy(id = nextId++)
            all.value = all.value + made
            return made.id
        }
        all.value = all.value.map { if (it.id == list.id) list else it }
        return list.id
    }

    override suspend fun delete(id: Long) {
        all.value = all.value.filter { it.id != id }
    }
}
