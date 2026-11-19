package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class BundledResourceFingerprintsTest {
    @Test fun containsSixResourcesExtractedFromSuppliedGame() {
        assertEquals(setOf("coal", "grain", "potatoes", "lumber", "cocoa", "silk"),
            BundledResourceFingerprints.knownResources())
    }

    @Test fun neverGuessesUnrelatedHash() {
        assertNull(BundledResourceFingerprints.candidate(-1L))
    }

    @Test fun rejectsSamplesWithNoMargin() {
        // Even an otherwise close candidate cannot be trusted without separation.
        assertNull(BundledResourceFingerprints.candidate(0L, maxDistance = 64, minMargin = 65))
    }

    @Test fun strictMatchingDoesNotAcceptUncalibratedSample() {
        assertNull(BundledResourceFingerprints.candidate(0L, maxDistance = 0))
    }
}
