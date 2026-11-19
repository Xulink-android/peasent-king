package fr.local.peasantassistant

import java.text.Normalizer

/**
 * OCR screen landmarks actually seen in the user's 109-second game recording.
 * Classification is diagnostic only: the video does not prove safe gesture
 * coordinates, cargo slots, or whether a contract was fulfilled.
 */
object GameScreenClassifier {
    enum class Screen {
        WORLD_MAP, SHIPYARD, SHIP_SAILING, UNLOAD_CARGO, PAYMENT_IN_TRANSIT,
        COLLECT_PAYMENT, MARKET, VILLAGE, UNKNOWN
    }

    data class Result(val screen: Screen, val evidence: List<String>, val confidence: String)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .uppercase()
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun classify(lines: List<String>): Result {
        val strings = lines.map(::normalize).filter { it.isNotBlank() }
        fun has(fragment: String) = strings.any { normalize(fragment) in it }
        val matches = mutableListOf<Pair<Screen, List<String>>>()
        if (has("CHANTIER NAVAL") && has("ENTREPOT")) matches +=
            Screen.SHIPYARD to listOf("CHANTIER NAVAL", "ENTREPOT")
        if (has("DECHARGEMENT DU NAVIRE") && has("ENTREPOT DU ROYAUME")) matches +=
            Screen.UNLOAD_CARGO to listOf("DECHARGEMENT DU NAVIRE", "ENTREPOT DU ROYAUME")
        if (has("PAIEMENT EN ROUTE") && has("ENTREPOT DU ROYAUME")) matches +=
            Screen.PAYMENT_IN_TRANSIT to listOf("PAIEMENT EN ROUTE", "ENTREPOT DU ROYAUME")
        if (has("COLLECTER LE PAIEMENT") && has("ENTREPOT DU ROYAUME")) matches +=
            Screen.COLLECT_PAYMENT to listOf("COLLECTER LE PAIEMENT", "ENTREPOT DU ROYAUME")
        if (has("FLOTTE OCCUPEE") && has("CONTRAT")) matches +=
            Screen.SHIP_SAILING to listOf("FLOTTE OCCUPEE", "CONTRAT")
        if (has("CONFIANCE DES ROYAUMES") && has("PORT") && !has("CHANTIER NAVAL")) matches +=
            Screen.WORLD_MAP to listOf("CONFIANCE DES ROYAUMES", "PORT")
        if (has("MARCHE") && has("PRIX DU BLE")) matches +=
            Screen.MARKET to listOf("MARCHE", "PRIX DU BLE")
        if (has("TRAVAILLEURS TEMPORAIRES") && has("AGRANDIR")) matches +=
            Screen.VILLAGE to listOf("TRAVAILLEURS TEMPORAIRES", "AGRANDIR")
        // Overlapping matches cannot authorize a workflow transition.
        if (matches.size != 1) return Result(Screen.UNKNOWN, emptyList(), "inconnu ou ambigu")
        val (screen, evidence) = matches.single()
        return Result(screen, evidence, "indices OCR concordants (non validé sur appareil)")
    }
}
