package com.example.sincronin.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pantalla de la app para conceder el permiso de superposición y activar o
 * detener el botón flotante del sistema.
 */
@Composable
fun OverlayControlScreen(
    hasOverlayPermission: Boolean,
    isOverlayRunning: Boolean,
    onGrantPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    needsLocalNetwork: Boolean = false,
    onGrantLocalNetwork: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sincronin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "El botón flotante vive por encima de todas las apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Sin este permiso el panel diría «no se pudo conectar» sin más pista:
        // las conexiones a la LAN se cortan antes de salir del móvil.
        if (needsLocalNetwork) {
            Text(
                text = "Android pide permiso para acceder a la red local. Sin él, el panel no " +
                    "puede alcanzar el servidor de tu red.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onGrantLocalNetwork) {
                Text("Dar acceso a la red local")
            }
            Spacer(Modifier.height(16.dp))
        }

        when {
            !hasOverlayPermission -> {
                Text(
                    text = "Para verlo sobre WhatsApp o cualquier otra app, concede el permiso " +
                        "«Mostrar sobre otras apps».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGrantPermission) {
                    Text("Conceder permiso")
                }
            }

            isOverlayRunning -> {
                Text(
                    text = "Botón flotante activo. Ábrelo desde cualquier app y vuelve aquí para detenerlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onStopOverlay) {
                    Text("Detener botón flotante")
                }
            }

            else -> {
                Button(onClick = onStartOverlay) {
                    Text("Activar botón flotante")
                }
            }
        }
    }
}
