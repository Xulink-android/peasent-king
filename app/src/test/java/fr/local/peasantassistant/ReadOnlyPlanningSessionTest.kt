package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyPlanningSessionTest {
    private val contract = listOf(ObservationGate.Snapshot("Eastmere", "grain", 95))
    private val port = VerifiedStockGate.Verified(
        listOf(ShipmentPlanner.Supply("grain", 120, 10)), 100, 1
    )

    @Test fun requiresBothVerifiedScreensAndCurrentContract() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        assertNull(session.proposal(1001, contract))
        session.observePort(port, 2000)
        assertNull(session.proposal(2001, emptyList()))
        assertEquals(95, session.proposal(2001, contract)?.quantity)
    }

    @Test fun stalePortIsRejected() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        assertNull(session.proposal(32001, contract))
    }

    @Test fun changedContractInvalidatesOldStock() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        val changed = listOf(ObservationGate.Snapshot("Eastmere", "grain", 96))
        session.observeContracts(changed, 3000)
        assertNull(session.proposal(3001, changed))
    }

    @Test fun neverPlansWithInsufficientStock() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port.copy(supplies = listOf(ShipmentPlanner.Supply("grain", 100, 10))), 2000)
        assertNull(session.proposal(2001, contract))
    }

    @Test fun resetDiscardsEverything() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        session.reset()
        assertNull(session.proposal(2001, contract))
    }
}
