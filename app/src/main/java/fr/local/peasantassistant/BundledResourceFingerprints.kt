package fr.local.peasantassistant

/**
 * Non-reversible dHash fingerprints generated from the resource artwork in the
 * user-supplied Peasant to King 0.1.46 APK archive (Godot .ctex WebP).
 * Two neutral compositing backgrounds are represented per icon.
 *
 * Fingerprints identify candidates for diagnostics ONLY. In-game rendering,
 * background and icon crops have not been calibrated, so a match must never
 * authorize an Android gesture or an actual shipment.
 */
object BundledResourceFingerprints {
    private val references: Map<String, List<Long>> = mapOf(
        "coal" to listOf("0000181cdc700000", "001070f8f8f01000"),
        "grain" to listOf("0e0f171f1e1e1e0e", "b0f4f4fcf8f8d870"),
        "potatoes" to listOf("000c0e2b4f462600", "0078e8e8cee43800"),
        "lumber" to listOf("00070f373f3f3c10", "087cfcf0fcf8f060"),
        "cocoa" to listOf("00080c2c266c1000", "001878f8e0781000"),
        "silk" to listOf("000006271ecc1000", "0030f0e4dcf83000")
    ).mapValues { (_, hashes) ->
        hashes.map { java.lang.Long.parseUnsignedLong(it, 16) }
    }

    fun candidate(sample: Long, maxDistance: Int = 4, minMargin: Int = 6): String? {
        val ranked = references.map { (label, hashes) ->
            label to hashes.minOf { IconSamples.distance(sample, it) }
        }.sortedBy { it.second }
        if (ranked.size < 2) return null
        val best = ranked[0]
        val runnerUp = ranked[1]
        return best.first.takeIf {
            best.second <= maxDistance && runnerUp.second - best.second >= minMargin
        }
    }

    fun knownResources(): Set<String> = references.keys
}
