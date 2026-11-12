package com.example.sincronin.presentation.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sincronin.ui.theme.FabDark
import com.example.sincronin.ui.theme.SincroninTheme

/** Tamaño del botón: no cambia nunca. */
private val FabSize = 56.dp

/** Opacidad del botón en reposo, para no llamar la atención sobre un video. */
private const val IdleAlpha = 0.35f

/** Color del icono del botón: blanco, para contrastar sobre [FabDark]. */
private val FabOnDark = Color.White

/**
 * Contenido de la ventana pequeña: el botón flotante.
 *
 * - [idle]: en reposo solo se atenúa ([IdleAlpha]); el tamaño sigue siendo el
 *   de siempre y el toque abre el menú igual, así que la atenuación no cuesta
 *   ningún toque extra. El botón acoplado también se atenúa: medio círculo pegado
 *   al borde y difuminado es cuando menos estorba sobre un video.
 * - Al tocarlo se abre el menú y se recupera el color, también acoplado al
 *   borde: el toque siempre abre, nunca solo despierta.
 *
 * El botón está **siempre centrado** en su ventana y el acople lo hace el
 * servicio moviendo la ventana media fuera de la pantalla. El contenido no se
 * desplaza nunca, así que el círculo que se dibuja y la zona que recibe el
 * toque son el mismo rectángulo: no hay forma de que la mitad visible deje de
 * responder. Solo el sistema recorta ambos al borde real de la pantalla.
 *
 * Esta ventana no cambia de tamaño ni de posición al abrir el menú (el panel
 * vive en otra ventana), así que el botón nunca se mueve ni parpadea.
 *
 * [onFabDrag] recibe los píxeles que el dedo mueve el botón; el servicio es
 * quien reubica la ventana porque el contenido no puede cambiar su posición.
 */
@Composable
fun FloatingFabContent(
    idle: Boolean,
    onFabClick: () -> Unit,
    onFabDragStart: () -> Unit,
    onFabDrag: (Offset) -> Unit,
    onFabDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // La atenuación es lo único que cambia de apariencia: un muelle corto la
    // hace discreta sin que el botón baile.
    val alpha by animateFloatAsState(
        targetValue = if (idle) IdleAlpha else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 700f,
            visibilityThreshold = 0.001f
        ),
        label = "fabAlpha"
    )
    val interactionSource = remember { MutableInteractionSource() }

    // Los callbacks se leen siempre actualizados: el detector de gestos no se
    // reinicia entre arrastres, así que si se quedara con las lambdas de la
    // primera composición serían versiones viejas.
    val currentDragStart = rememberUpdatedState(onFabDragStart)
    val currentDrag = rememberUpdatedState(onFabDrag)
    val currentDragEnd = rememberUpdatedState(onFabDragEnd)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Capa táctil: exactamente el círculo, sin desplazamientos propios, así
        // que cuando la ventana asoma media fuera el sistema recorta el toque
        // igual que el dibujo. Se arrastra directo desde el borde y el
        // movimiento lo aplica el servicio sin animación, pegado al dedo.
        Box(
            modifier = Modifier
                .size(FabSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentDragStart.value() },
                        onDragEnd = { currentDragEnd.value() },
                        onDragCancel = { currentDragEnd.value() }
                    ) { change, dragAmount ->
                        change.consume()
                        currentDrag.value(dragAmount)
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onFabClick()
                }
        )

        // Círculo visible: al atenuarse deja ver el video de debajo. No recibe
        // toques, deja pasar al de abajo.
        Box(
            modifier = Modifier
                .size(FabSize)
                // La opacidad envuelve sombra, fondo e icono: en reposo se
                // desvanece el botón entero, no solo el círculo.
                .alpha(alpha.coerceIn(0f, 1f))
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(FabDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Abrir menú",
                tint = FabOnDark,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Preview(widthDp = 72, heightDp = 72)
@Composable
private fun FloatingFabIdlePreview() {
    SincroninTheme {
        FloatingFabContent(
            idle = true,
            onFabClick = {},
            onFabDragStart = {},
            onFabDrag = {},
            onFabDragEnd = {}
        )
    }
}

@Preview(widthDp = 72, heightDp = 72)
@Composable
private fun FloatingFabActivePreview() {
    SincroninTheme {
        FloatingFabContent(
            idle = false,
            onFabClick = {},
            onFabDragStart = {},
            onFabDrag = {},
            onFabDragEnd = {}
        )
    }
}
