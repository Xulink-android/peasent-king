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
    @Test fun fillsPortAfterLoadingWithoutOverfilling() {
        val decision = StockPlanner.plan(StockPlanner.Stock("coal", 200, 70, 20, 160, 0, 50))
        assertEquals(160, decision.transfer)
    }
    @Test fun respectsRegionalReserve() {
        val decision = StockPlanner.plan(StockPlanner.Stock("grain", 200, 70, 20, 160, 40, 50))
        assertEquals(120, decision.transfer)
    }
    @Test fun rejectsInconsistentInput() {
        val decision = StockPlanner.plan(StockPlanner.Stock("coal", 100, 95, 10, 200))
        assertEquals(0, decision.transfer)
    }
    @Test fun emptyTextNeverCreatesContracts() {
        assertTrue(ContractParser.parse(emptyList()).contracts.isEmpty())
    }
}
