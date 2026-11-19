package fr.local.peasantassistant

import kotlin.math.abs

/** Pure OCR interpretation; never guesses the resource from a quantity. */
object ContractParser {
    data class Line(val text: String, val x: Int, val y: Int)
    data class Contract(val kingdom: String?, val amount: Int, val y: Int, val resource: String?)
    data class Result(val contracts: List<Contract>, val warnings: List<String>, val context: String)

    private val send = Regex("""^(?:ENVOYER|SEND)\s+([0-9][0-9 .]*)\s*$""", RegexOption.IGNORE_CASE)
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
            val match = send.matchEntire(line.text.trim()) ?: return@mapNotNull null
            val n = match.groupValues[1].filter { it.isDigit() }.toIntOrNull()
                ?: return@mapNotNull null
            if (n <= 0 || n > 1000000) return@mapNotNull null
            line to n
        }
        val contracts = sends.map { (line, n) ->
            // Prevent adjacent contract columns from sharing a kingdom or resource.
            // A candidate belongs only to its uniquely closest SEND column.
            val nearby = lines.filter { candidate ->
                if (candidate === line || abs(candidate.y - line.y) > 15 ||
                    abs(candidate.x - line.x) > 30) return@filter false
                val distances = sends.map { abs(candidate.x - it.first.x) }
                val nearest = distances.minOrNull() ?: return@filter false
                distances.count { it == nearest } == 1 &&
                    abs(candidate.x - line.x) == nearest
            }
            val kingdom = nearby.filter { it.y >= line.y && it.y <= line.y + 10 }
                .filter { candidate ->
                    val value = candidate.text.trim()
                    value.length in 4..36 && value.uppercase() !in excluded &&
                        !value.any { it.isDigit() } &&
                        !value.contains("CONTRAT", true) && !value.contains("ENVOYER", true)
                }.minByOrNull { abs(it.y - line.y - 2) }?.text
            val resource = nearby.mapNotNull { candidate ->
                resourceWords.entries.firstOrNull { (word, _) ->
                    Regex("""(?<![\p{L}])${Regex.escape(word)}(?![\p{L}])""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(candidate.text)
                }?.value
            }.distinct().singleOrNull()
            Contract(kingdom, n, line.y, resource)
        }
        val warnings = mutableListOf<String>()
        if (contracts.isEmpty()) warnings += "Aucune demande ENVOYER reconnue."
        if (contracts.any { it.resource == null }) warnings += "Ressource inconnue : icônes non reconnues. Aucun chargement automatique."
        if (contracts.any { it.kingdom == null }) warnings += "Certains royaumes ne sont pas associés avec certitude."
        val context = when {
            lines.any { Regex("""\bCONTRAT(?:S)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) } -> "Écran de contrats probable"
            lines.any { it.text.contains("ENTREPÔT", true) || it.text.contains("WAREHOUSE", true) } -> "Écran de stocks probable"
            else -> "Écran non identifié"
        }
        return Result(contracts, warnings, context)
    }
}
