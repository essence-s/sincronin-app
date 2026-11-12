package com.example.sincronin.domain.room

import kotlin.random.Random

/**
 * Código de sala: 6 caracteres del alfabeto sin letras ni dígitos ambiguos
 * (`I`, `L`, `O`, `U`, `0`, `1`), que es el que acepta el servidor.
 */
class RoomCode(val value: String) {

    companion object {
        /** Alfabeto del protocolo. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

        const val LENGTH = 6

        /**
         * Lo escrito se lee en mayúsculas: el servidor rechaza las minúsculas.
         * Devuelve `null` si no es un código que la sala pueda aceptar.
         */
        fun parse(raw: String): RoomCode? {
            val normalized = raw.uppercase()
            val valid = normalized.length == LENGTH && normalized.all { it in ALPHABET }
            return if (valid) RoomCode(normalized) else null
        }

        /** Código propio, para ofrecer la sala al otro equipo. */
        fun generate(random: Random = Random.Default): RoomCode =
            RoomCode((1..LENGTH).map { ALPHABET.random(random) }.joinToString(""))
    }
}
