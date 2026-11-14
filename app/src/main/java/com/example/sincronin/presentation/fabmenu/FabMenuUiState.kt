package com.example.sincronin.presentation.fabmenu

import com.example.sincronin.domain.media.MediaSnapshot
import com.example.sincronin.domain.pairing.PairingState
import com.example.sincronin.domain.room.RoomCode

/** Los dos paneles del menú: emparejar o controlar la reproducción. */
enum class PanelTab { Connection, Controls }

/**
 * Estado inmutable que la vista del menú flotante necesita para dibujarse: si
 * está abierto, cuál es mi código de sala, qué estoy escribiendo, cómo va el
 * emparejamiento, qué pestaña se ve y qué se está reproduciendo.
 */
data class FabMenuUiState(
    val isExpanded: Boolean = false,
    val myCode: RoomCode? = null,
    val typedCode: String = "",
    val pairing: PairingState = PairingState(),
    val tab: PanelTab = PanelTab.Connection,
    val media: MediaSnapshot = MediaSnapshot()
)
