package fr.local.peasantassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShipyardBadgeStockParserTest {
    @Test fun parsesIconLabeledCountersAsPileAmountsNotReserves() {
        val result = ShipyardBadgeStockParser.parse(listOf(
            ShipyardBadgeStockParser.OcrLine("111/200", "lumber"),
            ShipyardBadgeStockParser.OcrLine("112/200", "arms")
        ))
        assertEquals(listOf(
            ShipyardBadgeStockParser.Stack("lumber", 111, 200),
            ShipyardBadgeStockParser.Stack("arms", 112, 200)
        ), result.stacks)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test fun unknownIconsAndBadCountsNeverBecomeUsableStacks() {
        val result = ShipyardBadgeStockParser.parse(listOf(
            ShipyardBadgeStockParser.OcrLine("100/200", null),
            ShipyardBadgeStockParser.OcrLine("250/200", "coal"),
            ShipyardBadgeStockParser.OcrLine("100/0", "grain")
        ))
        assertEquals(1, result.stacks.size)
        assertNull(result.stacks.single().resource)
        assertEquals(3, result.warnings.size)
    }

    @Test fun duplicateResourceBadgesAreRejectedRatherThanSummed() {
        val result = ShipyardBadgeStockParser.parse(listOf(
            ShipyardBadgeStockParser.OcrLine("60/200", "coal"),
            ShipyardBadgeStockParser.OcrLine("40/200", "coal")
        ))
        assertEquals(listOf(null, null), result.stacks.map { it.resource })
        assertEquals(1, result.warnings.size)
    }
}
