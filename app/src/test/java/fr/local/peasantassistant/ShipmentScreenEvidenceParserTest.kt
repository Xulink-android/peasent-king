package fr.local.peasantassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentScreenEvidenceParserTest {
    @Test fun extractsOnlyExplicitValuesSeenInTheShipmentPanel() {
        val evidence = ShipmentScreenEvidenceParser.parse(listOf(
            "Capacité 200 · Vitesse 88 % · Condition 80 %",
            "ENVOYER 48/89",
            "GAGNER 8",
            "Naviguez vers Eastmere"
        ))
        assertEquals(48, evidence.loadedQuantity)
        assertEquals(89, evidence.requestedQuantity)
        assertEquals(200, evidence.shipCapacity)
        assertEquals(8, evidence.reward)
        assertEquals("Eastmere", evidence.kingdom)
        assertTrue(evidence.warnings.isEmpty())
    }

    @Test fun acceptsObservedArrivalAndPaymentLabelsWithoutInferringSuccess() {
        val evidence = ShipmentScreenEvidenceParser.parse(listOf(
            "Highvale Star à Eastmere",
            "89 cargaison restante",
            "Récupérer 8"
        ))
        assertEquals("Eastmere", evidence.kingdom)
        assertEquals(89, evidence.remainingCargo)
        assertEquals(8, evidence.paymentAmount)
        assertNull(evidence.loadedQuantity)
    }

    @Test fun conflictingProgressOrDestinationIsMarkedAmbiguous() {
        val evidence = ShipmentScreenEvidenceParser.parse(listOf(
            "ENVOYER 48/89", "ENVOYER 89/89",
            "Naviguez vers Eastmere", "Naviguez vers Gullhaven Isles"
        ))
        assertNull(evidence.loadedQuantity)
        assertNull(evidence.kingdom)
        assertTrue(evidence.warnings.any { it.contains("ambiguë") })
    }

    @Test fun malformedProgressAndMissingValuesStayUnknown() {
        val evidence = ShipmentScreenEvidenceParser.parse(listOf(
            "ENVOYER 94/89", "Capacité ???", "GAGNER 0"
        ))
        assertNull(evidence.loadedQuantity)
        assertNull(evidence.requestedQuantity)
        assertNull(evidence.shipCapacity)
        assertNull(evidence.reward)
        assertTrue(evidence.warnings.isNotEmpty())
    }

    @Test fun contractQuantityWithoutProgressSlashIsNotMisreportedAsAnOcrError() {
        val evidence = ShipmentScreenEvidenceParser.parse(listOf("ENVOYER 89", "GAGNER 8"))
        assertNull(evidence.loadedQuantity)
        assertTrue(evidence.warnings.isEmpty())
    }
}
