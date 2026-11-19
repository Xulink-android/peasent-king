package fr.local.peasantassistant

/** Fail-closed lifecycle for accepting one contract and completing its return trip. */
class ShipmentCycleMachine {
    enum class Screen { CONTRACTS, WORLD_MAP, SHIP_SAILING, UNLOAD_CARGO, PAYMENT_IN_TRANSIT, RETURN_READY, SHIPYARD_READY, UNKNOWN }
    enum class State { STOPPED, WAITING_FOR_CONTRACT, VERIFYING_ACCEPTANCE, WAITING_FOR_DEPARTURE, WAITING_FOR_ARRIVAL, VERIFYING_UNLOAD, WAITING_FOR_PAYMENT, VERIFYING_RETURN, COMPLETED, BLOCKED }
    enum class Action { ACCEPT_CONTRACT, UNLOAD_CARGO, RETURN_SHIP }

    data class Shipment(val kingdom: String, val resource: String, val quantity: Int, val expectedReward: Int)
    data class Observation(
        val screen: Screen,
        val resource: String? = null,
        val quantity: Int? = null,
        val kingdom: String? = null,
        val stockAvailable: Int? = null,
        val shipCapacity: Int? = null,
        val reward: Int? = null,
        val contractVerified: Boolean = false,
        val contractAccepted: Boolean = false,
        val acceptButtonVisible: Boolean = false,
        val departureAccepted: Boolean = false,
        val arrivalConfirmed: Boolean = false,
        val cargoUnloaded: Boolean = false,
        val paymentSecured: Boolean = false,
        val shipReturnedReady: Boolean = false,
        val unambiguous: Boolean = false
    )
    data class Decision(val state: State, val action: Action? = null, val reason: String)

    private var shipment: Shipment? = null
    private var state = State.STOPPED
    private var userEnabled = false
    private var pending: Action? = null
    private var actionAtMs = 0L
    private var previousFingerprint: String? = null
    private var stableCount = 0
    private var lastTimeMs = 0L
    private var paymentConfirmedForShipment = false

    fun stop() {
        shipment = null
        state = State.STOPPED
        userEnabled = false
        pending = null
        previousFingerprint = null
        stableCount = 0
        paymentConfirmedForShipment = false
    }

    fun start(plan: Shipment, userApproved: Boolean, nowMs: Long): Decision {
        stop()
        if (!userApproved || plan.kingdom.isBlank() || plan.resource.isBlank() ||
            plan.quantity <= 0 || plan.expectedReward <= 0 || nowMs < 0) return block("Plan ou accord utilisateur invalide")
        shipment = plan
        userEnabled = true
        state = State.WAITING_FOR_CONTRACT
        lastTimeMs = nowMs
        return Decision(state, reason = "En attente d'un contrat disponible, du stock et du bouton Accepter")
    }

    fun observe(value: Observation, nowMs: Long): Decision {
        if (!userEnabled || shipment == null) return Decision(state, reason = "Cycle arrêté")
        if (nowMs < lastTimeMs || nowMs - lastTimeMs > 30_000L) return block("Observation expirée")
        lastTimeMs = nowMs
        if (!value.unambiguous || value.screen == Screen.UNKNOWN) return block("Écran inconnu ou ambigu")
        if (pending != null) {
            return if (nowMs - actionAtMs > 15_000L) block("Résultat du geste non confirmé")
            else Decision(state, reason = "Attente d'une nouvelle preuve après le geste")
        }
        val fingerprint = listOf(value.screen, value.resource, value.quantity, value.kingdom,
            value.stockAvailable, value.shipCapacity, value.reward, value.contractVerified,
            value.contractAccepted, value.acceptButtonVisible, value.departureAccepted,
            value.arrivalConfirmed, value.cargoUnloaded, value.paymentSecured,
            value.shipReturnedReady).joinToString("|")
        stableCount = if (fingerprint == previousFingerprint) stableCount + 1 else 1
        previousFingerprint = fingerprint
        if (stableCount < 3) return Decision(state, reason = "Stabilisation : $stableCount/3")
        val plan = shipment!!
        fun planMatches() = value.resource == plan.resource && value.quantity == plan.quantity &&
            value.kingdom == plan.kingdom && value.reward == plan.expectedReward
        val action = when (state) {
            State.WAITING_FOR_CONTRACT -> {
                if (value.screen != Screen.CONTRACTS || !value.contractVerified || value.contractAccepted ||
                    !value.acceptButtonVisible || !planMatches() || value.stockAvailable == null ||
                    value.stockAvailable < plan.quantity || value.shipCapacity == null ||
                    value.shipCapacity < plan.quantity)
                    return block("Contrat proposé, bouton, ressource, quantité, destination, stock ou capacité non confirmés")
                Action.ACCEPT_CONTRACT
            }
            State.VERIFYING_ACCEPTANCE -> {
                if (value.contractVerified && value.contractAccepted && planMatches()) {
                    state = if (value.screen == Screen.SHIP_SAILING && value.departureAccepted)
                        State.WAITING_FOR_ARRIVAL else State.WAITING_FOR_DEPARTURE
                    clearStability()
                    return Decision(state, reason = "Acceptation confirmée ; aucun second clic")
                }
                if (nowMs - actionAtMs > 25_000L) return block("Acceptation non confirmée ; aucun nouvel essai")
                return Decision(state, reason = "Vérification de l'acceptation ; aucun second clic")
            }
            State.WAITING_FOR_DEPARTURE -> {
                if (value.screen == Screen.SHIP_SAILING && value.contractAccepted &&
                    value.departureAccepted && planMatches()) {
                    state = State.WAITING_FOR_ARRIVAL
                    clearStability()
                    return Decision(state, reason = "Départ confirmé ; l'acceptation ne sera pas répétée")
                }
                return Decision(state, reason = "En attente du départ du navire")
            }
            State.WAITING_FOR_ARRIVAL -> {
                if (value.screen != Screen.UNLOAD_CARGO || !value.arrivalConfirmed || !planMatches())
                    return Decision(state, reason = "En attente de l'arrivée et de la cargaison confirmées")
                Action.UNLOAD_CARGO
            }
            State.VERIFYING_UNLOAD -> {
                if (value.cargoUnloaded && value.screen == Screen.PAYMENT_IN_TRANSIT) {
                    state = State.WAITING_FOR_PAYMENT
                    clearStability()
                    return Decision(state, reason = "Déchargement vérifié")
                }
                if (nowMs - actionAtMs > 25_000L) return block("Déchargement non confirmé ; aucun nouvel essai")
                return Decision(state, reason = "Vérification du déchargement ; aucun second glissement")
            }
            State.WAITING_FOR_PAYMENT -> {
                if (value.screen != Screen.RETURN_READY || value.kingdom != plan.kingdom ||
                    value.reward != plan.expectedReward || !value.paymentSecured)
                    return Decision(state, reason = "Paiement sécurisé et bouton de retour non confirmés")
                paymentConfirmedForShipment = true
                Action.RETURN_SHIP
            }
            State.VERIFYING_RETURN -> {
                if (value.screen == Screen.SHIPYARD_READY && value.shipReturnedReady && paymentConfirmedForShipment) {
                    state = State.COMPLETED
                    userEnabled = false
                    return Decision(state, reason = "Retour du navire et paiement sécurisé confirmés")
                }
                if (nowMs - actionAtMs > 25_000L) return block("Retour non confirmé ; aucun nouvel essai")
                return Decision(state, reason = "Vérification du retour ; aucun second clic")
            }
            else -> return Decision(state, reason = "Cycle terminé ou bloqué")
        }
        pending = action
        actionAtMs = nowMs
        return Decision(state, action, "Action proposée après trois observations cohérentes")
    }

    fun acknowledge(action: Action, androidCompleted: Boolean): Decision {
        if (pending != action || !androidCompleted) return block("Geste échoué ou inattendu")
        pending = null
        clearStability()
        actionAtMs = lastTimeMs
        state = when (action) {
            Action.ACCEPT_CONTRACT -> State.VERIFYING_ACCEPTANCE
            Action.UNLOAD_CARGO -> State.VERIFYING_UNLOAD
            Action.RETURN_SHIP -> State.VERIFYING_RETURN
        }
        return Decision(state, reason = "Geste Android terminé ; la suite attend une preuve OCR")
    }

    private fun clearStability() { previousFingerprint = null; stableCount = 0 }
    private fun block(reason: String): Decision {
        state = State.BLOCKED
        userEnabled = false
        pending = null
        return Decision(state, reason = reason)
    }
}
