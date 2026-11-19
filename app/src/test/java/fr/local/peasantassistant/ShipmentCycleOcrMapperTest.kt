package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentCycleOcrMapperTest {
    private val plan = ShipmentCycleMachine.Shipment("Eastmere", "lumber", 89, 8)

    private fun map(
        screen: ShipmentCycleMachine.Screen,
        contract: ObservationGate.Snapshot? = null,
        evidence: ShipmentScreenEvidenceParser.Evidence = ShipmentScreenEvidenceParser.Evidence(),
        stock: Int? = null,
        capacity: Int? = null,
        carried: Boolean = false,
        paymentCollected: Boolean = false
    ) = ShipmentCycleOcrMapper.map(ShipmentCycleOcrMapper.Input(
        screen = screen,
        plan = plan,
        contract = contract,
        observedResource = "lumber",
        screenEvidence = evidence,
        stockAvailable = stock,
        shipCapacity = capacity,
        continuityBound = carried,
        paymentCollected = paymentCollected
    ))

    @Test fun contractsNeedExactSignedContractAndSufficientStockAndCapacity() {
        val signed = ObservationGate.Snapshot("Eastmere", "lumber", 89, 8, true)
        val valid = map(ShipmentCycleMachine.Screen.CONTRACTS, signed, stock = 100, capacity = 120)
        assertTrue(valid.unambiguous)
        assertTrue(valid.contractAccepted)
        assertEquals(89, valid.quantity)

        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, signed.copy(accepted = false), stock = 100, capacity = 120).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, signed, stock = 88, capacity = 120).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, signed, stock = 100, capacity = 88).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.CONTRACTS, signed.copy(reward = null), stock = 100, capacity = 120).unambiguous)
    }

    @Test fun loadingAndReadyScreensNeedExactObservedProgress() {
        val loading = map(ShipmentCycleMachine.Screen.SHIP_LOADING,
            evidence = ShipmentScreenEvidenceParser.Evidence(loadedQuantity = 48, requestedQuantity = 89),
            stock = 100, capacity = 120, carried = true)
        assertTrue(loading.unambiguous)
        assertEquals(48, loading.loadedQuantity)
        assertFalse(map(ShipmentCycleMachine.Screen.SHIP_LOADING,
            evidence = ShipmentScreenEvidenceParser.Evidence(loadedQuantity = 48, requestedQuantity = 89,
                warnings = listOf("ambiguous")), stock = 100, capacity = 120, carried = true).unambiguous)

        val ready = map(ShipmentCycleMachine.Screen.SHIP_READY,
            evidence = ShipmentScreenEvidenceParser.Evidence(loadedQuantity = 89, requestedQuantity = 89),
            carried = true)
        assertTrue(ready.unambiguous)
        assertTrue(ready.cargoLoaded)
        assertEquals(89, ready.loadedQuantity)
        assertFalse(map(ShipmentCycleMachine.Screen.SHIP_READY,
            evidence = ShipmentScreenEvidenceParser.Evidence(loadedQuantity = 88, requestedQuantity = 89),
            carried = true).unambiguous)
    }

    @Test fun arrivalAndPaymentRequireDestinationAndExactQuantities() {
        val arrived = map(ShipmentCycleMachine.Screen.UNLOAD_CARGO,
            evidence = ShipmentScreenEvidenceParser.Evidence(remainingCargo = 89, kingdom = "Eastmere"), carried = true)
        assertTrue(arrived.arrivalConfirmed)
        assertTrue(arrived.unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.UNLOAD_CARGO,
            evidence = ShipmentScreenEvidenceParser.Evidence(remainingCargo = 88, kingdom = "Eastmere"), carried = true).unambiguous)

        val collect = map(ShipmentCycleMachine.Screen.COLLECT_PAYMENT,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere", paymentAmount = 8), carried = true)
        assertTrue(collect.unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.COLLECT_PAYMENT,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Gullhaven", paymentAmount = 8), carried = true).unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.COLLECT_PAYMENT,
            evidence = ShipmentScreenEvidenceParser.Evidence(kingdom = "Eastmere", paymentAmount = 7), carried = true).unambiguous)
    }

    @Test fun collectedPaymentRequiresPostGestureScreenEvidence() {
        val proof = map(ShipmentCycleMachine.Screen.WORLD_MAP, carried = true, paymentCollected = true)
        assertTrue(proof.paymentCollected)
        assertTrue(proof.unambiguous)
        assertFalse(map(ShipmentCycleMachine.Screen.COLLECT_PAYMENT, carried = true, paymentCollected = true).paymentCollected)
        assertFalse(map(ShipmentCycleMachine.Screen.WORLD_MAP, carried = true).unambiguous)
    }
}
