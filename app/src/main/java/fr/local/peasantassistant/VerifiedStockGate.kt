package fr.local.peasantassistant

/**
 * Confirms a complete port snapshot across three identical observations.
 * Never carries stock from a different screen or app session.
 */
class VerifiedStockGate(private val requiredMatches: Int = 3) {
    data class Verified(
        val supplies: List<ShipmentPlanner.Supply>,
        val capacity: Int,
        val ships: Int
    )
    private var previous: Verified? = null
    private var matches = 0

    fun reset() {
        previous = null
        matches = 0
    }

    fun observe(screenConfirmed: Boolean, result: StockScreenParser.Result): Verified? {
        if (!screenConfirmed || result.warnings.isNotEmpty() ||
            result.supplies.isEmpty() || result.shipCapacity == null ||
            result.availableShips == null || result.availableShips <= 0) {
            reset()
            return null
        }
        val current = Verified(
            result.supplies.sortedBy { it.resource },
            result.shipCapacity,
            result.availableShips
        )
        matches = if (current == previous) matches + 1 else 1
        previous = current
        return current.takeIf { matches >= requiredMatches }
    }
}
