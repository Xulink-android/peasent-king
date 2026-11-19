package fr.local.peasantassistant

import kotlin.math.abs

/** Pure OCR interpretation; never guesses the resource from a quantity. */
object ContractParser {
    data class Line(val text: String, val x: Int, val y: Int)
    data class Contract(val kingdom: String?, val amount: Int, val y: Int, val resource: String?)
    data class Result(val contracts: List<Contract>, val warnings: List<String>, val context: String)

    private val send = Regex("""(?:ENVOYER|SEND)\\s*([0-9][0-9 .]*)""", RegexOption.IGNORE_CASE)
    private val resourceWords = linkedMapOf(
        "coal" to "coal", "charbon" to "coal",
        "grain" to "grain", "wheat" to "grain", "blé" to "grain",
        "potatoes" to "potatoes", "pomme de terre" to "potatoes",
        "lumber" to "lumber", "wood" to "lumber", "bois" to "lumber",
        "cocoa" to "cocoa", "cacao" to "cocoa",
        "silk" to "silk", "soie" to "silk"
    )
    private val excluded = setOf("CONTRAT", "ACCEPTER", "GAGNER", "PORT", "MARCHÉ", "MARCHE", "FERME", "TERRE", "MER", "MONDE", "ROYAUME", "DOMAINE", "AD")
    fun parse(lines: List<Line>): Result {
        val sends = lines.mapNotNull { line ->
            val match = send.find(line.text) ?: return@mapNotNull null
            val n = match.groupValues[1].filter { it.isDigit() }.toIntOrNull()
                ?: return@mapNotNull null
            if (n <= 0 || n > 1000000) return@mapNotNull null
            line to n
        }
        val contracts = sends.map { (line, n) ->
            val nearby = lines.filter { it !== line && abs(it.y - line.y) <= 15 && abs(it.x - line.x) <= 30 }
            val kingdom = nearby.filter { it.y >= line.y && it.y <= line.y + 10 }
                .filter { candidate ->
                    val value = candidate.text.trim()
                    value.length in 4..36 && value.uppercase() !in excluded &&
                        !value.any { it.isDigit() } &&
                        !value.contains("CONTRAT", true) && !value.contains("ENVOYER", true)
                }.minByOrNull { abs(it.y - line.y - 2) }?.text
            val resource = nearby.mapNotNull { candidate ->
                resourceWords.entries.firstOrNull { candidate.text.contains(it.key, true) }?.value
            }.distinct().singleOrNull()
            Contract(kingdom, n, line.y, resource)
        }
        val warnings = mutableListOf<String>()
        if (contracts.isEmpty()) warnings += "Aucune demande ENVOYER reconnue."
        if (contracts.any { it.resource == null }) warnings += "Ressource inconnue : icônes non reconnues. Aucun chargement automatique."
        if (contracts.any { it.kingdom == null }) warnings += "Certains royaumes ne sont pas associés avec certitude."
        val context = when {
            lines.any { it.text.contains("CONTRAT", true) } -> "Écran de contrats probable"
            lines.any { it.text.contains("ENTREPÔT", true) || it.text.contains("WAREHOUSE", true) } -> "Écran de stocks probable"
            else -> "Écran non identifié"
        }
        return Result(contracts, warnings, context)
    }
}
