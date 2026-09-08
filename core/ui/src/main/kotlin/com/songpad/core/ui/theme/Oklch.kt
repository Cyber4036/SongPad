package com.songpad.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * Layer 1 of the token architecture (§14): primitives are expressed in OKLCH so tonal steps
 * (soft/strong/pressed variants) are derived perceptually rather than by eyeballing hex. Conversion is
 * the standard OKLab pipeline (Björn Ottosson) with sRGB transfer; pure math, JVM-tested.
 */
data class Oklch(val l: Double, val c: Double, val h: Double, val alpha: Double = 1.0) {

    fun lighten(delta: Double) = copy(l = (l + delta).coerceIn(0.0, 1.0))
    fun withChroma(chroma: Double) = copy(c = chroma.coerceAtLeast(0.0))
    fun withAlpha(a: Double) = copy(alpha = a.coerceIn(0.0, 1.0))

    fun toColor(): Color {
        val hr = Math.toRadians(h)
        val a = c * cos(hr)
        val b = c * sin(hr)
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val l3 = l_ * l_ * l_
        val m3 = m_ * m_ * m_
        val s3 = s_ * s_ * s_
        val rl = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        val gl = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        val bl = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
        return Color(gamma(rl).toFloat(), gamma(gl).toFloat(), gamma(bl).toFloat(), alpha.toFloat())
    }

    companion object {
        fun fromColor(color: Color): Oklch {
            val r = linear(color.red.toDouble())
            val g = linear(color.green.toDouble())
            val b = linear(color.blue.toDouble())
            val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
            val A = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
            val B = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
            val c = sqrt(A * A + B * B)
            var h = Math.toDegrees(atan2(B, A))
            if (h < 0) h += 360.0
            return Oklch(L, c, if (c < 1e-6) 0.0 else h, color.alpha.toDouble())
        }

        private fun linear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        private fun gamma(v: Double): Double {
            val x = v.coerceIn(0.0, 1.0)
            return if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
        }
    }
}

/** Derivations used by the semantic layer; every derived tone comes from here, never from a hand-picked hex. */
object Tone {
    fun soft(base: Color, alpha: Double = 0.18): Color = Oklch.fromColor(base).withAlpha(alpha).toColor()
    fun pressed(base: Color, isDark: Boolean): Color = Oklch.fromColor(base).lighten(if (isDark) 0.06 else -0.06).toColor()
    fun muted(base: Color, isDark: Boolean): Color = Oklch.fromColor(base).let { it.withChroma(it.c * 0.6).lighten(if (isDark) -0.02 else 0.02) }.toColor()
}
