package com.songpad.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * One coherent icon family (Phase 0 §17). The material core set lacks Mic/Pause/Stop/Piano/Lyrics, so the
 * handful we need are authored here on the same 24dp grid; the rest come from `Icons.Default`.
 */
object SongPadIcons {
    private fun icon(name: String, builder: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply(builder).build()

    val Mic: ImageVector by lazy {
        icon("Mic") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                verticalLineTo(5f); curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f); curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                verticalLineTo(11f); curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
                moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f); curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
                horizontalLineTo(5f); curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f); verticalLineTo(21f); horizontalLineTo(13f)
                verticalLineTo(17.72f); curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f); horizontalLineTo(17.3f); close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
                moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") { path(fill = SolidColor(Color.Black)) { moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close() } }
    }

    val Piano: ImageVector by lazy {
        icon("Piano") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 3f); horizontalLineTo(20f); curveTo(20.55f, 3f, 21f, 3.45f, 21f, 4f); verticalLineTo(20f)
                curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f); horizontalLineTo(4f); curveTo(3.45f, 21f, 3f, 20.55f, 3f, 20f)
                verticalLineTo(4f); curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f); close()
                moveTo(5f, 5f); verticalLineTo(19f); horizontalLineTo(7.5f); verticalLineTo(13f); horizontalLineTo(6.5f); verticalLineTo(5f); close()
                moveTo(9.5f, 5f); verticalLineTo(13f); horizontalLineTo(8.5f); verticalLineTo(19f); horizontalLineTo(11f); verticalLineTo(13f); horizontalLineTo(10.5f); verticalLineTo(5f); close()
                moveTo(13.5f, 5f); verticalLineTo(13f); horizontalLineTo(12f); verticalLineTo(19f); horizontalLineTo(15.5f); verticalLineTo(13f); horizontalLineTo(14.5f); verticalLineTo(5f); close()
                moveTo(17.5f, 5f); verticalLineTo(13f); horizontalLineTo(16.5f); verticalLineTo(19f); horizontalLineTo(19f); verticalLineTo(5f); close()
            }
        }
    }

    val Lyrics: ImageVector by lazy {
        icon("Lyrics") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5f); horizontalLineTo(21f); verticalLineTo(7f); horizontalLineTo(3f); close()
                moveTo(3f, 9f); horizontalLineTo(15f); verticalLineTo(11f); horizontalLineTo(3f); close()
                moveTo(3f, 13f); horizontalLineTo(21f); verticalLineTo(15f); horizontalLineTo(3f); close()
                moveTo(3f, 17f); horizontalLineTo(12f); verticalLineTo(19f); horizontalLineTo(3f); close()
            }
        }
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f); verticalLineTo(13.55f); curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
                curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f); curveTo(6f, 19.21f, 7.79f, 21f, 10f, 21f); curveTo(12.21f, 21f, 14f, 19.21f, 14f, 17f)
                verticalLineTo(7f); horizontalLineTo(18f); verticalLineTo(3f); close()
            }
        }
    }

    val Archive: ImageVector by lazy {
        icon("Archive") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.54f, 5.23f); lineTo(19.15f, 3.55f); curveTo(18.88f, 3.21f, 18.47f, 3f, 18f, 3f); horizontalLineTo(6f)
                curveTo(5.53f, 3f, 5.12f, 3.21f, 4.84f, 3.55f); lineTo(3.46f, 5.23f); curveTo(3.17f, 5.57f, 3f, 6.02f, 3f, 6.5f)
                verticalLineTo(19f); curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f); horizontalLineTo(19f); curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(6.5f); curveTo(21f, 6.02f, 20.83f, 5.57f, 20.54f, 5.23f); close()
                moveTo(12f, 17.5f); lineTo(6.5f, 12f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(14f); verticalLineTo(12f)
                horizontalLineTo(17.5f); close(); moveTo(5.12f, 5f); lineTo(5.93f, 4f); horizontalLineTo(17.93f); lineTo(18.87f, 5f); close()
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 9f); horizontalLineTo(15f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(5f)
                lineTo(12f, 16f); close(); moveTo(5f, 18f); verticalLineTo(20f); horizontalLineTo(19f); verticalLineTo(18f); close()
            }
        }
    }

    val Upload: ImageVector by lazy {
        icon("Upload") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 16f); horizontalLineTo(15f); verticalLineTo(10f); horizontalLineTo(19f)
                lineTo(12f, 3f); lineTo(5f, 10f); horizontalLineTo(9f); close()
                moveTo(5f, 18f); verticalLineTo(20f); horizontalLineTo(19f); verticalLineTo(18f); close()
            }
        }
    }

    val Waveform: ImageVector by lazy {
        icon("Waveform") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 12f); lineTo(3f, 12f); moveTo(6f, 8f); lineTo(6f, 16f); moveTo(9f, 5f); lineTo(9f, 19f)
                moveTo(12f, 9f); lineTo(12f, 15f); moveTo(15f, 3f); lineTo(15f, 21f); moveTo(18f, 8f); lineTo(18f, 16f); moveTo(21f, 11f); lineTo(21f, 13f)
            }
        }
    }

    val Bell: ImageVector by lazy {
        icon("Bell") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6f, 9.5f)
                curveTo(6f, 6.18f, 8.68f, 3.5f, 12f, 3.5f)
                curveTo(15.32f, 3.5f, 18f, 6.18f, 18f, 9.5f)
                curveTo(18f, 14f, 20f, 16f, 20f, 16f)
                horizontalLineTo(4f)
                curveTo(4f, 16f, 6f, 14f, 6f, 9.5f)
                close()
                moveTo(10f, 19.5f)
                curveTo(10.5f, 20.6f, 11.2f, 21f, 12f, 21f)
                curveTo(12.8f, 21f, 13.5f, 20.6f, 14f, 19.5f)
            }
        }
    }

    val StarOutline: ImageVector by lazy {
        icon("StarOutline") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.4f); lineTo(14.7f, 9.0f); lineTo(20.8f, 9.9f); lineTo(16.4f, 14.2f)
                lineTo(17.4f, 20.3f); lineTo(12f, 17.4f); lineTo(6.6f, 20.3f); lineTo(7.6f, 14.2f)
                lineTo(3.2f, 9.9f); lineTo(9.3f, 9.0f); close()
            }
        }
    }

    val StarFilled: ImageVector by lazy {
        icon("StarFilled") {
            path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.4f); lineTo(14.7f, 9.0f); lineTo(20.8f, 9.9f); lineTo(16.4f, 14.2f)
                lineTo(17.4f, 20.3f); lineTo(12f, 17.4f); lineTo(6.6f, 20.3f); lineTo(7.6f, 14.2f)
                lineTo(3.2f, 9.9f); lineTo(9.3f, 9.0f); close()
            }
        }
    }

    val EditPencil: ImageVector by lazy {
        icon("EditPencil") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 20f); horizontalLineTo(8f); lineTo(18f, 10f); lineTo(14f, 6f); lineTo(4f, 16f); close()
                moveTo(13.5f, 6.5f); lineTo(17.5f, 10.5f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(20f, 6.5f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f)
            }
        }
    }

    val Minus: ImageVector by lazy {
        icon("Minus") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(5f, 12f); horizontalLineTo(19f)
            }
        }
    }

    val MoreHoriz: ImageVector by lazy {
        icon("MoreHoriz") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 12f); curveTo(5f, 10.9f, 4.1f, 10f, 3f, 10f); curveTo(1.9f, 10f, 1f, 10.9f, 1f, 12f); curveTo(1f, 13.1f, 1.9f, 14f, 3f, 14f); curveTo(4.1f, 14f, 5f, 13.1f, 5f, 12f); close()
                moveTo(14f, 12f); curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f); curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f); curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f); curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f); close()
                moveTo(23f, 12f); curveTo(23f, 10.9f, 22.1f, 10f, 21f, 10f); curveTo(19.9f, 10f, 19f, 10.9f, 19f, 12f); curveTo(19f, 13.1f, 19.9f, 14f, 21f, 14f); curveTo(22.1f, 14f, 23f, 13.1f, 23f, 12f); close()
            }
        }
    }
}
