package org.vera20k

/** Holds a real engine key across touch taps for actions such as Planning Mode. */
internal class TouchHudKeyLatch(private val send: (Int, Boolean) -> Unit) {
    private val held = linkedMapOf<String, Int>()

    fun toggle(id: String, keyCode: Int): Boolean {
        if (keyCode == 0) return false
        val previous = held.remove(id)
        if (previous != null) {
            send(previous, false)
            return false
        }
        held[id] = keyCode
        send(keyCode, true)
        return true
    }

    fun releaseAll() {
        held.values.toList().asReversed().forEach { send(it, false) }
        held.clear()
    }
}
