package com.example.sincronin.domain.link

import com.example.sincronin.domain.pairing.PairingState
import com.example.sincronin.domain.pairing.Role
import com.example.sincronin.domain.room.RoomCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkStateTest {

    private val code = RoomCode.parse("ABC234")!!

    @Test
    fun `un enlace nuevo empieza cerrado aunque este solo en la sala`() {
        val alone = PairingState().joining(code).joined(people = 1)

        assertEquals(LinkStatus.Closed, alone.link.status)
        assertNull(alone.link.peer)
    }

    @Test
    fun `solo el anfitrion propone el enlace y el invitado espera la propuesta`() {
        val host = LinkState().peerPresent(peer = 7, role = Role.Host)

        assertEquals(LinkIntent.Propose, host.intent)
        assertEquals(LinkStatus.Negotiating, host.state.status)
        assertEquals(7L, host.state.peer)

        val guest = LinkState().peerPresent(peer = 7, role = Role.Guest)

        assertEquals(LinkIntent.None, guest.intent)
        assertEquals(LinkStatus.Negotiating, guest.state.status)
    }

    @Test
    fun `un mensaje dirigido a otro par no cambia el enlace`() {
        val link = LinkState(status = LinkStatus.Negotiating, peer = 7)

        val step = link.receive(from = 9, signal = LinkSignal.Offer("v=0"))

        assertEquals(link, step.state)
        assertEquals(LinkIntent.None, step.intent)
    }

    @Test
    fun `cuando el invitado contesta el enlace queda listo`() {
        val host = LinkState(status = LinkStatus.Negotiating, peer = 7)

        val step = host.receive(from = 7, signal = LinkSignal.Answer("v=0"))

        assertEquals(LinkStatus.Ready, step.state.status)
        assertEquals(7L, step.state.peer)
    }

    @Test
    fun `un enlace fallido no vuelve a enlazando sin reintento`() {
        val failed = LinkState(status = LinkStatus.Negotiating, peer = 7).failed()

        assertEquals(LinkStatus.Failed, failed.status)

        val stillFailed = failed.peerPresent(peer = 7, role = Role.Host)

        assertEquals(LinkStatus.Failed, stillFailed.state.status)
        assertEquals(LinkIntent.None, stillFailed.intent)

        val retried = failed.again().peerPresent(peer = 7, role = Role.Host)

        assertEquals(LinkStatus.Negotiating, retried.state.status)
        assertEquals(LinkIntent.Propose, retried.intent)
    }
}
