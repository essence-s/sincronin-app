package com.example.sincronin.ports

import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.room.RoomCode
import kotlinx.coroutines.flow.Flow

/** Lo que el panel necesita saber del servidor de señalización. */
sealed interface SignalingEvent {

    /** Entré a la sala y ahora hay [people] personas, contándome. */
    data class Joined(val people: Int) : SignalingEvent

    /** No se pudo entrar o se cayó la conexión. */
    data class Failed(val error: PairingError) : SignalingEvent
}

/**
 * Canal de señalización visto por el resto de la app: hablar de salas y de
 * personas, nunca de WebSocket. El adaptador decide cómo viaja.
 */
interface SignalingSocket {

    val events: Flow<SignalingEvent>

    /** Entra a la sala; reemplaza la conexión anterior si había una. */
    fun connect(room: RoomCode)

    fun close()
}
