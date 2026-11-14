package com.example.sincronin.ports

import kotlinx.coroutines.flow.Flow

/** Lo que el transporte entre pares le cuenta al resto de la app. */
sealed interface PeerEvent {

    /** El canal quedó abierto: ya se puede mandar. */
    data object Connected : PeerEvent

    /** La negociación no llegó a nada. */
    data object Failed : PeerEvent

    /** El canal se cortó: hay que reintentar. */
    data object Disconnected : PeerEvent

    /** Llegó un mensaje del otro equipo por el canal. */
    data class Message(val text: String) : PeerEvent
}

/**
 * Canal directo con el otro equipo, visto sin WebRTC: el resto de la app solo
 * sabe que hay algo que se puede abrir, aceptar y usar para mandar texto.
 */
interface PeerTransport {

    val events: Flow<PeerEvent>

    /** Abre el canal y produce la propuesta (lo hace el anfitrión). */
    fun propose()

    /** Acepta la propuesta del otro equipo y contesta (lo hace el invitado). */
    fun answer(json: String)

    /** Recibe la respuesta a mi propuesta, sin contestar nada. */
    fun accept(json: String)

    fun addCandidate(json: String)

    /** Reinicia el ICE y rehace la oferta, conservando el canal. */
    fun restart()

    fun send(text: String)

    fun close()
}
