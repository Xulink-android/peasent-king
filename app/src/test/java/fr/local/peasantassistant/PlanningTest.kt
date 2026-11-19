package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class PlanningTest {
    @Test fun detectsTwoContractAmountsWithoutGuessingResources() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("CONTRAT", 8, 37),
            ContractParser.Line("ENVOYER 110", 11, 39),
            ContractParser.Line("Eastmere", 32, 40),
            ContractParser.Line("ENVOYER 104", 74, 36),
            ContractParser.Line("Gullhaven Isles", 76, 47)
        ))
        assertEquals(listOf(110, 104), result.contracts.map { it.amount })
        assertTrue(result.contracts.all { it.resource == null })
    }
    @Test fun associatesVisibleRewardOnlyWithItsContractColumn() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("ENVOYER 89", 12, 39),
            ContractParser.Line("Eastmere", 13, 41),
            ContractParser.Line("GAGNER 8", 12, 42),
            ContractParser.Line("ENVOYER 76", 78, 39),
            ContractParser.Line("Gullhaven Isles", 78, 41),
            ContractParser.Line("GAGNER 6", 79, 42)
        ))
        assertEquals(listOf(8, 6), result.contracts.map { it.reward })
    }
    @Test fun fillsPortAfterLoadingWithoutOverfilling() {
        val decision = StockPlanner.plan(StockPlanner.Stock("coal", 200, 70, 20, 160, 0, 50))
        assertEquals(110, decision.transfer)
    }
    @Test fun respectsRegionalReserve() {
        val decision = StockPlanner.plan(StockPlanner.Stock("grain", 200, 70, 20, 160, 40, 50))
        assertEquals(110, decision.transfer)
    }
    @Test fun rejectsInconsistentInput() {
        val decision = StockPlanner.plan(StockPlanner.Stock("coal", 100, 95, 10, 200))
        assertEquals(0, decision.transfer)
    }
    @Test fun gateNeedsThreeIdenticalCompleteObservations() {
        val gate = ObservationGate()
        val complete = ContractParser.Result(
            listOf(ContractParser.Contract("Eastmere", 110, 39, "grain")),
            emptyList(), "Contrats"
        )
        assertTrue(gate.observe(complete).stable.isEmpty())
        assertTrue(gate.observe(complete).stable.isEmpty())
        assertEquals(1, gate.observe(complete).stable.size)
        val changed = complete.copy(contracts = listOf(
            ContractParser.Contract("Eastmere", 111, 39, "grain")
        ))
        assertTrue(gate.observe(changed).stable.isEmpty())
        assertTrue(gate.observe(complete).stable.isEmpty())
    }
    @Test fun gateResetsWhenResourceUnknown() {
        val gate = ObservationGate()
        val complete = ContractParser.Result(
            listOf(ContractParser.Contract("Eastmere", 110, 39, "grain")),
            emptyList(), "Contrats"
        )
        gate.observe(complete)
        gate.observe(complete)
        gate.observe(complete.copy(contracts = listOf(
            ContractParser.Contract("Eastmere", 110, 39, null)
        )))
        assertTrue(gate.observe(complete).stable.isEmpty())
    }
    @Test fun reservedCargoCannotFreePortCapacity() {
        val decision = StockPlanner.plan(StockPlanner.Stock("grain", 100, 90, 0, 200, 0, 80))
        assertEquals(10, decision.transfer)
    }
    @Test fun shipmentPlannerDoesNotDoubleSpendStock() {
        val plan = ShipmentPlanner.plan(
            listOf(
                ShipmentPlanner.Demand("Eastmere", "grain", 110),
                ShipmentPlanner.Demand("Other", "grain", 104)
            ),
            listOf(ShipmentPlanner.Supply("grain", 150)),
            shipCapacity = 200, maxShips = 2
        )
        assertEquals(1, plan.proposals.size)
        assertEquals(104, plan.proposals.single().quantity)
    }
    @Test fun shipmentPlannerRejectsUnknownSupplyAndOvercapacity() {
        val plan = ShipmentPlanner.plan(
            listOf(ShipmentPlanner.Demand("Eastmere", "grain", 110)),
            emptyList(), shipCapacity = 100, maxShips = 1
        )
        assertTrue(plan.proposals.isEmpty())
    }
    @Test fun shipmentPlannerRespectsReservations() {
        val plan = ShipmentPlanner.plan(
            listOf(ShipmentPlanner.Demand("Eastmere", "grain", 110)),
            listOf(ShipmentPlanner.Supply("grain", 120, 30)),
            shipCapacity = 150, maxShips = 1
        )
        assertTrue(plan.proposals.isEmpty())
    }
    @Test fun automationBlocksUnknownScreensAndMissingStocks() {
        val coordinator = AutomationCoordinator()
        val contract = ObservationGate.Snapshot("Eastmere", "grain", 95)
        assertEquals(AutomationCoordinator.Phase.BLOCKED,
            coordinator.evaluate(AutomationCoordinator.Snapshot("port", listOf(contract))).phase)
        assertEquals(AutomationCoordinator.Phase.WAITING_FOR_STOCK,
            coordinator.evaluate(AutomationCoordinator.Snapshot("contracts", listOf(contract))).phase)
    }
    @Test fun automationProposesButNeverExecutes() {
        val coordinator = AutomationCoordinator()
        val contract = ObservationGate.Snapshot("Eastmere", "grain", 95)
        val stocks = listOf(ShipmentPlanner.Supply("grain", 100, 5))
        val decision = coordinator.evaluate(AutomationCoordinator.Snapshot(
            "contracts", listOf(contract), stocks, 120, 1
        ))
        assertEquals(AutomationCoordinator.Phase.READY_FOR_REVIEW, decision.phase)
        assertEquals(95, decision.proposal?.quantity)
    }
    @Test fun automationRequiresRevalidationWhenContractChanges() {
        val coordinator = AutomationCoordinator()
        val stocks = listOf(ShipmentPlanner.Supply("grain", 200))
        fun snapshot(amount: Int) = AutomationCoordinator.Snapshot(
            "contracts", listOf(ObservationGate.Snapshot("Eastmere", "grain", amount)),
            stocks, 200, 1
        )
        assertEquals(AutomationCoordinator.Phase.READY_FOR_REVIEW, coordinator.evaluate(snapshot(95)).phase)
        assertEquals(AutomationCoordinator.Phase.WAITING_FOR_STABLE_CONTRACT,
            coordinator.evaluate(snapshot(96)).phase)
    }
    @Test fun adjacentContractColumnsDoNotBorrowEachOthersKingdom() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("CONTRAT", 8, 35),
            ContractParser.Line("ENVOYER 95", 10, 39),
            ContractParser.Line("Eastmere", 32, 41),
            ContractParser.Line("ENVOYER 111", 70, 39),
            ContractParser.Line("Gullhaven Isles", 75, 41)
        ))
        assertEquals("Eastmere", result.contracts[0].kingdom)
        assertEquals("Gullhaven Isles", result.contracts[1].kingdom)
    }
    @Test fun sendMustBeAnExactOcrLine() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("PROMO ENVOYER 95", 10, 39),
            ContractParser.Line("ENVOYER 95 RESTANT", 10, 41),
            ContractParser.Line("ENVOYER 95", 10, 43)
        ))
        assertEquals(1, result.contracts.size)
        assertEquals(95, result.contracts.single().amount)
    }

    @Test fun resourceMatchingDoesNotUseWordFragments() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("CONTRAT", 8, 35),
            ContractParser.Line("ENVOYER 95", 10, 39),
            ContractParser.Line("Eastmere", 12, 41),
            ContractParser.Line("charcoal", 12, 38)
        ))
        assertNull(result.contracts.single().resource)
    }

    @Test fun wholeWordResourceIsRecognized() {
        val result = ContractParser.parse(listOf(
            ContractParser.Line("CONTRAT", 8, 35),
            ContractParser.Line("ENVOYER 95", 10, 39),
            ContractParser.Line("Eastmere", 12, 41),
            ContractParser.Line("CHARBON", 12, 38)
        ))
        assertEquals("coal", result.contracts.single().resource)
    }

    @Test fun emptyTextNeverCreatesContracts() {
        assertTrue(ContractParser.parse(emptyList()).contracts.isEmpty())
    }
}
