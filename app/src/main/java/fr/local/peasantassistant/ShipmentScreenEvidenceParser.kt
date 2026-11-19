package fr.local.peasantassistant

import java.text.Normalizer

/**
 * Extracts explicit shipment facts visible in the game UI. This parser never
 * infers a resource or emits an action; conflicting OCR values become unknown.
 */
object ShipmentScreenEvidenceParser {
    data class Evidence(
        val loadedQuantity: Int? = null,
        val requestedQuantity: Int? = null,
        val remainingCargo: Int? = null,
        val shipCapacity: Int? = null,
        val reward: Int? = null,
        val kingdom: String? = null,
        val paymentAmount: Int? = null,
        val paymentSecuredAmount: Int? = null,
        val warnings: List<String> = emptyList()
    )

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .uppercase()
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun uniqueInt(values: List<Int>, label: String, warnings: MutableList<String>): Int? {
        val distinct = values.distinct()
        if (distinct.size > 1) {
            warnings += "Valeur OCR ambiguë : $label"
            return null
        }
        return distinct.singleOrNull()
    }

    private fun uniqueText(values: List<String>, label: String, warnings: MutableList<String>): String? {
        val distinct = values.map(String::trim).filter(String::isNotBlank).distinctBy(::normalize)
        if (distinct.size > 1) {
            warnings += "Valeur OCR ambiguë : $label"
            return null
        }
        return distinct.singleOrNull()
    }

    fun parse(lines: List<String>): Evidence {
        val warnings = mutableListOf<String>()
        val normalized = lines.map(::normalize).filter(String::isNotBlank)
        val progressPattern = Regex("""^(?:ENVOYER|SEND)\s+([0-9][0-9 ]*)\s*/\s*([0-9][0-9 ]*)(?:\s+.*)?$""")
        // Contract cards use "ENVOYER 89"; only slash-form values are ship progress.
        val sendLikeLines = normalized.filter {
            (it.startsWith("ENVOYER ") || it.startsWith("SEND ")) && it.contains('/')
        }
        val progress = sendLikeLines.mapNotNull { line ->
            progressPattern.matchEntire(line)?.let { match ->
                val loaded = match.groupValues[1].replace(" ", "").toIntOrNull()
                val requested = match.groupValues[2].replace(" ", "").toIntOrNull()
                if (loaded == null || requested == null || requested <= 0 || loaded > requested) null
                else loaded to requested
            }
        }.distinct()
        if (sendLikeLines.size != progress.size) warnings += "Progression de cargaison absente ou invalide"
        if (progress.size > 1) warnings += "Progression de cargaison ambiguë"

        val capacities = normalized.mapNotNull { line ->
            Regex("""^CAPACITE\s*[:\-]?\s*([0-9][0-9 ]*)(?:\s+.*)?$""")
                .matchEntire(line)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
        val rewards = normalized.mapNotNull { line ->
            Regex("""^(?:GAGNER|EARN)\s+([0-9][0-9 ]*)$""")
                .matchEntire(line)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
        val payments = normalized.mapNotNull { line ->
            Regex("""^(?:RECUPERER|COLLECT)\s+([0-9][0-9 ]*)(?:\s+.*)?$""")
                .matchEntire(line)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
        val remainingCargoValues = normalized.mapNotNull { line ->
            Regex("""^([0-9][0-9 ]*) CARGAISON RESTANTE$""")
                .matchEntire(line)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
        val securedPaymentValues = normalized.mapNotNull { line ->
            Regex("""^([0-9][0-9 ]*) ARGENT CHARGE$""")
                .matchEntire(line)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
        val destinations = lines.mapNotNull { raw ->
            val line = raw.trim()
            Regex("""^NAVIGUEZ VERS\s+(.+)$""", RegexOption.IGNORE_CASE)
                .matchEntire(line)?.groupValues?.get(1)
                ?: Regex("""^.+\s+à\s+(.+)$""", RegexOption.IGNORE_CASE)
                    .matchEntire(line)?.groupValues?.get(1)
        }.filterNot { normalize(it) in setOf("RETOURNER", "LA MER", "LA TERRE", "L'EST") }

        return Evidence(
            loadedQuantity = progress.singleOrNull()?.first,
            requestedQuantity = progress.singleOrNull()?.second,
            remainingCargo = uniqueInt(remainingCargoValues, "cargaison restante", warnings),
            shipCapacity = uniqueInt(capacities, "capacité", warnings),
            reward = uniqueInt(rewards, "récompense", warnings),
            kingdom = uniqueText(destinations, "destination", warnings),
            paymentAmount = uniqueInt(payments, "paiement", warnings),
            paymentSecuredAmount = uniqueInt(securedPaymentValues, "paiement sécurisé", warnings),
            warnings = warnings
        )
    }
}
