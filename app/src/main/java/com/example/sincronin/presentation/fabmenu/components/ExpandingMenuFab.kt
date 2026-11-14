package com.example.sincronin.presentation.fabmenu.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.graphics.lerp as lerpColor
import com.example.sincronin.domain.link.LinkStatus
import com.example.sincronin.domain.pairing.PairingError
import com.example.sincronin.domain.pairing.PairingState
import com.example.sincronin.domain.pairing.PairingStatus
import com.example.sincronin.domain.pairing.Role
import com.example.sincronin.presentation.fabmenu.FabMenuUiState
import com.example.sincronin.ui.theme.FabDark
import kotlinx.coroutines.delay

private val FabMargin = 20.dp

/** Ancho del panel: el que necesita la fila del código con su botón de copiar. */
private val PanelMaxWidth = 256.dp

/** Alto justo para cabecera, divisor, campo y línea de estado. */
private val PanelHeight = 176.dp

/** Radio de las esquinas: 28 dp en un botón de 56 dp es un círculo. */
private val CircleRadius = 28.dp

/** Abierto, el panel es una tarjeta de esquinas apenas redondeadas. */
private val PanelRadius = 20.dp

/**
 * Fondo del panel: un degradado de negros con muy poco recorrido, para que se
 * note el volumen sin que aparezca ningún tinte de color.
 */
private val PanelGradient = Brush.verticalGradient(
    listOf(Color(0xFF171717), Color(0xFF0C0C0C))
)

/** Negro neutro del panel abierto: no depende de la paleta dinámica del tema. */
private val PanelBase = Color(0xFF101010)

private val PanelText = Color(0xFFF4F4F8)
private val PanelSecondary = Color(0xFF8D8D99)
private val PanelMuted = Color(0xFF6F6F7C)
private val PanelAccent = Color(0xFF7C7CFF)
private val PanelError = Color(0xFFFF7A7A)
private val FieldBackground = Color(0xFF121212)

/**
 * Botón flotante que crece hasta convertirse en el panel de Conexión.
 *
 * Todo el morph lo gobierna [progress] (0 = botón, 1 = panel), de modo que
 * tamaño, posición, radio, color y sombra avanzan juntos y el panel **crece
 * desde el botón**: [fabRectPx] es su rectángulo real en px del área segura y
 * el panel se ancla a su esquina inferior derecha, moviéndose solo lo justo
 * para no salirse del área segura.
 *
 * [progress] puede sobrepasar 1 con el rebote del muelle; el desbordamiento se
 * aplica solo a la geometría (un estirón), nunca al color ni a las opacidades.
 */
@Composable
fun ExpandingMenuFab(
    progress: Float,
    interactive: Boolean,
    fabRectPx: IntRect,
    state: FabMenuUiState,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
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
        val containerColor = lerpColor(FabDark, PanelBase, visual)

        // El icono se va en el primer tercio y el panel llega al final: en
        // progress 0 esto se ve exactamente igual que el botón real.
        val iconAlpha = (1f - visual / 0.3f).coerceIn(0f, 1f)
        val panelAlpha = ((visual - 0.4f) / 0.6f).coerceIn(0f, 1f)

        // Círculo al cerrar, tarjeta al abrir: el radio se interpola con la
        // misma animación que el tamaño, así que el borde no salta.
        val shape = RoundedCornerShape(lerpDp(CircleRadius, PanelRadius, visual))

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = left, y = top)
                .size(width = width, height = height)
                .shadow(elevation = elevation, shape = shape, clip = false)
                .clip(shape)
                .background(containerColor)
        ) {
            // Fondo del diseño, solo mientras el panel está abierto.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(visual)
                    .background(PanelGradient)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color.White.copy(alpha = 0.08f * visual), shape)
            )

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
                ConnectionPanel(
                    state = state,
                    enabled = interactive,
                    onClose = onClose,
                    onCodeChange = onCodeChange,
                    onJoin = onJoin
                )
            }
        }
    }
}

/**
 * Panel de Conexión: mi código de sala para dictarlo, el campo para entrar a la
 * sala del otro equipo y una línea que dice cómo va el emparejamiento.
 */
@Composable
private fun ConnectionPanel(
    state: FabMenuUiState,
    enabled: Boolean,
    onClose: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 22.dp)
    ) {
        // El aspa cierra desde la esquina de la tarjeta y no gasta fila propia.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TU SALA",
                color = PanelMuted,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            CloseButton(enabled = enabled, onClose = onClose)
        }

        // Copiar comparte fila con el código, así que su texto queda centrado
        // respecto a la cifra y no entre la etiqueta y el número.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.myCode?.value ?: "······",
                color = PanelText,
                fontSize = 19.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            StatusDot(state.pairing)
            Spacer(Modifier.weight(1f))
            CopyButton(code = state.myCode?.value, enabled = enabled)
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CodeField(
                value = state.typedCode,
                enabled = enabled,
                onValueChange = onCodeChange,
                onDone = onJoin,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onJoin,
                enabled = enabled && state.typedCode.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelAccent,
                    contentColor = Color(0xFF0B0B12),
                    disabledContainerColor = Color(0xFF23232C),
                    disabledContentColor = PanelMuted
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(text = "Entrar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.weight(1f))

        StatusLine(state.pairing)
    }
}

@Composable
private fun CodeField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = PanelText,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(PanelAccent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FieldBackground),
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Código de sala",
                        color = PanelMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                field()
            }
        }
    )
}

/**
 * Cerrar desde la esquina superior derecha: un aspa pequeña y callada, sin una
 * fila propia que se comiera media tarjeta. Tocar el fondo también cierra.
 */
@Composable
private fun CloseButton(enabled: Boolean, onClose: () -> Unit) {
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

/**
 * Copiar el código propio. Cambia a «Copiado» un momento para que se note que
 * el toque hizo algo, y se corta solo: no deja un estado que haya que limpiar.
 */
@Composable
private fun CopyButton(code: String?, enabled: Boolean) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }

    // Caja de altura fija: el texto se centra dentro de ella y no por su
    // interlineado, así queda a la altura del código de la sala.
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && code != null) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sala de Sincronin", code))
                copied = true
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (copied) "Copiado" else "Copiar",
            color = if (copied) PanelAccent else PanelSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusDot(pairing: PairingState) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(statusColor(pairing))
    )
}

@Composable
private fun StatusLine(pairing: PairingState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(pairing)
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusText(pairing),
            color = statusColor(pairing),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

private fun statusColor(pairing: PairingState): Color = when (val status = pairing.status) {
    PairingStatus.Idle, PairingStatus.Waiting -> PanelMuted
    PairingStatus.Connected -> when (pairing.link.status) {
        LinkStatus.Ready -> PanelAccent
        LinkStatus.Failed, LinkStatus.Disconnected -> PanelError
        LinkStatus.Reconnecting -> PanelMuted
        LinkStatus.Closed, LinkStatus.Negotiating -> PanelMuted
    }
    is PairingStatus.Failed -> PanelError
}

private fun statusText(pairing: PairingState): String = when (val status = pairing.status) {
    PairingStatus.Idle -> "Escribí el código del otro equipo"
    PairingStatus.Waiting -> "${roleLabel(pairing)} · esperando al otro equipo"
    PairingStatus.Connected -> when (pairing.link.status) {
        LinkStatus.Negotiating -> "${roleLabel(pairing)} · enlazando…"
        LinkStatus.Ready -> "${roleLabel(pairing)} · enlace listo"
        LinkStatus.Failed -> "No se pudo enlazar con el otro equipo"
        LinkStatus.Reconnecting -> "${roleLabel(pairing)} · Reconectando…"
        LinkStatus.Disconnected -> "${roleLabel(pairing)} · Sin conexión"
        LinkStatus.Closed -> "${roleLabel(pairing)} · ${pairing.people} en la sala"
    }
    is PairingStatus.Failed -> when (status.error) {
        PairingError.InvalidCode -> "Ese código no es válido"
        PairingError.ServerUnreachable -> "No se pudo conectar con el servidor"
    }
}

private fun roleLabel(pairing: PairingState): String = when (pairing.role) {
    Role.Host -> "Anfitrión"
    Role.Guest -> "Invitado"
    null -> "En la sala"
}
