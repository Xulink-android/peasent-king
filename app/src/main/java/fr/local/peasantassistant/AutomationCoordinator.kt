package fr.local.peasantassistant

/**
 * Conservative automation coordinator. Generates a single suggested next step;
 * never performs taps or swipes. Any uncertain screen or changing observation
 * immediately blocks the sequence.
 */
class AutomationCoordinator {
    enum class Phase { IDLE, WAITING_FOR_STABLE_CONTRACT, WAITING_FOR_STOCK, READY_FOR_REVIEW, BLOCKED }
    data class Snapshot(
        val screen: String,
        val stableContracts: List<ObservationGate.Snapshot>,
        val stocks: List<ShipmentPlanner.Supply> = emptyList(),
        val shipCapacity: Int? = null,
        val availableShips: Int? = null
    )
    data class Decision(val phase: Phase, val explanation: String, val proposal: ShipmentPlanner.Proposal? = null)

    private var previousKey: String? = null
    private var lastDecision = Decision(Phase.IDLE, "En attente d'une observation")

    fun reset() {
        previousKey = null
        lastDecision = Decision(Phase.IDLE, "En attente d'une observation")
    }

    fun evaluate(snapshot: Snapshot): Decision {
        if (snapshot.screen != "contracts") {
            reset()
            return Decision(Phase.BLOCKED, "Écran de contrats non confirmé : aucune action")
        }
        if (snapshot.stableContracts.isEmpty()) {
            reset()
            return Decision(Phase.WAITING_FOR_STABLE_CONTRACT, "Contrats non confirmés : attendre trois lectures identiques")
        }
        val key = snapshot.stableContracts.joinToString("|") {
            "${it.kingdom}:${it.resource}:${it.amount}"
        }
        if (snapshot.stableContracts.any { it.amount <= 0 || it.kingdom.isBlank() || it.resource.isBlank() } ||
            snapshot.stableContracts.distinct().size != snapshot.stableContracts.size
        ) {
            reset()
            return Decision(Phase.BLOCKED, "Contrat ambigu ou quantité invalide")
        }
        if (previousKey != null && previousKey != key) {
            previousKey = key
            lastDecision = Decision(Phase.WAITING_FOR_STABLE_CONTRACT, "Contrat modifié : revalidation obligatoire")
            return lastDecision
        }
        previousKey = key
        if (snapshot.shipCapacity == null || snapshot.availableShips == null || snapshot.stocks.isEmpty()) {
            lastDecision = Decision(Phase.WAITING_FOR_STOCK, "Stocks et capacité non vérifiés : aucune expédition")
            return lastDecision
        }
        val plan = ShipmentPlanner.plan(
            snapshot.stableContracts.map {
                ShipmentPlanner.Demand(it.kingdom, it.resource, it.amount, it.reward)
            },
            snapshot.stocks,
            snapshot.shipCapacity,
            snapshot.availableShips
        )
        val proposal = plan.proposals.firstOrNull()
        lastDecision = if (proposal == null) {
            Decision(Phase.BLOCKED, plan.warnings.joinToString("; ").ifBlank { "Aucune expédition sûre" })
        } else {
            Decision(Phase.READY_FOR_REVIEW,
                "Proposition uniquement : vérifier visuellement le navire, le stock et la destination",
                proposal)
        }
        return lastDecision
    }
}
