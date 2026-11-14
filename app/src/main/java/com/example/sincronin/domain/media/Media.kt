package com.example.sincronin.domain.media

/** En qué estado está el reproductor que comparte el otro equipo. */
enum class PlaybackStatus { Playing, Paused, Stopped, Unknown }

/** Lo que el invitado le pide al anfitrión por el canal de datos. */
enum class MediaCommand { Play, Pause }

/**
 * Fotografía de lo que se está reproduciendo, tal como se dibuja en el panel.
 *
 * Las posiciones van en milisegundos: el protocolo las manda así y Android
 * trabaja así, sin pasar por ticks.
 */
data class MediaSnapshot(
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val status: PlaybackStatus = PlaybackStatus.Unknown,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackRate: Double = 1.0,
    /** El reproductor permite play o pausa. */
    val canToggle: Boolean = false,
    /** El reproductor permite buscar posiciones. */
    val canSeek: Boolean = false
) {

    /**
     * Dónde va la reproducción [elapsedMs] después de tomar esta foto.
     *
     * El otro equipo publica su estado cada segundo; sin esto la barra
     * avanzaría a saltos. Solo corre si está sonando, y se clava en la
     * duración para no pasarse del final.
     */
    fun positionAt(elapsedMs: Long): Long {
        if (!hasSession || status != PlaybackStatus.Playing) return positionMs
        val advanced = positionMs + (elapsedMs * playbackRate).toLong()
        return if (durationMs > 0) {
            advanced.coerceIn(0, durationMs)
        } else {
            advanced.coerceAtLeast(0)
        }
    }
}
