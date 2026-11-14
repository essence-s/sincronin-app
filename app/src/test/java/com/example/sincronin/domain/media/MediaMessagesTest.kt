package com.example.sincronin.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMessagesTest {

    @Test
    fun `el estado del otro equipo se lee con tema, artista, posicion y duracion`() {
        val json = """{"type":"state","has_session":true,"playback_status":"playing",
            "position_ms":125000,"duration_ms":3600000,"playback_rate":1.0,
            "title":"Canción","artist":"Artista","can_toggle":true,"can_seek":false}"""

        val snapshot = MediaMessages.snapshot(json)

        assertEquals("Canción", snapshot?.title)
        assertEquals("Artista", snapshot?.artist)
        assertEquals(PlaybackStatus.Playing, snapshot?.status)
        assertEquals(125_000L, snapshot?.positionMs)
        assertEquals(3_600_000L, snapshot?.durationMs)
    }

    @Test
    fun `un estado sin capacidades se puede controlar si hay sesion`() {
        val json = """{"type":"state","has_session":true,"playback_status":"paused",
            "position_ms":0,"duration_ms":0,"title":"Canción","artist":"Artista"}"""

        val snapshot = MediaMessages.snapshot(json)

        assertTrue(snapshot?.canToggle == true)
        assertTrue(snapshot?.canSeek == true)
    }

    @Test
    fun `play y pause viajan como texto y lo desconocido se ignora`() {
        assertEquals("play", MediaMessages.command(MediaCommand.Play))
        assertEquals("pause", MediaMessages.command(MediaCommand.Pause))

        assertEquals(MediaCommand.Play, MediaMessages.parseCommand(" play "))
        assertNull(MediaMessages.parseCommand("hola"))
        assertNull(MediaMessages.snapshot("play"))
    }

    @Test
    fun `la posicion avanza sola mientras suena y se clava en la duracion`() {
        val playing = MediaSnapshot(
            hasSession = true,
            status = PlaybackStatus.Playing,
            positionMs = 1_000,
            durationMs = 10_000
        )
        val paused = playing.copy(status = PlaybackStatus.Paused)

        assertEquals(3_000L, playing.positionAt(2_000))
        assertEquals(1_000L, paused.positionAt(2_000))
        assertEquals(10_000L, playing.positionAt(60_000))
    }
}
