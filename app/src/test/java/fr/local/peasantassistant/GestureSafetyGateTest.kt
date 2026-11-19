package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class GestureSafetyGateTest {
    private val safe = GestureSafetyGate.Request(
        userEnabled = true, gameForeground = true,
        screenConfirmed = true, targetConfirmed = true,
        observationAgeMs = 300, screenWidth = 1440,
        screenHeight = 3120, x = 500, y = 1000
    )

    @Test fun requiresExplicitOptIn() {
        assertFalse(GestureSafetyGate.allowed(safe.copy(userEnabled = false)))
    }

    @Test fun blocksOtherAppsAndUnknownScreens() {
        assertFalse(GestureSafetyGate.allowed(safe.copy(gameForeground = false)))
        assertFalse(GestureSafetyGate.allowed(safe.copy(screenConfirmed = false)))
        assertFalse(GestureSafetyGate.allowed(safe.copy(targetConfirmed = false)))
    }

    @Test fun rejectsStaleAndInvalidCoordinates() {
        assertFalse(GestureSafetyGate.allowed(safe.copy(observationAgeMs = 1501)))
        assertFalse(GestureSafetyGate.allowed(safe.copy(observationAgeMs = -1)))
        assertFalse(GestureSafetyGate.allowed(safe.copy(x = -1)))
        assertFalse(GestureSafetyGate.allowed(safe.copy(y = 3120)))
        assertTrue(GestureSafetyGate.allowed(safe))
    }
}
