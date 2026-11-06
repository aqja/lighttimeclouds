package com.umfluten.lighttimeclouds.sky

import java.nio.ByteBuffer
import kotlin.math.floor

/**
 * The sky as the moon lights it. Light adds up, so instead of colors for every combination of sun,
 * moon and phase this holds the light of the sky ([SkyModel.skyLight]) for a body at altitudes from
 * [MIN_ALTITUDE] to 90° in [ALTITUDE_STEP] steps, at view elevations from the horizon up to
 * [SkyProjection.MAX_TOP_ELEVATION_DEG] in [ELEVATION_STEP] steps, the mean luminance of the whole
 * sky for the same altitudes, and the aureole's light ([SkyModel.aureoleLight]) for altitudes from
 * 0° to 89° in [AUREOLE_STEP] steps at [SunTable.ANGLES_DEG]. The moon's sky is the sun's scaled by
 * [SkyModel.moonlightRatio]; the sum is seen as the eye sees it ([SkyModel.perceiveLight]) when
 * drawing. Stored as the resource [RESOURCE] (big-endian floats), which [main] in
 * SkyTableGenerator.kt writes at build time.
 */
object MoonlitSky {
    const val MIN_ALTITUDE = SkyGradientTable.MIN_ALTITUDE
    const val ALTITUDE_STEP = 0.5
    const val ELEVATION_STEP = 1.0
    const val AUREOLE_STEP = 1.0
    val STOPS = (SkyProjection.MAX_TOP_ELEVATION_DEG / ELEVATION_STEP).toInt() + 1
    val ROWS = ((90.0 - MIN_ALTITUDE) / ALTITUDE_STEP).toInt() + 1
    val AUREOLE_ROWS = (89.0 / AUREOLE_STEP).toInt() + 1
    const val RESOURCE = "/com/umfluten/lighttimeclouds/sky/moonlit_sky.bin"

    private const val CHANNELS = SkyModel.LIGHT_CHANNELS
    private val ANGLES get() = SunTable.ANGLES_DEG.size
    private val SKY_OFFSET = ROWS
    private val AUREOLE_OFFSET = SKY_OFFSET + ROWS * STOPS * CHANNELS
    private val SIZE get() = AUREOLE_OFFSET + AUREOLE_ROWS * ANGLES * CHANNELS

    private val table: FloatArray by lazy {
        val bytes = readTable(RESOURCE, SIZE * Float.SIZE_BYTES)
        FloatArray(SIZE).also { ByteBuffer.wrap(bytes).asFloatBuffer().get(it) }
    }

    /** The table content as [SkyModel] computes it, in the resource's byte layout; only [rows] and [aureoleRows] are filled. */
    fun compute(rows: Iterable<Int> = 0 until ROWS, aureoleRows: Iterable<Int> = 0 until AUREOLE_ROWS): ByteArray {
        val floats = FloatArray(SIZE)
        val elevations = DoubleArray(STOPS) { it * ELEVATION_STEP }
        fun store(offset: Int, light: List<DoubleArray>) = light.forEachIndexed { stop, l ->
            for (channel in 0 until CHANNELS) floats[offset + stop * CHANNELS + channel] = l[channel].toFloat()
        }
        rows.toList().parallelStream().forEach { row ->
            val altitude = MIN_ALTITUDE + row * ALTITUDE_STEP
            floats[row] = SkyModel.skyLuminance(altitude).toFloat()
            store(SKY_OFFSET + row * STOPS * CHANNELS, SkyModel.skyLight(altitude, elevations))
        }
        aureoleRows.toList().parallelStream().forEach { row ->
            store(AUREOLE_OFFSET + row * ANGLES * CHANNELS, SkyModel.aureoleLight(row * AUREOLE_STEP, SunTable.ANGLES_DEG))
        }
        return ByteBuffer.allocate(SIZE * Float.SIZE_BYTES).apply { asFloatBuffer().put(floats) }.array()
    }

    /**
     * [stops] sky colors (packed 0xRRGGBB) from the horizon up to [topElevationDeg], evenly spaced
     * in [SkyProjection], with the sun at [sunAltitudeDeg] and the moon, lit by the share
     * [fraction], at [moonAltitudeDeg]: the light of both skies as an eye adapted to both sees it.
     * Altitudes below [MIN_ALTITUDE] count as [MIN_ALTITUDE].
     */
    fun colors(sunAltitudeDeg: Double, moonAltitudeDeg: Double, fraction: Double, topElevationDeg: Double, stops: Int): IntArray {
        require(stops >= 2) { "need at least two stops" }
        val ratio = SkyModel.moonlightRatio(fraction)
        val adaptation = adaptation(sunAltitudeDeg, moonAltitudeDeg, ratio)
        return IntArray(stops) { k ->
            val elevation = SkyProjection.elevation(k.toDouble() / (stops - 1), topElevationDeg)
            SkyModel.perceiveLight(light(sunAltitudeDeg, moonAltitudeDeg, ratio, elevation), adaptation)
        }
    }

    /**
     * Colors (packed 0xRRGGBB) of the moon's aureole at [SunTable.ANGLES_DEG], to lay over the ring's
     * color at the moon's altitude in "screen" blending: the sky around the moon, like
     * [SkyModel.aureole] for the sun, at the moon's brightness. Colors go by the sky's own luminance
     * at each angle, as next to the moon the cones still see them.
     */
    fun aureole(moonAltitudeDeg: Double, sunAltitudeDeg: Double, fraction: Double): IntArray {
        val moon = moonAltitudeDeg.coerceIn(0.0, 89.0)
        val ratio = SkyModel.moonlightRatio(fraction)
        val adaptation = adaptation(sunAltitudeDeg, moon, ratio)
        val ring = light(sunAltitudeDeg, moon, ratio, moon)
        fun seen(light: DoubleArray) = SkyModel.perceiveLight(light, adaptation, colorLuminance = light[SkyModel.LIGHT_LUMINANCE])
        val background = seen(ring)
        val position = moon / AUREOLE_STEP
        val row = floor(position).toInt().coerceAtMost(AUREOLE_ROWS - 2)
        val t = position - row
        return SkyModel.risingInward(IntArray(ANGLES) { angle ->
            val total = DoubleArray(CHANNELS) { channel ->
                fun at(r: Int) = table[AUREOLE_OFFSET + (r * ANGLES + angle) * CHANNELS + channel].toDouble()
                ring[channel] + ratio * (at(row) + (at(row + 1) - at(row)) * t)
            }
            SkyModel.screenOver(background, seen(total))
        })
    }

    private fun adaptation(sunAltitudeDeg: Double, moonAltitudeDeg: Double, ratio: Double): Double {
        fun at(altitudeDeg: Double): Double {
            val (row, t) = altitudeRow(altitudeDeg)
            return table[row] + (table[row + 1] - table[row]) * t
        }
        return at(sunAltitudeDeg) + ratio * at(moonAltitudeDeg)
    }

    /** The light of the sky at [elevationDeg] from the sun and from the moon, whose light is [ratio] of the sun's. */
    private fun light(sunAltitudeDeg: Double, moonAltitudeDeg: Double, ratio: Double, elevationDeg: Double): DoubleArray {
        val position = elevationDeg.coerceIn(0.0, SkyProjection.MAX_TOP_ELEVATION_DEG) / ELEVATION_STEP
        val stop = floor(position).toInt().coerceAtMost(STOPS - 2)
        val u = position - stop
        val (sunRow, sunT) = altitudeRow(sunAltitudeDeg)
        val (moonRow, moonT) = altitudeRow(moonAltitudeDeg)
        return DoubleArray(CHANNELS) { channel ->
            fun at(row: Int, s: Int) = table[SKY_OFFSET + (row * STOPS + s) * CHANNELS + channel].toDouble()
            fun inRow(row: Int) = at(row, stop) + (at(row, stop + 1) - at(row, stop)) * u
            fun sky(row: Int, t: Double) = inRow(row) + (inRow(row + 1) - inRow(row)) * t
            sky(sunRow, sunT) + ratio * sky(moonRow, moonT)
        }
    }

    private fun altitudeRow(altitudeDeg: Double): Pair<Int, Double> {
        val position = (altitudeDeg.coerceIn(MIN_ALTITUDE, 90.0) - MIN_ALTITUDE) / ALTITUDE_STEP
        val row = floor(position).toInt().coerceAtMost(ROWS - 2)
        return row to position - row
    }
}
