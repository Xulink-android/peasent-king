package fr.local.peasantassistant

/** Maps screen OCR to a cycle observation without guessing whether the offer was accepted. */
object ShipmentCycleOcrMapper {
    data class Input(
        val screen: ShipmentCycleMachine.Screen,
        val plan: ShipmentCycleMachine.Shipment,
        val contract: ObservationGate.Snapshot? = null,
        val observedResource: String? = null,
        val acceptButtonVisible: Boolean = false,
        val screenEvidence: ShipmentScreenEvidenceParser.Evidence = ShipmentScreenEvidenceParser.Evidence(),
        val stockAvailable: Int? = null,
        val shipCapacity: Int? = null,
        val continuityBound: Boolean = false
    )

    fun map(input: Input): ShipmentCycleMachine.Observation {
        val plan = input.plan
        val contract = input.contract
        val contractStatus = contract?.accepted
        val evidence = input.screenEvidence
        val contractMatches = contract != null && contract.kingdom == plan.kingdom &&
            contract.resource == plan.resource && contract.amount == plan.quantity &&
            contract.reward == plan.expectedReward
        val carried = input.continuityBound
        val resource = input.observedResource ?: contract?.resource ?: plan.resource.takeIf { carried }
        val quantity = evidence.requestedQuantity ?: evidence.remainingCargo ?: contract?.amount
            ?: plan.quantity.takeIf { carried }
        val kingdom = evidence.kingdom ?: contract?.kingdom ?: plan.kingdom.takeIf { carried }
        val reward = evidence.reward ?: evidence.paymentAmount ?: evidence.paymentSecuredAmount
            ?: contract?.reward ?: plan.expectedReward.takeIf { carried }
        val departure = input.screen == ShipmentCycleMachine.Screen.SHIP_SAILING && carried &&
            contractMatches && contract?.accepted == true && input.observedResource == plan.resource &&
            kingdom == plan.kingdom
        val arrived = input.screen == ShipmentCycleMachine.Screen.UNLOAD_CARGO && carried &&
            evidence.kingdom == plan.kingdom && evidence.remainingCargo == plan.quantity
        val unloaded = input.screen == ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT && carried &&
            evidence.kingdom == plan.kingdom
        val paymentSecured = input.screen == ShipmentCycleMachine.Screen.RETURN_READY && carried &&
            evidence.kingdom == plan.kingdom && evidence.paymentSecuredAmount == plan.expectedReward
        val returnedReady = input.screen == ShipmentCycleMachine.Screen.SHIPYARD_READY && carried
        val positiveEvidence = when (input.screen) {
            ShipmentCycleMachine.Screen.CONTRACTS -> contractMatches && contractStatus != null &&
                input.observedResource == plan.resource && (contractStatus == true ||
                (input.acceptButtonVisible && input.stockAvailable != null &&
                    input.stockAvailable >= plan.quantity && input.shipCapacity != null &&
                    input.shipCapacity >= plan.quantity))
            ShipmentCycleMachine.Screen.SHIP_SAILING -> departure
            ShipmentCycleMachine.Screen.UNLOAD_CARGO -> arrived
            ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT -> unloaded
            ShipmentCycleMachine.Screen.RETURN_READY -> paymentSecured
            ShipmentCycleMachine.Screen.SHIPYARD_READY -> returnedReady
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
            contractVerified = contractMatches,
            contractAccepted = contract?.accepted == true,
            acceptButtonVisible = input.acceptButtonVisible,
            departureAccepted = departure,
            arrivalConfirmed = arrived,
            cargoUnloaded = unloaded,
            paymentSecured = paymentSecured,
            shipReturnedReady = returnedReady,
            unambiguous = positiveEvidence && evidence.warnings.isEmpty()
        )
    }
}
