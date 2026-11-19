package fr.local.peasantassistant

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject

/**
 * Local, opt-in labels for user-reviewed icon crops. Never uploads samples.
 * A visual match is diagnostic only and must not authorize gestures.
 */
object IconLibrary {
    val labels = listOf("coal", "grain", "potatoes", "lumber", "cocoa", "silk", "arms")
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
        val sample = IconSamples.perceptualHash(bitmap)
        val inGameCandidate = InGameResourceBadges.candidate(sample)
        if (refs.size < 2) return inGameCandidate ?: BundledResourceFingerprints.candidate(sample)
        val scores = refs.map { (label, hashes) ->
            label to hashes.minOf { IconSamples.distance(sample, it) }
        }.sortedBy { it.second }
        val best = scores[0]
        val runnerUp = scores[1]
        val userCandidate = best.first.takeIf { best.second <= 4 && runnerUp.second - best.second >= 5 }
        val bundledCandidate = BundledResourceFingerprints.candidate(sample)
        // Conflicting reference sets are not a reliable identification.
        return when {
            userCandidate != null && inGameCandidate != null && userCandidate != inGameCandidate -> null
            userCandidate != null && bundledCandidate != null && userCandidate != bundledCandidate -> null
            else -> userCandidate ?: inGameCandidate ?: bundledCandidate
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
