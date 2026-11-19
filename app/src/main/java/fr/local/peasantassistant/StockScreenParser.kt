package fr.local.peasantassistant

/**
 * Read-only extraction of explicit, labelled stock quantities.
 * Rejects unlabeled numbers, ambiguous duplicate rows, and missing reservations.
 * Never infers port stock from a contract quantity.
 */
object StockScreenParser {
    data class Result(
        val supplies: List<ShipmentPlanner.Supply>,
        val warnings: List<String>,
        val shipCapacity: Int?,
        val availableShips: Int?
    )

    private val stockRow = Regex(
        """^(?:PORT|STOCK|ENTREPOT|ENTREPÔT)\s*[:\-]?\s*(CHARBON|COAL|BLE|BLÉ|GRAIN|WHEAT|BOIS|WOOD|LUMBER|SOIE|SILK|CACAO|COCOA|POTATOES|POMMES DE TERRE)\s*[:\-]?\s*([0-9][0-9 ]*)\s*(?:[/|]\s*RESERVE(?:S)?\s*[:\-]?\s*([0-9][0-9 ]*))?$""",
        RegexOption.IGNORE_CASE
    )
    private val capacityRow = Regex(
        """^(?:CAPACITE|CAPACITÉ|SHIP CAPACITY)\s*[:\-]?\s*([0-9][0-9 ]*)$""",
        RegexOption.IGNORE_CASE
    )
    private val shipsRow = Regex(
        """^(?:NAVIRES DISPONIBLES|AVAILABLE SHIPS)\s*[:\-]?\s*([0-9][0-9 ]*)$""",
        RegexOption.IGNORE_CASE
    )
    private val names = mapOf(
        "CHARBON" to "coal", "COAL" to "coal",
        "BLE" to "grain", "BLÉ" to "grain", "GRAIN" to "grain", "WHEAT" to "grain",
        "BOIS" to "lumber", "WOOD" to "lumber", "LUMBER" to "lumber",
        "SOIE" to "silk", "SILK" to "silk",
        "CACAO" to "cocoa", "COCOA" to "cocoa",
        "POTATOES" to "potatoes", "POMMES DE TERRE" to "potatoes"
    )

    fun parse(lines: List<String>): Result {
        val warnings = mutableListOf<String>()
        val supplies = mutableListOf<ShipmentPlanner.Supply>()
        val seen = mutableSetOf<String>()
        val rejected = mutableSetOf<String>()
        var capacity: Int? = null
        var ships: Int? = null
        for (line in lines) {
            val value = line.trim().replace(Regex("""\s+"""), " ")
            val match = stockRow.matchEntire(value)
            if (match != null) {
                val resource = names[match.groupValues[1].uppercase()] ?: continue
                val amount = match.groupValues[2].replace(" ", "").toIntOrNull()
                val reserveText = match.groupValues[3]
                val reserved = if (reserveText.isBlank()) null else reserveText.replace(" ", "").toIntOrNull()
                if (amount == null || amount < 0 || reserved == null || reserved < 0 || reserved > amount) {
                    warnings += "Stock ou réserve non vérifié : $resource"
                    continue
                }
                if (!seen.add(resource)) {
                    rejected.add(resource)
                    supplies.removeAll { it.resource == resource }
                    warnings += "Doublon de stock : $resource"
                    continue
                }
                if (resource !in rejected) supplies += ShipmentPlanner.Supply(resource, amount, reserved)
                continue
            }
            capacityRow.matchEntire(value)?.let {
                val n = it.groupValues[1].replace(" ", "").toIntOrNull()
                if (capacity != null || n == null || n <= 0) {
                    capacity = null
                    warnings += "Capacité ambiguë"
                } else capacity = n
            }
            shipsRow.matchEntire(value)?.let {
                val n = it.groupValues[1].replace(" ", "").toIntOrNull()
                if (ships != null || n == null || n < 0) {
                    ships = null
                    warnings += "Navires ambigus"
                } else ships = n
            }
        }
        if (supplies.isEmpty()) warnings += "Aucun stock de port avec réserve explicite"
        if (capacity == null) warnings += "Capacité navire non confirmée"
        if (ships == null) warnings += "Nombre de navires non confirmé"
        return Result(supplies, warnings, capacity, ships)
    }
}
