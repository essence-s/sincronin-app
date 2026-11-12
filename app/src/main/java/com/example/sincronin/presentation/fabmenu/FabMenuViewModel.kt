package com.example.sincronin.presentation.fabmenu

import androidx.lifecycle.ViewModel
import com.example.sincronin.BuildConfig
import com.example.sincronin.adapters.OkHttpSignalingSocket
import com.example.sincronin.domain.link.LinkIntent
import com.example.sincronin.domain.link.LinkSignal
import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.pairing.PairingStatus
import com.example.sincronin.domain.pairing.Role
import com.example.sincronin.domain.room.RoomCode
import com.example.sincronin.ports.PeerEvent
import com.example.sincronin.ports.PeerTransport
import com.example.sincronin.ports.SignalingEvent
import com.example.sincronin.ports.SignalingSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Estado del botón flotante, de la sala y del enlace con el otro equipo.
 *
 * La vista (ventana flotante) solo observa y emite eventos; aquí se decide qué
 * significa cada cosa que contesta el servidor. El socket y el transporte
 * entran por sus puertos, así que este archivo no sabe que hay un WebSocket ni
 * WebRTC.
 */
class FabMenuViewModel(
    private val signaling: SignalingSocket = OkHttpSignalingSocket(BuildConfig.SIGNAL_URL),
    private val newPeer: ((onSignal: (String) -> Unit) -> PeerTransport)? = null
) : ViewModel() {

    // Sin ciclo de vida propio: este ViewModel lo vive y lo destruye el
    // servicio, que ya tiene su propio scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val myCode = RoomCode.generate()

    private val _uiState = MutableStateFlow(FabMenuUiState(myCode = myCode))
    val uiState: StateFlow<FabMenuUiState> = _uiState.asStateFlow()

    /** Mi identificador en la sala; el servidor decide quién es el primero. */
    private var myId: Long? = null

    /** Identificador del otro equipo, el único al que se le habla. */
    private var remote: Long? = null

    private var peer: PeerTransport? = null

    init {
        scope.launch { signaling.events.collect(::onSignalingEvent) }
        // Desde que arranca el servicio se ofrece la sala propia: quien escriba
        // este código entra como invitado.
        enter(myCode)
    }

    fun onFabClick() {
        _uiState.update { it.copy(isExpanded = true) }
    }

    fun onDismiss() {
        _uiState.update { it.copy(isExpanded = false) }
    }

    /**
     * Lo que se escribe en el campo: en mayúsculas y sin separadores, porque el
     * código son 6 letras o dígitos. Los caracteres que el alfabeto no acepta
     * se dejan a la vista y se explican al confirmar, no se borran en silencio.
     */
    fun onCodeChange(text: String) {
        val cleaned = text.uppercase()
            .filter { it.isLetterOrDigit() }
            .take(RoomCode.LENGTH)
        _uiState.update { state ->
            val pairing = if (state.pairing.status is PairingStatus.Failed) {
                state.pairing.retrying()
            } else {
                state.pairing
            }
            state.copy(typedCode = cleaned, pairing = pairing)
        }
    }

    fun onJoin() {
        val room = RoomCode.parse(_uiState.value.typedCode)
        if (room == null) {
            _uiState.update { it.copy(pairing = it.pairing.failed(PairingError.InvalidCode)) }
            return
        }
        enter(room)
    }

    private fun enter(room: RoomCode) {
        closePeer()
        _uiState.update { it.copy(pairing = it.pairing.joining(room)) }
        signaling.connect(room)
    }

    private fun onSignalingEvent(event: SignalingEvent) {
        when (event) {
            is SignalingEvent.Joined -> {
                myId = event.id
                _uiState.update { it.copy(pairing = it.pairing.joined(event.people)) }
            }

            is SignalingEvent.PeerJoined -> {
                val pairing = _uiState.value.pairing.joined(event.people)
                val step = pairing.link.peerPresent(event.id, pairing.role ?: Role.Host)
                _uiState.update { it.copy(pairing = pairing.copy(link = step.state)) }
                if (step.intent == LinkIntent.Propose) {
                    openPeer(event.id)?.propose()
                }
            }

            is SignalingEvent.PeerLeft -> {
                closePeer()
                _uiState.update { it.copy(pairing = it.pairing.joined(event.people)) }
            }

            is SignalingEvent.Relayed -> onRelayed(event.from, event.json)

            is SignalingEvent.Failed -> {
                closePeer()
                _uiState.update { it.copy(pairing = it.pairing.failed(event.error)) }
            }
        }
    }

    /** Un mensaje del par: se decide qué es y se le pasa al enlace. */
    private fun onRelayed(from: Long, json: String) {
        val type = runCatching { JSONObject(json).getString("type") }.getOrNull() ?: return
        val signal = when (type) {
            OFFER -> LinkSignal.Offer(json)
            ANSWER -> LinkSignal.Answer(json)
            ICE -> LinkSignal.Candidate(json)
            else -> return
        }

        val step = _uiState.value.pairing.link.receive(from, signal)
        _uiState.update { it.copy(pairing = it.pairing.copy(link = step.state)) }

        when {
            // Una oferta se contesta en el acto: el invitado abre su lado.
            step.intent == LinkIntent.Respond -> openPeer(from)?.answer(json)
            signal is LinkSignal.Answer -> peer?.accept(json)
            signal is LinkSignal.Candidate -> peer?.addCandidate(json)
            signal is LinkSignal.Offer -> Unit
        }
    }

    private fun onPeerEvent(event: PeerEvent) {
        when (event) {
            PeerEvent.Connected -> _uiState.update {
                it.copy(pairing = it.pairing.copy(link = it.pairing.link.ready()))
            }

            PeerEvent.Failed -> _uiState.update {
                it.copy(pairing = it.pairing.copy(link = it.pairing.link.failed()))
            }

            // Los mensajes del canal son la fase siguiente: ver qué suena.
            is PeerEvent.Message -> Unit
        }
    }

    /** Abre el transporte con el par [id] y lo deja escuchando. */
    private fun openPeer(id: Long): PeerTransport? {
        closePeer()
        val transport = newPeer?.invoke(::sendToPeer) ?: return null
        remote = id
        peer = transport
        scope.launch { transport.events.collect(::onPeerEvent) }
        return transport
    }

    private fun sendToPeer(json: String) {
        remote?.let { signaling.send(it, json) }
    }

    private fun closePeer() {
        peer?.close()
        peer = null
        remote = null
    }

    override fun onCleared() {
        closePeer()
        scope.cancel()
        signaling.close()
        super.onCleared()
    }

    private companion object {
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE = "ice"
    }
}
