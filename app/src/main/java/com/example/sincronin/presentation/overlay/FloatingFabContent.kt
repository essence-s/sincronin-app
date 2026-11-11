package com.example.sincronin.presentation.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sincronin.ui.theme.FabDark
import com.example.sincronin.ui.theme.SincroninTheme

/** Tamaño del botón: no cambia nunca. */
private val FabSize = 56.dp

/** Opacidad del botón en reposo, para no llamar la atención sobre un video. */
private const val IdleAlpha = 0.55f

/** Color del icono del botón: blanco, para contrastar sobre [FabDark]. */
private val FabOnDark = Color.White

/**
 * Contenido de la ventana pequeña: el botón flotante.
 *
 * - [idle]: en reposo solo se atenúa ([IdleAlpha]); el tamaño sigue siendo el
 *   de siempre y el toque abre el menú igual, así que la atenuación no cuesta
 *   ningún toque extra. La lengüeta también se atenúa: pegada al borde y
 *   difuminada es cuando menos estorba sobre un video.
 * - [peekSide]: si es distinto de 0, el botón está pegado a ese lateral y el
 *   círculo se dibuja desplazado hasta que su centro cae justo sobre el borde,
 *   así que **solo se ve la mitad**. El recorte lo hace esta misma ventana
 *   ([clipToBounds]), sin mandar la ventana fuera de la pantalla.
 * - [peekPull]: cuánto se ha sacado esa lengüeta arrastrándola, de 0 (media
 *   fuera) a 1 (entera dentro). Se sigue al dedo sin animación, así el botón
 *   sale del borde pegado al dedo y sin recortes intermedios.
 * - Al tocarlo se abre el menú y se recupera el color, también desde la
 *   lengüeta: el toque siempre abre, nunca solo despierta.
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
    peekSide: Int,
    peekPull: Float,
    onFabClick: () -> Unit,
    onFabDragStart: () -> Unit,
    onFabDrag: (Offset) -> Unit,
    onFabDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // La atenuación es lo único que cambia de tamaño aparente: un muelle corto
    // la hace discreta sin que el botón baile. Da igual que el botón esté
    // entero o como lengüeta: en reposo siempre se difumina.
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

    // Los callbacks se leen siempre actualizados: el detector de gestos ya no
    // se reinicia al cambiar [peekSide] (ver más abajo), así que si se quedara
    // con las lambdas de la primera composición serían versiones viejas.
    val currentDragStart = rememberUpdatedState(onFabDragStart)
    val currentDrag = rememberUpdatedState(onFabDrag)
    val currentDragEnd = rememberUpdatedState(onFabDragEnd)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Media ventana: el desplazamiento que pone el centro del círculo sobre
        // el borde de la pantalla y deja ver solo la otra mitad.
        val halfWindow = maxWidth / 2
        // [peekPull] va sacando la lengüeta: 0 deja ver la mitad, 1 la mete
        // entera. Al seguirse al dedo el recorte cae siempre en el borde de la
        // pantalla, nunca dentro.
        val remaining = 1f - peekPull.coerceIn(0f, 1f)
        val targetShift: Dp = when {
            peekSide < 0 -> -halfWindow * remaining
            peekSide > 0 -> halfWindow * remaining
            else -> 0.dp
        }

        // Mientras se arrastra, el desplazamiento sigue al dedo sin animación:
        // el servicio no mueve la ventana hasta que la lengüeta está fuera, así
        // que el botón sale del borde pegado al dedo y el recorte cae siempre
        // en el borde de la pantalla. En los demás casos (toque, volver a la
        // lengüeta al cerrar) se anima con el muelle.
        var pulling by remember { mutableStateOf(false) }
        val shift = remember { Animatable(0.dp, Dp.VectorConverter) }
        LaunchedEffect(targetShift, pulling) {
            if (pulling) {
                shift.snapTo(targetShift)
            } else {
                shift.animateTo(
                    targetValue = targetShift,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f)
                )
            }
        }

        // Capa táctil: del tamaño del círculo, y como va desplazada con él,
        // cuando asoma la mitad solo la mitad visible es tocable, así que el
        // resto no le roba toques al video. El tamaño no cambia nunca: si
        // cambiara al sacar la lengüeta, el nodo se movería en mitad del
        // arrastre.
        Box(
            modifier = Modifier
                .size(FabSize)
                .offset(x = shift.value)
                // Sin clave a propósito: arrastrar desde la lengüeta cambia
                // peekSide en el servicio (al terminar de sacarla), y con
                // pointerInput(peekSide) ese cambio reiniciaba el detector en
                // mitad del gesto y lo cancelaba: había que sacar el botón con
                // un toque antes de poder moverlo. Sin clave se arrastra
                // directo desde el borde.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            pulling = true
                            currentDragStart.value()
                        },
                        onDragEnd = {
                            pulling = false
                            currentDragEnd.value()
                        },
                        onDragCancel = {
                            pulling = false
                            currentDragEnd.value()
                        }
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
                .offset(x = shift.value)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    FabDark.copy(alpha = alpha.coerceIn(0f, 1f))
                ),
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
            peekSide = 0,
            peekPull = 0f,
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
            peekSide = 0,
            peekPull = 0f,
            onFabClick = {},
            onFabDragStart = {},
            onFabDrag = {},
            onFabDragEnd = {}
        )
    }
}
