package com.example.sincronin.presentation.fabmenu

import androidx.lifecycle.ViewModel
import com.example.sincronin.BuildConfig
import com.example.sincronin.adapters.OkHttpSignalingSocket
import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.pairing.PairingStatus
import com.example.sincronin.domain.room.RoomCode
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

/**
 * Estado del botón flotante y de la sala a la que está conectado.
 *
 * La vista (ventana flotante) solo observa y emite eventos; aquí se decide qué
 * significa cada cosa que contesta el servidor. El socket entra por el puerto
 * [SignalingSocket], así que este archivo no sabe que hay un WebSocket.
 */
class FabMenuViewModel(
    private val signaling: SignalingSocket = OkHttpSignalingSocket(BuildConfig.SIGNAL_URL)
) : ViewModel() {

    // Sin ciclo de vida propio: este ViewModel lo vive y lo destruye el
    // servicio, que ya tiene su propio scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val myCode = RoomCode.generate()

    private val _uiState = MutableStateFlow(FabMenuUiState(myCode = myCode))
    val uiState: StateFlow<FabMenuUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(pairing = it.pairing.joining(room)) }
        signaling.connect(room)
    }

    private fun onSignalingEvent(event: SignalingEvent) {
        _uiState.update { state ->
            when (event) {
                is SignalingEvent.Joined -> state.copy(pairing = state.pairing.joined(event.people))
                is SignalingEvent.Failed -> state.copy(pairing = state.pairing.failed(event.error))
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        signaling.close()
        super.onCleared()
    }
}
