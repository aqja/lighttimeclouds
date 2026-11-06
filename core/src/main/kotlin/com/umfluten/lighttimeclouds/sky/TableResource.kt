package com.umfluten.lighttimeclouds.sky

/** The table [resource] written at build time, checked to hold [size] bytes. */
internal fun readTable(resource: String, size: Int): ByteArray {
    val bytes = SkyModel::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
        ?: error("Missing resource $resource")
    check(bytes.size == size) { "$resource has ${bytes.size} bytes, expected $size" }
    return bytes
}

/** Stores the packed color [rgb] (0xRRGGBB) as three bytes from [index] on. */
internal fun ByteArray.putRgb(index: Int, rgb: Int) {
    this[index] = (rgb shr 16).toByte()
    this[index + 1] = (rgb shr 8).toByte()
    this[index + 2] = rgb.toByte()
}
