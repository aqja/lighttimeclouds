package com.umfluten.lighttimeclouds.sky

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Precomputed [SkyModel.averagedGradient] gradients for sun altitudes from [MIN_ALTITUDE] to 90° in
 * [ALTITUDE_STEP] steps, [STOPS] colors each (horizon first), so drawing the ring needs no
 * scattering integration. Stored as the resource [RESOURCE] (rows × stops × RGB bytes); a unit
 * test checks it against the model and can regenerate it.
 */
object SkyGradientTable {
    const val MIN_ALTITUDE = -18.0
    const val ALTITUDE_STEP = 0.5
    const val STOPS = 16
    val ROWS = ((90.0 - MIN_ALTITUDE) / ALTITUDE_STEP).toInt() + 1
    const val RESOURCE = "/com/umfluten/lighttimeclouds/sky/sky_gradients.bin"

    private val table: ByteArray by lazy {
        val bytes = SkyGradientTable::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
            ?: error("Missing resource $RESOURCE")
        check(bytes.size == ROWS * STOPS * 3) { "$RESOURCE has ${bytes.size} bytes, expected ${ROWS * STOPS * 3}" }
        bytes
    }

    /** The table content as [SkyModel] computes it, in the resource's byte layout; only [rows] are filled. */
    fun compute(rows: Iterable<Int> = 0 until ROWS): ByteArray {
        val bytes = ByteArray(ROWS * STOPS * 3)
        for (row in rows) {
            SkyModel.averagedGradient(MIN_ALTITUDE + row * ALTITUDE_STEP, STOPS).forEachIndexed { stop, rgb ->
                val i = (row * STOPS + stop) * 3
                bytes[i] = (rgb shr 16).toByte()
                bytes[i + 1] = (rgb shr 8).toByte()
                bytes[i + 2] = rgb.toByte()
            }
        }
        return bytes
    }

    /**
     * Sky colors (packed 0xRRGGBB, horizon first) for the sun at [sunAltitudeDeg], linearly
     * interpolated between table rows; below [MIN_ALTITUDE] as at [MIN_ALTITUDE] (dark).
     */
    fun colors(sunAltitudeDeg: Double): IntArray {
        val position = ((sunAltitudeDeg.coerceIn(MIN_ALTITUDE, 90.0) - MIN_ALTITUDE) / ALTITUDE_STEP)
        val row0 = floor(position).toInt().coerceAtMost(ROWS - 2)
        val t = position - row0
        return IntArray(STOPS) { stop ->
            var rgb = 0
            for (channel in 0..2) {
                fun at(row: Int) = table[(row * STOPS + stop) * 3 + channel].toInt() and 0xFF
                val value = at(row0) + (at(row0 + 1) - at(row0)) * t
                rgb = (rgb shl 8) or value.roundToInt().coerceIn(0, 255)
            }
            rgb
        }
    }
}
