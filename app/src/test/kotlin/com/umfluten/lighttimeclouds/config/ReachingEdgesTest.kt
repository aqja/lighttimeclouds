package com.umfluten.lighttimeclouds.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReachingEdgesTest {

    @Test
    fun `values in the middle of their hours are held level out to both edges`() {
        val gusts = listOf(15f to 4f, 45f to 6f, 75f to 5f)
        assertEquals(listOf(0f to 4f, 15f to 4f, 45f to 6f, 75f to 5f, 90f to 5f), reachingEdges(gusts, 0f, 90f))
    }

    @Test
    fun `values at the start of their hours only reach out to the right edge`() {
        val wind = listOf(0f to 2f, 30f to 3f)
        assertEquals(listOf(0f to 2f, 30f to 3f, 60f to 3f), reachingEdges(wind, 0f, 60f))
    }

    @Test
    fun `a missing value at an end stays missing`() {
        val gusts = listOf(15f to null, 45f to 6f)
        assertEquals(listOf(0f to null, 15f to null, 45f to 6f, 60f to 6f), reachingEdges(gusts, 0f, 60f))
    }

    @Test
    fun `no values give no line`() {
        assertEquals(emptyList<Pair<Float, Float?>>(), reachingEdges(emptyList(), 0f, 60f))
    }
}
