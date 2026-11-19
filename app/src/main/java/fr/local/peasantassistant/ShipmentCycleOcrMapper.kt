package fr.local.peasantassistant

/** Converts current OCR facts plus a still-valid accepted shipment binding to a cycle observation. */
object ShipmentCycleOcrMapper {
    data class Input(
        val screen: ShipmentCycleMachine.Screen,
        val plan: ShipmentCycleMachine.Shipment,
        val contract: ObservationGate.Snapshot? = null,
        val observedResource: String? = null,
        val screenEvidence: ShipmentScreenEvidenceParser.Evidence = ShipmentScreenEvidenceParser.Evidence(),
        val stockAvailable: Int? = null,
        val shipCapacity: Int? = null,
        val continuityBound: Boolean = false,
        val paymentCollected: Boolean = false
    )

    fun map(input: Input): ShipmentCycleMachine.Observation {
        val plan = input.plan
        val contract = input.contract
        val evidence = input.screenEvidence
        val contractMatches = contract != null && contract.accepted == true &&
            contract.kingdom == plan.kingdom && contract.resource == plan.resource &&
            contract.amount == plan.quantity && contract.reward == plan.expectedReward
        val carried = input.continuityBound
        val resource = input.observedResource ?: contract?.resource
            ?: plan.resource.takeIf { carried }
        val quantity = evidence.requestedQuantity ?: evidence.remainingCargo ?: contract?.amount
            ?: plan.quantity.takeIf { carried }
        val kingdom = evidence.kingdom ?: contract?.kingdom ?: plan.kingdom.takeIf { carried }
        val reward = evidence.reward ?: evidence.paymentAmount ?: contract?.reward
            ?: plan.expectedReward.takeIf { carried }
        val progressMatches = evidence.requestedQuantity == plan.quantity
        val ready = input.screen == ShipmentCycleMachine.Screen.SHIP_READY &&
            progressMatches && evidence.loadedQuantity == plan.quantity
        val departure = input.screen == ShipmentCycleMachine.Screen.SHIP_SAILING &&
            contractMatches && input.observedResource == plan.resource
        val arrived = input.screen == ShipmentCycleMachine.Screen.UNLOAD_CARGO && carried &&
            evidence.kingdom == plan.kingdom && evidence.remainingCargo == plan.quantity
        val unloaded = input.screen == ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT && carried &&
            evidence.kingdom == plan.kingdom
        val correctPayment = input.screen == ShipmentCycleMachine.Screen.COLLECT_PAYMENT && carried &&
            evidence.kingdom == plan.kingdom && evidence.paymentAmount == plan.expectedReward
        val paymentProof = input.paymentCollected && input.screen in setOf(
            ShipmentCycleMachine.Screen.WORLD_MAP,
            ShipmentCycleMachine.Screen.SHIPYARD,
            ShipmentCycleMachine.Screen.SHIP_READY
        )
        val positiveEvidence = when (input.screen) {
            ShipmentCycleMachine.Screen.CONTRACTS -> contractMatches &&
                input.observedResource == plan.resource && input.stockAvailable != null &&
                input.stockAvailable >= plan.quantity && input.shipCapacity != null &&
                input.shipCapacity >= plan.quantity
            ShipmentCycleMachine.Screen.SHIP_LOADING -> carried && progressMatches &&
                input.observedResource == plan.resource && input.stockAvailable != null &&
                input.shipCapacity != null && input.shipCapacity >= plan.quantity
            ShipmentCycleMachine.Screen.SHIP_READY -> carried && ready &&
                input.observedResource == plan.resource
            ShipmentCycleMachine.Screen.SHIP_SAILING -> carried && departure &&
                kingdom == plan.kingdom
            ShipmentCycleMachine.Screen.UNLOAD_CARGO -> arrived
            ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT -> unloaded
            ShipmentCycleMachine.Screen.COLLECT_PAYMENT -> correctPayment
            ShipmentCycleMachine.Screen.WORLD_MAP -> paymentProof || (carried && departure)
            else -> false
        }
        return ShipmentCycleMachine.Observation(
            screen = input.screen,
            resource = resource,
            quantity = quantity,
            kingdom = kingdom,
            stockAvailable = input.stockAvailable,
            shipCapacity = input.shipCapacity,
            reward = reward,
            contractVerified = contractMatches || carried,
            contractAccepted = contract?.accepted == true || carried,
            cargoLoaded = ready,
            loadedQuantity = evidence.loadedQuantity,
            departureAccepted = departure,
            arrivalConfirmed = arrived,
            cargoUnloaded = unloaded,
            paymentCollected = paymentProof,
            unambiguous = positiveEvidence && evidence.warnings.isEmpty()
        )
    }
}
