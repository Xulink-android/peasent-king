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
 * Read-only screen inspector. Never sends touches or edits game data.
 * Accessibility screenshots require explicit user consent in Android settings.
 */
class GameAccessibilityService : AccessibilityService() {
    companion object {
        private const val GAME_PACKAGE = "com.peasanttoking.game"
    }

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!panelDismissed) showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString()
        }
    }

    override fun onInterrupt() { /* No ongoing gestures to interrupt */ }

    override fun onDestroy() {
        overlay?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) { }
        }
        overlay = null
        autoScan = false
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
            text = "X Fermer"
            setOnClickListener {
                panelDismissed = true
                autoScan = false
                scanPending = false
                ui.removeCallbacksAndMessages(null)
                capturing = false
                this@GameAccessibilityService.overlay?.let { view ->
                    try { manager.removeView(view) } catch (_: Exception) { }
                }
                this@GameAccessibilityService.overlay = null
                disableSelf()
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
                if (!autoScan) ui.removeCallbacks(scanLoop)
                text = if (autoScan) "Suivi ON" else "Suivi OFF"
                status.text = if (autoScan) "Suivi OCR toutes les 5 secondes." else "Suivi arrêté."
                if (autoScan) ui.post(scanLoop)
            }
        }
        buttons.addView(analyze)
        buttons.addView(copy)
        buttons.addView(repeat)
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
        capturing = true
        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onFailure(errorCode: Int) {
                ui.post {
                    capturing = false
                    finish("Capture impossible (code $errorCode).")
                }
            }

            override fun onSuccess(result: ScreenshotResult) {
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
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { detected ->
                        val lines = detected.textBlocks.flatMap { it.lines }
                            .mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                val text = line.text.trim().replace("\n", " ")
                                if (text.isBlank()) return@mapNotNull null
                                val x = (100.0 * rect.exactCenterX() / width).toInt()
                                val y = (100.0 * rect.exactCenterY() / height).toInt()
                                "$x%,$y% : $text"
                            }
                        val parsed = ContractParser.parse(
                            detected.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                                val rect = line.boundingBox ?: return@mapNotNull null
                                ContractParser.Line(
                                    line.text.trim(),
                                    (100.0 * rect.exactCenterX() / width).toInt(),
                                    (100.0 * rect.exactCenterY() / height).toInt()
                                )
                            }
                        )
                        lastReport = buildString {
                            appendLine("Peasant King — diagnostic OCR en lecture seule")
                            appendLine("Écran : $width x $height")
                            appendLine("Textes détectés : ${lines.size}")
                            lines.forEach { appendLine(it) }
                            appendLine("SIMULATION / INTERPRÉTATION")
                            appendLine(parsed.context)
                            parsed.contracts.forEach { contract ->
                                appendLine("Contrat : royaume=" + (contract.kingdom ?: "inconnu") +
                                    ", quantité=" + contract.amount + ", ressource=" + (contract.resource ?: "inconnue"))
                            }
                            parsed.warnings.forEach { appendLine("Attention : " + it) }
                            appendLine("Aucune action envoyée au jeu.")
                            appendLine("La lecture des icônes et des contrats invisibles n'est pas prise en charge.")
                        }
                        ui.post {
                            capturing = false
                            finish("Analyse : ${lines.size} textes, ${parsed.contracts.size} contrats probables. Copier rapport.")
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
}
