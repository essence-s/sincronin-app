package com.example.sincronin.presentation.fabmenu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Cabecera común de los dos paneles: el selector de vista y el aspa de cerrar.
 *
 * Las dos vistas van dentro de una misma píldora, como un control segmentado:
 * así se lee que hay dos interfaces y que una está elegida, en vez de dos
 * enlaces sueltos pegados al código. Es la versión horizontal del menú lateral
 * del escritorio, que también cambia entre Conexión y Controles dentro de la
 * misma ventana.
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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(11.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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

/**
 * Una vista del selector. La elegida se queda con un fondo del acento y el
 * texto aclarado; la otra es solo texto, para que el segmentado tenga un
 * centro claro sin competir con el código de la sala.
 */
@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) PanelAccent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) PanelSoft else PanelSecondary,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
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
