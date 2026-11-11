package com.example.sincronin.presentation.fabmenu

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Mantiene el estado del botón flotante: si está expandido.
 * La vista (ventana flotante) solo observa y emite eventos.
 */
class FabMenuViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FabMenuUiState())
    val uiState: StateFlow<FabMenuUiState> = _uiState.asStateFlow()

    fun onFabClick() {
        _uiState.update { it.copy(isExpanded = true) }
    }

    fun onDismiss() {
        _uiState.update { it.copy(isExpanded = false) }
    }
}
