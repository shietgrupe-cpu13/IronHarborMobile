package org.vera20k

/** Holds a real engine key across touch taps for actions such as Planning Mode. */
internal class TouchHudKeyLatch(private val send: (Int, Boolean) -> Unit) {
    private val held = linkedMapOf<String, List<Int>>()
    private val references = mutableMapOf<Int, Int>()

    fun toggle(id: String, keyCode: Int): Boolean = toggle(id, listOf(keyCode))

    fun toggle(id: String, keyCodes: List<Int>): Boolean {
        val previous = held.remove(id)
        if (previous != null) {
            release(previous)
            return false
        }
        val keys = keyCodes.distinct().filter { it != 0 }
        if (keys.isEmpty()) return false
        held[id] = keys
        keys.forEach { key ->
            val count = references[key] ?: 0
            references[key] = count + 1
            if (count == 0) send(key, true)
        }
        return true
    }

    fun releaseAll() {
        held.values.toList().asReversed().forEach(::release)
        held.clear()
    }

    private fun release(keys: List<Int>) {
        keys.asReversed().forEach { key ->
            val count = references[key] ?: return@forEach
            if (count <= 1) {
                references.remove(key)
                send(key, false)
            } else {
                references[key] = count - 1
            }
        }
    }
}
