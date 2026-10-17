package com.umfluten.lighttimeclouds.sky

import java.io.File

/**
 * Build step (Gradle task `generateSkyTable` in `core/build.gradle.kts`): writes the
 * [SkyGradientTable] resource below the directory given as the only argument, so the table is
 * always computed from [SkyModel] and never kept as a binary in the source tree.
 */
fun main(args: Array<String>) {
    val outputDir = File(requireNotNull(args.singleOrNull()) { "usage: SkyTableGenerator <output directory>" })
    val file = File(outputDir, SkyGradientTable.RESOURCE.removePrefix("/"))
    file.parentFile.mkdirs()
    file.writeBytes(SkyGradientTable.compute())
}
