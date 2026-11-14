package com.example.sincronin.presentation.fabmenu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sincronin.presentation.fabmenu.PanelTab

/**
 * Cabecera común de los dos paneles: las pestañas y el aspa de cerrar.
 *
 * Es la versión horizontal del menú lateral del escritorio, que también cambia
 * entre Conexión y Controles dentro de la misma ventana.
 */
@Composable
internal fun PanelHeader(
    tab: PanelTab,
    enabled: Boolean,
    onSelect: (PanelTab) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TabButton(
                label = "Conexión",
                selected = tab == PanelTab.Connection,
                enabled = enabled,
                onClick = { onSelect(PanelTab.Connection) }
            )
            TabButton(
                label = "Controles",
                selected = tab == PanelTab.Controls,
                enabled = enabled,
                onClick = { onSelect(PanelTab.Controls) }
            )
        }
        Spacer(Modifier.weight(1f))
        CloseButton(enabled = enabled, onClose = onClose)
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) PanelAccent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) PanelAccent else PanelMuted,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Cerrar desde la esquina superior derecha: un aspa pequeña y callada, sin una
 * fila propia que se comiera media tarjeta. Tocar el fondo también cierra.
 */
@Composable
internal fun CloseButton(enabled: Boolean, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Cerrar menú",
            tint = PanelMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
