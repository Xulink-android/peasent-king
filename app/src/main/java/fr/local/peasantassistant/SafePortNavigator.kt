package fr.local.peasantassistant

import kotlin.math.abs

/**
 * Recognises the Port navigation button from live OCR bounds.
 * Only a uniquely labelled button in the expected world-map region is eligible.
 * No fixed tap coordinates and no game gesture implementation here.
 */
object SafePortNavigator {
    data class TextBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Target(val x: Int, val y: Int, val width: Int, val height: Int)

    fun detect(boxes: List<TextBox>, width: Int, height: Int): Target? {
        if (width < 300 || height < 600 || width >= height) return null
        val worldScreen = boxes.any {
            val label = it.text.trim().uppercase()
            (label == "MONDE" && it.top > height * 0.92 && it.left > width * 0.65) ||
            (label == "TERRE" && it.top < height * 0.18)
        }
        if (!worldScreen) return null
        val candidates = boxes.filter {
            it.text.trim().equals("PORT", ignoreCase = true) &&
            it.left >= width * 0.60 && it.right <= width &&
            it.top >= height * 0.82 && it.bottom <= height * 0.96 &&
            it.right > it.left && it.bottom > it.top
        }
        if (candidates.size != 1) return null
        val hit = candidates.single()
        if (hit.right - hit.left < 8 || hit.right - hit.left > width / 4 ||
            hit.bottom - hit.top < 8 || hit.bottom - hit.top > height / 15) return null
        val x = (hit.left + hit.right) / 2
        val y = (hit.top + hit.bottom) / 2
        return Target(x, y, width, height)
    }
}

/**
 * Three consecutive readings must identify the *same* location. Any ambiguity
 * or screen transition resets the gate. No tap is triggered by this class.
 */
class PortTargetGate(private val matchesRequired: Int = 3) {
    private var previous: SafePortNavigator.Target? = null
    private var matches = 0

    fun reset() {
        previous = null
        matches = 0
    }

    fun observe(candidate: SafePortNavigator.Target?): SafePortNavigator.Target? {
        if (candidate == null) {
            reset()
            return null
        }
        val old = previous
        matches = if (old != null && old.width == candidate.width &&
            old.height == candidate.height &&
            abs(old.x - candidate.x) <= candidate.width / 100 &&
            abs(old.y - candidate.y) <= candidate.height / 100) matches + 1 else 1
        previous = candidate
        return candidate.takeIf { matches >= matchesRequired }
    }
}
