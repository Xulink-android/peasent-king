package fr.local.peasantassistant

/** Conservative stock planner, for verified values only. No game interaction. */
object StockPlanner {
    data class Stock(
        val resource: String,
        val capacity: Int,
        val inPort: Int,
        val inbound: Int,
        val inRegion: Int,
        val regionalReserve: Int = 0,
        val plannedShipLoad: Int = 0
    )
    data class Decision(val resource: String, val transfer: Int, val reason: String)

    fun plan(input: Stock): Decision {
        if (input.capacity <= 0 || input.inPort < 0 || input.inbound < 0 ||
            input.inRegion < 0 || input.regionalReserve < 0 || input.plannedShipLoad < 0 ||
            input.inPort.toLong() + input.inbound.toLong() > input.capacity ||
            input.plannedShipLoad > input.inPort) {
            return Decision(input.resource, 0, "Données incohérentes : aucune action")
        }
        // Planned ship cargo remains in the warehouse until departure.
        val free = (input.capacity.toLong() - input.inPort - input.inbound)
            .coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val available = (input.inRegion - input.regionalReserve).coerceAtLeast(0)
        val transfer = minOf(free, available)
        return Decision(input.resource, transfer,
            if (transfer == 0) "Pas de transfert nécessaire ou stock régional insuffisant"
            else "Proposition de transfert à confirmer dans le jeu")
    }
}
