package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentExecutionMachineTest {
    private val plan = ShipmentExecutionMachine.Shipment("Eastmere", "grain", 95)
    private fun contracts() = ShipmentExecutionMachine.Observation(
        ShipmentExecutionMachine.Screen.CONTRACTS, unambiguous = true
    )
    private fun port() = ShipmentExecutionMachine.Observation(
        ShipmentExecutionMachine.Screen.PORT, unambiguous = true
    )
    @Test fun noActionsWithoutExplicitApproval() {
        val machine = ShipmentExecutionMachine()
        assertEquals(ShipmentExecutionMachine.State.BLOCKED,
            machine.start(plan, false, 1000).state)
        assertNull(machine.observe(contracts(), 1100).next)
    }
    @Test fun requiresThreeObservationsBeforeFirstAction() {
        val machine = ShipmentExecutionMachine()
        machine.start(plan, true, 1000)
        assertNull(machine.observe(contracts(), 1100).next)
        assertNull(machine.observe(contracts(), 1200).next)
        assertEquals(ShipmentExecutionMachine.Step.OPEN_PORT,
            machine.observe(contracts(), 1300).next)
        assertNull(machine.observe(contracts(), 1400).next)
    }
    @Test fun eachActionNeedsAndroidAckAndNewScreenProof() {
        val machine = ShipmentExecutionMachine()
        machine.start(plan, true, 1000)
        repeat(3) { machine.observe(contracts(), 1100L + it * 100L) }
        assertEquals(ShipmentExecutionMachine.State.WAITING_FOR_LOAD,
            machine.acknowledge(ShipmentExecutionMachine.Step.OPEN_PORT, true).state)
        assertNull(machine.observe(port(), 1400).next)
        assertNull(machine.observe(port(), 1500).next)
        assertEquals(ShipmentExecutionMachine.Step.CHOOSE_RESOURCE,
            machine.observe(port(), 1600).next)
    }
    @Test fun unknownOrExpiredScreensBlockForever() {
        val machine = ShipmentExecutionMachine()
        machine.start(plan, true, 1000)
        assertEquals(ShipmentExecutionMachine.State.BLOCKED,
            machine.observe(ShipmentExecutionMachine.Observation(
                ShipmentExecutionMachine.Screen.UNKNOWN), 1100).state)
        assertNull(machine.observe(contracts(), 1200).next)
        machine.start(plan, true, 2000)
        assertEquals(ShipmentExecutionMachine.State.BLOCKED,
            machine.observe(contracts(), 33001).state)
    }
    @Test fun sendingCannotRepeatEvenWhenOnlyAndroidAckWasReceived() {
        val machine = ShipmentExecutionMachine()
        machine.start(plan, true, 1000)
        fun arrive(screen: ShipmentExecutionMachine.Screen, at: Long,
            resource: String? = null, quantity: Int? = null, kingdom: String? = null
        ): ShipmentExecutionMachine.Decision {
            val observation = ShipmentExecutionMachine.Observation(
                screen, resource, quantity, kingdom, unambiguous = true)
            var decision = machine.observe(observation, at)
            decision = machine.observe(observation, at + 100)
            decision = machine.observe(observation, at + 200)
            return decision
        }
        assertEquals(ShipmentExecutionMachine.Step.OPEN_PORT, arrive(
            ShipmentExecutionMachine.Screen.CONTRACTS, 1100).next)
        machine.acknowledge(ShipmentExecutionMachine.Step.OPEN_PORT, true)
        assertEquals(ShipmentExecutionMachine.Step.CHOOSE_RESOURCE, arrive(
            ShipmentExecutionMachine.Screen.PORT, 1400).next)
        machine.acknowledge(ShipmentExecutionMachine.Step.CHOOSE_RESOURCE, true)
        assertEquals(ShipmentExecutionMachine.Step.SET_QUANTITY, arrive(
            ShipmentExecutionMachine.Screen.SHIP_LOAD, 1700, "grain").next)
        machine.acknowledge(ShipmentExecutionMachine.Step.SET_QUANTITY, true)
        assertEquals(ShipmentExecutionMachine.Step.SELECT_DESTINATION, arrive(
            ShipmentExecutionMachine.Screen.SHIP_LOAD, 2000, "grain", 95).next)
        machine.acknowledge(ShipmentExecutionMachine.Step.SELECT_DESTINATION, true)
        assertEquals(ShipmentExecutionMachine.Step.CONFIRM_SEND, arrive(
            ShipmentExecutionMachine.Screen.SHIP_CONFIRMATION, 2300, "grain", 95, "Eastmere").next)
        assertEquals(ShipmentExecutionMachine.State.VERIFYING_RESULT,
            machine.acknowledge(ShipmentExecutionMachine.Step.CONFIRM_SEND, true).state)
        val pending = machine.observe(port(), 2600)
        assertNull(pending.next)
        assertEquals(ShipmentExecutionMachine.State.VERIFYING_RESULT, pending.state)
        assertEquals(ShipmentExecutionMachine.State.VERIFYING_RESULT,
            machine.observe(port().copy(sendCompleted = true), 2700).state)
        assertEquals(ShipmentExecutionMachine.State.COMPLETED,
            machine.observe(port().copy(sendCompleted = true), 2800).state)
        assertNull(machine.observe(port().copy(sendCompleted = true), 2900).next)
    }
}
