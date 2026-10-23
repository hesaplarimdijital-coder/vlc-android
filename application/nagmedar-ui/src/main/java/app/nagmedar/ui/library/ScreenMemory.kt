package app.nagmedar.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

/**
 * What the library's screens last read, kept while their window lives (stale-while-revalidate).
 * A screen is disposed when another one is chosen; coming back it shows what it last had in its
 * very first frame and reads again behind it, so switching screens never fades into a loading
 * state for what was on screen a moment ago. Used on the main thread only, as the screens are.
 */
class ScreenMemory {
    private val values = HashMap<String, Any>()
    private val derived = HashMap<String, Pair<Any, Any>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(name: String): T? = values[name] as T?

    operator fun set(name: String, value: Any?) {
        if (value == null) values.remove(name) else values[name] = value
    }

    /**
     * [compute] of [input], made again only when [input] is not the one it was last made of
     * (compared with `==`, which an unchanged list or map answers at once): a screen coming back
     * to the same data does not group or sort it again in its first frame.
     */
    @Suppress("UNCHECKED_CAST")
    fun <I : Any, T : Any> derived(name: String, input: I, compute: (I) -> T): T {
        val last = derived[name]
        if (last != null && last.first == input) return last.second as T
        return compute(input).also { derived[name] = input to it }
    }
}

/**
 * [read]'s value for a screen: what it last read under [name] in [memory] from the first frame
 * (null only the first time, or without a [memory]), then each new value [read] gives, which
 * [memory] keeps in turn. [keys] restart [read], as `produceState`'s do; the value on screen stays
 * until the new one comes.
 */
@Composable
fun <T : Any> rememberRead(memory: ScreenMemory?, name: String, vararg keys: Any?, read: suspend ProduceStateScope<T?>.() -> Unit): State<T?> {
    val state = produceState(memory?.get<T>(name), *keys, producer = read)
    val value = state.value
    SideEffect { if (value != null) memory?.set(name, value) }
    return state
}
