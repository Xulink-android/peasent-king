package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class StockScreenParserTest {
    @Test fun rejectsUnlabelledQuantities() {
        val result = StockScreenParser.parse(listOf("ENVOYER 95", "100", "GAGNER 7"))
        assertTrue(result.supplies.isEmpty())
        assertNull(result.shipCapacity)
        assertNull(result.availableShips)
    }

    @Test fun acceptsOnlyExplicitStockWithReservation() {
        val result = StockScreenParser.parse(listOf(
            "PORT BLÉ: 150 / RESERVE: 20",
            "CAPACITÉ: 200",
            "NAVIRES DISPONIBLES: 2"
        ))
        assertEquals(listOf(ShipmentPlanner.Supply("grain", 150, 20)), result.supplies)
        assertEquals(200, result.shipCapacity)
        assertEquals(2, result.availableShips)
    }

    @Test fun unknownReservationNeverBecomesAvailableStock() {
        val result = StockScreenParser.parse(listOf("PORT BOIS 400"))
        assertTrue(result.supplies.isEmpty())
    }

    @Test fun duplicatesAreAlwaysRejected() {
        val result = StockScreenParser.parse(listOf(
            "PORT COAL 100 / RESERVE 0",
            "PORT COAL 200 / RESERVE 0",
            "PORT COAL 300 / RESERVE 0"
        ))
        assertTrue(result.supplies.isEmpty())
    }

    @Test fun repeatedCapacityNeverRecoversOnThirdLine() {
        val result = StockScreenParser.parse(listOf(
            "PORT COAL 100 / RESERVE 0",
            "CAPACITÉ 200", "CAPACITÉ 200", "CAPACITÉ 200",
            "NAVIRES DISPONIBLES 2"
        ))
        assertNull(result.shipCapacity)
    }

    @Test fun repeatedShipsNeverRecoversOnThirdLine() {
        val result = StockScreenParser.parse(listOf(
            "PORT COAL 100 / RESERVE 0",
            "CAPACITÉ 200",
            "NAVIRES DISPONIBLES 2", "NAVIRES DISPONIBLES 2", "NAVIRES DISPONIBLES 2"
        ))
        assertNull(result.availableShips)
    }

    @Test fun malformedFirstStockRowCannotBeReplacedByLaterRow() {
        val result = StockScreenParser.parse(listOf(
            "PORT COAL 100",
            "PORT COAL 100 / RESERVE 0",
            "CAPACITÉ 200",
            "NAVIRES DISPONIBLES 2"
        ))
        assertTrue(result.supplies.isEmpty())
    }

    @Test fun invalidReservedAmountIsRejected() {
        val result = StockScreenParser.parse(listOf("PORT COAL 100 / RESERVE 150"))
        assertTrue(result.supplies.isEmpty())
    }
}
