package org.vera20k

/** Prevents a second preflight or NativeActivity launch until the game returns. */
internal class GameLaunchGate {
    var isPending: Boolean = false
        private set

    fun begin(): Boolean {
        if (isPending) return false
        isPending = true
        return true
    }

    fun completedWithoutLaunch() {
        isPending = false
    }

    fun returnedToLauncher() {
        isPending = false
    }
}
