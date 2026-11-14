package com.example.sincronin.domain.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPlanTest {

    @Test
    fun `la escalera devuelve 2 5 10 20 30 y luego 5 de gracia`() {
        var plan = RetryPlan()
        val delays = mutableListOf<Long?>()
        while (plan.nextIsRetry()) {
            delays.add(plan.nextDelaySeconds())
            plan = plan.advance()
        }
        assertEquals(listOf(2L, 5L, 10L, 20L, 30L), delays)
        // Tras los cinco reintentos queda la gracia.
        assertEquals(5L, plan.nextDelaySeconds())
        assertFalse(plan.nextIsRetry())
        assertFalse(plan.isExhausted())
    }

    @Test
    fun `se da por vencido tras la gracia`() {
        var plan = RetryPlan()
        repeat(6) { plan = plan.advance() }
        assertTrue(plan.isExhausted())
        assertEquals(null, plan.nextDelaySeconds())
    }

    @Test
    fun `los cinco primeros pasos son reintentos de verdad`() {
        var plan = RetryPlan()
        repeat(5) {
            assertTrue(plan.nextIsRetry())
            plan = plan.advance()
        }
        assertFalse(plan.nextIsRetry())
    }
}