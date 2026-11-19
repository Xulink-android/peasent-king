package fr.local.peasantassistant

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject

/**
 * Local, opt-in labels for user-reviewed icon crops. Never uploads samples.
 * A visual match is diagnostic only and must not authorize gestures.
 */
object IconLibrary {
    val labels = listOf("coal", "grain", "potatoes", "lumber", "cocoa", "silk")
    private const val PREFS = "icon_training_v1"

    fun save(context: Context, label: String, bitmap: Bitmap): Boolean {
        if (label !in labels) return false
        val hash = IconSamples.perceptualHash(bitmap)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt("count_$label", 0)
        val index = count.coerceAtMost(19)
        return prefs.edit()
            .putString("hash_${label}_$index", java.lang.Long.toUnsignedString(hash))
            .putInt("count_$label", (count + 1).coerceAtMost(20))
            .commit()
    }

    fun references(context: Context): Map<String, List<Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return labels.mapNotNull { label ->
            val hashes = (0 until prefs.getInt("count_$label", 0)).mapNotNull { i ->
                prefs.getString("hash_${label}_$i", null)
                    ?.let { runCatching { java.lang.Long.parseUnsignedLong(it) }.getOrNull() }
            }
            if (hashes.isEmpty()) null else label to hashes
        }.toMap()
    }

    fun recognize(context: Context, bitmap: Bitmap): String? {
        val refs = references(context)
        if (refs.size < 2) return null
        val sample = IconSamples.perceptualHash(bitmap)
        val scores = refs.map { (label, hashes) ->
            label to hashes.minOf { IconSamples.distance(sample, it) }
        }.sortedBy { it.second }
        val best = scores[0]
        val runnerUp = scores[1]
        return best.first.takeIf { best.second <= 4 && runnerUp.second - best.second >= 5 }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
