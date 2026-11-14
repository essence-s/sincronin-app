package com.example.sincronin.presentation.fabmenu.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sincronin.domain.link.LinkStatus
import com.example.sincronin.domain.media.PlaybackStatus
import com.example.sincronin.presentation.fabmenu.FabMenuUiState
import com.example.sincronin.presentation.fabmenu.PanelTab

/**
 * Panel de Controles, con la misma disposición que el del escritorio: el tema
 * a la izquierda, play/pausa a la derecha y la línea de progreso a lo ancho.
 *
 * Muestra lo que publica el otro equipo. La barra todavía no se arrastra: eso
 * es otra historia.
 */
@Composable
internal fun ControlsPanel(
    state: FabMenuUiState,
    enabled: Boolean,
    onTabSelect: (PanelTab) -> Unit,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val media = state.media
    // El botón solo responde con el enlace abierto y si el reproductor del otro
    // equipo permite play/pausa.
    val linked = enabled && state.pairing.link.status == LinkStatus.Ready
    val canToggle = linked && media.hasSession && media.canToggle

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
    ) {
        PanelHeader(
            tab = state.tab,
            enabled = enabled,
            onSelect = onTabSelect,
            onClose = onClose
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.title.ifEmpty { "Esperando al reproductor…" },
                    color = if (media.hasSession) PanelText else PanelSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (media.artist.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = media.artist,
                        color = PanelSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            PlayPauseButton(
                playing = media.status == PlaybackStatus.Playing,
                enabled = canToggle,
                onClick = onTogglePlayPause
            )
        }

        Spacer(Modifier.weight(1f))

        ProgressBar(positionMs = media.positionMs, durationMs = media.durationMs)

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(media.positionMs),
                color = PanelMuted,
                fontSize = 9.sp,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(media.durationMs),
                color = PanelMuted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Botón principal: reproduce o pausa. Va con el acento, como el botón de
 * entrar del panel de Conexión, y se apaga cuando el reproductor no lo permite.
 *
 * Los iconos se dibujan a mano: «pausa» no está en el paquete de iconos que usa
 * la app y no vale traer uno entero por dos barras.
 */
@Composable
private fun PlayPauseButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelAccent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            if (playing) {
                val bar = size.width * 0.3f
                drawRoundRect(
                    color = PanelInk,
                    topLeft = Offset(0f, 0f),
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(bar / 2, bar / 2)
                )
                drawRoundRect(
                    color = PanelInk,
                    topLeft = Offset(size.width - bar, 0f),
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(bar / 2, bar / 2)
                )
            } else {
                val triangle = Path().apply {
                    moveTo(size.width * 0.2f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(size.width * 0.2f, size.height)
                    close()
                }
                drawPath(triangle, PanelInk)
            }
        }
    }
}

/** Línea de progreso de solo lectura: la posición que publica el otro equipo. */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val centerY = size.height / 2
        val thickness = 4.dp.toPx()
        val left = thickness / 2
        val track = (size.width - thickness).coerceAtLeast(0f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(left, centerY - thickness / 2),
            size = Size(track, thickness),
            cornerRadius = CornerRadius(thickness / 2)
        )

        val filled = track * fraction
        if (filled > 0f) {
            drawRoundRect(
                color = PanelAccent,
                topLeft = Offset(left, centerY - thickness / 2),
                size = Size(filled, thickness),
                cornerRadius = CornerRadius(thickness / 2)
            )
        }

        if (durationMs > 0) {
            drawCircle(
                color = PanelAccent,
                radius = 4.dp.toPx(),
                center = Offset(left + filled, centerY)
            )
        }
    }
}

/** `m:ss`, o `h:mm:ss` si pasa de una hora. */
private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
