package fr.local.peasantassistant

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Screen inspector with one optional, user-initiated Port navigation gesture.
 * It cannot send cargo, accept a contract or edit game data.
 * Accessibility is enabled only through Android's normal consent UI.
 */
class GameAccessibilityService : AccessibilityService() {
    companion object {
        private const val GAME_PACKAGE = "com.peasanttoking.game"
    }

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val observationGate = ObservationGate()
    private val verifiedStockGate = VerifiedStockGate()
    private val badgeInventoryGate = BadgeInventoryGate()
    private val planningSession = ReadOnlyPlanningSession()
    private val automationCoordinator = AutomationCoordinator()
    private val portTargetGate = PortTargetGate()
    private val shipmentCycleMachine = ShipmentCycleMachine()
    private val shipmentCycleBridge = ShipmentCycleGestureBridge(shipmentCycleMachine)
    private var overlay: View? = null
    private var lastReport: String = ""
    private var currentPackage: String? = null
    private var capturing = false
    private var panelDismissed = false
    private var collapsed = false
    private var autoScan = false
    private var panelView: View? = null
    private var scanPending = false
    private var scanStatus: TextView? = null
    private var collectIconSamples = false
    private var portNavigationArmed = false
    private var portVerificationPending = false
    private var shipmentAutomationEnabled = false
    private var shipmentAutomationButton: Button? = null
    private var activeShipment: ShipmentCycleMachine.Shipment? = null
    private var activeContract: ObservationGate.Snapshot? = null
    private var cycleContinuityBound = false
    private var lastStableContracts: List<ObservationGate.Snapshot> = emptyList()
    private var lastContractContext = false
    private var lastGameScreen = GameScreenClassifier.Screen.UNKNOWN
    private val attemptedShipmentKeys = mutableSetOf<String>()
    private var shipmentStatusMessage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Never display the overlay outside the target game.
        if (isGameVisible() && !panelDismissed) showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val nextPackage = event.packageName?.toString() ?: return
        if (nextPackage == packageName) return // Ignore our own accessibility overlay.
        val wasGame = currentPackage == GAME_PACKAGE
        currentPackage = nextPackage
        if (nextPackage != GAME_PACKAGE) {
            stopShipmentAutomation("Automatisation arrêtée : jeu en arrière-plan.", stopScanning = false)
            autoScan = false
            scanPending = false
            collectIconSamples = false
            portNavigationArmed = false
            portVerificationPending = false
            portTargetGate.reset()
            observationGate.reset()
            verifiedStockGate.reset()
            badgeInventoryGate.reset()
            planningSession.reset()
            lastStableContracts = emptyList()
            lastContractContext = false
            lastGameScreen = GameScreenClassifier.Screen.UNKNOWN
            automationCoordinator.reset()
            ui.removeCallbacks(scanLoop)
            overlay?.visibility = View.GONE
        } else {
            if (!wasGame && panelDismissed) panelDismissed = false
            if (!panelDismissed) {
                if (overlay == null) ui.post { if (isGameVisible()) showPanel() }
                else overlay?.visibility = View.VISIBLE
            }
        }
    }

    override fun onInterrupt() {
        stopShipmentAutomation("Automatisation arrêtée : service interrompu.", stopScanning = true)
        portNavigationArmed = false
        portVerificationPending = false
        portTargetGate.reset()
        badgeInventoryGate.reset()
        verifiedStockGate.reset()
        planningSession.reset()
        observationGate.reset()
        lastStableContracts = emptyList()
        lastContractContext = false
        lastGameScreen = GameScreenClassifier.Screen.UNKNOWN
        automationCoordinator.reset()
        autoScan = false
        ui.removeCallbacks(scanLoop)
    }

    override fun onDestroy() {
        stopShipmentAutomation("Automatisation arrêtée : service fermé.", stopScanning = true)
        overlay?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) { }
        }
        overlay = null
        autoScan = false
        portNavigationArmed = false
        portVerificationPending = false
        portTargetGate.reset()
        observationGate.reset()
        verifiedStockGate.reset()
        badgeInventoryGate.reset()
        planningSession.reset()
        lastStableContracts = emptyList()
        lastContractContext = false
        lastGameScreen = GameScreenClassifier.Screen.UNKNOWN
        automationCoordinator.reset()
        ui.removeCallbacksAndMessages(null)
        recognizer.close()
        worker.shutdown()
        super.onDestroy()
    }

    private fun isGameVisible(): Boolean {
        val rootPackage = rootInActiveWindow?.packageName?.toString()
        return rootPackage == GAME_PACKAGE ||
            (rootPackage == null && currentPackage == GAME_PACKAGE)
    }

    private fun showPanel() {
        if (overlay != null) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xE5222930.toInt())
        }
        panelView = panel
        val status = TextView(this).apply {
            text = "Peasant King : prêt à analyser"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 8
        }
        scanStatus = status
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val analyze = Button(this).apply {
            text = "Analyser"
            setOnClickListener {
                if (capturing || scanPending) return@setOnClickListener
                if (!isGameVisible()) {
                    status.text = "Ouvre Peasant to King avant l'analyse."
                    return@setOnClickListener
                }
                status.text = "Analyse OCR en cours…"
                // Hide the overlay before taking a screenshot.
                collectIconSamples = false
                scanPending = true
                panel.visibility = View.INVISIBLE
                ui.postDelayed({
                    if (panelDismissed) return@postDelayed
                    inspect { message ->
                        scanPending = false
                        if (!panelDismissed) {
                            panel.visibility = View.VISIBLE
                            status.text = message.take(3000)
                        }
                    }
                }, 350)
            }
        }
        val copy = Button(this).apply {
            text = "Copier rapport"
            setOnClickListener {
                if (lastReport.isBlank()) {
                    status.text = "Analyse d'abord un écran."
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Peasant King OCR", lastReport))
                    status.text = "Rapport OCR copié. Colle-le dans ChatGPT."
                }
            }
        }
        val close = Button(this).apply {
            text = "X Masquer"
            setOnClickListener {
                panelDismissed = true
                stopShipmentAutomation("Automatisation arrêtée : panneau fermé.", stopScanning = true)
                portNavigationArmed = false
                portVerificationPending = false
                portTargetGate.reset()
                autoScan = false
                scanPending = false
                ui.removeCallbacksAndMessages(null)
                capturing = false
                this@GameAccessibilityService.overlay?.let { view ->
                    try { manager.removeView(view) } catch (_: Exception) { }
                }
                this@GameAccessibilityService.overlay = null
                panelView = null
                scanStatus = null
                observationGate.reset()
                verifiedStockGate.reset()
                badgeInventoryGate.reset()
                planningSession.reset()
        lastStableContracts = emptyList()
        lastContractContext = false
        lastGameScreen = GameScreenClassifier.Screen.UNKNOWN
                automationCoordinator.reset()
                // Hide the panel without revoking the Android authorization.
            }
        }
        val collapse = Button(this).apply {
            text = "Réduire"
            setOnClickListener {
                collapsed = !collapsed
                buttons.visibility = if (collapsed) View.GONE else View.VISIBLE
                text = if (collapsed) "Agrandir" else "Réduire"
            }
        }
        val repeat = Button(this).apply {
            text = "Suivi OFF"
            setOnClickListener {
                autoScan = !autoScan
                if (!autoScan) {
                    stopShipmentAutomation("Automatisation arrêtée avec le suivi.", stopScanning = false)
                    portNavigationArmed = false
                    portTargetGate.reset()
                    badgeInventoryGate.reset()
                    verifiedStockGate.reset()
                    planningSession.reset()
                    ui.removeCallbacks(scanLoop)
                    observationGate.reset()
                    automationCoordinator.reset()
                }
                text = if (autoScan) "Suivi ON" else "Suivi OFF"
                status.text = if (autoScan) "Suivi OCR toutes les 5 secondes." else "Suivi arrêté."
                if (autoScan) ui.post(scanLoop)
            }
        }
        val icons = Button(this).apply {
            text = "Capturer icônes"
            setOnClickListener {
                if (capturing || scanPending || !isGameVisible()) {
                    status.text = "Ouvre le jeu et attends la fin de l'analyse."
                    return@setOnClickListener
                }
                collectIconSamples = true
                scanPending = true
                panel.visibility = View.INVISIBLE
                ui.postDelayed({
                    if (panelDismissed) return@postDelayed
                    inspect { message ->
                        scanPending = false
                        if (!panelDismissed) {
                            panel.visibility = View.VISIBLE
                            status.text = message.take(3000)
                        }
                    }
                }, 350)
            }
        }
        val shipmentToggle = Button(this).apply {
            text = if (shipmentAutomationEnabled) "Arrêter l'automatisation" else "Démarrer l'automatisation"
            setOnClickListener {
                if (shipmentAutomationEnabled) {
                    stopShipmentAutomation("Automatisation arrêtée par l'utilisateur.", stopScanning = true)
                } else {
                    startShipmentAutomation()
                }
            }
        }
        shipmentAutomationButton = shipmentToggle
        val openPort = Button(this).apply {
            text = "Auto Port (1 fois)"
            setOnClickListener {
                if (!isGameVisible()) {
                    status.text = "Ouvre le jeu pour autoriser la navigation vers Port."
                    return@setOnClickListener
                }
                portNavigationArmed = !portNavigationArmed
                portTargetGate.reset()
                if (portNavigationArmed) {
                    autoScan = true
                    repeat.text = "Suivi ON"
                    ui.removeCallbacks(scanLoop)
                    ui.post(scanLoop)
                    status.text = "Navigation Port armée : trois reconnaissances OCR identiques requises. Un seul geste au maximum."
                } else {
                    status.text = "Navigation Port annulée."
                }
                text = if (portNavigationArmed) "Annuler Auto Port" else "Auto Port (1 fois)"
            }
        }
        buttons.addView(analyze)
        buttons.addView(icons)
        buttons.addView(copy)
        buttons.addView(repeat)
        panel.addView(openPort)
        panel.addView(shipmentToggle)
        panel.addView(collapse)
        panel.addView(close)
        panel.addView(buttons)
        panel.addView(ScrollView(this).apply {
            addView(status)
            layoutParams = LinearLayout.LayoutParams(630, 220)
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 6
            y = 100
        }
        panel.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (startX + event.rawX - touchX).toInt().coerceAtLeast(0)
                        params.y = (startY + event.rawY - touchY).toInt().coerceAtLeast(0)
                        try { manager.updateViewLayout(panel, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
        try {
            manager.addView(panel, params)
            overlay = panel
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun startShipmentAutomation() {
        if (!isGameVisible() || currentPackage != GAME_PACKAGE) {
            scanStatus?.text = "Ouvre Peasant to King avant de démarrer."
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (!lastContractContext || lastGameScreen != GameScreenClassifier.Screen.WORLD_MAP) {
            scanStatus?.text = "Affiche l'offre sur la carte et vérifie les stocks du port avant de démarrer."
            return
        }
        val proposal = planningSession.proposal(now, lastStableContracts)
        if (proposal == null) {
            scanStatus?.text = "Pas d'offre unique confirmée avec récompense, ressource et stock suffisants."
            return
        }
        val contract = lastStableContracts.singleOrNull {
            it.accepted == false && it.kingdom == proposal.kingdom &&
                it.resource == proposal.resource && it.amount == proposal.quantity &&
                it.reward == proposal.expectedReward
        }
        val reward = proposal?.expectedReward
        if (contract == null || reward == null || reward <= 0) {
            scanStatus?.text = "Pas d'offre unique confirmée avec récompense, ressource et stock suffisants."
            return
        }
        val key = listOf(contract.kingdom, contract.resource, contract.amount, contract.reward).joinToString("|")
        if (key in attemptedShipmentKeys) {
            scanStatus?.text = "Cette offre a déjà été tentée pendant cette session ; aucun second clic."
            return
        }
        val plan = ShipmentCycleMachine.Shipment(proposal.kingdom, proposal.resource, proposal.quantity, reward)
        val started = shipmentCycleBridge.start(plan, userApproved = true, nowMs = now)
        if (started.state == ShipmentCycleMachine.State.BLOCKED) {
            scanStatus?.text = started.reason
            return
        }
        attemptedShipmentKeys += key
        activeShipment = plan
        activeContract = contract
        cycleContinuityBound = false
        portNavigationArmed = false
        portTargetGate.reset()
        shipmentAutomationEnabled = true
        shipmentStatusMessage = "Automatisation active : vérification de l'offre."
        shipmentAutomationButton?.text = "Arrêter l'automatisation"
        autoScan = true
        ui.removeCallbacks(scanLoop)
        ui.post(scanLoop)
        scanStatus?.text = "Automatisation démarrée : vérification de l'offre, du stock et du bouton Accepter."
    }

    private fun stopShipmentAutomation(reason: String, stopScanning: Boolean) {
        shipmentAutomationEnabled = false
        shipmentStatusMessage = reason
        shipmentCycleBridge.stop()
        activeShipment = null
        activeContract = null
        cycleContinuityBound = false
        shipmentAutomationButton?.text = "Démarrer l'automatisation"
        if (stopScanning) {
            autoScan = false
            scanPending = false
            ui.removeCallbacks(scanLoop)
        }
        scanStatus?.text = reason
    }

    private fun machineScreen(
        contractContext: Boolean,
        screen: GameScreenClassifier.Screen
    ): ShipmentCycleMachine.Screen {
        when (screen) {
            GameScreenClassifier.Screen.SHIP_SAILING -> return ShipmentCycleMachine.Screen.SHIP_SAILING
            GameScreenClassifier.Screen.UNLOAD_CARGO -> return ShipmentCycleMachine.Screen.UNLOAD_CARGO
            GameScreenClassifier.Screen.PAYMENT_IN_TRANSIT -> return ShipmentCycleMachine.Screen.PAYMENT_IN_TRANSIT
            GameScreenClassifier.Screen.RETURN_READY -> return ShipmentCycleMachine.Screen.RETURN_READY
            GameScreenClassifier.Screen.SHIPYARD_READY -> return ShipmentCycleMachine.Screen.SHIPYARD_READY
            else -> Unit
        }
        if (contractContext) return ShipmentCycleMachine.Screen.CONTRACTS
        return when (screen) {
            GameScreenClassifier.Screen.WORLD_MAP -> ShipmentCycleMachine.Screen.WORLD_MAP
            else -> ShipmentCycleMachine.Screen.UNKNOWN
        }
    }

    private fun advanceShipmentAutomation(
        context: String,
        gameScreen: GameScreenClassifier.Result,
        evidence: ShipmentScreenEvidenceParser.Evidence,
        stableContracts: List<ObservationGate.Snapshot>,
        iconMatches: List<Triple<String, String, ContractIconLinker.Candidate>>,
        stock: VerifiedStockGate.Verified?,
        observedAtMs: Long,
        width: Int,
        height: Int,
        boxes: List<ObservedGameTargetResolver.Box>
    ) {
        val plan = activeShipment ?: return
        if (!shipmentAutomationEnabled) return
        val contractContext = context == "Écran de contrats probable"
        val contractsNow = if (contractContext) stableContracts else lastStableContracts
        val verifiedPort = planningSession.verifiedPort(observedAtMs, contractsNow)
        val matchingContract = stableContracts.singleOrNull {
            it.kingdom == plan.kingdom && it.resource == plan.resource &&
                it.amount == plan.quantity && it.reward == plan.expectedReward
        }
        if (matchingContract?.accepted == true) {
            activeContract = matchingContract
            cycleContinuityBound = true
        }
        val contract = matchingContract ?: activeContract
        val iconForPlan = iconMatches.filter { (line, label) ->
            line.trim().matches(Regex("(?:ENVOYER|SEND)\\s+${plan.quantity}(?:\\s|$).*", RegexOption.IGNORE_CASE)) &&
                label == plan.resource
        }.map { it.second }.distinct().singleOrNull()
        val observedResource = when {
            contractContext -> matchingContract?.resource
            gameScreen.screen == GameScreenClassifier.Screen.SHIP_SAILING -> iconForPlan
            cycleContinuityBound -> plan.resource
            else -> null
        }
        val portSupply = verifiedPort?.supplies?.singleOrNull { it.resource == plan.resource }
        val stockAvailable = if (contractContext) portSupply?.let { it.availableAtPort - it.reserved }
            else stock?.supplies?.singleOrNull { it.resource == plan.resource }?.let { it.availableAtPort - it.reserved }
        val shipCapacity = if (contractContext) verifiedPort?.capacity else stock?.capacity
            ?: verifiedPort?.capacity
        val acceptEvidence = ObservedGameTargetResolver.ShipmentEvidence(
            kingdom = plan.kingdom,
            resource = plan.resource,
            quantity = plan.quantity,
            confirmedAvailable = stockAvailable,
            shipCapacity = shipCapacity,
            observedResource = observedResource,
            unambiguous = evidence.warnings.isEmpty(),
            expectedReward = plan.expectedReward,
            loadedQuantity = evidence.loadedQuantity
        )
        val acceptTarget = if (contractContext && gameScreen.screen == GameScreenClassifier.Screen.WORLD_MAP)
            ObservedGameTargetResolver.acceptContractTap(gameScreen.screen, boxes, width, height, acceptEvidence)
        else null
        val cycleScreen = machineScreen(contractContext, gameScreen.screen)
        val observation = ShipmentCycleOcrMapper.map(ShipmentCycleOcrMapper.Input(
            screen = cycleScreen,
            plan = plan,
            contract = contract,
            observedResource = observedResource,
            acceptButtonVisible = acceptTarget != null,
            screenEvidence = evidence,
            stockAvailable = stockAvailable,
            shipCapacity = shipCapacity,
            continuityBound = cycleContinuityBound
        ))
        val frameEvidence = ObservedGameTargetResolver.ShipmentEvidence(
            kingdom = plan.kingdom,
            resource = plan.resource,
            quantity = plan.quantity,
            confirmedAvailable = stockAvailable,
            shipCapacity = shipCapacity,
            observedResource = observedResource ?: plan.resource.takeIf { cycleContinuityBound },
            unambiguous = evidence.warnings.isEmpty() && plan.expectedReward > 0,
            expectedReward = plan.expectedReward,
            loadedQuantity = evidence.loadedQuantity
        )
        val frame = ShipmentCycleGestureBridge.Frame(
            screen = gameScreen.screen,
            boxes = boxes,
            width = width,
            height = height,
            evidence = frameEvidence,
            available = stockAvailable
        )
        val result = shipmentCycleBridge.observe(observation, frame, observedAtMs)
        if (result.decision.state == ShipmentCycleMachine.State.BLOCKED) {
            stopShipmentAutomation("Automatisation arrêtée : ${result.decision.reason}", stopScanning = false)
            return
        }
        if (result.decision.state == ShipmentCycleMachine.State.COMPLETED) {
            shipmentAutomationEnabled = false
            activeShipment = null
            activeContract = null
            shipmentAutomationButton?.text = "Démarrer l'automatisation"
            shipmentStatusMessage = "Expédition vérifiée. Actualise les stocks et sélectionne une nouvelle offre pour le cycle suivant."
            return
        }
        val action = result.decision.action
        if (action == null) {
            shipmentStatusMessage = result.decision.reason
            return
        }
        val dispatched = shipmentCycleBridge.dispatch(
            result = result,
            dispatcher = AccessibilityGestureDispatcher(this),
            userEnabled = shipmentAutomationEnabled,
            gameForeground = isGameVisible() && currentPackage == GAME_PACKAGE,
            observedAtMs = observedAtMs,
            width = width,
            height = height
        ) { decision ->
            ui.post {
                if (decision.state == ShipmentCycleMachine.State.BLOCKED) {
                    stopShipmentAutomation("Automatisation arrêtée : ${decision.reason}", stopScanning = false)
                } else {
                    shipmentStatusMessage = when (action) {
                        ShipmentCycleMachine.Action.ACCEPT_CONTRACT -> "Contrat accepté par le geste Android ; vérification OCR avant la suite."
                        ShipmentCycleMachine.Action.UNLOAD_CARGO -> "Déchargement demandé ; vérification OCR avant la suite."
                        ShipmentCycleMachine.Action.RETURN_SHIP -> "Retour demandé ; vérification du navire et du paiement."
                    }
                    scanStatus?.text = shipmentStatusMessage
                }
            }
        }
        if (dispatched) {
            shipmentStatusMessage = "Geste ${action.name} transmis ; aucune action suivante avant vérification OCR."
            scanStatus?.text = shipmentStatusMessage
        }
    }

    private val scanLoop = object : Runnable {
        override fun run() {
            if (!autoScan || panelDismissed) return
            if (shipmentAutomationEnabled && (!isGameVisible() || currentPackage != GAME_PACKAGE)) {
                stopShipmentAutomation("Automatisation arrêtée : jeu non actif au premier plan.", stopScanning = true)
                return
            }
            if (!capturing && !scanPending && isGameVisible()) {
                collectIconSamples = false
                scanPending = true
                panelView?.visibility = View.INVISIBLE
                ui.postDelayed({
                    if (!autoScan || panelDismissed || !isGameVisible()) {
                        scanPending = false
                        if (!panelDismissed) panelView?.visibility = View.VISIBLE
                        return@postDelayed
                    }
                    inspect { message ->
                        scanPending = false
                        if (!panelDismissed) {
                            panelView?.visibility = View.VISIBLE
                            scanStatus?.text = if (shipmentAutomationEnabled)
                                (message + "\n" + shipmentStatusMessage).take(3000) else message.take(3000)
                        }
                    }
                }, 350)
            }
            ui.postDelayed(this, 5000)
        }
    }

    private fun inspect(finish: (String) -> Unit) {
        if (capturing) {
            finish("Une analyse est déjà en cours.")
            return
        }
        if (!isGameVisible()) {
            if (shipmentAutomationEnabled) stopShipmentAutomation("Automatisation arrêtée : le jeu n'est plus au premier plan.", stopScanning = true)
            finish("Capture refusée : Peasant to King n'est pas au premier plan.")
            return
        }
        capturing = true
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onFailure(errorCode: Int) {
                ui.post {
                    if (shipmentAutomationEnabled) stopShipmentAutomation("Automatisation arrêtée : capture écran impossible.", stopScanning = true)
                    capturing = false
                    finish("Capture impossible (code $errorCode).")
                }
            }

            override fun onSuccess(result: ScreenshotResult) {
                if (!isGameVisible()) {
                    result.hardwareBuffer.close()
                    ui.post {
                        if (shipmentAutomationEnabled) stopShipmentAutomation("Automatisation arrêtée : jeu quitté pendant la capture.", stopScanning = true)
                        capturing = false
                        finish("Capture ignorée : le jeu n'est plus visible.")
                    }
                    return
                }
                val wrapped = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                if (bitmap == null) {
                    ui.post {
                        if (shipmentAutomationEnabled) stopShipmentAutomation("Automatisation arrêtée : image de capture indisponible.", stopScanning = true)
                        capturing = false
                        finish("Capture indisponible.")
                    }
                    return
                }
                val width = bitmap.width
                val height = bitmap.height
                val shouldCollect = collectIconSamples
                collectIconSamples = false
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { detected ->
                        if (!isGameVisible()) {
                            stopShipmentAutomation("Automatisation arrêtée : jeu quitté pendant l'OCR.", stopScanning = true)
                            observationGate.reset()
                            verifiedStockGate.reset()
                            planningSession.reset()
                            automationCoordinator.reset()
                            ui.post {
                                capturing = false
                                finish("Analyse annulée : changement d'application.")
                            }
                            return@addOnSuccessListener
                        }
                        val lines = detected.textBlocks.flatMap { it.lines }
                            .mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                val text = line.text.trim().replace("\n", " ")
                                if (text.isBlank()) return@mapNotNull null
                                val x = (100.0 * rect.exactCenterX() / width).toInt()
                                val y = (100.0 * rect.exactCenterY() / height).toInt()
                                "$x%,$y% : $text"
                            }
                        val parsedFromText = ContractParser.parse(
                            detected.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                ContractParser.Line(
                                    line.text.trim(),
                                    (100.0 * rect.exactCenterX() / width).toInt(),
                                    (100.0 * rect.exactCenterY() / height).toInt()
                                )
                            }
                        )
                        val rawOcrLines = detected.textBlocks.flatMap { it.lines }
                        val gameScreen = GameScreenClassifier.classify(rawOcrLines.map { it.text.trim() })
                        val savedIcons = if (shouldCollect) {
                            val matches = detected.textBlocks.flatMap { it.lines }
                                .filter { it.text.contains(Regex("ENVOYER|SEND", RegexOption.IGNORE_CASE)) }
                                .take(10)
                            matches.mapIndexed { index, line ->
                                val rect = line.boundingBox
                                if (rect == null) false else {
                                    val crop = IconSamples.cropRightOfText(bitmap, rect)
                                    if (crop == null) false else {
                                        val saved = IconSamples.saveCandidate(
                                            java.io.File(filesDir, "icon_candidates"), crop, index
                                        )
                                        crop.recycle()
                                        saved
                                    }
                                }
                            }.count { it }
                        } else 0
                        val iconMatches = detected.textBlocks.flatMap { it.lines }
                            .filter { it.text.contains(Regex("ENVOYER|SEND", RegexOption.IGNORE_CASE)) }
                            .mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                val crop = IconSamples.cropRightOfText(bitmap, rect)
                                    ?: return@mapNotNull null
                                try {
                                    val label = IconLibrary.recognize(this@GameAccessibilityService, crop)
                                    Triple(
                                        line.text.trim(),
                                        label ?: "non reconnu",
                                        ContractIconLinker.Candidate(
                                            (100.0 * rect.exactCenterX() / width).toInt(),
                                            (100.0 * rect.exactCenterY() / height).toInt(),
                                            label ?: "non reconnu"
                                        )
                                    )
                                } finally {
                                    crop.recycle()
                                }
                            }
                        val stockCountPattern = Regex("""^\s*[0-9][0-9 .]*\s*/\s*[0-9][0-9 .]*\s*$""")
                        val stockBadgeDetails = if (parsedFromText.context == "Écran de stocks probable" ||
                            gameScreen.screen == GameScreenClassifier.Screen.SHIP_LOADING) {
                            rawOcrLines.filter { stockCountPattern.matches(it.text) }
                                .map { line ->
                                    val resource = line.boundingBox?.let { rect ->
                                        val crop = IconSamples.cropResourceBadgeRightOfText(bitmap, rect)
                                        if (crop == null) null else try {
                                            IconLibrary.recognize(this@GameAccessibilityService, crop)
                                        } finally {
                                            crop.recycle()
                                        }
                                    }
                                    Triple(line, resource, line.boundingBox)
                                }
                        } else emptyList()
                        val stockBadgeLines = stockBadgeDetails.map { (line, resource) ->
                            ShipyardBadgeStockParser.OcrLine(line.text.trim(), resource)
                        }
                        val badgeStock = ShipyardBadgeStockParser.parse(stockBadgeLines)
                        val savedStockIcons = if (shouldCollect && stockBadgeLines.isNotEmpty()) {
                            detected.textBlocks.flatMap { it.lines }
                                .filter { stockCountPattern.matches(it.text) }
                                .take((10 - savedIcons).coerceAtLeast(0))
                                .mapIndexed { index, line ->
                                    val crop = line.boundingBox?.let { rect ->
                                        IconSamples.cropResourceBadgeRightOfText(bitmap, rect)
                                    }
                                    if (crop == null) false else try {
                                        IconSamples.saveCandidate(
                                            java.io.File(filesDir, "icon_candidates"), crop,
                                            savedIcons + index
                                        )
                                    } finally {
                                        crop.recycle()
                                    }
                                }.count { it }
                        } else 0
                        val pixelBoxes = detected.textBlocks.flatMap { it.lines }
                            .mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                SafePortNavigator.TextBox(line.text.trim(), rect.left, rect.top,
                                    rect.right, rect.bottom)
                            }
                        val portTarget = SafePortNavigator.detect(pixelBoxes, width, height)
                        val stablePortTarget = if (portNavigationArmed) {
                            portTargetGate.observe(portTarget)
                        } else null
                        val observedAtMs = android.os.SystemClock.elapsedRealtime()
                        val verificationWasPending = portVerificationPending
                        if (verificationWasPending) portVerificationPending = false
                        val parsed = ContractIconLinker.annotate(
                            parsedFromText, iconMatches.map { it.third }
                        )
                        val shipmentEvidence = ShipmentScreenEvidenceParser.parse(
                            detected.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                        )
                        val stockObservation = StockScreenParser.parse(
                            detected.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                        )
                        val verifiedStock = verifiedStockGate.observe(
                            parsed.context == "Écran de stocks probable", stockObservation
                        )
                        val badgeVerifiedStock = badgeInventoryGate.observe(
                            selectedShipScreen = gameScreen.screen == GameScreenClassifier.Screen.SHIP_LOADING,
                            badges = badgeStock,
                            shipCapacity = shipmentEvidence.shipCapacity
                        )
                        val trustedStock = verifiedStock ?: badgeVerifiedStock
                        val verdict = observationGate.observe(parsed)
                        val observationTime = android.os.SystemClock.elapsedRealtime()
                        if (parsed.context == "Écran de contrats probable") {
                            planningSession.observeContracts(verdict.stable, observationTime)
                            lastStableContracts = verdict.stable
                            lastContractContext = verdict.stable.isNotEmpty()
                        } else {
                            lastContractContext = false
                        }
                        lastGameScreen = gameScreen.screen
                        if (parsed.context == "Écran de stocks probable" ||
                            gameScreen.screen == GameScreenClassifier.Screen.SHIP_LOADING) {
                            planningSession.observePort(trustedStock, observationTime)
                        }
                        if (parsed.context == "Écran non identifié" && gameScreen.screen == GameScreenClassifier.Screen.UNKNOWN) {
                            planningSession.reset()
                            observationGate.reset()
                            verifiedStockGate.reset()
                            badgeInventoryGate.reset()
                            lastStableContracts = emptyList()
                            if (shipmentAutomationEnabled) {
                                stopShipmentAutomation("Automatisation arrêtée : écran non identifié.", stopScanning = false)
                            }
                        }
                        val suggestedShipment = planningSession.proposal(
                            observationTime,
                            if (parsed.context == "Écran de contrats probable") verdict.stable else emptyList()
                        )
                        if (shipmentAutomationEnabled) {
                            advanceShipmentAutomation(
                                context = parsed.context,
                                gameScreen = gameScreen,
                                evidence = shipmentEvidence,
                                stableContracts = verdict.stable,
                                iconMatches = iconMatches,
                                stock = trustedStock,
                                observedAtMs = observationTime,
                                width = width,
                                height = height,
                                boxes = pixelBoxes.map { ObservedGameTargetResolver.Box(
                                    it.text, it.left, it.top, it.right, it.bottom
                                ) }
                            )
                        }
                        val decision = automationCoordinator.evaluate(
                            AutomationCoordinator.Snapshot(
                                if (parsed.context == "Écran de contrats probable") "contracts" else "unknown",
                                verdict.stable
                            )
                        )
                        lastReport = buildString {
                            appendLine("Peasant King — inspection OCR et état d'automatisation")
                            appendLine("Écran : $width x $height")
                            appendLine("Textes détectés : ${lines.size}")
                            lines.forEach { appendLine(it) }
                            appendLine("PHASE OBSERVÉE : " + gameScreen.screen)
                            appendLine("INDICES : " + gameScreen.evidence.joinToString(" ; "))
                            appendLine("FAITS NAVIRE : cargaison restante=${shipmentEvidence.remainingCargo ?: "inconnue"} ; capacité=${shipmentEvidence.shipCapacity ?: "inconnue"} ; récompense=${shipmentEvidence.reward ?: "inconnue"} ; paiement sécurisé=${shipmentEvidence.paymentSecuredAmount ?: "inconnu"} ; destination=${shipmentEvidence.kingdom ?: "inconnue"}")
                            shipmentEvidence.warnings.forEach { appendLine("OCR navire : " + it) }
                            appendLine("Automatisation d'expédition : " + if (shipmentAutomationEnabled) shipmentStatusMessage else "arrêtée ; démarrage explicite requis")
                            appendLine("SIMULATION / INTERPRÉTATION")
                            appendLine(parsed.context)
                            appendLine("VÉRIFICATION : " + verdict.message)
                            appendLine("Références d'icônes issues de l'APK : correspondances indicatives, NON validées sur l'écran réel.")
                            iconMatches.forEach { (contract, label, _) ->
                                appendLine("ICÔNE CANDIDATE : $contract -> $label (diagnostic non validé)")
                            }
                            appendLine("AUTOMATISATION : " + decision.phase + " — " + decision.explanation)
                            appendLine("PLAN MULTI-ÉCRANS : " + (suggestedShipment?.let {
                                "${it.kingdom} / ${it.resource} / ${it.quantity} / récompense ${it.expectedReward ?: "inconnue"}"
                            } ?: "attente de contrats et stocks confirmés, récents"))
                            appendLine("LECTURE PORT (expérimentale, sans action) :")
                            appendLine(if (trustedStock == null) {
                                "Stock non confirmé : aucune proposition d'expédition"
                            } else {
                                "Stock OCR confirmé sur trois lectures (piles de l'entrepôt ; réservations non garanties ; diagnostic uniquement)"
                            })
                            stockObservation.supplies.forEach {
                                appendLine("Stock: ${it.resource} = ${it.availableAtPort}, réservé = ${it.reserved}")
                            }
                            appendLine("Capacité : ${stockObservation.shipCapacity ?: "inconnue"} ; navires : ${stockObservation.availableShips ?: "inconnus"}")
                            stockObservation.warnings.forEach { appendLine("Port : " + it) }
                            badgeStock.stacks.forEach {
                                appendLine("Badge stock candidat : ${it.amount}/${it.stackCapacity} -> ${it.resource ?: "non reconnu"}")
                            }
                            badgeStock.warnings.forEach { appendLine("Badge stock : " + it) }
                            if (badgeStock.stacks.isNotEmpty()) appendLine("Ces compteurs décrivent les piles visibles ; une offre ne peut passer qu'avec une lecture stable, une seule offre non signée et un stock/capacité suffisants.")
                            if (shouldCollect) appendLine("Candidats icônes enregistrés localement : ${savedIcons + savedStockIcons}")
                            if (shouldCollect) appendLine("Images non étiquetées : identification non garantie.")
                            parsed.contracts.forEach { contract ->
                                appendLine("Contrat : royaume=" + (contract.kingdom ?: "inconnu") +
                                    ", quantité=" + contract.amount + ", ressource=" + (contract.resource ?: "inconnue") +
                                    ", statut=" + when (contract.accepted) {
                                        true -> "signé (OCR)"
                                        false -> "à accepter (OCR)"
                                        null -> "inconnu"
                                    })
                            }
                            parsed.warnings.forEach { appendLine("Attention : " + it) }
                            appendLine("Navigation Port : " + when {
                                verificationWasPending && portTarget != null ->
                                    "Le bouton Port est toujours visible : navigation non confirmée"
                                verificationWasPending ->
                                    "Bouton Port non retrouvé ; transition possible, à confirmer dans le jeu"
                                portNavigationArmed && stablePortTarget != null ->
                                    "Cible stable, geste unique en cours de validation"
                                portNavigationArmed -> "Attente de trois positions OCR identiques"
                                else -> "désactivée par défaut"
                            })
                            appendLine("Geste de contrat uniquement après activation explicite et vérifications concordantes. Aucun chargement manuel : le PNJ du jeu est censé charger le bateau.")
                        }
                        try {
                            openFileOutput("last_ocr_report.txt", Context.MODE_PRIVATE).use {
                                it.write(lastReport.toByteArray(Charsets.UTF_8))
                            }
                        } catch (_: Exception) { /* Clipboard remains available */ }
                        ui.post {
                            if (stablePortTarget != null && portNavigationArmed) {
                                navigateToPortOnce(stablePortTarget, observedAtMs)
                            }
                            capturing = false
                            finish(if (shouldCollect) "Icônes candidates : $savedIcons. Rapport disponible." else "Analyse : ${lines.size} textes, ${parsed.contracts.size} contrats probables. Copier rapport.")
                        }
                    }
                    .addOnFailureListener { error ->
                        ui.post {
                            if (shipmentAutomationEnabled) stopShipmentAutomation("Automatisation arrêtée : OCR incertain.", stopScanning = true)
                            capturing = false
                            finish("Échec OCR : ${error.message}")
                        }
                    }
                    .addOnCompleteListener { bitmap.recycle() }
            }
        })
    }

    private fun navigateToPortOnce(target: SafePortNavigator.Target, observedAtMs: Long) {
        if (!portNavigationArmed) return
        val age = android.os.SystemClock.elapsedRealtime() - observedAtMs
        val allowed = GestureSafetyGate.allowed(GestureSafetyGate.Request(
            userEnabled = portNavigationArmed,
            gameForeground = isGameVisible() && currentPackage == GAME_PACKAGE,
            screenConfirmed = true,
            targetConfirmed = true,
            observationAgeMs = age,
            screenWidth = target.width,
            screenHeight = target.height,
            x = target.x, y = target.y
        ))
        // One-shot: disarm even when Android rejects or cancels the gesture.
        portNavigationArmed = false
        portTargetGate.reset()
        autoScan = false
        ui.removeCallbacks(scanLoop)
        if (!allowed) {
            scanStatus?.text = "Navigation annulée : écran, cible ou lecture non fiable."
            return
        }

        val gestureTarget = ObservedGameTargetResolver.Gesture(
            ObservedGameTargetResolver.Point(target.x, target.y),
            ObservedGameTargetResolver.Point(target.x, target.y),
            isDrag = false
        )
        val dispatched = AccessibilityGestureDispatcher(this).dispatch(
            target = gestureTarget,
            userEnabled = true,
            gameForeground = isGameVisible() && currentPackage == GAME_PACKAGE,
            screenConfirmed = true,
            targetConfirmed = true,
            observedAtMs = observedAtMs,
            width = target.width,
            height = target.height
        ) { completed ->
            ui.post {
                if (completed) {
                    scanStatus?.text = "Geste Port exécuté. Vérification OCR de l'écran suivant."
                    verifyPortOnce()
                } else {
                    scanStatus?.text = "Geste Port annulé par Android. Aucun nouvel essai."
                }
            }
        }
        if (!dispatched) {
            scanStatus?.text = "Android a refusé la navigation. Aucun nouvel essai."
        }
    }

    private fun verifyPortOnce() {
        if (!isGameVisible() || panelDismissed || capturing || scanPending) return
        portVerificationPending = true
        ui.postDelayed({
            if (!isGameVisible() || panelDismissed || capturing || scanPending) {
                portVerificationPending = false
                return@postDelayed
            }
            scanPending = true
            panelView?.visibility = View.INVISIBLE
            ui.postDelayed({
                if (!isGameVisible() || panelDismissed) {
                    portVerificationPending = false
                    scanPending = false
                    return@postDelayed
                }
                inspect { message ->
                    scanPending = false
                    if (!panelDismissed) {
                        panelView?.visibility = View.VISIBLE
                        scanStatus?.text = "Contrôle OCR après geste (résultat à confirmer) : " + message.take(90)
                    }
                }
            }, 350)
        }, 1200)
    }

}
