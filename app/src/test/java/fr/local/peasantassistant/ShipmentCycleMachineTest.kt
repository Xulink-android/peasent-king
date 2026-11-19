package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentCycleMachineTest {
    private val plan = ShipmentCycleMachine.Shipment("Eastmere", "lumber", 89, 8)

    private fun stable(machine: ShipmentCycleMachine, value: ShipmentCycleMachine.Observation, start: Long) =
        (0..2).map { machine.observe(value, start + it * 100L) }.last()

    private fun offered() = ShipmentCycleMachine.Observation(
        screen = ShipmentCycleMachine.Screen.CONTRACTS, resource = "lumber", quantity = 89,
        kingdom = "Eastmere", stockAvailable = 111, shipCapacity = 200, reward = 8,
        contractVerified = true, contractAccepted = false, acceptButtonVisible = true, unambiguous = true
    )

    @Test fun fullCycleAcceptsOfferThenWaitsForNpcLoadedDepartureArrivalPaymentAndReturn() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, userApproved = true, nowMs = 1000)
        assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT,
            stable(machine, offered(), 1100).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_ACCEPTANCE,
            machine.acknowledge(ShipmentCycleMachine.Action.ACCEPT_CONTRACT, true).state)

        val accepted = offered().copy(contractAccepted = true, acceptButtonVisible = false)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_DEPARTURE,
            stable(machine, accepted, 1500).state)
        val sailing = accepted.copy(screen = ShipmentCycleMachine.Screen.SHIP_SAILING, departureAccepted = true)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_ARRIVAL,
            stable(machine, sailing, 1900).state)

        val arrival = sailing.copy(screen = ShipmentCycleMachine.Screen.UNLOAD_CARGO, arrivalConfirmed = true)
        assertEquals(ShipmentCycleMachine.Action.UNLOAD_CARGO, stable(machine, arrival, 2300).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_UNLOAD,
            machine.acknowledge(ShipmentCycleMachine.Action.UNLOAD_CARGO, true).state)
        val unloaded = arrival.copy(screen = ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT,
            arrivalConfirmed = false, cargoUnloaded = true)
        assertEquals(ShipmentCycleMachine.State.WAITING_FOR_PAYMENT,
            stable(machine, unloaded, 2700).state)

        val paid = unloaded.copy(screen = ShipmentCycleMachine.Screen.RETURN_READY,
            cargoUnloaded = false, paymentSecured = true)
        assertEquals(ShipmentCycleMachine.Action.RETURN_SHIP, stable(machine, paid, 3100).action)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_RETURN,
            machine.acknowledge(ShipmentCycleMachine.Action.RETURN_SHIP, true).state)
        val returned = paid.copy(screen = ShipmentCycleMachine.Screen.SHIPYARD_READY,
            paymentSecured = false, shipReturnedReady = true)
        assertEquals(ShipmentCycleMachine.State.COMPLETED, stable(machine, returned, 3500).state)
        assertNull(machine.observe(returned, 3900).action)
    }

    @Test fun insufficientStockCapacityOrUncertainOfferBlocksBeforeAcceptance() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            stable(machine, offered().copy(stockAvailable = 88), 1100).state)
        machine.start(plan, true, 2000)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            stable(machine, offered().copy(shipCapacity = 88), 2100).state)
        machine.start(plan, true, 3000)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            stable(machine, offered().copy(acceptButtonVisible = false), 3100).state)
        machine.start(plan, true, 4000)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            machine.observe(offered().copy(unambiguous = false), 4100).state)
    }

    @Test fun acceptedContractIsNeverAcceptedAgainAndInterruptionStopsActions() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            stable(machine, offered().copy(contractAccepted = true, acceptButtonVisible = false), 1100).state)

        machine.start(plan, true, 2000)
        assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT,
            stable(machine, offered(), 2100).action)
        machine.stop()
        assertNull(machine.observe(offered(), 2500).action)
    }

    @Test fun failedAcceptanceAndUnverifiedArrivalNeverCauseASecondGesture() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT,
            stable(machine, offered(), 1100).action)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            machine.acknowledge(ShipmentCycleMachine.Action.ACCEPT_CONTRACT, false).state)

        machine.start(plan, true, 2000)
        assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT,
            stable(machine, offered(), 2100).action)
        machine.acknowledge(ShipmentCycleMachine.Action.ACCEPT_CONTRACT, true)
        assertEquals(ShipmentCycleMachine.State.VERIFYING_ACCEPTANCE,
            stable(machine, offered(), 2500).state)
        assertNull(machine.observe(offered(), 2900).action)
        assertEquals(ShipmentCycleMachine.State.BLOCKED,
            machine.observe(offered(), 28_000).state)
    }

    @Test fun wrongDestinationAndWrongSecuredRewardCannotAdvance() {
        val machine = ShipmentCycleMachine()
        machine.start(plan, true, 1000)
        assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT,
            stable(machine, offered(), 1100).action)
        machine.acknowledge(ShipmentCycleMachine.Action.ACCEPT_CONTRACT, true)
        val wrong = offered().copy(contractAccepted = true, acceptButtonVisible = false, kingdom = "Gullhaven Isles")
        assertEquals(ShipmentCycleMachine.State.VERIFYING_ACCEPTANCE,
            stable(machine, wrong, 1500).state)
    }
}
