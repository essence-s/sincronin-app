package com.example.sincronin.domain.link

import com.example.sincronin.domain.pairing.Role

/** Cómo va el canal directo entre los dos equipos. */
sealed interface LinkStatus {

    /** Todavía no hay con quién hablar. */
    data object Closed : LinkStatus

    /** Hay par y se está negociando el canal. */
    data object Negotiating : LinkStatus

    /** El canal está abierto: ya se puede mandar. */
    data object Ready : LinkStatus

    /** La negociación no llegó a nada. */
    data object Failed : LinkStatus

    /** El canal se cortó y se está reintentando. */
    data object Reconnecting : LinkStatus

    /** Se agotaron los reintentos: hay que volver a emparejar. */
    data object Disconnected : LinkStatus
}

/**
 * Lo que la negociación le pide al transporte. El SDP y los candidatos los
 * pone el adaptador: el dominio solo decide a quién le toca mover.
 */
enum class LinkIntent { None, Propose, Respond }

/** Señal que llegó del otro equipo por la señalización. */
sealed interface LinkSignal {
    data class Offer(val sdp: String) : LinkSignal
    data class Answer(val sdp: String) : LinkSignal
    data class Candidate(val candidate: String) : LinkSignal
}

/** Estado nuevo y, si toca, lo que hay que mandar. */
data class LinkStep(val state: LinkState, val intent: LinkIntent = LinkIntent.None)

/**
 * El enlace directo con el otro equipo, tal como lo dibuja el panel.
 *
 * Solo se habla con un par: el que el servidor identificó al entrar. El
 * anfitrión propone y el invitado contesta, igual que en el protocolo.
 */
data class LinkState(
    val status: LinkStatus = LinkStatus.Closed,
    val peer: Long? = null
) {

    /** Aparece el otro equipo: el anfitrión propone, el invitado espera. */
    fun peerPresent(peer: Long, role: Role): LinkStep {
        if (status == LinkStatus.Failed) return LinkStep(this)
        return LinkStep(
            state = copy(status = LinkStatus.Negotiating, peer = peer),
            intent = if (role == Role.Host) LinkIntent.Propose else LinkIntent.None
        )
    }

    /** Llegó algo del otro equipo; lo que venga de otro par se descarta.
     *  Si todavía no se conoce el par (controlador tras una reconexión), se
     *  aprende de la primera oferta que llegue, igual que en el escritorio. */
    fun receive(from: Long, signal: LinkSignal): LinkStep {
        val knownPeer = peer ?: if (signal is LinkSignal.Offer) from else null
        if (knownPeer == null || from != knownPeer || status == LinkStatus.Failed) return LinkStep(this)
        val withPeer = if (peer == null) copy(peer = knownPeer) else this
        return when (signal) {
            // Una oferta se contesta, pero el enlace no está listo hasta que
            // el canal abra.
            is LinkSignal.Offer -> LinkStep(
                state = withPeer.copy(status = LinkStatus.Negotiating),
                intent = LinkIntent.Respond
            )

            is LinkSignal.Answer -> LinkStep(withPeer.copy(status = LinkStatus.Ready))

            is LinkSignal.Candidate -> LinkStep(withPeer)
        }
    }

    /** El canal quedó abierto. */
    fun ready(): LinkState = copy(status = LinkStatus.Ready)

    /** La negociación no llegó a nada. */
    fun failed(): LinkState = copy(status = LinkStatus.Failed)

    /** Otra vez desde cero: se olvida el par y se vuelve a empezar. */
    fun again(): LinkState = LinkState()

    /** El canal se cortó: se está reintentando. */
    fun reconnecting(): LinkState = copy(status = LinkStatus.Reconnecting)

    /** Se agotaron los reintentos. */
    fun disconnected(): LinkState = copy(status = LinkStatus.Disconnected)
}
