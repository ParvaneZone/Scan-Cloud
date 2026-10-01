package com.example.cfscanner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

fun svgIcon(vararg paths: String): ImageVector {
    val builder = ImageVector.Builder("icon", 24.dp, 24.dp, 24f, 24f)
    try {
        paths.forEach { path ->
            builder.addPath(
                PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    } catch (_: Exception) {
    }
    return builder.build()
}

private const val CIRCLE = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z"

val IconMenu by lazy {
    svgIcon("M3 12h18", "M3 6h18", "M3 18h18")
}

val IconCloud by lazy {
    svgIcon("M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z")
}

val IconGlobe by lazy {
    svgIcon(
        CIRCLE,
        "M2 12h20",
        "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
    )
}

val IconInfo by lazy {
    svgIcon(CIRCLE, "M12 16v-4", "M12 8h.01")
}

val IconRefresh by lazy {
    svgIcon(
        "M23 4v6h-6",
        "M1 20v-6h6",
        "M3.51 9a9 9 0 0 1 14.85-3.36L23 10",
        "M1 14l4.64 4.36A9 9 0 0 0 20.49 15"
    )
}

val IconSend by lazy {
    svgIcon("M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z")
}

val IconUsers by lazy {
    svgIcon(
        "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2",
        "M9 3a4 4 0 1 0 0 8a4 4 0 0 0 0-8z",
        "M23 21v-2a4 4 0 0 0-3-3.87",
        "M16 3.13a4 4 0 0 1 0 7.75"
    )
}

val IconDownload by lazy {
    svgIcon("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3")
}
