package fr.local.peasantassistant

/**
 * A deterministic, fail-closed shipment workflow.
 *
 * This class only issues an instruction to a future screen-calibrated executor.
 * It never taps a view, never infers missing game fields and never retries an
 * unverified shipment. It can be tested without Android or the game.
 */
class ShipmentExecutionMachine {
    enum class Screen { CONTRACTS, PORT, SHIP_LOAD, SHIP_CONFIRMATION, UNKNOWN }
    enum class Step { OPEN_PORT, CHOOSE_RESOURCE, SET_QUANTITY, SELECT_DESTINATION, CONFIRM_SEND }
    enum class State { STOPPED, WAITING_FOR_PORT, WAITING_FOR_LOAD, WAITING_FOR_QUANTITY, WAITING_FOR_DESTINATION, WAITING_FOR_CONFIRMATION, VERIFYING_RESULT, COMPLETED, BLOCKED }

    data class Shipment(val kingdom: String, val resource: String, val quantity: Int)
    data class Observation(
        val screen: Screen,
        val resource: String? = null,
        val quantity: Int? = null,
        val kingdom: String? = null,
        val sendCompleted: Boolean = false,
        val unambiguous: Boolean = false
    )
    data class Decision(val state: State, val next: Step? = null, val reason: String)

    private var shipment: Shipment? = null
    private var state = State.STOPPED
    private var pending: Step? = null
    private var actionAtMs: Long = 0
    private var previousScreen: Screen? = null
    private var stableCount = 0
    private var lastTimeMs = 0L
    private var userEnabled = false

    fun stop() {
        shipment = null
        state = State.STOPPED
        pending = null
        previousScreen = null
        stableCount = 0
        userEnabled = false
    }

    fun start(plan: Shipment, userApproved: Boolean, nowMs: Long): Decision {
        stop()
        if (!userApproved || plan.quantity <= 0 || plan.resource.isBlank() ||
            plan.kingdom.isBlank() || nowMs < 0) {
            return block("Expédition ou accord utilisateur invalide")
        }
        shipment = plan
        userEnabled = true
        state = State.WAITING_FOR_PORT
        lastTimeMs = nowMs
        return Decision(state, reason = "En attente d'une lecture fiable du Port")
    }

    /**
     * Advance only if the screen is stable for three independent observations.
     * A pending action requires an explicit acknowledgement before any next step.
     */
    fun observe(observation: Observation, nowMs: Long): Decision {
        if (!userEnabled || shipment == null) return Decision(state, reason = "Aucune expédition autorisée")
        if (nowMs < lastTimeMs || nowMs - lastTimeMs > 30_000L) return block("Observations expirées")
        lastTimeMs = nowMs
        if (!observation.unambiguous || observation.screen == Screen.UNKNOWN) return block("Écran inconnu ou ambigu")
        if (pending != null) {
            if (nowMs - actionAtMs > 5_000L) return block("Résultat du geste non confirmé")
            return Decision(state, reason = "Attente de confirmation du geste")
        }
        stableCount = if (observation.screen == previousScreen) stableCount + 1 else 1
        previousScreen = observation.screen
        if (stableCount < 3) return Decision(state, reason = "Lecture en cours : $stableCount/3")
        val plan = shipment!!
        val next = when (state) {
            State.WAITING_FOR_PORT ->
                if (observation.screen == Screen.CONTRACTS) Step.OPEN_PORT else return block("Contrats non visibles")
            State.WAITING_FOR_LOAD ->
                if (observation.screen == Screen.PORT) Step.CHOOSE_RESOURCE else return block("Port non confirmé")
            State.WAITING_FOR_QUANTITY ->
                if (observation.screen == Screen.SHIP_LOAD && observation.resource == plan.resource)
                    Step.SET_QUANTITY else return block("Ressource non confirmée")
            State.WAITING_FOR_DESTINATION ->
                if (observation.screen == Screen.SHIP_LOAD && observation.resource == plan.resource &&
                    observation.quantity == plan.quantity) Step.SELECT_DESTINATION
                else return block("Quantité non confirmée")
            State.WAITING_FOR_CONFIRMATION ->
                if (observation.screen == Screen.SHIP_CONFIRMATION &&
                    observation.resource == plan.resource &&
                    observation.quantity == plan.quantity &&
                    observation.kingdom == plan.kingdom) Step.CONFIRM_SEND
                else return block("Destinataire, ressource ou quantité incorrects")
            State.VERIFYING_RESULT -> {
                if (observation.sendCompleted && observation.screen == Screen.PORT) {
                    state = State.COMPLETED
                    userEnabled = false
                    return Decision(state, reason = "Résultat observé : ne pas répéter l'envoi")
                }
                return Decision(state, reason = "Résultat non confirmé : aucun nouvel envoi")
            }
            else -> return Decision(state, reason = "Expédition terminée ou bloquée")
        }
        pending = next
        actionAtMs = nowMs
        return Decision(state, next, "Geste proposé, à valider par un exécuteur calibré")
    }

    /**
     * Only the gesture executor may call this, after Android reports completion.
     * That acknowledgement is not proof the game accepted the action.
     */
    fun acknowledge(step: Step, androidCompleted: Boolean): Decision {
        if (pending != step || !androidCompleted) return block("Geste échoué ou inattendu")
        pending = null
        stableCount = 0
        previousScreen = null
        state = when (step) {
            Step.OPEN_PORT -> State.WAITING_FOR_LOAD
            Step.CHOOSE_RESOURCE -> State.WAITING_FOR_QUANTITY
            Step.SET_QUANTITY -> State.WAITING_FOR_DESTINATION
            Step.SELECT_DESTINATION -> State.WAITING_FOR_CONFIRMATION
            Step.CONFIRM_SEND -> State.VERIFYING_RESULT
        }
        return Decision(state, reason = "Android a exécuté le geste ; contrôler le jeu")
    }

    private fun block(reason: String): Decision {
        state = State.BLOCKED
        pending = null
        userEnabled = false
        return Decision(state, reason = reason)
    }
}
