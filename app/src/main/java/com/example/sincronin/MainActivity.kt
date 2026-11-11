package com.example.sincronin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.example.sincronin.overlay.FloatingOverlayService
import com.example.sincronin.presentation.overlay.OverlayControlScreen
import com.example.sincronin.ui.theme.SincroninTheme

class MainActivity : ComponentActivity() {

    private val hasOverlayPermission = mutableStateOf(false)
    private val isOverlayRunning = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshOverlayState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app es siempre oscura: iconos claros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        refreshOverlayState()

        setContent {
            SincroninTheme(darkTheme = true) {
                OverlayControlScreen(
                    hasOverlayPermission = hasOverlayPermission.value,
                    isOverlayRunning = isOverlayRunning.value,
                    onGrantPermission = ::openOverlaySettings,
                    onStartOverlay = ::startOverlay,
                    onStopOverlay = ::stopOverlay
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
    }

    private fun refreshOverlayState() {
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        isOverlayRunning.value = FloatingOverlayService.isRunning
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { permissionLauncher.launch(intent) }
            .onFailure { refreshOverlayState() }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, FloatingOverlayService::class.java)
        )
        isOverlayRunning.value = true
    }

    private fun stopOverlay() {
        stopService(Intent(this, FloatingOverlayService::class.java))
        isOverlayRunning.value = false
    }
}
