package fr.local.peasantassistant

/**
 * Confirms a single selected ship's visible warehouse piles over repeated frames.
 * It refuses unknown or repeated badge labels and never turns a partial grid into stock.
 */
class BadgeInventoryGate(private val requiredMatches: Int = 3) {
    private var previous: VerifiedStockGate.Verified? = null
    private var matches = 0

    fun reset() {
        previous = null
        matches = 0
    }

    fun observe(
        selectedShipScreen: Boolean,
        badges: ShipyardBadgeStockParser.Result,
        shipCapacity: Int?
    ): VerifiedStockGate.Verified? {
        if (!selectedShipScreen || badges.stacks.isEmpty() || badges.warnings.isNotEmpty() ||
            badges.stacks.any { it.resource == null || it.amount <= 0 } ||
            badges.stacks.map { it.resource }.distinct().size != badges.stacks.size ||
            shipCapacity == null || shipCapacity <= 0) {
            reset()
            return null
        }
        val snapshot = VerifiedStockGate.Verified(
            supplies = badges.stacks.map {
                ShipmentPlanner.Supply(it.resource!!, it.amount, reserved = 0)
            }.sortedBy { it.resource },
            capacity = shipCapacity,
            ships = 1
        )
        matches = if (snapshot == previous) matches + 1 else 1
        previous = snapshot
        return snapshot.takeIf { matches >= requiredMatches }
    }
}
