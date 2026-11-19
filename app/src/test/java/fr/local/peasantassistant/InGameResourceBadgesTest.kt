package fr.local.peasantassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InGameResourceBadgesTest {
    @Test fun recognizesOnlyBadgesObservedBesideStockCounts() {
        assertEquals(setOf("grain", "potatoes", "coal", "lumber", "arms"),
            InGameResourceBadges.knownResources())
        assertEquals("grain", InGameResourceBadges.candidate(0xd01c2e2e3e9c9c3dUL.toLong()))
        assertEquals("potatoes", InGameResourceBadges.candidate(0x8820991c365e0c8dUL.toLong()))
        assertEquals("potatoes", InGameResourceBadges.candidate(0x8820991c365e0c8fUL.toLong()))
        assertEquals("coal", InGameResourceBadges.candidate(0x8c9c1899a8608180UL.toLong()))
        assertEquals("lumber", InGameResourceBadges.candidate(0x8e1e367e7e7c38c0UL.toLong()))
        assertEquals("arms", InGameResourceBadges.candidate(0x000315250d993941UL.toLong()))
        assertEquals("arms", InGameResourceBadges.candidate(0x040b15650d993941UL.toLong()))
    }

    @Test fun toleratesOneHashBitButRejectsUnknownBadges() {
        assertEquals("grain", InGameResourceBadges.candidate(0xd01c2e2e3e9c9c3cUL.toLong()))
        assertNull(InGameResourceBadges.candidate(0L))
    }
}
