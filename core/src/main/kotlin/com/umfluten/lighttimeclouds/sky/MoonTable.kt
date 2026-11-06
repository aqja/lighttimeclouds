package com.umfluten.lighttimeclouds.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Precomputed colors of the moon's lit part ([SkyModel.moonDisc]) for moon altitudes from 0° to 90°
 * in [MOON_STEP] steps, each with the sun from [MIN_SUN_ALTITUDE] to 90° in [SUN_STEP] steps and the
 * disc lit by each of [FRACTIONS], so drawing the moon needs no scattering integration. Stored as
 * the resource [RESOURCE] (moon altitudes × sun altitudes × fractions × RGB bytes), which [main] in
 * SkyTableGenerator.kt writes at build time.
 */
object MoonTable {
    const val MOON_STEP = 1.0
    const val SUN_STEP = 2.0
    const val MIN_SUN_ALTITUDE = SkyGradientTable.MIN_ALTITUDE
    val ROWS = (90.0 / MOON_STEP).toInt() + 1
    private val SUN_ALTITUDES = ((90.0 - MIN_SUN_ALTITUDE) / SUN_STEP).toInt() + 1
    const val RESOURCE = "/com/umfluten/lighttimeclouds/sky/moon.bin"

    /** Lit shares of the disc in the table, denser where the moon's brightness changes most. */
    val FRACTIONS = doubleArrayOf(0.02, 0.1, 0.3, 0.6, 1.0)

    private val ROW_SIZE = SUN_ALTITUDES * FRACTIONS.size * 3

    private val table: ByteArray by lazy { readTable(RESOURCE, ROWS * ROW_SIZE) }

    /** The table content as [SkyModel] computes it, in the resource's byte layout; only [rows] are filled. */
    fun compute(rows: Iterable<Int> = 0 until ROWS): ByteArray {
        val bytes = ByteArray(ROWS * ROW_SIZE)
        val sunAltitudes = DoubleArray(SUN_ALTITUDES) { MIN_SUN_ALTITUDE + it * SUN_STEP }
        rows.toList().parallelStream().forEach { row ->
            SkyModel.moonDiscs(row * MOON_STEP, sunAltitudes, FRACTIONS).forEachIndexed { cell, rgb ->
                bytes.putRgb(row * ROW_SIZE + cell * 3, rgb)
            }
        }
        return bytes
    }

    /**
     * Color (packed 0xRRGGBB) of the moon's lit part at [moonAltitudeDeg], lit by the share
     * [fraction], with the sun at [sunAltitudeDeg]; interpolated in the table. Sun altitudes below
     * [MIN_SUN_ALTITUDE] count as [MIN_SUN_ALTITUDE], as for the sky.
     */
    fun disc(moonAltitudeDeg: Double, sunAltitudeDeg: Double, fraction: Double): Int {
        val moon = cell(moonAltitudeDeg.coerceIn(0.0, 90.0) / MOON_STEP, ROWS)
        val sun = cell((sunAltitudeDeg.coerceIn(MIN_SUN_ALTITUDE, 90.0) - MIN_SUN_ALTITUDE) / SUN_STEP, SUN_ALTITUDES)
        val lit = fraction.coerceIn(FRACTIONS.first(), FRACTIONS.last())
        val f0 = FRACTIONS.indexOfLast { it <= lit }.coerceAtMost(FRACTIONS.size - 2)
        val f = (lit - FRACTIONS[f0]) / (FRACTIONS[f0 + 1] - FRACTIONS[f0])
        var rgb = 0
        for (channel in 0..2) {
            fun at(m: Int, s: Int, k: Int) =
                table[m * ROW_SIZE + (s * FRACTIONS.size + k) * 3 + channel].toInt() and 0xFF
            fun inFraction(m: Int, s: Int) = at(m, s, f0) + (at(m, s, f0 + 1) - at(m, s, f0)) * f
            fun inSun(m: Int) = inFraction(m, sun.first) + (inFraction(m, sun.first + 1) - inFraction(m, sun.first)) * sun.second
            val value = inSun(moon.first) + (inSun(moon.first + 1) - inSun(moon.first)) * moon.second
            rgb = (rgb shl 8) or value.roundToInt().coerceIn(0, 255)
        }
        return rgb
    }

    /** The index of the table cell at [position] of [size] entries, and the position within it. */
    private fun cell(position: Double, size: Int): Pair<Int, Double> {
        val index = floor(position).toInt().coerceAtMost(size - 2)
        return index to position - index
    }
}
