package com.example.sincronin.ports

import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.room.RoomCode
import kotlinx.coroutines.flow.Flow

/** Lo que el panel necesita saber del servidor de señalización. */
sealed interface SignalingEvent {

    /** Entré a la sala con este [id]; ahora somos [people], contándome. */
    data class Joined(val id: Long, val people: Int) : SignalingEvent

    /** Entró otro equipo y ahora somos [people]. */
    data class PeerJoined(val id: Long, val people: Int) : SignalingEvent

    /** Se fue un equipo; quedan [people]. */
    data class PeerLeft(val people: Int) : SignalingEvent

    /** Un mensaje de un par, tal cual lo reenvió el servidor. */
    data class Relayed(val from: Long, val json: String) : SignalingEvent

    /** No se pudo entrar o se cayó la conexión. */
    data class Failed(val error: PairingError) : SignalingEvent
}

/**
 * Canal de señalización visto por el resto de la app: hablar de salas, de
 * personas y de mensajes entre pares, nunca de WebSocket. El adaptador decide
 * cómo viaja.
 */
interface SignalingSocket {

    val events: Flow<SignalingEvent>

    /** Entra a la sala; reemplaza la conexión anterior si había una. */
    fun connect(room: RoomCode)

    /** Manda el mensaje [json] (ya con su `type`) solo al par [to]. */
    fun send(to: Long, json: String)

    fun close()
}
