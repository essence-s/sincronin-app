package com.example.sincronin

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
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
    private val hasLocalNetwork = mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshOverlayState()
    }

    /**
     * Android 17 pide permiso para tocar la red local. Se pide antes de
     * arrancar el botón: si se deniega, el panel no podría alcanzar el
     * servidor y solo diría «no se pudo conectar» sin explicar por qué.
     */
    private val localNetworkLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocalNetwork.value = granted
        if (granted) startOverlay()
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
                    needsLocalNetwork = needsLocalNetwork() && !hasLocalNetwork.value,
                    onGrantPermission = ::openOverlaySettings,
                    onGrantLocalNetwork = ::requestLocalNetwork,
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
        hasLocalNetwork.value = !needsLocalNetwork() ||
            ContextCompat.checkSelfPermission(this, ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Solo a partir de Android 17 el permiso existe y hace falta. */
    private fun needsLocalNetwork(): Boolean = Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK

    private fun requestLocalNetwork() {
        if (!needsLocalNetwork()) {
            hasLocalNetwork.value = true
            return
        }
        runCatching { localNetworkLauncher.launch(ACCESS_LOCAL_NETWORK) }
            .onFailure { hasLocalNetwork.value = false }
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
        if (needsLocalNetwork() && !hasLocalNetwork.value) {
            requestLocalNetwork()
            return
        }
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

    private companion object {
        /** Permiso de red local: existe desde Android 17 (API 37). */
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val LOCAL_NETWORK_SDK = 37
    }
}
