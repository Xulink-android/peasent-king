package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class SafePortNavigatorTest {
    private val port = SafePortNavigator.TextBox("Port", 540, 1380, 610, 1410)
    private val monde = SafePortNavigator.TextBox("Monde", 585, 1475, 645, 1515)
    private val terre = SafePortNavigator.TextBox("Terre", 160, 115, 220, 142)

    @Test fun findsRealScreenRegionWithoutHardcodedTapCoordinates() {
        val found = SafePortNavigator.detect(listOf(port, terre, monde), 709, 1536)
        assertNotNull(found)
        assertEquals(575, found?.x)
        assertEquals(1395, found?.y)
    }

    @Test fun requiresWorldContextAndBottomRightLocation() {
        assertNull(SafePortNavigator.detect(listOf(port), 709, 1536))
        assertNull(SafePortNavigator.detect(listOf(terre,
            port.copy(left = 30, right = 100)), 709, 1536))
        assertNull(SafePortNavigator.detect(listOf(terre,
            port.copy(top = 200, bottom = 230)), 709, 1536))
        assertNull(SafePortNavigator.detect(listOf(terre,
            port.copy(text = "PORTAIL")), 709, 1536))
    }

    @Test fun duplicateOrConflictingPortButtonNeverAuthorizesTap() {
        assertNull(SafePortNavigator.detect(listOf(terre, port, port.copy(left = 620, right = 690)), 709, 1536))
        assertNull(SafePortNavigator.detect(listOf(monde, port), 1536, 709))
    }

    @Test fun requiresThreeStableFramesAndResetsOnMissingFrame() {
        val gate = PortTargetGate()
        val target = SafePortNavigator.detect(listOf(terre, port), 709, 1536)!!
        assertNull(gate.observe(target))
        assertNull(gate.observe(target.copy(x = target.x + 2)))
        assertNotNull(gate.observe(target))
        assertNull(gate.observe(null))
        assertNull(gate.observe(target))
    }

    @Test fun changingCoordinatesResetsConfirmation() {
        val gate = PortTargetGate()
        val target = SafePortNavigator.detect(listOf(terre, port), 709, 1536)!!
        repeat(2) { gate.observe(target) }
        assertNull(gate.observe(target.copy(x = target.x + 50)))
        assertNull(gate.observe(target))
    }
}
