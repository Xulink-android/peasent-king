package fr.local.peasantassistant

import org.junit.Assert.*
import org.junit.Test

class ContractIconLinkerTest {
    private val base = ContractParser.Result(
        listOf(
            ContractParser.Contract("Eastmere", 110, 39, null, 11),
            ContractParser.Contract("Gullhaven Isles", 104, 39, null, 74)
        ), emptyList(), "Écran de contrats probable"
    )

    @Test fun linksOnlyExactMatchingRowAndColumn() {
        val result = ContractIconLinker.annotate(base, listOf(
            ContractIconLinker.Candidate(11, 39, "grain"),
            ContractIconLinker.Candidate(74, 39, "potatoes")
        ))
        assertEquals(listOf("grain", "potatoes"), result.contracts.map { it.resource })
    }

    @Test fun rejectsAmbiguousAndUnknownIcons() {
        val result = ContractIconLinker.annotate(base, listOf(
            ContractIconLinker.Candidate(11, 39, "grain"),
            ContractIconLinker.Candidate(11, 39, "coal"),
            ContractIconLinker.Candidate(74, 39, "unrecognized")
        ))
        assertTrue(result.contracts.all { it.resource == null })
    }

    @Test fun rejectsIconsFromOtherRowsOrScreens() {
        val wrongRow = ContractIconLinker.annotate(base, listOf(
            ContractIconLinker.Candidate(11, 50, "grain")
        ))
        assertNull(wrongRow.contracts[0].resource)
        val wrongScreen = ContractIconLinker.annotate(
            base.copy(context = "Écran de stocks probable"),
            listOf(ContractIconLinker.Candidate(11, 39, "grain"))
        )
        assertNull(wrongScreen.contracts[0].resource)
    }

    @Test fun doesNotOverrideTextRecognizedResource() {
        val text = base.copy(contracts = listOf(base.contracts[0].copy(resource = "coal")))
        val result = ContractIconLinker.annotate(text,
            listOf(ContractIconLinker.Candidate(11, 39, "grain")))
        assertEquals("coal", result.contracts.single().resource)
    }
}
