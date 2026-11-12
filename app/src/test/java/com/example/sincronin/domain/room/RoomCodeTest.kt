package com.example.sincronin.domain.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoomCodeTest {

    @Test
    fun `escribir en minusculas se lee en mayusculas`() {
        val code = RoomCode.parse("abc234")

        assertEquals("ABC234", code?.value)
    }

    @Test
    fun `rechaza largos distintos de 6`() {
        assertNull(RoomCode.parse("ABC23"))
        assertNull(RoomCode.parse("ABC2345"))
    }

    @Test
    fun `rechaza caracteres fuera del alfabeto`() {
        listOf("ABC01U", "ABCI23", "ABCO23", "ABCL23", "abc-23").forEach { raw ->
            assertNull(raw, RoomCode.parse(raw))
        }
    }

    @Test
    fun `el codigo que ofrece la app es valido y de 6 caracteres`() {
        // Alfabeto del protocolo, copiado a mano a propósito: el valor esperado
        // no puede salir de la misma constante que usa el código.
        val alphabet = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        repeat(200) { seed ->
            val value = RoomCode.generate(Random(seed)).value

            assertEquals(6, value.length)
            assertTrue(value, value.all { it in alphabet })
        }
    }
}
