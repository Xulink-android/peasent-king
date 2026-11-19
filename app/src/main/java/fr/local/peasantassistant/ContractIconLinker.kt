package fr.local.peasantassistant

import kotlin.math.abs

/**
 * Associates a diagnostic image classification with exactly one SEND row.
 * Never infers a missing icon, merges columns or authorizes a gesture.
 */
object ContractIconLinker {
    data class Candidate(val x: Int, val y: Int, val label: String)

    fun annotate(
        result: ContractParser.Result,
        candidates: List<Candidate>
    ): ContractParser.Result {
        if (result.context != "Écran de contrats probable") return result
        val attached = result.contracts.map { contract ->
            if (contract.x < 0 || contract.resource != null) return@map contract
            val near = candidates.filter {
                abs(it.y - contract.y) <= 2 &&
                    abs(it.x - contract.x) <= 2 &&
                    it.label in BundledResourceFingerprints.knownResources()
            }
            if (near.size != 1) return@map contract
            val match = near.single()
            val contenders = result.contracts.count {
                it.x >= 0 && abs(it.y - match.y) <= 2 && abs(it.x - match.x) <= 2
            }
            if (contenders != 1) contract else contract.copy(resource = match.label)
        }
        return result.copy(contracts = attached)
    }
}
