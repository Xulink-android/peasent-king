package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ObservedGameTargetResolverTest {
    private val good = ObservedGameTargetResolver.ShipmentEvidence(
        kingdom = "Eastmere", resource = "lumber", quantity = 89,
        confirmedAvailable = 111, shipCapacity = 200, observedResource = "lumber", unambiguous = true
    )

    @Test fun destinationUsesExactVisibleButtonAndCompleteShipmentProof() {
        val boxes = listOf(
            ObservedGameTargetResolver.Box("ENVOYER 89/89", 620, 800, 900, 850),
            ObservedGameTargetResolver.Box("Naviguez vers Eastmere", 365, 1248, 744, 1335)
        )
        val target = ObservedGameTargetResolver.destinationTap(
            GameScreenClassifier.Screen.SHIP_READY_TO_SAIL, boxes, 1080, 2340, good)
        assertNotNull(target)
        assertEquals(554, target!!.start.x)
        assertEquals(1291, target.start.y)
    }

    @Test fun destinationFailsClosedForWrongResourceQuantityScreenOrDuplicateButton() {
        val boxes = listOf(
            ObservedGameTargetResolver.Box("ENVOYER 89/89", 620, 800, 900, 850),
            ObservedGameTargetResolver.Box("Naviguez vers Eastmere", 365, 1248, 744, 1335)
        )
        assertNull(ObservedGameTargetResolver.destinationTap(
            GameScreenClassifier.Screen.SHIP_READY_TO_SAIL, boxes, 1080, 2340,
            good.copy(observedResource = "coal")))
        assertNull(ObservedGameTargetResolver.destinationTap(
            GameScreenClassifier.Screen.SHIP_READY_TO_SAIL, boxes, 1080, 2340,
            good.copy(confirmedAvailable = 88)))
        assertNull(ObservedGameTargetResolver.destinationTap(
            GameScreenClassifier.Screen.SHIP_LOADING, boxes, 1080, 2340, good))
        assertNull(ObservedGameTargetResolver.destinationTap(
            GameScreenClassifier.Screen.SHIP_READY_TO_SAIL,
            boxes + boxes.last(), 1080, 2340, good))
    }

    @Test fun loadingRequiresVerifiedResourceStockAndCapacity() {
        val boxes = listOf(
            ObservedGameTargetResolver.Box("Maintiens la cargaison, glisse-la à bord et relâche", 80, 900, 980, 990),
            ObservedGameTargetResolver.Box("ENTREPÔT", 430, 1390, 650, 1430)
        )
        val stock = ObservedGameTargetResolver.Box("111/200", 365, 1690, 520, 1735)
        val target = ObservedGameTargetResolver.loadCargoDrag(
            GameScreenClassifier.Screen.SHIP_LOADING, boxes, 1080, 2340, stock, 111, good)
        assertNotNull(target)
        assertTrue(target!!.isDrag)
        assertEquals(442, target.start.x)
        assertEquals(1712, target.start.y)
        assertEquals(540, target.end.x)
        assertEquals(1190, target.end.y)
        assertNull(ObservedGameTargetResolver.loadCargoDrag(
            GameScreenClassifier.Screen.SHIP_LOADING, boxes, 1080, 2340, stock, 88, good))
        assertNull(ObservedGameTargetResolver.loadCargoDrag(
            GameScreenClassifier.Screen.SHIP_LOADING, boxes, 1080, 2340, stock, 111,
            good.copy(observedResource = "coal")))
    }

    @Test fun paymentRequiresExpectedKingdomAmountAndUniqueButton() {
        val boxes = listOf(
            ObservedGameTargetResolver.Box("Highvale Star à Eastmere", 300, 600, 780, 650),
            ObservedGameTargetResolver.Box("Récupérer 8", 390, 1250, 690, 1320)
        )
        assertNotNull(ObservedGameTargetResolver.paymentTap(
            GameScreenClassifier.Screen.COLLECT_PAYMENT, boxes, 1080, 2340, "Eastmere", 8))
        assertNull(ObservedGameTargetResolver.paymentTap(
            GameScreenClassifier.Screen.COLLECT_PAYMENT, boxes, 1080, 2340, "Gullhaven Isles", 8))
        assertNull(ObservedGameTargetResolver.paymentTap(
            GameScreenClassifier.Screen.SHIPYARD, boxes, 1080, 2340, "Eastmere", 8))
    }
}
