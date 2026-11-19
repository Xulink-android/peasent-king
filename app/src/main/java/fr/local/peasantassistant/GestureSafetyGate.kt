package fr.local.peasantassistant

/**
 * Safety checks for a future user-enabled gesture executor.
 * No gesture is dispatched by this class.
 */
object GestureSafetyGate {
    data class Request(
        val userEnabled: Boolean,
        val gameForeground: Boolean,
        val screenConfirmed: Boolean,
        val targetConfirmed: Boolean,
        val observationAgeMs: Long,
        val screenWidth: Int,
        val screenHeight: Int,
        val x: Int,
        val y: Int
    )

    fun allowed(request: Request): Boolean =
        request.userEnabled &&
        request.gameForeground &&
        request.screenConfirmed &&
        request.targetConfirmed &&
        request.observationAgeMs in 0..1500 &&
        request.screenWidth > 0 && request.screenHeight > 0 &&
        request.x in 1 until request.screenWidth - 1 &&
        request.y in 1 until request.screenHeight - 1
}
