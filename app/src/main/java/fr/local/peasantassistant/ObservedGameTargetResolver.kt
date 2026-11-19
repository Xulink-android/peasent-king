package fr.local.peasantassistant

import java.text.Normalizer

/** Converts live OCR bounds into a gesture target only when the game phase and plan agree. */
object ObservedGameTargetResolver {
    data class Box(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        fun valid(width: Int, height: Int) = left >= 0 && top >= 0 && right <= width && bottom <= height &&
            right > left && bottom > top
    }
    data class Point(val x: Int, val y: Int)
    data class Gesture(val start: Point, val end: Point, val isDrag: Boolean)
    data class ShipmentEvidence(
        val kingdom: String,
        val resource: String,
        val quantity: Int,
        val confirmedAvailable: Int?,
        val shipCapacity: Int?,
        val observedResource: String?,
        val unambiguous: Boolean,
        val expectedReward: Int? = null,
        val loadedQuantity: Int? = 0
    )

    private fun norm(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase().replace(Regex("\\s+"), " ").trim()

    fun destinationTap(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        evidence: ShipmentEvidence
    ): Gesture? {
        if (screen != GameScreenClassifier.Screen.SHIP_READY_TO_SAIL || !validIdentity(evidence) ||
            evidence.shipCapacity == null || evidence.shipCapacity < evidence.quantity || evidence.observedResource != evidence.resource ||
            evidence.loadedQuantity != evidence.quantity) return null
        val exactCargo = Regex("^ENVOYER\\s+${evidence.quantity}/${evidence.quantity}(?:\\s.*)?$")
        if (boxes.none { exactCargo.matches(norm(it.text)) }) return null
        val expected = norm("Naviguez vers ${evidence.kingdom}")
        val targets = boxes.filter { norm(it.text) == expected && it.valid(width, height) }
        return targets.singleOrNull()?.let { Gesture(Point(it.centerX, it.centerY), Point(it.centerX, it.centerY), false) }
    }

    /** Taps only the unique accept button adjacent to the exact planned contract card. */
    fun acceptContractTap(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        evidence: ShipmentEvidence
    ): Gesture? {
        val reward = evidence.expectedReward ?: return null
        if (screen != GameScreenClassifier.Screen.WORLD_MAP || !validLoadPlan(evidence) ||
            evidence.observedResource != evidence.resource || reward <= 0) return null
        val send = boxes.filter { norm(it.text) == "ENVOYER ${evidence.quantity}" && it.valid(width, height) }
        val earn = boxes.filter { norm(it.text) == "GAGNER $reward" && it.valid(width, height) }
        val accept = boxes.filter { norm(it.text) in setOf("ACCEPTER", "ACCEPT") && it.valid(width, height) }
        val eligible = accept.filter { button ->
            send.any { kotlin.math.abs(it.centerY - button.centerY) <= height / 12 } &&
                earn.any { kotlin.math.abs(it.centerY - button.centerY) <= height / 10 }
        }
        val target = eligible.singleOrNull() ?: return null
        return Gesture(Point(target.centerX, target.centerY), Point(target.centerX, target.centerY), false)
    }

    /** Drag one verified resource stack onto the ship. Stock OCR and both resource identifications are mandatory. */
    fun loadCargoDrag(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        stockLabel: Box,
        available: Int?,
        evidence: ShipmentEvidence
    ): Gesture? {
        val loaded = evidence.loadedQuantity ?: return null
        val remaining = evidence.quantity - loaded
        if (screen != GameScreenClassifier.Screen.SHIP_LOADING || !validLoadPlan(evidence) ||
            loaded !in 0 until evidence.quantity || evidence.observedResource != evidence.resource ||
            available == null || available != evidence.confirmedAvailable || available < remaining ||
            evidence.confirmedAvailable == null || evidence.confirmedAvailable < remaining ||
            evidence.shipCapacity == null || evidence.shipCapacity < evidence.quantity ||
            !stockLabel.valid(width, height)) return null
        val normalized = norm(stockLabel.text)
        if (!Regex("^${available}\\s*/\\s*[0-9]+$").matches(normalized)) return null
        val hint = boxes.singleOrNull { norm(it.text).contains("MAINTIENS LA CARGAISON") } ?: return null
        val warehouse = boxes.singleOrNull { norm(it.text) == "ENTREPOT" } ?: return null
        if (!hint.valid(width, height) || !warehouse.valid(width, height) || hint.bottom >= warehouse.top) return null
        val sourceY = stockLabel.top - ((stockLabel.bottom - stockLabel.top) * 2.5f).toInt()
        val source = Point(stockLabel.centerX, sourceY)
        // The recorded UI places the cargo drop zone between its instruction and the warehouse heading.
        val target = Point(width / 2, (hint.bottom + warehouse.top) / 2)
        if (!inside(source, width, height) || !inside(target, width, height)) return null
        return Gesture(source, target, true)
    }

    fun paymentTap(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        evidence: ShipmentEvidence
    ): Gesture? {
        val expectedPayment = evidence.expectedReward ?: return null
        if (screen != GameScreenClassifier.Screen.COLLECT_PAYMENT || evidence.kingdom.isBlank() || expectedPayment <= 0) return null
        val destinationSeen = boxes.any { norm(it.text).contains(norm(evidence.kingdom)) }
        if (!destinationSeen) return null
        val expected = Regex("^RECUPERER\\s+${expectedPayment}(?:\\s.*)?$")
        val targets = boxes.filter { expected.matches(norm(it.text)) && it.valid(width, height) }
        return targets.singleOrNull()?.let { Gesture(Point(it.centerX, it.centerY), Point(it.centerX, it.centerY), false) }
    }

    /** Return only after the game's own return-ready screen shows the exact secured reward. */
    fun returnShipTap(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        evidence: ShipmentEvidence
    ): Gesture? {
        val reward = evidence.expectedReward ?: return null
        if (screen != GameScreenClassifier.Screen.RETURN_READY || evidence.kingdom.isBlank() || reward <= 0) return null
        val destinationSuffix = " A ${norm(evidence.kingdom)}"
        val destination = boxes.any {
            val label = norm(it.text)
            label.contains(" A ") && label.endsWith(destinationSuffix)
        }
        val secured = boxes.any { norm(it.text) == "$reward ARGENT CHARGE" }
        if (!destination || !secured) return null
        val targets = boxes.filter { norm(it.text) == "REPARTIR" && it.valid(width, height) }
        return targets.singleOrNull()?.let { Gesture(Point(it.centerX, it.centerY), Point(it.centerX, it.centerY), false) }
    }

    /** Drag confirmed cargo from the arriving ship into the specifically named kingdom warehouse. */
    fun unloadCargoDrag(
        screen: GameScreenClassifier.Screen,
        boxes: List<Box>,
        width: Int,
        height: Int,
        evidence: ShipmentEvidence
    ): Gesture? {
        if (screen != GameScreenClassifier.Screen.UNLOAD_CARGO || !validIdentity(evidence) ||
            evidence.observedResource != evidence.resource) return null
        val ship = boxes.singleOrNull { norm(it.text).contains(" A ${norm(evidence.kingdom)}") } ?: return null
        val hint = boxes.singleOrNull { norm(it.text).contains("FAITES-LA GLISSER VERS LE MAGASIN") } ?: return null
        val warehouse = boxes.singleOrNull { norm(it.text) == "ENTREPOT DU ROYAUME" } ?: return null
        val remaining = boxes.singleOrNull { Regex("^[0-9]+ CARGAISON RESTANTE$").matches(norm(it.text)) } ?: return null
        if (listOf(ship, hint, warehouse, remaining).any { !it.valid(width, height) } ||
            ship.bottom >= hint.top || hint.bottom >= warehouse.top || warehouse.bottom >= remaining.top) return null
        val source = Point(width / 2, (ship.bottom + hint.top) / 2)
        val destination = Point(width / 2, (warehouse.bottom + remaining.top) / 2)
        if (!inside(source, width, height) || !inside(destination, width, height)) return null
        return Gesture(source, destination, true)
    }

    private fun validIdentity(value: ShipmentEvidence) = value.unambiguous && value.kingdom.isNotBlank() &&
        value.resource.isNotBlank() && value.quantity > 0

    private fun validLoadPlan(value: ShipmentEvidence) = validIdentity(value) &&
        value.confirmedAvailable != null && value.shipCapacity != null

    private fun inside(point: Point, width: Int, height: Int) =
        point.x in 1 until width - 1 && point.y in 1 until height - 1
}
