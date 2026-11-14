package com.example.sincronin.domain.media

import org.json.JSONObject

/**
 * Mensajes de medios del canal de datos, con los mismos formatos que el
 * escritorio: el estado llega como JSON con `type: "state"` y los comandos
 * viajan como texto plano.
 */
object MediaMessages {

    /** Lo que el invitado pide, en el vocabulario del protocolo. */
    fun command(command: MediaCommand): String = when (command) {
        MediaCommand.Play -> "play"
        MediaCommand.Pause -> "pause"
    }

    /** Lee un comando de texto; `null` si no es uno conocido. */
    fun parseCommand(text: String): MediaCommand? = when (text.trim()) {
        "play" -> MediaCommand.Play
        "pause" -> MediaCommand.Pause
        else -> null
    }

    /**
     * Lee el estado que publica el otro equipo; `null` si el mensaje no es un
     * estado válido.
     */
    fun snapshot(text: String): MediaSnapshot? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (json.optString("type") != "state" || !json.has("has_session")) return null

        val hasSession = json.optBoolean("has_session")
        return MediaSnapshot(
            hasSession = hasSession,
            title = json.optString("title"),
            artist = json.optString("artist"),
            status = statusOf(json.optString("playback_status")),
            positionMs = json.optLong("position_ms").coerceAtLeast(0),
            durationMs = json.optLong("duration_ms").coerceAtLeast(0),
            playbackRate = json.optDouble("playback_rate", 1.0),
            // Un anfitrión de una versión anterior no manda las capacidades:
            // si hay sesión, se asume que se puede controlar.
            canToggle = if (json.has("can_toggle")) json.optBoolean("can_toggle") else hasSession,
            canSeek = if (json.has("can_seek")) json.optBoolean("can_seek") else hasSession
        )
    }

    private fun statusOf(name: String): PlaybackStatus = when (name) {
        "playing" -> PlaybackStatus.Playing
        "paused" -> PlaybackStatus.Paused
        "stopped" -> PlaybackStatus.Stopped
        else -> PlaybackStatus.Unknown
    }
}
