package com.example.sincronin.adapters

import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.room.RoomCode
import com.example.sincronin.ports.SignalingEvent
import com.example.sincronin.ports.SignalingSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Señalización por WebSocket contra el servidor del proyecto
 * (`ws://host:8787/ws?room=CODIGO`).
 *
 * El código va en el query, así que la sala se conoce antes de abrir el socket:
 * si el servidor no la acepta responde 400 y aquí se traduce a
 * [PairingError.InvalidCode], sin que el dominio vea HTTP.
 */
class OkHttpSignalingSocket(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) : SignalingSocket {

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 8)
    override val events: Flow<SignalingEvent> = _events

    private var socket: WebSocket? = null

    /** Personas en la sala, contándome; el servidor manda los eventos. */
    private var people = 1

    override fun connect(room: RoomCode) {
        close()
        people = 1
        val request = Request.Builder()
            .url("$baseUrl?room=${room.value}")
            .build()
        socket = client.newWebSocket(request, listener)
    }

    override fun close() {
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
    }

    private val listener = object : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (message.optString("type")) {
                "joined" -> {
                    people = (message.optJSONArray("peers")?.length() ?: 0) + 1
                    _events.tryEmit(SignalingEvent.Joined(people))
                }

                "peerJoined" -> {
                    people += 1
                    _events.tryEmit(SignalingEvent.Joined(people))
                }

                "peerLeft" -> {
                    people = (people - 1).coerceAtLeast(1)
                    _events.tryEmit(SignalingEvent.Joined(people))
                }

                // El servidor no dio la sala: para el panel es el mismo caso
                // que no poder conectar.
                "error" -> _events.tryEmit(SignalingEvent.Failed(PairingError.ServerUnreachable))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = if (response?.code == 400) {
                PairingError.InvalidCode
            } else {
                PairingError.ServerUnreachable
            }
            _events.tryEmit(SignalingEvent.Failed(error))
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
