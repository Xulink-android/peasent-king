package fr.local.peasantassistant

/**
 * Pure planning logic. No touches, game hooks or permissions.
 * All quantities must be verified independently before use.
 */
object ShipmentPlanner {
    data class Demand(val kingdom: String, val resource: String, val quantity: Int)
    data class Supply(val resource: String, val availableAtPort: Int, val reserved: Int = 0)
    data class Proposal(val kingdom: String, val resource: String, val quantity: Int, val note: String)
    data class Plan(val proposals: List<Proposal>, val warnings: List<String>)

    fun plan(
        demands: List<Demand>,
        supplies: List<Supply>,
        shipCapacity: Int,
        maxShips: Int
    ): Plan {
        val warnings = mutableListOf<String>()
        if (shipCapacity <= 0 || maxShips <= 0 ||
            demands.any { it.quantity <= 0 || it.kingdom.isBlank() || it.resource.isBlank() } ||
            supplies.any { it.availableAtPort < 0 || it.reserved < 0 ||
                it.reserved > it.availableAtPort || it.resource.isBlank() } ||
            supplies.map { it.resource }.distinct().size != supplies.size ||
            demands.map { it.kingdom to it.resource }.distinct().size != demands.size
        ) return Plan(emptyList(), listOf("Données invalides ou ambiguës : aucun chargement proposé"))

        val remaining = supplies.associate {
            it.resource to (it.availableAtPort - it.reserved)
        }.toMutableMap()
        val proposals = mutableListOf<Proposal>()
        // Smaller fully satisfiable contracts first. No partial contract assumed.
        for (demand in demands.sortedWith(compareBy({ it.quantity }, { it.kingdom }))) {
            if (proposals.size >= maxShips) {
                warnings += "Navires disponibles insuffisants"
                break
            }
            if (demand.quantity > shipCapacity) {
                warnings += "${demand.kingdom} : contrat supérieur à la capacité du navire"
                continue
            }
            val stock = remaining[demand.resource]
            if (stock == null || stock < demand.quantity) {
                warnings += "${demand.kingdom} : stock vérifié insuffisant ou inconnu"
                continue
            }
            proposals += Proposal(demand.kingdom, demand.resource, demand.quantity,
                "Simulation : vérifier le navire et la destination avant tout envoi")
            remaining[demand.resource] = stock - demand.quantity
        }
        return Plan(proposals, warnings)
    }
}
