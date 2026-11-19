package fr.local.peasantassistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
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
        val info = TextView(this).apply {
            text = """
                Peasant King Assistant — diagnostic 1.4

                Cette version ne joue pas à ta place.
                Elle lit uniquement les textes visibles dans Peasant to King pour préparer la reconnaissance des contrats et des stocks.

                1. Active le service d'accessibilité ci-dessous.
                2. Ouvre le jeu.
                3. Touche « Analyser » dans le panneau flottant.
                4. Touche « Copier rapport » pour partager le texte OCR.

                Aucune sauvegarde n'est lue et aucun geste n'est envoyé au jeu.
                Désactive le service après les tests.
            """.trimIndent()
            textSize = 16f
        }
        val button = Button(this).apply {
            text = "Ouvrir les paramètres d'accessibilité"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(info)
        root.addView(button)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
