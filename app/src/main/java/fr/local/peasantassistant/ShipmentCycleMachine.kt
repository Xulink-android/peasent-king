package fr.local.peasantassistant

/** Fail-closed lifecycle for one shipment, including arrival, unloading and payment collection. */
class ShipmentCycleMachine {
    enum class Screen { CONTRACTS, WORLD_MAP, SHIPYARD, SHIP_LOADING, SHIP_READY, SHIP_SAILING, UNLOAD_CARGO, PAYMENT_IN_TRANSIT, COLLECT_PAYMENT, UNKNOWN }
    enum class State { STOPPED, WAITING_FOR_CONTRACT, WAITING_FOR_LOAD, VERIFYING_LOAD, WAITING_FOR_READY, VERIFYING_DEPARTURE, WAITING_FOR_ARRIVAL, VERIFYING_UNLOAD, WAITING_FOR_PAYMENT, VERIFYING_PAYMENT, COMPLETED, BLOCKED }
    enum class Action { OPEN_PORT, LOAD_CARGO, SEND_SHIP, UNLOAD_CARGO, COLLECT_PAYMENT }

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
        val cargoLoaded: Boolean = false,
        val loadedQuantity: Int? = null,
        val departureAccepted: Boolean = false,
        val arrivalConfirmed: Boolean = false,
        val cargoUnloaded: Boolean = false,
        val paymentCollected: Boolean = false,
        val unambiguous: Boolean = false
    )
    data class Decision(val state: State, val action: Action? = null, val reason: String)

    private var shipment: Shipment? = null
    private var state = State.STOPPED
    private var userEnabled = false
    private var pending: Action? = null
    private var pendingLoadBaseline: Int? = null
    private var loadBaseline: Int? = null
    private var actionAtMs = 0L
    private var previousFingerprint: String? = null
    private var stableCount = 0
    private var lastTimeMs = 0L

    fun stop() {
        shipment = null; state = State.STOPPED; userEnabled = false; pending = null
        pendingLoadBaseline = null; loadBaseline = null
        previousFingerprint = null; stableCount = 0
    }

    fun start(plan: Shipment, userApproved: Boolean, nowMs: Long): Decision {
        stop()
        if (!userApproved || plan.kingdom.isBlank() || plan.resource.isBlank() ||
            plan.quantity <= 0 || plan.expectedReward <= 0 || nowMs < 0) return block("Plan ou accord utilisateur invalide")
        shipment = plan; userEnabled = true; state = State.WAITING_FOR_CONTRACT; lastTimeMs = nowMs
        return Decision(state, reason = "En attente du contrat et des stocks confirmés")
    }

    fun observe(value: Observation, nowMs: Long): Decision {
        if (!userEnabled || shipment == null) return Decision(state, reason = "Cycle arrêté")
        if (nowMs < lastTimeMs || nowMs - lastTimeMs > 30_000L) return block("Observation expirée")
        lastTimeMs = nowMs
        if (!value.unambiguous || value.screen == Screen.UNKNOWN) return block("Écran inconnu ou ambigu")
        if (pending != null) {
            return if (nowMs - actionAtMs > 5_000L) block("Résultat du geste non confirmé")
            else Decision(state, reason = "Attente d'une nouvelle preuve après le geste")
        }
        val fingerprint = listOf(value.screen, value.resource, value.quantity, value.kingdom,
            value.stockAvailable, value.shipCapacity, value.reward, value.contractVerified,
            value.cargoLoaded, value.loadedQuantity, value.departureAccepted, value.arrivalConfirmed,
            value.cargoUnloaded, value.paymentCollected).joinToString("|")
        stableCount = if (fingerprint == previousFingerprint) stableCount + 1 else 1
        previousFingerprint = fingerprint
        if (stableCount < 3) return Decision(state, reason = "Stabilisation : $stableCount/3")
        val plan = shipment!!
        fun cargoMatches() = value.resource == plan.resource && value.quantity == plan.quantity &&
            value.kingdom == plan.kingdom
        val action = when (state) {
            State.WAITING_FOR_CONTRACT -> {
                if (value.screen != Screen.CONTRACTS || !value.contractVerified || !cargoMatches() ||
                    value.reward != plan.expectedReward ||
                    value.stockAvailable == null || value.stockAvailable < plan.quantity ||
                    value.shipCapacity == null || value.shipCapacity < plan.quantity)
                    return block("Contrat, ressource, quantité, destination, stock ou capacité non confirmés")
                Action.OPEN_PORT
            }
            State.WAITING_FOR_LOAD -> {
                val loaded = value.loadedQuantity
                if (value.screen != Screen.SHIP_LOADING || !cargoMatches() || loaded == null ||
                    loaded !in 0 until plan.quantity ||
                    value.stockAvailable == null || value.stockAvailable < plan.quantity - loaded ||
                    value.shipCapacity == null || value.shipCapacity < plan.quantity)
                    return block("Chargement refusé : données ou stock incohérents")
                pendingLoadBaseline = loaded
                Action.LOAD_CARGO
            }
            State.WAITING_FOR_READY -> {
                if (value.screen != Screen.SHIP_READY || !cargoMatches() || !value.cargoLoaded ||
                    value.loadedQuantity != plan.quantity)
                    return block("Cargaison complète ou destination non confirmée")
                Action.SEND_SHIP
            }
            State.VERIFYING_LOAD -> {
                val baseline = loadBaseline ?: return block("Base de chargement absente")
                val loaded = value.loadedQuantity
                if (value.screen != Screen.SHIP_LOADING || !cargoMatches() || loaded == null ||
                    loaded <= baseline || loaded > plan.quantity)
                    return block("Aucune progression de chargement confirmée ; aucun nouvel essai")
                state = if (loaded == plan.quantity) State.WAITING_FOR_READY else State.WAITING_FOR_LOAD
                clearStability()
                return Decision(state, reason = if (loaded == plan.quantity)
                    "Quantité totale chargée confirmée" else "Chargement partiel confirmé ; la suite attend une nouvelle lecture")
            }
            State.VERIFYING_DEPARTURE -> {
                if (value.screen == Screen.SHIP_SAILING && value.departureAccepted) {
                    state = State.WAITING_FOR_ARRIVAL
                    clearStability()
                    return Decision(state, reason = "Départ observé ; aucun second envoi")
                }
                return Decision(state, reason = "Départ non confirmé ; aucun nouvel envoi")
            }
            State.WAITING_FOR_ARRIVAL -> {
                if (value.screen != Screen.UNLOAD_CARGO || !value.arrivalConfirmed || !cargoMatches())
                    return Decision(state, reason = "En attente de l'arrivée confirmée")
                Action.UNLOAD_CARGO
            }
            State.VERIFYING_UNLOAD -> {
                if (value.cargoUnloaded && value.screen in setOf(Screen.PAYMENT_IN_TRANSIT, Screen.COLLECT_PAYMENT)) {
                    state = State.WAITING_FOR_PAYMENT
                    clearStability()
                    return Decision(state, reason = "Déchargement vérifié")
                }
                return Decision(state, reason = "Déchargement non confirmé ; ne pas répéter")
            }
            State.WAITING_FOR_PAYMENT -> {
                if (value.screen != Screen.COLLECT_PAYMENT || value.kingdom != plan.kingdom ||
                    value.reward != plan.expectedReward || value.paymentCollected)
                    return Decision(state, reason = "Paiement non disponible ou non confirmé")
                Action.COLLECT_PAYMENT
            }
            State.VERIFYING_PAYMENT -> {
                if (value.paymentCollected && value.screen in setOf(Screen.WORLD_MAP, Screen.SHIPYARD, Screen.SHIP_READY)) {
                    state = State.COMPLETED; userEnabled = false
                    return Decision(state, reason = "Paiement collecté ; cycle terminé")
                }
                return Decision(state, reason = "Collecte non confirmée ; ne pas répéter")
            }
            else -> return Decision(state, reason = "Cycle terminé ou bloqué")
        }
        pending = action
        actionAtMs = nowMs
        return Decision(state, action, "Action proposée après trois observations cohérentes")
    }

    fun acknowledge(action: Action, androidCompleted: Boolean): Decision {
        if (pending != action || !androidCompleted) return block("Geste échoué ou inattendu")
        pending = null; clearStability()
        state = when (action) {
            Action.OPEN_PORT -> State.WAITING_FOR_LOAD
            Action.LOAD_CARGO -> {
                loadBaseline = pendingLoadBaseline
                pendingLoadBaseline = null
                State.VERIFYING_LOAD
            }
            Action.SEND_SHIP -> State.VERIFYING_DEPARTURE
            Action.UNLOAD_CARGO -> State.VERIFYING_UNLOAD
            Action.COLLECT_PAYMENT -> State.VERIFYING_PAYMENT
        }
        return Decision(state, reason = "Geste Android terminé ; la suite attend une preuve OCR")
    }

    private fun clearStability() { previousFingerprint = null; stableCount = 0 }
    private fun block(reason: String): Decision {
        state = State.BLOCKED; pending = null; userEnabled = false
        return Decision(state, reason = reason)
    }
}
