package fr.local.peasantassistant

/**
 * Carries independently verified observations between the contracts and port screens.
 * A proposal is diagnostic only. Both observations expire and a changed contract
 * invalidates the previously verified port snapshot.
 */
class ReadOnlyPlanningSession(private val maxAgeMs: Long = 30_000L) {
    private data class Timed<T>(val value: T, val at: Long)
    private var contracts: Timed<List<ObservationGate.Snapshot>>? = null
    private var port: Timed<VerifiedStockGate.Verified>? = null

    fun reset() {
        contracts = null
        port = null
    }

    fun observeContracts(
        stable: List<ObservationGate.Snapshot>,
        nowMs: Long
    ): String {
        if (stable.isEmpty()) return "Contrats non confirmés"
        val sorted = stable.sortedWith(compareBy({ it.kingdom }, { it.resource }, { it.amount }))
        if (contracts?.value != null && contracts?.value != sorted) {
            port = null
        }
        contracts = Timed(sorted, nowMs)
        return "Contrats confirmés : ${sorted.size}"
    }

    fun observePort(verified: VerifiedStockGate.Verified?, nowMs: Long): String {
        if (verified == null) return "Port non confirmé"
        port = Timed(verified, nowMs)
        return "Port confirmé : ${verified.supplies.size} ressources"
    }

    fun proposal(nowMs: Long, contractScreenConfirmed: Boolean): ShipmentPlanner.Proposal? {
        if (!contractScreenConfirmed) return null
        val c = contracts ?: return null
        val p = port ?: return null
        if (nowMs < c.at || nowMs < p.at ||
            nowMs - c.at > maxAgeMs || nowMs - p.at > maxAgeMs) return null
        return ShipmentPlanner.plan(
            c.value.map { ShipmentPlanner.Demand(it.kingdom, it.resource, it.amount) },
            p.value.supplies, p.value.capacity, p.value.ships
        ).proposals.firstOrNull()
    }
}
