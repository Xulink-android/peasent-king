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

    @Test fun fullCycleRequiresAcknowledgementAndFreshEvidenceAtEveryPhase() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, userApproved = true, nowMs = 1000)
        val contract = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true)
        assertEquals(ShipmentCycleMachine.Action.OPEN_PORT, stable(machine, contract, 1100).action)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_LOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, true).state)

        assertEquals(ShipmentCycleMachine.Action.LOAD_CARGO,
            stable(machine, base(ShipmentCycleMachine.Screen.SHIP_LOADING), 1500).action)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_READY,
            machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true).state)

        val ready = base(ShipmentCycleMachine.Screen.SHIP_READY).copy(cargoLoaded = true)
        assertEquals(ShipmentCycleMachine.Action.SEND_SHIP, stable(machine, ready, 1900).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_DEPARTURE,
            machine.acknowledge(ShipmentCycleMachine.Action.SEND_SHIP, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_ARRIVAL,
            stable(machine, base(ShipmentCycleMachine.Screen.SHIP_SAILING).copy(departureAccepted = true), 2300).state)

        assertEquals(ShipmentCycleMachine.Action.UNLOAD_CARGO,
            stable(machine, base(ShipmentCycleMachine.Screen.UNLOAD_CARGO).copy(arrivalConfirmed = true), 2700).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_UNLOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.UNLOAD_CARGO, true).state)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_PAYMENT,
            stable(machine, base(ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT).copy(cargoUnloaded = true), 3100).state)

        assertEquals(ShipmentCycleMachine.Action.COLLECT_PAYMENT,
            stable(machine, base(ShipmentCycleMachine.Screen.COLLECT_PAYMENT), 3500).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_PAYMENT,
            machine.acknowledge(ShipmentCycleMachine.Action.COLLECT_PAYMENT, true).state)
        assertEquals(ShipmentCycleMachine.State.COMPLETED,
            stable(machine, base(ShipmentCycleMachine.Screen.WORLD_MAP).copy(paymentCollected = true), 3900).state)
        assertNull(machine.observe(base(ShipmentCycleMachine.Screen.WORLD_MAP), 4300).action)
    }

    @Test fun insufficientStockAndAmbiguousOcrBlockBeforeAnyAction() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        val lowStock = base(ShipmentCycleMachine.Screen.CONTRACTS)
            .copy(contractVerified = true, stockAvailable = 88)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, stable(machine, lowStock, 1100).state)

        machine.start(plan, true, 2000)
        val uncertain = base(ShipmentCycleMachine.Screen.CONTRACTS)
            .copy(contractVerified = true, unambiguous = false)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, machine.observe(uncertain, 2100).state)
    }

    @Test fun departureIsNeverRetriedAndStopCancelsPendingActions() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        val contract = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true)
        stable(machine, contract, 1100)
        machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, true)
        stable(machine, base(ShipmentCycleMachine.Screen.SHIP_LOADING), 1500)
        machine.acknowledge(ShipmentCycleMachine.Action.LOAD_CARGO, true)
        stable(machine, base(ShipmentCycleMachine.Screen.SHIP_READY).copy(cargoLoaded = true), 1900)
        machine.acknowledge(ShipmentCycleMachine.Action.SEND_SHIP, true)

        val notAccepted = base(ShipmentCycleMachine.Screen.WORLD_MAP)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_DEPARTURE,
            stable(machine, notAccepted, 2300).state)
        assertNull(machine.observe(notAccepted, 2700).action)

        machine.stop()
        assertNull(machine.observe(base(ShipmentCycleMachine.Screen.SHIP_SAILING), 2800).action)
    }

    @Test fun wrongDestinationOrFailedGestureBlocksTheCycle() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        val wrong = base(ShipmentCycleMachine.Screen.CONTRACTS).copy(
            kingdom = "Gullhaven Isles", contractVerified = true)
        assertEquals(ShipmentCycleMachine.State.BLOCKED, stable(machine, wrong, 1100).state)

        machine.start(plan, true, 2000)
        stable(machine, base(ShipmentCycleMachine.Screen.CONTRACTS).copy(contractVerified = true), 2100)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            machine.acknowledge(ShipmentCycleMachine.Action.OPEN_PORT, false).state)
    }
}
