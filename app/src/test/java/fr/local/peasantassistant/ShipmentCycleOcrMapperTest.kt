package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentCycleOcrMapperTest {
    private val plan = ShipmentCycleMachine.Shipment("Eastmere", "lumber", 89, 8)

    private fun map(
        screen: ShipmentCycleMachine.Screen,
        accepted: Boolean? = false,
        kingdom: String = "Eastmere",
        reward: Int? = 8,
        evidence: ShipmentScreenEvidenceParser.Evidence = ShipmentScreenEvidenceParser.Evidence(),
        stock: Int? = null,
        capacity: Int? = null,
        acceptVisible: Boolean = false,
        carried: Boolean = false,
        observedResource: String? = "lumber"
    ) = ShipmentCycleOcrMapper.map(ShipmentCycleOcrMapper.Input(
        screen = screen,
        plan = plan,
        contract = ObservationGate.Snapshot(kingdom, "lumber", 89, reward, accepted),
        observedResource = observedResource,
        acceptButtonVisible = acceptVisible,
        screenEvidence = evidence,
        stockAvailable = stock,
        shipCapacity = capacity,
        continuityBound = carried
    ))

    @Test fun contractOfferRequiresExactFactsStockCapacityAndVisibleAcceptButton() {
        val valid = map(ShipmentCycleMachine.Screen.CONTRACTS, stock = 100, capacity = 120, acceptVisible = true)
        assertTrue(valid.unambiguous)
        assertTrue(valid.contractVerified)
        assertFalse(valid.contractAccepted)
        assertTrue(valid.acceptButtonVisible)
        assertEquals(89, valid.quantity)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, accepted = true,
            stock = 100, capacity = 120, acceptVisible = true).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, stock = 88, capacity = 120,
            acceptVisible = true).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, stock = 100, capacity = 88,
            acceptVisible = true).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, stock = 100, capacity = 120).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, kingdom = "Gullhaven Isles",
            stock = 100, capacity = 120, acceptVisible = true).unambiguous)
    }

    @Test fun sailingRequiresAcceptedMatchingContractAndArrivalRequiresExactCargo() {
        val sailing = map(ShipmentCycleMachine.Screen.SHIP_SAILING, accepted = true,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere"), carried = true)
            .copy(departureAccepted = true)
        assertTrue(sailing.unambiguous)
        assertTrue(sailing.contractAccepted)
        assertTrue(map(ShipmentCycleMachine.Screen.SHIP_SAILING, accepted = false, carried = true,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere")).let { it.copy(departureAccepted = true) }.unambiguous.not())

        val arrived = map(ShipmentCycleMachine.Screen.UNLOAD_CARGO,
            evidence = ShipmentScreenEvidenceParser.Evidence(remainingCargo = 89, kingdom = "Eastmere"), carried = true)
        assertTrue(arrived.arrivalConfirmed)
        assertTrue(arrived.unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.UNLOAD_CARGO,
            evidence = ShipmentScreenEvidenceParser.Evidence(remainingCargo = 88, kingdom = "Eastmere"), carried = true).unambiguous)
    }

    @Test fun securedPaymentAndShipReturnNeedExactDestinationAndAmount() {
        val returnReady = map(ShipmentCycleMachine.Screen.RETURN_READY,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere", paymentSecuredAmount = 8), carried = true)
        assertTrue(returnReady.unambiguous)
        assertTrue(returnReady.paymentSecured)
        assertFalse(map(ShipmentCycleMachine.Screen.RETURN_READY,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere", paymentSecuredAmount = 7), carried = true).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.RETURN_READY,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Gullhaven Isles", paymentSecuredAmount = 8), carried = true).unambiguous)
        assertTrue(map(ShipmentCycleMachine.Screen.SHIPYARD_READY, carried = true).shipReturnedReady)
        assertFalse(map(ShipmentCycleMachine.Screen.SHIPYARD_READY, carried = false).unambiguous)
    }
}
