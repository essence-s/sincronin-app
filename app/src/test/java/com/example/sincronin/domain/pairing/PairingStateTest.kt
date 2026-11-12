package com.example.sincronin.domain.pairing

import com.example.sincronin.domain.room.RoomCode
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateTest {

    private val code = RoomCode.parse("ABC234")!!

    @Test
    fun `sala vacia me deja de anfitrion y con gente de invitado`() {
        val alone = PairingState().joining(code).joined(people = 1)

        assertEquals(Role.Host, alone.role)
        assertEquals(PairingStatus.Waiting, alone.status)
        assertEquals(1, alone.people)

        val withPeer = PairingState().joining(code).joined(people = 2)

        assertEquals(Role.Guest, withPeer.role)
        assertEquals(PairingStatus.Connected, withPeer.status)
        assertEquals(2, withPeer.people)
    }

    @Test
    fun `un error deja el motivo a la vista y se puede reintentar`() {
        val failed = PairingState().joining(code).failed(PairingError.ServerUnreachable)

        assertEquals(PairingStatus.Failed(PairingError.ServerUnreachable), failed.status)
        assertEquals(code, failed.room)

        val retried = failed.retrying()

        assertEquals(PairingStatus.Waiting, retried.status)
        assertEquals(code, retried.room)
    }
}
