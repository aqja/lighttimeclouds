package com.umfluten.lighttimeclouds.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Precomputed [SkyModel.ringGradient] gradients for sun altitudes from [MIN_ALTITUDE] to 90° in
 * [ALTITUDE_STEP] steps, each at view elevations from the horizon up to
 * [SkyProjection.MAX_TOP_ELEVATION_DEG] in [ELEVATION_STEP] steps ([STOPS] colors, horizon first),
 * so drawing the ring needs no scattering integration. Stored as the resource [RESOURCE]
 * (rows × stops × RGB bytes), which [main] in SkyTableGenerator.kt writes at build time.
 */
object SkyGradientTable {
    const val MIN_ALTITUDE = -18.0
    const val ALTITUDE_STEP = 0.5
    const val ELEVATION_STEP = 1.0
    val STOPS = (SkyProjection.MAX_TOP_ELEVATION_DEG / ELEVATION_STEP).toInt() + 1
    val ROWS = ((90.0 - MIN_ALTITUDE) / ALTITUDE_STEP).toInt() + 1
    const val RESOURCE = "/com/umfluten/lighttimeclouds/sky/sky_gradients.bin"

    private val table: ByteArray by lazy { readTable(RESOURCE, ROWS * STOPS * 3) }

    /** The table content as [SkyModel] computes it, in the resource's byte layout; only [rows] are filled. */
    fun compute(rows: Iterable<Int> = 0 until ROWS): ByteArray {
        val bytes = ByteArray(ROWS * STOPS * 3)
        val elevations = DoubleArray(STOPS) { it * ELEVATION_STEP }
        rows.toList().parallelStream().forEach { row ->
            SkyModel.ringGradient(MIN_ALTITUDE + row * ALTITUDE_STEP, elevations).forEachIndexed { stop, rgb ->
                bytes.putRgb((row * STOPS + stop) * 3, rgb)
            }
        }
        return bytes
    }

    /**
     * [stops] sky colors (packed 0xRRGGBB) from the horizon up to [topElevationDeg] (at most
     * [SkyProjection.MAX_TOP_ELEVATION_DEG]), evenly spaced in [SkyProjection], for the sun at
     * [sunAltitudeDeg]; bilinearly interpolated in the table. Sun altitudes below [MIN_ALTITUDE]
     * count as [MIN_ALTITUDE]: by then only the night sky's own glow is left.
     */
    fun colors(sunAltitudeDeg: Double, topElevationDeg: Double, stops: Int): IntArray {
        require(stops >= 2) { "need at least two stops" }
        val position = ((sunAltitudeDeg.coerceIn(MIN_ALTITUDE, 90.0) - MIN_ALTITUDE) / ALTITUDE_STEP)
        val row0 = floor(position).toInt().coerceAtMost(ROWS - 2)
        val t = position - row0
        return IntArray(stops) { k ->
            val column = SkyProjection.elevation(k.toDouble() / (stops - 1), topElevationDeg) / ELEVATION_STEP
            val col0 = floor(column).toInt().coerceAtMost(STOPS - 2)
            val u = column - col0
            var rgb = 0
            for (channel in 0..2) {
                fun at(row: Int, col: Int) = table[(row * STOPS + col) * 3 + channel].toInt() and 0xFF
                fun inRow(row: Int) = at(row, col0) + (at(row, col0 + 1) - at(row, col0)) * u
                val value = inRow(row0) + (inRow(row0 + 1) - inRow(row0)) * t
                rgb = (rgb shl 8) or value.roundToInt().coerceIn(0, 255)
            }
            rgb
        }
    }
}
