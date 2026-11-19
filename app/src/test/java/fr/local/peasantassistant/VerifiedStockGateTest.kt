package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class VerifiedStockGateTest {
    private val valid = StockScreenParser.parse(listOf(
        "PORT BLÉ: 150 / RESERVE: 20",
        "CAPACITÉ: 200",
        "NAVIRES DISPONIBLES: 2"
    ))

    @Test fun requiresThreeIdenticalPortScreens() {
        val gate = VerifiedStockGate()
        assertNull(gate.observe(true, valid))
        assertNull(gate.observe(true, valid))
        assertEquals(150, gate.observe(true, valid)?.supplies?.single()?.availableAtPort)
    }

    @Test fun appOrScreenSwitchResetsTrust() {
        val gate = VerifiedStockGate()
        repeat(3) { gate.observe(true, valid) }
        assertNull(gate.observe(false, valid))
        assertNull(gate.observe(true, valid))
    }

    @Test fun changesInStockResetTrust() {
        val gate = VerifiedStockGate()
        repeat(3) { gate.observe(true, valid) }
        val changed = StockScreenParser.parse(listOf(
            "PORT BLÉ: 149 / RESERVE: 20",
            "CAPACITÉ: 200",
            "NAVIRES DISPONIBLES: 2"
        ))
        assertNull(gate.observe(true, changed))
    }

    @Test fun incompleteStockCannotBeVerified() {
        val gate = VerifiedStockGate()
        val incomplete = StockScreenParser.parse(listOf(
            "PORT BLÉ: 150", "CAPACITÉ: 200", "NAVIRES DISPONIBLES: 2"
        ))
        repeat(4) { assertNull(gate.observe(true, incomplete)) }
    }
}
