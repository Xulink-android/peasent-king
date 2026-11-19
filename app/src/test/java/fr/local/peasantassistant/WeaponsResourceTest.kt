package fr.local.peasantassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaponsResourceTest {
    @Test fun armsAliasIsAvailableForLocalIconTraining() {
        assertTrue(IconLibrary.labels.contains("arms"))
    }

    @Test fun contractParserRecognizesArmsAliasesWithoutGuessingOtherItems() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("ENVOYER 89", 300, 100),
            ContractParser.Line("Eastmere", 300, 102),
            ContractParser.Line("ARMES", 300, 108),
            ContractParser.Line("GAGNER 8", 300, 110)
        ))
        assertEquals("arms", result.contracts.single().resource)
    }

    @Test fun stockParserAcceptsArmsOnlyWhenReserveIsExplicit() {
        val valid = StockScreenParser.parse(listOf("PORT ARMES 120 / RESERVE 20"))
        assertEquals(listOf(ShipmentPlanner.Supply("arms", 120, 20)), valid.supplies)
        val unknownReserve = StockScreenParser.parse(listOf("PORT WEAPONS 120"))
        assertTrue(unknownReserve.supplies.isEmpty())
    }
}
