package fr.local.peasantassistant

/**
 * dHash references for tiny resource badges rendered beside stock counts in the
 * user's Peasant to King 0.1.46 recording. These are fingerprints only; no game
 * artwork is bundled. Cocoa and silk badges were not present in that recording.
 */
object InGameResourceBadges {
    private val references = mapOf(
        "grain" to listOf(0xd01c2e2e3e9c9c3dUL.toLong()),
        "potatoes" to listOf(0x8820991c365e0c8dUL.toLong(), 0x8820991c365e0c8fUL.toLong()),
        "coal" to listOf(0x8c9c1899a8608180UL.toLong()),
        "lumber" to listOf(0x8e1e367e7e7c38c0UL.toLong()),
        "arms" to listOf(0x000315250d993941UL.toLong(), 0x040b15650d993941UL.toLong())
    )

    fun candidate(sample: Long, maxDistance: Int = 4, minMargin: Int = 6): String? {
        val ranked = references.map { (label, hashes) ->
            label to hashes.minOf { IconSamples.distance(sample, it) }
        }.sortedBy { it.second }
        if (ranked.size < 2) return null
        val best = ranked[0]
        val runnerUp = ranked[1]
        return best.first.takeIf { best.second <= maxDistance && runnerUp.second - best.second >= minMargin }
    }

    fun knownResources(): Set<String> = references.keys
}
