package com.example.sincronin.presentation.fabmenu

import com.example.sincronin.domain.pairing.PairingState
import com.example.sincronin.domain.room.RoomCode

/**
 * Estado inmutable que la vista del menú flotante necesita para dibujarse: si
 * está abierto, cuál es mi código de sala, qué estoy escribiendo y cómo va el
 * emparejamiento.
 */
data class FabMenuUiState(
    val isExpanded: Boolean = false,
    val myCode: RoomCode? = null,
    val typedCode: String = "",
    val pairing: PairingState = PairingState()
)
