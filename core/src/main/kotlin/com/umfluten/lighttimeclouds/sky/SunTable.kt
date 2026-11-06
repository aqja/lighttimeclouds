package com.umfluten.lighttimeclouds.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Precomputed colors of the sun's disc ([SkyModel.sunDisc]) and of its aureole
 * ([SkyModel.aureole] at [ANGLES_DEG]) for sun altitudes from 0° to 90°
 * in [ALTITUDE_STEP] steps, so drawing the sun needs no scattering integration. Stored as the
 * resource [RESOURCE] (rows × (disc + aureole) × RGB bytes), which [main] in SkyTableGenerator.kt
 * writes at build time.
 */
object SunTable {
    const val ALTITUDE_STEP = 0.5
    val ROWS = (90.0 / ALTITUDE_STEP).toInt() + 1
    const val RESOURCE = "/com/umfluten/lighttimeclouds/sky/sun.bin"

    /** How far the aureole reaches from the sun's center (degrees): by then it has faded into the sky. */
    const val AUREOLE_EXTENT_DEG = 12.0

    private const val ANGLES = 24

    /** Angles (degrees) from the sun's center at which the aureole is tabulated: from the disc's edge out to [AUREOLE_EXTENT_DEG], denser inside. */
    val ANGLES_DEG = DoubleArray(ANGLES) { i ->
        val start = SkyModel.SUN_SEMI_DIAMETER_DEG
        start * (AUREOLE_EXTENT_DEG / start).pow(i / (ANGLES - 1.0))
    }

    private val COLUMNS = 1 + ANGLES_DEG.size

    private val table: ByteArray by lazy { readTable(RESOURCE, ROWS * COLUMNS * 3) }

    /** The table content as [SkyModel] computes it, in the resource's byte layout; only [rows] are filled. */
    fun compute(rows: Iterable<Int> = 0 until ROWS): ByteArray {
        val bytes = ByteArray(ROWS * COLUMNS * 3)
        rows.toList().parallelStream().forEach { row ->
            val altitude = row * ALTITUDE_STEP
            val colors = intArrayOf(SkyModel.sunDisc(altitude)) + SkyModel.aureole(altitude, ANGLES_DEG)
            colors.forEachIndexed { column, rgb -> bytes.putRgb((row * COLUMNS + column) * 3, rgb) }
        }
        return bytes
    }

    /** Color (packed 0xRRGGBB) of the disc for the sun at [altitudeDeg]. */
    fun disc(altitudeDeg: Double): Int = row(altitudeDeg, 0, 1).first()

    /**
     * Colors (packed 0xRRGGBB) of the aureole at [ANGLES_DEG], to lay over the ring's color in
     * "screen" blending, for the sun at [altitudeDeg].
     */
    fun aureole(altitudeDeg: Double): IntArray = row(altitudeDeg, 1, ANGLES_DEG.size)

    private fun row(altitudeDeg: Double, first: Int, count: Int): IntArray {
        val position = altitudeDeg.coerceIn(0.0, 90.0) / ALTITUDE_STEP
        val row0 = floor(position).toInt().coerceAtMost(ROWS - 2)
        val t = position - row0
        return IntArray(count) { k ->
            var rgb = 0
            for (channel in 0..2) {
                fun at(row: Int) = table[(row * COLUMNS + first + k) * 3 + channel].toInt() and 0xFF
                rgb = (rgb shl 8) or (at(row0) + (at(row0 + 1) - at(row0)) * t).roundToInt().coerceIn(0, 255)
            }
            rgb
        }
    }
}
