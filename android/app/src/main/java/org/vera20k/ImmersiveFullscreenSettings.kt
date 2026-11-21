package org.vera20k

/** Launcher preference and the launch-time handoff to the separate game process. */
internal object ImmersiveFullscreenSettings {
    const val PREFERENCES = "iron_harbor_display"
    const val ENABLED = "immersive_fullscreen_gameplay"
    const val INTENT_EXTRA = "org.vera20k.extra.IMMERSIVE_FULLSCREEN"
    const val DEFAULT_ENABLED = true

    fun resolveLaunchValue(extra: Boolean?): Boolean = extra ?: DEFAULT_ENABLED
}
