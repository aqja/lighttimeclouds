package com.umfluten.lighttimeclouds.widget

/**
 * The fixed colors drawn on the sky, shared by the widget and the config app, as opaque ARGB.
 *
 * Everything else comes from elsewhere: the sky, the sun and the moon from the sky model, the
 * chrome (hands, numerals, labels) from the system's accent colors. Data colors are saturated
 * takes on the sky's own hues: a sky color used as is would vanish wherever the sky shows it.
 */
internal object SkyPalette {
    /** Air temperature: the gold of the glow around sunset, saturated. */
    const val GLOW_GOLD = 0xFFFFBE3C.toInt()

    /** Apparent temperature, dashed beside [GLOW_GOLD]: the gold low above the horizon by day. */
    const val HORIZON_GOLD = 0xFFE8C58B.toInt()

    /** Rain: a deep blue that stands apart from the day sky's lighter blue. */
    const val RAIN_BLUE = 0xFF2F5BFF.toInt()

    /** Snow: lavender, apart from the rain blue and from every sky. */
    const val LAVENDER = 0xFFA57BFF.toInt()

    /** Few clouds in the chart. */
    const val CLOUD_WHITE = 0xFFF5F7FA.toInt()

    /** Clouds on a dark background. */
    const val CLOUD_SILVER = 0xFFCDD0D7.toInt()

    /** Clouds on a light background and overcast clouds in the chart. */
    const val CLOUD_GREY = 0xFF737982.toInt()

    /** The alarm and the highest heavy rain warning: one signal red on light and dark backgrounds. */
    const val SIGNAL_RED = 0xFFDC1919.toInt()
}
