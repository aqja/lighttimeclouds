package com.umfluten.lighttimeclouds.widget

import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonPhaseDiscTest {

    /**
     * Renders the disc and compares every pixel inside the rim (less the outline) with the
     * analytic shape: lit where the coordinate x along the bright limb direction satisfies
     * x ≥ −(2k − 1)·√(r² − y²), whose area is k·πr². Returns the share of mismatching pixels and
     * the direction of the lit pixels' centroid (from the top, counterclockwise).
     */
    private fun render(fraction: Double, brightLimbDeg: Double): Pair<Double, Double> {
        val size = 241
        val c = size / 2f
        val r = 110.0
        val bitmap = createBitmap(size, size)
        val limb = Math.toRadians(brightLimbDeg)
        ClockFaceRenderer.drawMoonPhase(Canvas(bitmap), c, c, r.toFloat(), fraction, limb, Color.WHITE, Color.BLACK)
        val ux = -sin(limb)
        val uy = -cos(limb)
        val e = 2 * fraction - 1
        var inside = 0
        var wrong = 0
        var sx = 0.0
        var sy = 0.0
        val inner = r - 6
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5 - c
                val dy = y + 0.5 - c
                if (dx * dx + dy * dy > inner * inner) continue
                inside++
                val along = dx * ux + dy * uy
                val across = -dx * uy + dy * ux
                val expected = along >= -e * sqrt(r * r - across * across)
                val lit = Color.red(bitmap.getPixel(x, y)) > 127
                if (lit != expected) wrong++
                if (lit) {
                    sx += dx
                    sy += dy
                }
            }
        }
        return wrong.toDouble() / inside to Math.toDegrees(atan2(-sx, -sy)).mod(360.0)
    }

    @Test
    fun `lit part is the half disc bounded by the terminator ellipse`() {
        for (k in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)) {
            val wrong = render(k, 30.0).first
            assertTrue("k = $k: $wrong of the pixels differ", wrong < 0.01)
        }
    }

    @Test
    fun `lit side faces the bright limb angle`() {
        for (limb in listOf(0.0, 90.0, 200.0, 290.0)) {
            for (k in listOf(0.2, 0.7)) {
                val angle = render(k, limb).second
                val difference = ((angle - limb + 540.0) % 360.0) - 180.0
                assertTrue("limb $limb°, k $k: centroid at $angle°", abs(difference) < 2.0)
            }
        }
    }
}
