package fr.local.peasantassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeInventoryGateTest {
    private val badges = ShipyardBadgeStockParser.Result(
        stacks = listOf(
            ShipyardBadgeStockParser.Stack("lumber", 111, 200),
            ShipyardBadgeStockParser.Stack("grain", 200, 200)
        ),
        warnings = emptyList()
    )

    @Test fun confirmsOnlyAfterThreeIdenticalSelectedShipObservations() {
        val gate = BadgeInventoryGate()
        assertNull(gate.observe(true, badges, 200))
        assertNull(gate.observe(true, badges, 200))
        assertEquals(
            listOf(
                ShipmentPlanner.Supply("grain", 200, 0),
                ShipmentPlanner.Supply("lumber", 111, 0)
            ),
            gate.observe(true, badges, 200)?.supplies
        )
    }

    @Test fun unknownAmbiguousOrUnselectedInventoryNeverBecomesAvailableStock() {
        val gate = BadgeInventoryGate()
        repeat(3) { gate.observe(true, badges, 200) }
        assertNull(gate.observe(false, badges, 200))
        assertNull(gate.observe(true, badges.copy(warnings = listOf("unknown badge")), 200))
        assertNull(gate.observe(true, badges, null))
    }

    @Test fun duplicateResourceStacksAreRejectedInsteadOfDoubleCounted() {
        val gate = BadgeInventoryGate()
        val duplicate = ShipyardBadgeStockParser.Result(
            listOf(
                ShipyardBadgeStockParser.Stack("coal", 60, 200),
                ShipyardBadgeStockParser.Stack("coal", 40, 200)
            ), emptyList()
        )
        assertNull(gate.observe(true, duplicate, 200))
    }
}
