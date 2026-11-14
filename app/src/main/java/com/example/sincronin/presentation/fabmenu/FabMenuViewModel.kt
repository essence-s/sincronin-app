package com.example.sincronin.presentation.fabmenu

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.example.sincronin.BuildConfig
import com.example.sincronin.adapters.OkHttpSignalingSocket
import com.example.sincronin.domain.link.LinkIntent
import com.example.sincronin.domain.link.LinkSignal
import com.example.sincronin.domain.link.LinkStatus
import com.example.sincronin.domain.link.RetryPlan
import com.example.sincronin.domain.media.MediaCommand
import com.example.sincronin.domain.media.MediaMessages
import com.example.sincronin.domain.media.MediaSnapshot
import com.example.sincronin.domain.media.PlaybackStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Estado del botón flotante, de la sala, del enlace con el otro equipo y de lo
 * que se está reproduciendo.
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

    /** Escalera de reconexión en curso, si la hay. */
    private var retryJob: Job? = null

    /**
     * Última foto recibida del otro equipo y cuándo llegó: con eso la posición
     * avanza sola entre actualizaciones, sin pedirle nada a la red.
     */
    private var mediaBase: MediaSnapshot? = null
    private var mediaAt = 0L

    init {
        scope.launch { signaling.events.collect(::onSignalingEvent) }
        startMediaClock()
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

    /** Cambio de pestaña dentro del panel: Conexión o Controles. */
    fun onTabSelect(tab: PanelTab) {
        _uiState.update { it.copy(tab = tab) }
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

    /**
     * Play/pausa sobre lo que suena en el otro equipo. Solo se manda si el
     * enlace está abierto y el reproductor de allá lo permite; el panel ya
     * deshabilita el botón, esto es la última defensa.
     */
    fun onTogglePlayPause() {
        val state = _uiState.value
        if (state.pairing.link.status != LinkStatus.Ready) return
        if (!state.media.hasSession || !state.media.canToggle) return

        val command = if (state.media.status == PlaybackStatus.Playing) {
            MediaCommand.Pause
        } else {
            MediaCommand.Play
        }
        // Los comandos viajan por el canal de datos, como en el escritorio. La
        // señalización es solo para el SDP y los candidatos: un "play" por ahí
        // lo recibe el otro equipo como un mensaje que no entiende.
        peer?.send(MediaMessages.command(command))
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
                val wasDisconnected =
                    _uiState.value.pairing.link.status == LinkStatus.Disconnected
                _uiState.update { it.copy(pairing = it.pairing.joined(event.people)) }
                // Si la señalización vuelve tras un corte y el enlace seguía
                // caído, se reengancha solo: la escalera vuelve a correr.
                if (wasDisconnected &&
                    _uiState.value.pairing.link.status != LinkStatus.Closed
                ) {
                    _uiState.update {
                        it.copy(pairing = it.pairing.copy(link = it.pairing.link.reconnecting()))
                    }
                    startReconnect()
                }
            }

            is SignalingEvent.PeerJoined -> {
                if (remote == null) remote = event.id
                val pairing = _uiState.value.pairing.joined(event.people)
                val step = pairing.link.peerPresent(event.id, pairing.role ?: Role.Host)
                _uiState.update { it.copy(pairing = pairing.copy(link = step.state)) }
                if (step.intent == LinkIntent.Propose) {
                    openPeer(event.id)?.propose()
                }
            }

            is SignalingEvent.PeerLeft -> {
                cancelReconnect()
                closePeer()
                clearMedia()
                _uiState.update { it.copy(pairing = it.pairing.joined(event.people)) }
            }

            is SignalingEvent.Relayed -> onRelayed(event.from, event.json)

            is SignalingEvent.Failed -> {
                cancelReconnect()
                closePeer()
                clearMedia()
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

        // El controlador aprende su controlado de la primera oferta que
        // recibe, no del orden de los identificadores.
        if (signal is LinkSignal.Offer && remote == null) remote = from

        val step = _uiState.value.pairing.link.receive(from, signal)
        _uiState.update { it.copy(pairing = it.pairing.copy(link = step.state)) }

        when {
            // Una oferta se contesta en el acto. Si ya había canal (reconexión)
            // se contesta sobre él; si no, se abre el lado del invitado.
            step.intent == LinkIntent.Respond ->
                if (peer == null) openPeer(from)?.answer(json) else peer?.answer(json)

            signal is LinkSignal.Answer -> peer?.accept(json)
            signal is LinkSignal.Candidate -> peer?.addCandidate(json)
            signal is LinkSignal.Offer -> Unit
        }
    }

    private fun onPeerEvent(event: PeerEvent) {
        when (event) {
            PeerEvent.Connected -> {
                cancelReconnect()
                _uiState.update {
                    it.copy(pairing = it.pairing.copy(link = it.pairing.link.ready()))
                }
            }

            PeerEvent.Disconnected -> {
                val status = _uiState.value.pairing.link.status
                if (status == LinkStatus.Ready || status == LinkStatus.Negotiating) {
                    _uiState.update {
                        it.copy(pairing = it.pairing.copy(link = it.pairing.link.reconnecting()))
                    }
                    startReconnect()
                }
            }

            PeerEvent.Failed -> _uiState.update {
                it.copy(pairing = it.pairing.copy(link = it.pairing.link.failed()))
            }

            is PeerEvent.Message -> onMediaMessage(event.text)
        }
    }

    /**
     * Un mensaje del canal con el enlace ya abierto. El invitado pinta lo que
     * publica el anfitrión; los mensajes que no son un estado se ignoran.
     */
    private fun onMediaMessage(text: String) {
        // El anfitrión comparte; compartir desde el móvil llega en otra historia.
        if (_uiState.value.pairing.role == Role.Host) return

        val snapshot = MediaMessages.snapshot(text) ?: return
        mediaBase = snapshot
        mediaAt = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(media = snapshot) }
    }

    /** La posición avanza entre las fotos del otro equipo, sin tocar la red. */
    private fun startMediaClock() {
        scope.launch {
            while (true) {
                delay(MEDIA_CLOCK_MILLIS)
                val base = mediaBase ?: continue
                if (!base.hasSession || base.status != PlaybackStatus.Playing) continue

                val position = base.positionAt(SystemClock.elapsedRealtime() - mediaAt)
                if (position != _uiState.value.media.positionMs) {
                    _uiState.update { it.copy(media = it.media.copy(positionMs = position)) }
                }
            }
        }
    }

    private fun clearMedia() {
        mediaBase = null
        _uiState.update { it.copy(media = MediaSnapshot()) }
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
        cancelReconnect()
        peer?.close()
        peer = null
        remote = null
    }

    private fun cancelReconnect() {
        retryJob?.cancel()
        retryJob = null
    }

    /**
     * Escalera de reconexión: solo el controlado reofrece; el controlador
     * espera y contesta. Se para sola al volver el canal o al agotarse.
     */
    private fun startReconnect() {
        if (retryJob?.isActive == true) return
        val role = _uiState.value.pairing.role
        retryJob = scope.launch {
            var plan = RetryPlan()
            while (!plan.isExhausted()) {
                val delaySec = plan.nextDelaySeconds() ?: break
                delay(delaySec * 1000L)
                if (_uiState.value.pairing.link.status == LinkStatus.Ready) break
                if (plan.nextIsRetry() && role == Role.Host) {
                    peer?.restart()
                }
                plan = plan.advance()
            }
            if (_uiState.value.pairing.link.status != LinkStatus.Ready) {
                _uiState.update {
                    it.copy(pairing = it.pairing.copy(link = it.pairing.link.disconnected()))
                }
            }
            retryJob = null
        }
    }

    override fun onCleared() {
        cancelReconnect()
        closePeer()
        scope.cancel()
        signaling.close()
        super.onCleared()
    }

    private companion object {
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE = "ice"

        /** Con qué frecuencia se recalcula la posición entre fotos, en ms. */
        const val MEDIA_CLOCK_MILLIS = 500L
    }
}
