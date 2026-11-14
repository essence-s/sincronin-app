package com.example.sincronin.presentation.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import com.example.sincronin.presentation.fabmenu.FabMenuUiState
import com.example.sincronin.presentation.fabmenu.PanelTab
import com.example.sincronin.presentation.fabmenu.components.ExpandingMenuFab
import com.example.sincronin.ui.theme.SincroninTheme

/**
 * Muelle de apertura: arranca rápido y se asienta con un rebote mínimo, así el
 * cambio de tamaño se percibe como un estirón suave y no como un salto.
 */
private val ExpandSpec = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = 900f,
    visibilityThreshold = 0.001f
)

/** Cierre sin rebote y más corto: volver al botón no debe llamar la atención. */
private val CollapseSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 1400f,
    visibilityThreshold = 0.001f
)

/**
 * Velo del fondo: un degradado de negro que se apaga hacia abajo, muy suave.
 * No es un color, solo oscurece lo que hay debajo para que el panel resalte.
 */
private val ScrimGradient = Brush.verticalGradient(
    listOf(Color.Black, Color.Black.copy(alpha = 0.45f))
)

private const val ScrimMaxAlpha = 0.32f

/**
 * Contenido de la ventana del panel, que ocupa el área segura completa mientras
 * el menú está abierto.
 *
 * La ventana se añade al pulsar el botón y se quita cuando la animación de
 * cierre devuelve el panel sobre el botón ([onCollapseFinished]), así que la
 * expansión siempre nace del sitio donde está el FAB ([fabRectPx]).
 *
 * [onClose] se dispara al tocar el fondo oscurecido; los demás eventos son los
 * del panel y los resuelve el ViewModel.
 */
@Composable
fun FloatingOverlayContent(
    state: FabMenuUiState,
    fabRectPx: IntRect,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onTabSelect: (PanelTab) -> Unit,
    onTogglePlayPause: () -> Unit,
    onCollapseFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = panel convertido en botón, 1 = panel abierto. Una sola animación para
    // todo el morph, así que no puede arrancar desde otra esquina.
    val progress = remember { Animatable(0f) }

    // Los botones solo responden cuando el muelle ya se asentó.
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            settled = false
            progress.animateTo(targetValue = 1f, animationSpec = ExpandSpec)
            settled = true
        } else {
            settled = false
            progress.animateTo(targetValue = 0f, animationSpec = CollapseSpec)
            onCollapseFinished()
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val scrimAlpha = ScrimMaxAlpha * progress.value.coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha)
                .background(ScrimGradient)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = state.isExpanded,
                    onClick = onClose
                )
        )

        ExpandingMenuFab(
            progress = progress.value,
            interactive = state.isExpanded && settled,
            fabRectPx = fabRectPx,
            state = state,
            onClose = onClose,
            onCodeChange = onCodeChange,
            onJoin = onJoin,
            onTabSelect = onTabSelect,
            onTogglePlayPause = onTogglePlayPause,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun FloatingOverlayContentPreview() {
    SincroninTheme {
        FloatingOverlayContent(
            state = FabMenuUiState(isExpanded = true),
            fabRectPx = IntRect(left = 300, top = 700, right = 356, bottom = 756),
            onClose = {},
            onCodeChange = {},
            onJoin = {},
            onTabSelect = {},
            onTogglePlayPause = {},
            onCollapseFinished = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun FloatingOverlayControlsPreview() {
    SincroninTheme {
        FloatingOverlayContent(
            state = FabMenuUiState(isExpanded = true, tab = PanelTab.Controls),
            fabRectPx = IntRect(left = 300, top = 700, right = 356, bottom = 756),
            onClose = {},
            onCodeChange = {},
            onJoin = {},
            onTabSelect = {},
            onTogglePlayPause = {},
            onCollapseFinished = {}
        )
    }
}
