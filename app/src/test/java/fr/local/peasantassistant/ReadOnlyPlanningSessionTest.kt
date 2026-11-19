package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyPlanningSessionTest {
    private val contract = listOf(ObservationGate.Snapshot("Eastmere", "grain", 95, accepted = true))
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
        val changed = listOf(ObservationGate.Snapshot("Eastmere", "grain", 96, accepted = true))
        session.observeContracts(changed, 3000)
        assertNull(session.proposal(3001, changed))
    }

    @Test fun neverPlansWithInsufficientStock() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port.copy(supplies = listOf(ShipmentPlanner.Supply("grain", 100, 10))), 2000)
        assertNull(session.proposal(2001, contract))
    }

    @Test fun neverProposesAnUnacceptedOrUnknownContract() {
        val session = ReadOnlyPlanningSession()
        val pending = listOf(ObservationGate.Snapshot("Eastmere", "grain", 95, accepted = false))
        session.observeContracts(pending, 1000)
        session.observePort(port, 1100)
        assertNull(session.proposal(1200, pending))

        val unknown = listOf(ObservationGate.Snapshot("Eastmere", "grain", 95))
        session.observeContracts(unknown, 1300)
        session.observePort(port, 1400)
        assertNull(session.proposal(1500, unknown))
    }

    @Test fun refusesToChooseBetweenMultipleSignedContractsWithoutShipBinding() {
        val session = ReadOnlyPlanningSession()
        val signed = listOf(
            ObservationGate.Snapshot("Eastmere", "grain", 95, accepted = true),
            ObservationGate.Snapshot("Gullhaven Isles", "lumber", 76, accepted = true)
        )
        session.observeContracts(signed, 1000)
        session.observePort(port.copy(supplies = listOf(
            ShipmentPlanner.Supply("grain", 120), ShipmentPlanner.Supply("lumber", 120)
        )), 1100)
        assertNull(session.proposal(1200, signed))
    }

    @Test fun unverifiedPortInvalidatesPreviouslyConfirmedPort() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        assertNotNull(session.proposal(2001, contract))
        session.observePort(null, 2002)
        assertNull(session.proposal(2003, contract))
    }

    @Test fun incompleteContractObservationInvalidatesBothScreens() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        session.observeContracts(emptyList(), 2001)
        session.observeContracts(contract, 2002)
        assertNull(session.proposal(2003, contract))
    }

    @Test fun resetDiscardsEverything() {
        val session = ReadOnlyPlanningSession()
        session.observeContracts(contract, 1000)
        session.observePort(port, 2000)
        session.reset()
        assertNull(session.proposal(2001, contract))
    }
}
