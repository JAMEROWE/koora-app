package com.koora.app

import com.koora.app.ui.components.conventionalShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The adaptive versus-row label only shortens by dropping generic
 *  connectors; identity words are never removed or invented. */
class LabelShortenTest {

    @Test
    fun dropsGenericClubWord() {
        assertEquals("Raja Athletic", conventionalShort("Raja Club Athletic"))
    }

    @Test
    fun dropsConnectorDe() {
        assertEquals("Olympique Marseille", conventionalShort("Olympique de Marseille"))
    }

    @Test
    fun keepsTwoWordNames() {
        assertNull(conventionalShort("Wydad AC"))
        assertNull(conventionalShort("Manchester City"))
        assertNull(conventionalShort("FC Barcelona"))
    }

    @Test
    fun neverDropsIdentityAcronyms() {
        // FAR is the club's identity; no filler present -> no shortening.
        assertNull(conventionalShort("AS FAR Rabat"))
    }

    @Test
    fun resultAlwaysUsesTheClubsOwnWords() {
        val name = "Real Club Deportivo"
        val short = conventionalShort(name)
        if (short != null) {
            short.split(' ').forEach { word ->
                assert(name.split(' ').contains(word)) { "$word is not part of $name" }
            }
        }
    }
}
