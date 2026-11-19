package fr.local.peasantassistant

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.Spinner
import android.widget.ArrayAdapter
import java.io.File
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val d = (resources.displayMetrics.density * 18).toInt()
            setPadding(d, d, d, d)
        }
        fun label(value: String) = TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, 12, 0, 4)
        }
        root.addView(label("Peasant King Assistant — v2.18 bêta"))
        root.addView(label(
            "Lecture OCR locale et simulation des contrats. " +
            "Un geste facultatif Auto Port (1 fois) est disponible après activation et vérifications. " +
            "Le moteur de cycle et le pont de gestes sont encore en validation, non raccordés au suivi direct. La grille d'icônes du port ne permet pas encore de confirmer les stocks et réservations. Aucune expédition ni aucun transfert de ressources ne sont automatisés. " +
            "Le bouton Masquer ferme seulement le panneau, sans révoquer l'autorisation Android."
        ))
        root.addView(Button(this).apply {
            text = "Activer / désactiver le service d'accessibilité"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(label(
            "Pour éviter les autorisations répétées : laisse le service activé dans Android. " +
            "Dans le jeu, X Masquer ferme le panneau. Pour le revoir, quitte le jeu " +
            "puis reviens-y. Désactive le service depuis les paramètres Android " +
            "si tu ne souhaites plus lui donner accès à l'écran."
        ))
        val diagnostic = label("Dernier rapport : pas encore ouvert")
        root.addView(Button(this).apply {
            text = "Afficher le dernier rapport OCR enregistré"
            setOnClickListener {
                diagnostic.text = try {
                    openFileInput("last_ocr_report.txt").bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    "Aucun rapport enregistré. Analyse un écran du jeu d'abord."
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Copier le dernier rapport"
            setOnClickListener {
                val report = try {
                    openFileInput("last_ocr_report.txt").bufferedReader().use { it.readText() }
                } catch (_: Exception) { "" }
                if (report.isBlank()) {
                    diagnostic.text = "Aucun rapport disponible."
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Peasant King OCR", report))
                    diagnostic.text = "Rapport copié."
                }
            }
        })
        root.addView(diagnostic)
        root.addView(label("Bibliothèque locale d'icônes (expérimental)"))
        root.addView(label(
            "Dans le jeu, ouvre les contrats puis utilise Capturer icônes. " +
            "Les découpes sont approximatives ; les références extraites du jeu restent expérimentales."
        ))
        val gallery = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Button(this).apply {
            text = "Afficher les découpes capturées"
            setOnClickListener {
                gallery.removeAllViews()
                val dir = File(filesDir, "icon_candidates")
                val files = dir.listFiles()?.filter { it.extension == "png" }?.sortedBy { it.name }
                    ?: emptyList()
                if (files.isEmpty()) {
                    gallery.addView(label("Aucune icône capturée pour le moment."))
                }
                files.forEach { file ->
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                    val preview = ImageView(this@MainActivity).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                        layoutParams = LinearLayout.LayoutParams(180, 180)
                    }
                    gallery.addView(label(file.name + " — découpe non vérifiée"))
                    gallery.addView(preview)
                    val selector = Spinner(this@MainActivity)
                    selector.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        IconLibrary.labels
                    )
                    gallery.addView(selector)
                    gallery.addView(Button(this@MainActivity).apply {
                        text = "Valider cette icône"
                        setOnClickListener {
                            val selected = selector.selectedItem?.toString() ?: return@setOnClickListener
                            val accepted = IconLibrary.save(this@MainActivity, selected, bitmap)
                            diagnostic.text = if (accepted) {
                                "Exemple local enregistré pour $selected. Aucune action dans le jeu."
                            } else "Enregistrement impossible."
                        }
                    })
                }
            }
        })
        root.addView(gallery)
        root.addView(Button(this).apply {
            text = "Effacer les étiquettes d'icônes"
            setOnClickListener {
                IconLibrary.clear(this@MainActivity)
                diagnostic.text = "Bibliothèque d'icônes effacée localement."
            }
        })
        root.addView(label("Calculateur de réapprovisionnement — valeurs à saisir depuis le jeu"))
        val resource = EditText(this).apply { hint = "Ressource (ex. blé)" }
        root.addView(resource)
        fun number(hintText: String, defaultValue: String = "0"): EditText {
            val input = EditText(this).apply {
                hint = hintText
                setText(defaultValue)
                inputType = InputType.TYPE_CLASS_NUMBER
                contentDescription = hintText
            }
            root.addView(label(hintText))
            root.addView(input)
            return input
        }
        val capacity = number("Capacité totale du port", "200")
        val inPort = number("Stock actuellement dans le port", "0")
        val inbound = number("Marchandises déjà en route", "0")
        val inRegion = number("Stock disponible dans la région", "0")
        val reserve = number("Réserve minimale à garder dans la région", "0")
        val planned = number("Stock réservé pour un navire non encore parti", "0")
        val result = label("Résultat : aucune simulation exécutée")
        root.addView(Button(this).apply {
            text = "Calculer sans agir sur le jeu"
            setOnClickListener {
                val values = listOf(capacity, inPort, inbound, inRegion, reserve, planned)
                    .map { it.text.toString().toIntOrNull() }
                if (values.any { it == null } || resource.text.isBlank()) {
                    result.text = "Saisis une ressource et six nombres entiers valides."
                    return@setOnClickListener
                }
                val v = values.map { it!! }
                val decision = StockPlanner.plan(StockPlanner.Stock(
                    resource.text.toString(), v[0], v[1], v[2], v[3], v[4], v[5]
                ))
                result.text = "Transfert proposé : ${decision.transfer} unités de ${decision.resource}. " +
                    decision.reason + ". Aucun transfert exécuté."
            }
        })
        root.addView(result)
        root.addView(label(
            "Important : le planificateur ne connaît pas encore les stocks du jeu. " +
            "Il utilise exclusivement les chiffres saisis. Les marchandises réservées " +
            "pour un navire occupent toujours de la place jusqu'à son départ réel."
        ))
        root.addView(label("Simulation de contrat et de chargement (saisie manuelle)"))
        val kingdom = EditText(this).apply { hint = "Royaume destinataire" }
        val shipResource = EditText(this).apply { hint = "Ressource demandée" }
        root.addView(kingdom)
        root.addView(shipResource)
        val demand = number("Quantité du contrat", "110")
        val shipCapacity = number("Capacité du navire", "200")
        val ships = number("Nombre de navires disponibles", "1")
        val reserved = number("Stock déjà réservé au port", "0")
        val shipResult = label("Aucune expédition simulée")
        root.addView(Button(this).apply {
            text = "Simuler une expédition sans envoyer"
            setOnClickListener {
                val values = listOf(demand, shipCapacity, ships, reserved, inPort)
                    .map { it.text.toString().toIntOrNull() }
                if (values.any { it == null } || kingdom.text.isBlank() || shipResource.text.isBlank()) {
                    shipResult.text = "Complète tous les champs et les quantités."
                    return@setOnClickListener
                }
                val v = values.map { it!! }
                val plan = ShipmentPlanner.plan(
                    listOf(ShipmentPlanner.Demand(kingdom.text.toString(), shipResource.text.toString(), v[0])),
                    listOf(ShipmentPlanner.Supply(shipResource.text.toString(), v[4], v[3])),
                    v[1], v[2]
                )
                shipResult.text = if (plan.proposals.isEmpty()) {
                    "Aucun envoi proposé. " + plan.warnings.joinToString("; ")
                } else {
                    "Proposition : " + plan.proposals.joinToString { proposal ->
                        proposal.quantity.toString() + " " + proposal.resource + " vers " + proposal.kingdom
                    } + ". Aucune action effectuée dans le jeu."
                }
            }
        })
        root.addView(shipResult)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
