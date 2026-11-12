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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * Señalización por WebSocket contra el servidor del proyecto
 * (`ws://host:8787/ws?room=CODIGO`).
 *
 * El código va en el query, así que la sala se conoce antes de abrir el socket:
 * si el servidor no la acepta responde 400 y aquí se traduce a
 * [PairingError.InvalidCode], sin que el dominio vea HTTP.
 *
 * Los mensajes entre pares se mandan con `to` y el servidor los reenvía en
 * crudo dentro de un `relay`; aquí solo se quita el sobre.
 *
 * OkHttp no reconecta solo, así que el reintento vive aquí: espera creciente
 * con algo de azar para que dos móviles no vuelvan a la vez tras un corte de
 * Wi-Fi, y tope de [MAX_RETRY_MILLIS]. Un código inválido no se reintenta:
 * reintentarlo sería un bucle contra el servidor.
 */
class OkHttpSignalingSocket(
    private val baseUrl: String,
    private val client: OkHttpClient = DEFAULT_CLIENT
) : SignalingSocket {

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 8)
    override val events: Flow<SignalingEvent> = _events

    private var socket: WebSocket? = null

    /** Sala a la que se está yendo: el reintento vuelve a la misma. */
    private var room: RoomCode? = null

    /** Personas en la sala, contándome; el servidor manda los eventos. */
    private var people = 1

    /** Intentos fallidos seguidos, para calcular la espera. */
    private var attempt = 0

    private var pendingRetry: ScheduledFuture<*>? = null

    /** Lo cerró la app: no se reintenta. */
    @Volatile
    private var stopped = false

    override fun connect(room: RoomCode) {
        this.room = room
        stopped = false
        attempt = 0
        open(room)
    }

    override fun send(to: Long, json: String) {
        val message = runCatching { JSONObject(json) }.getOrNull() ?: return
        // El sobre que espera el servidor: plano, con versión y destinatario.
        message.put("version", VERSION).put("to", to)
        socket?.send(message.toString())
    }

    override fun close() {
        stopped = true
        pendingRetry?.cancel(false)
        pendingRetry = null
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        room = null
    }

    private fun open(target: RoomCode) {
        socket?.cancel()
        people = 1
        val request = Request.Builder()
            .url("$baseUrl?room=${target.value}")
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    /** Reintento con espera creciente y un poco de azar. */
    private fun retryLater() {
        val target = room ?: return
        if (stopped || pendingRetry?.isDone == false) return

        // 1 s, 2, 4, 8, 16 y tope de 30 s; el azar evita que los dos equipos
        // vuelvan exactamente a la vez.
        val base = min(MAX_RETRY_MILLIS, INITIAL_RETRY_MILLIS shl min(attempt, 5))
        val wait = base + Random.nextLong(0, base / 2 + 1)
        attempt += 1
        pendingRetry = RETRIES.schedule({
            if (!stopped) open(target)
        }, wait, TimeUnit.MILLISECONDS)
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (message.optString("type")) {
                "joined" -> {
                    people = (message.optJSONArray("peers")?.length() ?: 0) + 1
                    _events.tryEmit(SignalingEvent.Joined(message.optLong("id"), people))
                }

                "peerJoined" -> {
                    people += 1
                    _events.tryEmit(SignalingEvent.PeerJoined(message.optLong("peer"), people))
                }

                "peerLeft" -> {
                    people = (people - 1).coerceAtLeast(1)
                    _events.tryEmit(SignalingEvent.PeerLeft(people))
                }

                "relay" -> {
                    val inner = message.optJSONObject("message") ?: return
                    _events.tryEmit(SignalingEvent.Relayed(message.optLong("peer"), inner.toString()))
                }

                // El servidor no dio la sala: para el panel es el mismo caso
                // que no poder conectar.
                "error" -> _events.tryEmit(SignalingEvent.Failed(PairingError.ServerUnreachable))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // El servidor se está despidiendo: si no lo pedimos nosotros, se
            // vuelve a intentar cuando cierre.
            if (!stopped) retryLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (stopped) return
            if (response?.code == HTTP_BAD_REQUEST) {
                // Código rechazado: reintentar no lo va a arreglar.
                _events.tryEmit(SignalingEvent.Failed(PairingError.InvalidCode))
                return
            }
            // Solo se avisa la primera vez: si el reintento sale bien, el
            // `joined` limpia el aviso solo.
            if (attempt == 0) {
                _events.tryEmit(SignalingEvent.Failed(PairingError.ServerUnreachable))
            }
            retryLater()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HTTP_BAD_REQUEST = 400

        /** Versión del protocolo de señalización. */
        const val VERSION = 2

        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 30_000L

        /**
         * Un hilo compartido por todas las instancias: el adaptador se rehace
         * al cambiar de sala y no queremos un hilo por cada uno.
         */
        val RETRIES = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "sincronin-signal-retry").apply { isDaemon = true }
        }

        /**
         * Ping cada 20 s: mantiene viva la traducción de direcciones del router
         * y descubre los sockets que ya no llevan a ninguna parte.
         */
        val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
