package fr.local.peasantassistant

/** Connects lifecycle decisions to targets resolved from the current OCR frame. */
class ShipmentCycleGestureBridge(private val machine: ShipmentCycleMachine) {
    data class Frame(
        val screen: GameScreenClassifier.Screen,
        val boxes: List<ObservedGameTargetResolver.Box>,
        val width: Int,
        val height: Int,
        val evidence: ObservedGameTargetResolver.ShipmentEvidence,
        val stockLabel: ObservedGameTargetResolver.Box? = null,
        val available: Int? = null
    )
    data class Result(val decision: ShipmentCycleMachine.Decision, val gesture: ObservedGameTargetResolver.Gesture? = null)

    fun start(plan: ShipmentCycleMachine.Shipment, userApproved: Boolean, nowMs: Long) =
        machine.start(plan, userApproved, nowMs)

    fun observe(observation: ShipmentCycleMachine.Observation, frame: Frame, nowMs: Long): Result {
        val decision = machine.observe(observation, nowMs)
        val action = decision.action ?: return Result(decision)
        val gesture = resolve(action, frame)
        if (gesture == null) {
            return Result(machine.acknowledge(action, androidCompleted = false))
        }
        return Result(decision, gesture)
    }

    fun acknowledge(action: ShipmentCycleMachine.Action, androidCompleted: Boolean) =
        machine.acknowledge(action, androidCompleted)

    fun dispatch(
        result: Result,
        dispatcher: AccessibilityGestureDispatcher,
        userEnabled: Boolean,
        gameForeground: Boolean,
        observedAtMs: Long,
        width: Int,
        height: Int,
        callback: (ShipmentCycleMachine.Decision) -> Unit
    ): Boolean {
        val action = result.decision.action ?: return false
        val gesture = result.gesture ?: run {
            callback(machine.acknowledge(action, false))
            return false
        }
        var callbackDelivered = false
        val dispatched = dispatcher.dispatch(
            target = gesture,
            userEnabled = userEnabled,
            gameForeground = gameForeground,
            screenConfirmed = true,
            targetConfirmed = true,
            observedAtMs = observedAtMs,
            width = width,
            height = height
        ) { completed ->
            callbackDelivered = true
            callback(machine.acknowledge(action, completed))
        }
        if (!dispatched && !callbackDelivered) callback(machine.acknowledge(action, false))
        return dispatched
    }

    fun stop() = machine.stop()

    private fun resolve(action: ShipmentCycleMachine.Action, frame: Frame): ObservedGameTargetResolver.Gesture? {
        if (frame.width <= 0 || frame.height <= 0 || !frame.evidence.unambiguous) return null
        return when (action) {
            ShipmentCycleMachine.Action.ACCEPT_CONTRACT ->
                ObservedGameTargetResolver.acceptContractTap(frame.screen, frame.boxes, frame.width,
                    frame.height, frame.evidence)
            ShipmentCycleMachine.Action.UNLOAD_CARGO ->
                ObservedGameTargetResolver.unloadCargoDrag(frame.screen, frame.boxes, frame.width,
                    frame.height, frame.evidence)
            ShipmentCycleMachine.Action.RETURN_SHIP ->
                ObservedGameTargetResolver.returnShipTap(frame.screen, frame.boxes, frame.width,
                    frame.height, frame.evidence)
        }
    }
}
