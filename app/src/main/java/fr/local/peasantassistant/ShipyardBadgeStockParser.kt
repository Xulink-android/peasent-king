package fr.local.peasantassistant

/** Diagnostic-only parser for the small resource badges beside shipyard stock counters. */
object ShipyardBadgeStockParser {
    data class OcrLine(val text: String, val resource: String?)
    data class Stack(val resource: String?, val amount: Int, val stackCapacity: Int)
    data class Result(val stacks: List<Stack>, val warnings: List<String>)

    private val counter = Regex("""^\s*([0-9][0-9 .]*)\s*/\s*([0-9][0-9 .]*)\s*$""")

    fun parse(lines: List<OcrLine>): Result {
        val stacks = mutableListOf<Stack>()
        val warnings = mutableListOf<String>()
        for (line in lines) {
            val match = counter.matchEntire(line.text) ?: continue
            val amount = match.groupValues[1].filter { it.isDigit() }.toIntOrNull()
            val capacity = match.groupValues[2].filter { it.isDigit() }.toIntOrNull()
            if (amount == null || capacity == null || capacity <= 0 || amount > capacity) {
                warnings += "Compteur de pile incohérent : ${line.text}"
                continue
            }
            val resource = line.resource?.takeIf { it in InGameResourceBadges.knownResources() || it in IconLibrary.labels }
            if (resource == null) warnings += "Icône de ressource non reconnue : ${line.text}"
            stacks += Stack(resource, amount, capacity)
        }
        val duplicates = stacks.mapNotNull { it.resource }.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        duplicates.forEach { warnings += "Plusieurs piles ambiguës pour $it" }
        return Result(stacks.map {
            if (it.resource != null && it.resource in duplicates) it.copy(resource = null) else it
        }, warnings)
    }
}
