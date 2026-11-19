package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class GameScreenClassifierTest {
    @Test fun detectsNavalYardFromObservedLabels() {
        assertEquals(GameScreenClassifier.Screen.SHIPYARD,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "Highvale Star", "ENTREPÔT", "À la mer")).screen)
    }
    @Test fun distinguishesLoadingFromReadyToSailInRecordedYard() {
        assertEquals(GameScreenClassifier.Screen.SHIP_LOADING,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "CHARGEMENT DE MARCHANDISES", "ENTREPÔT")).screen)
        assertEquals(GameScreenClassifier.Screen.SHIP_READY_TO_SAIL,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "CARGAISON PRÊTE", "ENTREPÔT")).screen)
    }
    @Test fun recognizesPaymentCollectionInRecordedYard() {
        assertEquals(GameScreenClassifier.Screen.COLLECT_PAYMENT,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "DÉCHARGEMENT AU PORT", "Récupérer 8", "ENTREPÔT")).screen)
    }
    @Test fun detectsUnloadingAndPaymentAsDifferentPhases() {
        assertEquals(GameScreenClassifier.Screen.UNLOAD_CARGO,
            GameScreenClassifier.classify(listOf("Déchargement du navire", "ENTREPÔT DU ROYAUME")).screen)
        assertEquals(GameScreenClassifier.Screen.PAYMENT_IN_TRANSIT,
            GameScreenClassifier.classify(listOf("Paiement en route", "ENTREPÔT DU ROYAUME")).screen)
        assertEquals(GameScreenClassifier.Screen.COLLECT_PAYMENT,
            GameScreenClassifier.classify(listOf("Collecter le paiement", "ENTREPÔT DU ROYAUME")).screen)
    }
    @Test fun distinguishesSecuredPaymentReturnPromptFromShipyardReadyState() {
        assertEquals(GameScreenClassifier.Screen.RETURN_READY,
            GameScreenClassifier.classify(listOf("Paiement sécurisé", "Prêt à retourner", "Repartir")).screen)
        assertEquals(GameScreenClassifier.Screen.SHIPYARD_READY,
            GameScreenClassifier.classify(listOf("CHANTIER NAVAL", "Prêt pour un contrat", "ENTREPÔT")).screen)
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
