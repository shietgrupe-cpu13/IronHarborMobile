package org.vera20k

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** Applies system-bar policy only to the NativeActivity that owns the game surface. */
internal object GameImmersiveMode {
    fun apply(window: Window, enabled: Boolean) {
        applyCutoutPolicy(window, enabled)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 15+ enforces edge-to-edge for this target SDK. Explicitly
            // keep gameplay edge-to-edge and let visible bars overlay the game
            // when the player disables immersive mode.
            window.setDecorFitsSystemWindows(!enabled)
            val controller = window.insetsController
            if (controller != null) {
                if (enabled) {
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.systemBars())
                } else {
                    controller.show(WindowInsets.Type.systemBars())
                }
                return
            }
        }

        // API 26–29 fallback. IMMERSIVE_STICKY lets edge swipes reveal the
        // transient status/navigation bars without removing the game surface.
        window.decorView.systemUiVisibility = if (enabled) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun applyCutoutPolicy(window: Window, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val attributes = window.attributes
        val requested = if (enabled) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        if (attributes.layoutInDisplayCutoutMode != requested) {
            attributes.layoutInDisplayCutoutMode = requested
            window.attributes = attributes
        }
    }
}
