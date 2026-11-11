package com.example.sincronin.presentation.fabmenu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.graphics.lerp as lerpColor
import com.example.sincronin.ui.theme.FabDark

private val FabMargin = 20.dp

/**
 * El panel se ajusta al contenido: por ahora solo la cabecera de cierre, así
 * que cuanto más cerca queda del tamaño del botón, menos se nota el cambio.
 */
private val PanelMaxWidth = 248.dp

/** Alto del panel abierto: solo la cabecera de cierre. */
private val PanelHeight = 60.dp

/** Radio de las esquinas: 28 dp en un botón de 56 dp es un círculo. */
private val ShapeRadius = 28.dp

/**
 * Botón flotante que crece hasta convertirse en un pequeño panel con el botón
 * de cerrar. El menú está vacío por ahora, a la espera de sus secciones.
 *
 * Todo el morph lo gobierna [progress] (0 = botón, 1 = panel), de modo que
 * tamaño, posición, color y sombra avanzan juntos y el panel **crece desde el
 * botón**: [fabRectPx] es su rectángulo real en px del área segura y el panel
 * se ancla a su esquina inferior derecha, moviéndose solo lo justo para no
 * salirse del área segura.
 *
 * [progress] puede sobrepasar 1 con el rebote del muelle; el desbordamiento se
 * aplica solo a la geometría (un estirón), nunca al color ni a las opacidades.
 */
@Composable
fun ExpandingMenuFab(
    progress: Float,
    interactive: Boolean,
    fabRectPx: IntRect,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fabLeft = with(density) { fabRectPx.left.toDp() }
        val fabTop = with(density) { fabRectPx.top.toDp() }
        val fabWidth = with(density) { fabRectPx.width.toDp() }
        val fabHeight = with(density) { fabRectPx.height.toDp() }

        val panelWidth = minOf(maxWidth - FabMargin * 2, PanelMaxWidth)
        val panelHeight = PanelHeight.coerceAtMost(maxHeight - FabMargin * 2)
        // El panel nace de la esquina inferior derecha del botón y se coloca
        // dejando [FabMargin] de aire con el borde, pero nunca más de lo que
        // permite seguir tapando el botón: un botón que viene de la lengüeta
        // está a 8 dp del lateral, así que con el margen completo el panel se
        // apartaba 12 dp y dejaba ver esa franja del FAB detrás del menú.
        // El tope es la propia posición del botón: a 8 dp queda el mismo aire
        // que cuando está pegado al borde derecho o inferior, y le tapa justo
        // hasta su borde.
        val panelLeft = (fabLeft + fabWidth - panelWidth).coerceIn(
            minOf(FabMargin, fabLeft),
            minOf(fabLeft, (maxWidth - panelWidth).coerceAtLeast(0.dp))
        )
        val panelTop = (fabTop + fabHeight - panelHeight).coerceIn(
            minOf(FabMargin, fabTop),
            minOf(fabTop, (maxHeight - panelHeight).coerceAtLeast(0.dp))
        )

        val width = lerpDp(fabWidth, panelWidth, progress)
        val height = lerpDp(fabHeight, panelHeight, progress)

        // Geometría: admite el rebote (progress > 1) sin salirse de la pantalla.
        // No se usa FabMargin aquí: en progress 0 la posición tiene que ser
        // exactamente la del FAB, y un botón pegado al borde está a menos de
        // ese margen. Empujarlo a 20 dp hacía que al abrir el menú el botón
        // desapareciera de su sitio y apareciera 12 dp más adentro.
        val left = lerpDp(fabLeft, panelLeft, progress)
            .coerceIn(0.dp, (maxWidth - width).coerceAtLeast(0.dp))
        val top = lerpDp(fabTop, panelTop, progress)
            .coerceIn(0.dp, (maxHeight - height).coerceAtLeast(0.dp))

        // Color y opacidades se quedan dentro de 0..1.
        val visual = progress.coerceIn(0f, 1f)
        val elevation = lerpDp(8.dp, 14.dp, visual)
        // El círculo cerrado usa el mismo color que el botón real: mientras el
        // panel se cierra encima del FAB no puede notarse un cambio de color.
        val containerColor = lerpColor(
            FabDark,
            MaterialTheme.colorScheme.surface,
            visual
        )

        // El icono se va en el primer tercio y el panel llega al final: en
        // progress 0 esto se ve exactamente igual que el botón real.
        val iconAlpha = (1f - visual / 0.3f).coerceIn(0f, 1f)
        val panelAlpha = ((visual - 0.4f) / 0.6f).coerceIn(0f, 1f)

        val shape = RoundedCornerShape(ShapeRadius)

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = left, y = top)
                .size(width = width, height = height)
                .shadow(elevation = elevation, shape = shape, clip = false)
                .clip(shape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Abrir menú",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
                    .alpha(iconAlpha)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(panelAlpha)
            ) {
                MenuPanel(
                    onClose = onClose,
                    enabled = interactive
                )
            }
        }
    }
}

@Composable
private fun MenuPanel(
    onClose: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onClose,
                enabled = enabled,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar menú",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
