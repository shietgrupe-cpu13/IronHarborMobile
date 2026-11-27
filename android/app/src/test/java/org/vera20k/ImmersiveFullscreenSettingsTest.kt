package org.vera20k

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveFullscreenSettingsTest {
    @Test
    fun immersiveFullscreenDefaultsOnForExistingInstalls() {
        assertTrue(ImmersiveFullscreenSettings.resolveLaunchValue(null))
    }

    @Test
    fun explicitOffPreferenceSurvivesLaunchHandoff() {
        assertFalse(ImmersiveFullscreenSettings.resolveLaunchValue(false))
    }

    @Test
    fun explicitOnPreferenceSurvivesLaunchHandoff() {
        assertTrue(ImmersiveFullscreenSettings.resolveLaunchValue(true))
    }
}
