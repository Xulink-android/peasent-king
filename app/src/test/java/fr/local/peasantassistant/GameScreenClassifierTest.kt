package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class GameScreenClassifierTest {
    @Test fun detectsNavalYardFromObservedLabels() {
        assertEquals(GameScreenClassifier.Screen.SHIPYARD,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "Highvale Star", "ENTREPÔT", "À la mer")).screen)
    }
    @Test fun detectsUnloadingAndPaymentAsDifferentPhases() {
        assertEquals(GameScreenClassifier.Screen.UNLOAD_CARGO,
            GameScreenClassifier.classify(listOf("Déchargement du navire", "ENTREPÔT DU ROYAUME")).screen)
        assertEquals(GameScreenClassifier.Screen.PAYMENT_IN_TRANSIT,
            GameScreenClassifier.classify(listOf("Paiement en route", "ENTREPÔT DU ROYAUME")).screen)
        assertEquals(GameScreenClassifier.Screen.COLLECT_PAYMENT,
            GameScreenClassifier.classify(listOf("Collecter le paiement", "ENTREPÔT DU ROYAUME")).screen)
    }
    @Test fun rejectsAmbiguousOverlayScreens() {
        val ambiguous = listOf("CHANTIER NAVAL", "ENTREPÔT", "Déchargement du navire", "ENTREPÔT DU ROYAUME")
        assertEquals(GameScreenClassifier.Screen.UNKNOWN,
            GameScreenClassifier.classify(ambiguous).screen)
    }
    @Test fun mapAndUnknownAreSeparated() {
        assertEquals(GameScreenClassifier.Screen.WORLD_MAP,
            GameScreenClassifier.classify(listOf("Confiance des royaumes", "Port", "Mer")).screen)
        assertEquals(GameScreenClassifier.Screen.UNKNOWN,
            GameScreenClassifier.classify(listOf("100", "200", "ENVOYER 95")).screen)
    }
}
