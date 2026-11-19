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
        val unambiguous: Boolean
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
        if (screen != GameScreenClassifier.Screen.SHIP_READY_TO_SAIL || !validPlan(evidence) ||
            evidence.confirmedAvailable!! < evidence.quantity || evidence.shipCapacity!! < evidence.quantity ||
            evidence.observedResource != evidence.resource) return null
        val exactCargo = Regex("^ENVOYER\\s+${evidence.quantity}/${evidence.quantity}(?:\\s.*)?$")
        if (boxes.none { exactCargo.matches(norm(it.text)) }) return null
        val expected = norm("Naviguez vers ${evidence.kingdom}")
        val targets = boxes.filter { norm(it.text) == expected && it.valid(width, height) }
        return targets.singleOrNull()?.let { Gesture(Point(it.centerX, it.centerY), Point(it.centerX, it.centerY), false) }
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
        if (screen != GameScreenClassifier.Screen.SHIP_LOADING || !validPlan(evidence) ||
            evidence.observedResource != evidence.resource || available == null || available < evidence.quantity ||
            evidence.confirmedAvailable == null || evidence.confirmedAvailable < evidence.quantity ||
            evidence.shipCapacity == null || evidence.shipCapacity < evidence.quantity ||
            !stockLabel.valid(width, height)) return null
        val normalized = norm(stockLabel.text)
        if (!Regex("^${available}\\s*/\\s*[0-9]+$").matches(normalized)) return null
        val hint = boxes.singleOrNull { norm(it.text).contains("MAINTIENS LA CARGAISON") } ?: return null
        val warehouse = boxes.singleOrNull { norm(it.text) == "ENTREPOT" } ?: return null
        if (!hint.valid(width, height) || !warehouse.valid(width, height) || hint.bottom >= warehouse.top) return null
        val source = Point(stockLabel.centerX, stockLabel.centerY)
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
        kingdom: String,
        expectedPayment: Int
    ): Gesture? {
        if (screen != GameScreenClassifier.Screen.COLLECT_PAYMENT || kingdom.isBlank() || expectedPayment <= 0) return null
        val destinationSeen = boxes.any { norm(it.text).contains(norm(kingdom)) }
        if (!destinationSeen) return null
        val expected = Regex("^RECUPERER\\s+${expectedPayment}(?:\\s.*)?$")
        val targets = boxes.filter { expected.matches(norm(it.text)) && it.valid(width, height) }
        return targets.singleOrNull()?.let { Gesture(Point(it.centerX, it.centerY), Point(it.centerX, it.centerY), false) }
    }

    private fun validPlan(value: ShipmentEvidence) = value.unambiguous && value.kingdom.isNotBlank() &&
        value.resource.isNotBlank() && value.quantity > 0 && value.confirmedAvailable != null &&
        value.shipCapacity != null

    private fun inside(point: Point, width: Int, height: Int) =
        point.x in 1 until width - 1 && point.y in 1 until height - 1
}
