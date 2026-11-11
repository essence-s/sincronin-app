package com.example.sincronin.presentation.fabmenu

/**
 * Estado inmutable que la vista del menú flotante necesita para dibujarse.
 * Por ahora el menú no tiene secciones: solo si está abierto o cerrado.
 */
data class FabMenuUiState(
    val isExpanded: Boolean = false
)
