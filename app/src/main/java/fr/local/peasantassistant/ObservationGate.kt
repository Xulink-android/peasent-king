package fr.local.peasantassistant

/**
 * Safety gate for screen observations. A single OCR frame is never considered reliable.
 * Only a repeated, identical, unambiguous contract can become a candidate.
 */
class ObservationGate(private val requiredMatches: Int = 3) {
    data class Snapshot(val kingdom: String, val resource: String, val amount: Int)
    data class Verdict(val stable: List<Snapshot>, val message: String)

    private var previous: List<Snapshot>? = null
    private var matches = 0

    fun reset() {
        previous = null
        matches = 0
    }

    fun observe(result: ContractParser.Result): Verdict {
        if (result.contracts.isEmpty()) {
            reset()
            return Verdict(emptyList(), "Aucun contrat visible : observation réinitialisée")
        }
        if (result.contracts.any { it.kingdom.isNullOrBlank() || it.resource.isNullOrBlank() }) {
            reset()
            return Verdict(emptyList(), "Contrat incomplet : pas de décision")
        }
        val current = result.contracts.map {
            Snapshot(it.kingdom!!.trim(), it.resource!!.trim(), it.amount)
        }.sortedWith(compareBy({ it.kingdom }, { it.resource }, { it.amount }))
        if (current.distinct().size != current.size) {
            reset()
            return Verdict(emptyList(), "Doublons ambigus : pas de décision")
        }
        matches = if (current == previous) matches + 1 else 1
        previous = current
        return if (matches >= requiredMatches) {
            Verdict(current, "Contrats stables après $matches observations (simulation seulement)")
        } else {
            Verdict(emptyList(), "Confirmation OCR : $matches/$requiredMatches")
        }
    }
}
