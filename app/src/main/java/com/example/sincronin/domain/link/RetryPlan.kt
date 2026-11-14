package com.example.sincronin.domain.link

/**
 * Plan de reintentos de una conexión caída, igual que en el escritorio:
 * 2, 5, 10, 20 y 30 s de reintento y 5 s de gracia al final. Después se
 * da por vencido y el enlace pasa a «Sin conexión».
 *
 * Es un plazo por sesión, no una consulta periódica: el ViewModel avanza
 * el paso cuando toca y para cuando se agota.
 */
data class RetryPlan(val step: Int = 0) {

    /** Las esperas antes de cada paso, en segundos. */
    private val delays = longArrayOf(2, 5, 10, 20, 30, 5)

    /** Cuántos pasos son reintentos de verdad. */
    private val attempts = 5

    /** La espera antes del próximo paso, o null si ya no queda. */
    fun nextDelaySeconds(): Long? = delays.getOrNull(step)

    /** Si el próximo paso vence, hay que reintentar. */
    fun nextIsRetry(): Boolean = step < attempts

    /** Pasa al paso siguiente. */
    fun advance(): RetryPlan = copy(step = step + 1)

    /** Si ya no queda ningún paso: hay que darse por vencido. */
    fun isExhausted(): Boolean = step >= delays.size
}