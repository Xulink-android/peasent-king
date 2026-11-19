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
    private val planningSession = ReadOnlyPlanningSession()
    private val automationCoordinator = AutomationCoordinator()
    private val portTargetGate = PortTargetGate()
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
            autoScan = false
            scanPending = false
            collectIconSamples = false
            portNavigationArmed = false
            portVerificationPending = false
            portTargetGate.reset()
            observationGate.reset()
            verifiedStockGate.reset()
            planningSession.reset()
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
        portNavigationArmed = false
        portVerificationPending = false
        portTargetGate.reset()
        autoScan = false
        ui.removeCallbacks(scanLoop)
    }

    override fun onDestroy() {
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
        planningSession.reset()
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
                planningSession.reset()
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
                    portNavigationArmed = false
                    portTargetGate.reset()
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

    private val scanLoop = object : Runnable {
        override fun run() {
            if (!autoScan || panelDismissed) return
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
                            scanStatus?.text = message.take(3000)
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
            finish("Capture refusée : Peasant to King n'est pas au premier plan.")
            return
        }
        capturing = true
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onFailure(errorCode: Int) {
                ui.post {
                    capturing = false
                    finish("Capture impossible (code $errorCode).")
                }
            }

            override fun onSuccess(result: ScreenshotResult) {
                if (!isGameVisible()) {
                    result.hardwareBuffer.close()
                    ui.post {
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
                        val gameScreen = GameScreenClassifier.classify(
                            detected.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                        )
                        val stockObservation = StockScreenParser.parse(
                            detected.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                        )
                        val verifiedStock = verifiedStockGate.observe(
                            parsed.context == "Écran de stocks probable", stockObservation
                        )
                        val verdict = observationGate.observe(parsed)
                        val observationTime = android.os.SystemClock.elapsedRealtime()
                        if (parsed.context == "Écran de contrats probable") {
                            planningSession.observeContracts(verdict.stable, observationTime)
                        }
                        if (parsed.context == "Écran de stocks probable") {
                            planningSession.observePort(verifiedStock, observationTime)
                        }
                        if (parsed.context == "Écran non identifié") {
                            planningSession.reset()
                            observationGate.reset()
                            verifiedStockGate.reset()
                        }
                        val suggestedShipment = planningSession.proposal(
                            observationTime,
                            if (parsed.context == "Écran de contrats probable") verdict.stable else emptyList()
                        )
                        val decision = automationCoordinator.evaluate(
                            AutomationCoordinator.Snapshot(
                                if (parsed.context == "Écran de contrats probable") "contracts" else "unknown",
                                verdict.stable
                            )
                        )
                        lastReport = buildString {
                            appendLine("Peasant King — diagnostic OCR en lecture seule")
                            appendLine("Écran : $width x $height")
                            appendLine("Textes détectés : ${lines.size}")
                            lines.forEach { appendLine(it) }
                            appendLine("PHASE OBSERVÉE : " + gameScreen.screen)
                            appendLine("INDICES : " + gameScreen.evidence.joinToString(" ; "))
                            appendLine("Prudence : identification expérimentale issue des captures, aucune expédition déclenchée.")
                            appendLine("SIMULATION / INTERPRÉTATION")
                            appendLine(parsed.context)
                            appendLine("VÉRIFICATION : " + verdict.message)
                            appendLine("Références d'icônes issues de l'APK : correspondances indicatives, NON validées sur l'écran réel.")
                            iconMatches.forEach { (contract, label, _) ->
                                appendLine("ICÔNE CANDIDATE : $contract -> $label (diagnostic non validé)")
                            }
                            appendLine("AUTOMATISATION : " + decision.phase + " — " + decision.explanation)
                            appendLine("PLAN MULTI-ÉCRANS : " + (suggestedShipment?.let {
                                "${it.kingdom} / ${it.resource} / ${it.quantity} (simulation uniquement)"
                            } ?: "attente de contrats et stocks confirmés, récents"))
                            appendLine("LECTURE PORT (expérimentale, sans action) :")
                            appendLine(if (verifiedStock == null) {
                                "Stock non confirmé : aucune proposition d'expédition"
                            } else {
                                "Stock confirmé sur trois lectures identiques (diagnostic uniquement)"
                            })
                            stockObservation.supplies.forEach {
                                appendLine("Stock: ${it.resource} = ${it.availableAtPort}, réservé = ${it.reserved}")
                            }
                            appendLine("Capacité : ${stockObservation.shipCapacity ?: "inconnue"} ; navires : ${stockObservation.availableShips ?: "inconnus"}")
                            stockObservation.warnings.forEach { appendLine("Port : " + it) }
                            if (shouldCollect) appendLine("Candidats icônes enregistrés localement : $savedIcons")
                            if (shouldCollect) appendLine("Images non étiquetées : identification non garantie.")
                            parsed.contracts.forEach { contract ->
                                appendLine("Contrat : royaume=" + (contract.kingdom ?: "inconnu") +
                                    ", quantité=" + contract.amount + ", ressource=" + (contract.resource ?: "inconnue"))
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
                            appendLine("Actions autorisées : navigation Port seulement, après activation explicite. Aucun envoi.")
                            appendLine("La lecture des icônes et des contrats invisibles n'est pas prise en charge.")
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
