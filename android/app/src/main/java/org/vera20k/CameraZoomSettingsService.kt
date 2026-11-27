package org.vera20k

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Sends presentation-only camera limits to the already-running native process. */
class CameraZoomSettingsService : Service() {
    companion object {
        init {
            System.loadLibrary("vera20k_android")
        }
        const val EXTRA_MINIMUM = "camera_zoom_minimum"
        const val EXTRA_MAXIMUM = "camera_zoom_maximum"
    }

    private external fun nativeApplyCameraZoomLimits(minimum: Float, maximum: Float): Boolean

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minimum = intent?.getFloatExtra(EXTRA_MINIMUM, Float.NaN) ?: Float.NaN
        val maximum = intent?.getFloatExtra(EXTRA_MAXIMUM, Float.NaN) ?: Float.NaN
        if (minimum.isFinite() && maximum.isFinite() && minimum <= maximum) {
            nativeApplyCameraZoomLimits(minimum, maximum)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
