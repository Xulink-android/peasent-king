package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentCycleMachineTest {
    private val plan = ShipmentCycleMachine.Shipment("Eastmere", "lumber", 89, 8)

    private fun stable(machine: ShipmentCycleMachine, observation: ShipmentCycleMachine.Observation, start: Long): ShipmentCycleMachine.Decision {
        machine.observe(observation, start)
        machine.observe(observation, start + 100)
        return machine.observe(observation, start + 200)
    }

    private fun base(screen: ShipmentCycleMachine.Screen) = ShipmentCycleMachine.Observation(
        screen = screen, resource = "lumber", quantity = 89, kingdom = "Eastmere",
        stockAvailable = 111, shipCapacity = 200, reward = 8, unambiguous = true
    )

    private fun loadPart(machine: ShipmentCycleMachine, loaded: Int, start: Long): ShipmentCycleMachine.Decision =
        stable(machine, base(ShipmentCycleMachine.Screen.SHIP_LOADING).copy(
            loadedQuantity = loaded, stockAvailable = 111 - loaded
        ), start)

    @Test fun fullCycleRequiresAcknowledgementAndFreshEvidenceAtEveryPhase() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, userApproved = true, nowMs = 1000)
        val contract = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true, contractAccepted = true)
        assertEquals(ShipmentCycleMachine.Action.OPEN_PORT, stable(machine, contract, 1100).action)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_LOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, true).state)

        assertEquals(ShipmentCycleMachine.Action.LOAD_CARGO, loadPart(machine, 0, 1500).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_LOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_LOAD, loadPart(machine, 48, 1800).state)
        assertEquals(ShipmentCycleMachine.Action.LOAD_CARGO, loadPart(machine, 48, 2100).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_LOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_READY, loadPart(machine, 89, 2400).state)

        val ready = base(ShipmentCycleMachine.Screen.SHIP_READY)
            .copy(cargoLoaded = true, loadedQuantity = 89)
        assertEquals(ShipmentCycleMachine.Action.SEND_SHIP, stable(machine, ready, 2700).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_DEPARTURE,
            machine.acknowledge(ShipmentCycleMachine.Action.SEND_SHIP, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_ARRIVAL,
            stable(machine, base(ShipmentCycleMachine.Screen.SHIP_SAILING).copy(departureAccepted = true), 3000).state)

        assertEquals(ShipmentCycleMachine.Action.UNLOAD_CARGO,
            stable(machine, base(ShipmentCycleMachine.Screen.UNLOAD_CARGO).copy(arrivalConfirmed = true), 3300).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_UNLOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.UNLOAD_CARGO, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_PAYMENT,
            stable(machine, base(ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT).copy(cargoUnloaded = true), 3600).state)

        assertEquals(ShipmentCycleMachine.Action.COLLECT_PAYMENT,
            stable(machine, base(ShipmentCycleMachine.Screen.COLLECT_PAYMENT), 3900).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_PAYMENT,
            machine.acknowledge(ShipmentCycleMachine.Action.COLLECT_PAYMENT, true).state)
        assertEquals(ShipmentCycleMachine.State.COMPLETED,
            stable(machine, base(ShipmentCycleMachine.Screen.WORLD_MAP).copy(paymentCollected = true), 4200).state)
        assertNull(machine.observe(base(ShipmentCycleMachine.Screen.WORLD_MAP), 4500).action)
    }

    @Test fun insufficientStockAndAmbiguousOcrBlockBeforeAnyAction() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        val lowStock = base(ShipmentCycleMachine.Screen.CONTRACTS)
            .copy(contractVerified = true, contractAccepted = true, stockAvailable = 88)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, stable(machine, lowStock, 1100).state)

        machine.start(plan, true, 2000)
        val uncertain = base(ShipmentCycleMachine.Screen.CONTRACTS)
            .copy(contractVerified = true, contractAccepted = true, unambiguous = false)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, machine.observe(uncertain, 2100).state)
    }

    @Test fun unacceptedContractNeverOpensThePortForAnAutomatedCycle() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, userApproved = true, nowMs = 1000)
        val unaccepted = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(
            contractVerified = true,
            contractAccepted = false
        )
        assertEquals(ShipmentCycleMachine.State.BLOCKED, stable(machine, unaccepted, 1100).state)
        assertNull(machine.observe(unaccepted, 1500).action)
    }

    @Test fun departureIsNeverRetriedAndStopCancelsPendingActions() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        stable(machine, base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true, contractAccepted = true), 1100)
        machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, true)
        assertEquals(ShipmentCycleMachine.Action.LOAD_CARGO, loadPart(machine, 0, 1500).action)
        machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_READY, loadPart(machine, 89, 1800).state)
        val ready = base(ShipmentCycleMachine.Screen.SHIP_READY).copy(cargoLoaded = true, loadedQuantity = 89)
        stable(machine, ready, 2100)
        machine.acknowledge(ShipmentCycleMachine.Action.SEND_SHIP, true)

        val notAccepted = base(ShipmentCycleMachine.Screen.WORLD_MAP)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_DEPARTURE,
            stable(machine, notAccepted, 2400).state)
        assertNull(machine.observe(notAccepted, 2700).action)

        machine.stop()
        assertNull(machine.observe(base(ShipmentCycleMachine.Screen.SHIP_SAILING), 2800).action)
    }

    @Test fun wrongDestinationOrFailedGestureBlocksTheCycle() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        val wrong = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(
            kingdom = "Gullhaven Isles", contractVerified = true, contractAccepted = true)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, stable(machine, wrong, 1100).state)

        machine.start(plan, true, 2000)
        stable(machine, base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true, contractAccepted = true), 2100)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, false).state)
    }

    @Test fun noLoadProgressBlocksWithoutRepeatingTheDrag() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        stable(machine, base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true, contractAccepted = true), 1100)
        machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, true)
        assertEquals(ShipmentCycleMachine.Action.LOAD_CARGO, loadPart(machine, 0, 1500).action)
        machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, loadPart(machine, 0, 1800).state)
        assertNull(machine.observe(base(ShipmentCycleMachine.Screen.SHIP_LOADING).copy(loadedQuantity = 0), 2200).action)
    }
}
