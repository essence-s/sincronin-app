package com.example.sincronin.domain.pairing

import com.example.sincronin.domain.room.RoomCode

/** Quién comparte la reproducción: el primero que entró a la sala. */
enum class Role { Host, Guest }

/** Motivos que el panel puede explicar sin hablar de red. */
enum class PairingError { InvalidCode, ServerUnreachable }

sealed interface PairingStatus {
    /** Sin sala: el panel ofrece escribir un código. */
    data object Idle : PairingStatus

    /** Dentro de la sala, pero solo está mi conexión. */
    data object Waiting : PairingStatus

    /** Las dos partes están dentro. */
    data object Connected : PairingStatus

    data class Failed(val error: PairingError) : PairingStatus
}

/**
 * Estado del emparejamiento tal como lo dibuja el panel.
 *
 * Las funciones no tocan red: reciben lo que pasó y devuelven el estado nuevo,
 * así que se pueden probar sin servidor ni Android.
 */
data class PairingState(
    val status: PairingStatus = PairingStatus.Idle,
    val room: RoomCode? = null,
    val role: Role? = null,
    val people: Int = 0
) {

    /** Intento de entrar: la sala ya se conoce, el rol todavía no. */
    fun joining(room: RoomCode): PairingState =
        PairingState(status = PairingStatus.Waiting, room = room)

    /** El servidor confirmó la entrada y dice cuántas personas hay ahora. */
    fun joined(people: Int): PairingState = copy(
        people = people,
        role = if (people <= 1) Role.Host else Role.Guest,
        status = if (people <= 1) PairingStatus.Waiting else PairingStatus.Connected
    )

    /** No se pudo entrar; la sala se conserva para ofrecer reintentar. */
    fun failed(error: PairingError): PairingState =
        copy(status = PairingStatus.Failed(error))

    /** Reintento sobre la misma sala. */
    fun retrying(): PairingState =
        copy(status = if (room == null) PairingStatus.Idle else PairingStatus.Waiting)
}
