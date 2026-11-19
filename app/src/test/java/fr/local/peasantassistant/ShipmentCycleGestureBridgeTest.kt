package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ShipmentCycleGestureBridgeTest {
    private val plan = ShipmentCycleMachine.Shipment("Eastmere", "lumber", 89, 8)
    private val evidence = ObservedGameTargetResolver.ShipmentEvidence(
        "Eastmere", "lumber", 89, 111, 200, "lumber", true, 8
    )

    private fun frame(portBoxes: List<ObservedGameTargetResolver.Box>) = ShipmentCycleGestureBridge.Frame(
        screen = GameScreenClassifier.Screen.WORLD_MAP,
        boxes = portBoxes,
        width = 1080,
        height = 2340,
        evidence = evidence
    )

    @Test fun firstCycleActionResolvesOnlyTheOfferAcceptButton() {
        val bridge = ShipmentCycleGestureBridge(ShipmentCycleMachine())
        bridge.run {
            start(plan, true, 1000)
            val contract = ShipmentCycleMachine.Observation(
                screen = ShipmentCycleMachine.Screen.CONTRACTS,
                resource = "lumber", quantity = 89, kingdom = "Eastmere", stockAvailable = 111,
                shipCapacity = 200, reward = 8, contractVerified = true, acceptButtonVisible = true, unambiguous = true
            )
            val boxes = frame(listOf(
                ObservedGameTargetResolver.Box("Accepter", 100, 760, 300, 820),
                ObservedGameTargetResolver.Box("ENVOYER 89", 100, 890, 300, 940),
                ObservedGameTargetResolver.Box("GAGNER 8", 100, 955, 300, 1000)
            ))
            observe(contract, boxes, 1100)
            observe(contract, boxes, 1200)
            val resolved = observe(contract, boxes, 1300)
            assertEquals(ShipmentCycleMachine.Action.ACCEPT_CONTRACT, resolved.decision.action)
            assertNotNull(resolved.gesture)
            assertEquals(200, resolved.gesture!!.start.x)
        }
    }

    @Test fun unresolvedTargetFailsClosedInsteadOfAcknowledgingAction() {
        val bridge = ShipmentCycleGestureBridge(ShipmentCycleMachine())
        bridge.run {
            start(plan, true, 1000)
            val contract = ShipmentCycleMachine.Observation(
                screen = ShipmentCycleMachine.Screen.CONTRACTS,
                resource = "lumber", quantity = 89, kingdom = "Eastmere", stockAvailable = 111,
                shipCapacity = 200, reward = 8, contractVerified = true, acceptButtonVisible = true, unambiguous = true
            )
            val badFrame = frame(listOf(ObservedGameTargetResolver.Box("PORT", 800, 2070, 980, 2140)))
            observe(contract, badFrame, 1100)
            observe(contract, badFrame, 1200)
            val failed = observe(contract, badFrame, 1300)
            assertEquals(ShipmentCycleMachine.State.BLOCKED, failed.decision.state)
            assertNull(failed.gesture)
        }
    }

    private inline fun ShipmentCycleGestureBridge.run(block: ShipmentCycleGestureBridge.() -> Unit) = block()
}
