package fr.local.peasantassistant

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Local-only icon reference collector. Crops are intentionally unlabelled:
 * OCR cannot establish which resource a pictogram represents.
 */
object IconSamples {
    data class Candidate(val x: Int, val y: Int, val width: Int, val height: Int)

    fun cropLeftOfText(bitmap: Bitmap, textBounds: android.graphics.Rect): Bitmap? {
        val size = (textBounds.height() * 1.6f).toInt().coerceIn(28, 160)
        val right = (textBounds.left - size / 5).coerceAtMost(bitmap.width)
        val left = (right - size).coerceAtLeast(0)
        val top = (textBounds.centerY() - size / 2).coerceAtLeast(0)
        val w = (right - left).coerceAtMost(bitmap.width - left)
        val h = size.coerceAtMost(bitmap.height - top)
        if (w < 20 || h < 20) return null
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    fun saveCandidate(dir: File, bitmap: Bitmap, index: Int): Boolean {
        if (index !in 0..9) return false
        if (!dir.exists() && !dir.mkdirs()) return false
        val file = File(dir, "candidate_$index.png")
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) { false }
    }

    /** 8x8 grayscale difference; not enough on its own to authorize an action. */
    fun perceptualHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var bits = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val a = small.getPixel(x, y)
                val b = small.getPixel(x + 1, y)
                fun gray(color: Int): Int =
                    (Color.red(color) * 299 + Color.green(color) * 587 +
                        Color.blue(color) * 114) / 1000
                bits = (bits shl 1) or if (gray(a) > gray(b)) 1L else 0L
            }
        }
        if (small !== bitmap) small.recycle()
        return bits
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun classify(sample: Long, references: Map<String, Long>, maxDistance: Int = 6): String? {
        if (references.size < 2) return null
        val ranked = references.map { it.key to distance(sample, it.value) }.sortedBy { it.second }
        val best = ranked.first()
        val second = ranked[1]
        return best.first.takeIf { best.second <= maxDistance && second.second - best.second >= 4 }
    }
}
