package org.vera20k

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLaunchGateTest {
    @Test fun rejectsDuplicateStartsUntilGameReturns() {
        val gate = GameLaunchGate()

        assertTrue(gate.begin())
        assertFalse(gate.begin())
        assertTrue(gate.isPending)

        gate.returnedToLauncher()
        assertTrue(gate.begin())
    }

    @Test fun permitsRetryAfterPreflightFailure() {
        val gate = GameLaunchGate()

        assertTrue(gate.begin())
        gate.completedWithoutLaunch()

        assertTrue(gate.begin())
    }
}
