package com.umfluten.lighttimeclouds.sky

// The sky tables are computed while building the app, and F-Droid rebuilds the app to compare it
// byte for byte with the published one. The platform's Math functions may differ in the last bit
// between machines (and even between the interpreter and compiled code), so the sky model uses
// java.lang.StrictMath, whose results are the same everywhere. These shadow kotlin.math in this
// package wherever kotlin.math is not imported by name.

internal fun exp(x: Double): Double = StrictMath.exp(x)
internal fun ln(x: Double): Double = StrictMath.log(x)
internal fun log10(x: Double): Double = StrictMath.log10(x)
internal fun sin(x: Double): Double = StrictMath.sin(x)
internal fun cos(x: Double): Double = StrictMath.cos(x)
internal fun tan(x: Double): Double = StrictMath.tan(x)
internal fun acos(x: Double): Double = StrictMath.acos(x)
internal fun atan(x: Double): Double = StrictMath.atan(x)
internal fun cbrt(x: Double): Double = StrictMath.cbrt(x)
internal fun hypot(x: Double, y: Double): Double = StrictMath.hypot(x, y)
internal fun Double.pow(y: Double): Double = StrictMath.pow(this, y)
internal fun Double.pow(n: Int): Double = StrictMath.pow(this, n.toDouble())
