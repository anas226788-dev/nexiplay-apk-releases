package com.nexiplay.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 100% Pure PathBuilder Vector SVG Icon System for Nexiplay.
 * Built with explicit PathBuilder commands (moveTo, lineTo, curveTo, quadTo, arcTo, close)
 * so Android's PathParser never truncates compact SVG arc flags.
 * Every icon renders with razor-sharp geometry at any resolution.
 */
object NexiSvgIcons {

    private inline fun strokeVector(
        name: String,
        strokeWidth: Float = 2f,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    private fun ImageVector.Builder.addStrokePath(
        strokeWidth: Float = 2f,
        cap: StrokeCap = StrokeCap.Round,
        join: StrokeJoin = StrokeJoin.Round,
        pathBlock: PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = strokeWidth,
            strokeLineCap = cap,
            strokeLineJoin = join,
            pathBuilder = pathBlock
        )
    }

    private fun ImageVector.Builder.addFilledPath(
        fillColor: Color = Color.White,
        fillAlpha: Float = 1f,
        fillType: PathFillType = PathFillType.NonZero,
        pathBlock: PathBuilder.() -> Unit
    ) {
        path(
            fill = SolidColor(fillColor),
            fillAlpha = fillAlpha,
            stroke = null,
            pathFillType = fillType,
            pathBuilder = pathBlock
        )
    }

    /**
     * Helper to draw a geometrically exact circle in a PathBuilder using 4 cubic bezier segments.
     */
    private fun PathBuilder.addCircle(cx: Float, cy: Float, r: Float) {
        val k = 0.55228475f * r
        moveTo(cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        close()
    }

    /**
     * Helper to draw a rounded rectangle in a PathBuilder.
     */
    private fun PathBuilder.addRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rx: Float
    ) {
        val r = rx.coerceAtMost((right - left) / 2f).coerceAtMost((bottom - top) / 2f)
        moveTo(left + r, top)
        lineTo(right - r, top)
        quadTo(right, top, right, top + r)
        lineTo(right, bottom - r)
        quadTo(right, bottom, right - r, bottom)
        lineTo(left + r, bottom)
        quadTo(left, bottom, left, bottom - r)
        lineTo(left, top + r)
        quadTo(left, top, left + r, top)
        close()
    }

    // ── 1. Bottom Navigation Icons (Matches Mockup 1:1) ──

    val Home: ImageVector by lazy {
        strokeVector("NexiHome", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // House outer contour (roof + walls)
                moveTo(4.5f, 10.5f)
                lineTo(12f, 3.8f)
                lineTo(19.5f, 10.5f)
                lineTo(19.5f, 18.8f)
                quadTo(19.5f, 20f, 18.3f, 20f)
                lineTo(5.7f, 20f)
                quadTo(4.5f, 20f, 4.5f, 18.8f)
                close()
                // Doorway
                moveTo(9.8f, 20f)
                lineTo(9.8f, 14.2f)
                quadTo(9.8f, 13.5f, 10.5f, 13.5f)
                lineTo(13.5f, 13.5f)
                quadTo(14.2f, 13.5f, 14.2f, 14.2f)
                lineTo(14.2f, 20f)
            }
        }
    }

    val Movies: ImageVector by lazy {
        strokeVector("NexiMovies", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Rounded TV/Video screen frame
                addRoundRect(3f, 5.5f, 21f, 18.5f, 3.8f)
                // Play triangle inside screen
                moveTo(10.3f, 9.4f)
                lineTo(15.0f, 12.0f)
                lineTo(10.3f, 14.6f)
                close()
            }
        }
    }

    val Series: ImageVector by lazy {
        strokeVector("NexiSeries", strokeWidth = 1.9f) {
            addStrokePath(1.9f) {
                // TV Screen
                addRoundRect(2.5f, 4.5f, 21.5f, 17.5f, 2.5f)
                // Stand
                moveTo(12f, 17.5f); lineTo(12f, 21f)
                moveTo(7.5f, 21f); lineTo(16.5f, 21f)
            }
        }
    }

    val Anime: ImageVector by lazy {
        strokeVector("NexiAnimeSpiral", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Outer circle + smooth inward spiral matching mockup
                moveTo(19.8f, 14.5f)
                curveTo(18.6f, 18.3f, 15.1f, 20.6f, 11.2f, 20.4f)
                curveTo(6.6f, 20.2f, 3.2f, 16.4f, 3.4f, 11.8f)
                curveTo(3.6f, 7.2f, 7.5f, 3.6f, 12.2f, 3.8f)
                curveTo(16.4f, 4.0f, 19.6f, 7.5f, 19.4f, 11.7f)
                curveTo(19.2f, 15.1f, 16.3f, 17.6f, 12.9f, 17.4f)
                curveTo(9.9f, 17.2f, 7.6f, 14.7f, 7.8f, 11.8f)
                curveTo(8.0f, 9.3f, 10.1f, 7.5f, 12.5f, 7.7f)
                curveTo(14.5f, 7.9f, 16.0f, 9.6f, 15.8f, 11.6f)
                curveTo(15.6f, 13.2f, 14.2f, 14.3f, 12.6f, 14.1f)
                curveTo(11.4f, 13.9f, 10.6f, 12.8f, 10.8f, 11.6f)
                curveTo(11.0f, 10.8f, 11.8f, 10.4f, 12.5f, 10.8f)
            }
        }
    }

    val VibeSparkles: ImageVector by lazy {
        strokeVector("NexiVibe", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Primary 4-point AI star
                moveTo(11f, 3.2f)
                quadTo(11f, 9.8f, 17.6f, 9.8f)
                quadTo(11f, 9.8f, 11f, 16.4f)
                quadTo(11f, 9.8f, 4.4f, 9.8f)
                quadTo(11f, 9.8f, 11f, 3.2f)
                close()
                // Companion '+' sign at bottom-right
                moveTo(18f, 15f)
                lineTo(18f, 20f)
                moveTo(15.5f, 17.5f)
                lineTo(20.5f, 17.5f)
            }
        }
    }

    val Novels: ImageVector by lazy {
        strokeVector("NexiNovels", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Open book left page
                moveTo(12f, 6.2f)
                quadTo(9.2f, 4.6f, 5.2f, 4.6f)
                quadTo(3.8f, 4.6f, 3.2f, 5.3f)
                lineTo(3.2f, 18.2f)
                quadTo(4.4f, 17.5f, 6.2f, 17.5f)
                quadTo(9.2f, 17.5f, 12f, 19.2f)
                // Open book right page
                moveTo(12f, 6.2f)
                quadTo(14.8f, 4.6f, 18.8f, 4.6f)
                quadTo(20.2f, 4.6f, 20.8f, 5.3f)
                lineTo(20.8f, 18.2f)
                quadTo(19.6f, 17.5f, 17.8f, 17.5f)
                quadTo(14.8f, 17.5f, 12f, 19.2f)
                // Center spine
                moveTo(12f, 6.2f)
                lineTo(12f, 19.2f)
            }
        }
    }

    val User: ImageVector by lazy {
        strokeVector("NexiUser", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Head
                addCircle(12f, 8.0f, 3.5f)
                // Shoulders
                moveTo(5.5f, 19.5f)
                curveTo(5.5f, 15.5f, 8.2f, 14.0f, 12f, 14.0f)
                curveTo(15.8f, 14.0f, 18.5f, 15.5f, 18.5f, 19.5f)
            }
        }
    }

    // ── 2. Header, Search, Notification & Menu SVGs ──

    val Search: ImageVector by lazy {
        strokeVector("NexiSearch") {
            addStrokePath(2.2f) {
                addCircle(11f, 11f, 6.8f)
                moveTo(16f, 16f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    val Bell: ImageVector by lazy {
        strokeVector("NexiBell") {
            addStrokePath(2f) {
                moveTo(12f, 3f)
                lineTo(12f, 4.5f)
                // Bell dome
                moveTo(6.5f, 10.5f)
                curveTo(6.5f, 7f, 8.9f, 4.5f, 12f, 4.5f)
                curveTo(15.1f, 4.5f, 17.5f, 7f, 17.5f, 10.5f)
                lineTo(17.5f, 14.2f)
                lineTo(19.3f, 16.5f)
                lineTo(4.7f, 16.5f)
                lineTo(6.5f, 14.2f)
                close()
                // Clapper
                moveTo(9.8f, 17f)
                curveTo(10f, 18.8f, 11f, 19.8f, 12f, 19.8f)
                curveTo(13f, 19.8f, 14f, 18.8f, 14.2f, 17f)
            }
        }
    }

    val Menu: ImageVector by lazy {
        strokeVector("NexiMenu") {
            addStrokePath(2.2f) {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17.5f); lineTo(20f, 17.5f)
            }
        }
    }

    val Close: ImageVector by lazy {
        strokeVector("NexiClose") {
            addStrokePath(2.2f) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    // ── 3. Website Chatbot Cybernetic Robot / Visor Icon (`Chatbot.tsx` lines 410-432) ──

    val Bot: ImageVector by lazy {
        strokeVector("NexiBot", strokeWidth = 1.9f) {
            // Antenna & Frame
            addStrokePath(1.9f) {
                moveTo(12f, 2.5f); lineTo(12f, 6.5f)
                // Robot Head Armor
                addRoundRect(3.5f, 6.5f, 20.5f, 19.5f, 4f)
                // Visor frame
                addRoundRect(6f, 9.5f, 18f, 15.5f, 2.5f)
                // Smile
                moveTo(9.5f, 17.5f)
                quadTo(12f, 19f, 14.5f, 17.5f)
            }
            // Antenna Ball & Dual Glowing Eyes
            addFilledPath(Color.White) {
                addCircle(12f, 2.5f, 1.2f)
                addCircle(9f, 12.5f, 1.25f)
                addCircle(15f, 12.5f, 1.25f)
            }
        }
    }

    // ── 4. Entertainment Hub, Badges & Controls SVGs ──

    val LiveTv: ImageVector by lazy {
        strokeVector("NexiLiveTv", strokeWidth = 1.9f) {
            addStrokePath(1.9f) {
                // Antennas
                moveTo(8.5f, 3.5f); lineTo(12f, 7f); lineTo(15.5f, 3.5f)
                // Display body
                addRoundRect(3f, 7f, 21f, 20.5f, 2.5f)
                // Screen Play triangle
                moveTo(10.5f, 11f)
                lineTo(15f, 13.7f)
                lineTo(10.5f, 16.5f)
                close()
            }
        }
    }

    val Gamepad: ImageVector by lazy {
        strokeVector("NexiGamepad", strokeWidth = 1.9f) {
            addStrokePath(1.9f) {
                addRoundRect(2.5f, 6.5f, 21.5f, 17.5f, 4.5f)
                // D-pad
                moveTo(6.5f, 12f); lineTo(10.5f, 12f)
                moveTo(8.5f, 10f); lineTo(8.5f, 14f)
            }
            addFilledPath(Color.White) {
                addCircle(15.5f, 11f, 1.2f)
                addCircle(18f, 13.2f, 1.2f)
            }
        }
    }

    val Telegram: ImageVector by lazy {
        strokeVector("NexiTelegram") {
            addFilledPath(Color.White) {
                moveTo(21.2f, 4.2f)
                lineTo(2.9f, 11.3f)
                lineTo(8.5f, 13.6f)
                lineTo(17.8f, 7.6f)
                lineTo(10.6f, 14.6f)
                lineTo(10.4f, 19.6f)
                lineTo(13.6f, 16.5f)
                lineTo(17.8f, 19.6f)
                close()
            }
        }
    }

    val Flame: ImageVector by lazy {
        strokeVector("NexiFlame") {
            addStrokePath(2.1f) {
                moveTo(12f, 21f)
                curveTo(16.2f, 21f, 19f, 18f, 19f, 14.2f)
                curveTo(19f, 11f, 16.8f, 8.2f, 14.5f, 6.5f)
                curveTo(14.2f, 9f, 12.8f, 10.2f, 11.8f, 10.2f)
                curveTo(11.2f, 6.8f, 9.5f, 4.2f, 7.8f, 3f)
                curveTo(7.5f, 6.8f, 5f, 9.8f, 5f, 14.2f)
                curveTo(5f, 18f, 7.8f, 21f, 12f, 21f)
                close()
            }
        }
    }

    val Pin: ImageVector by lazy {
        strokeVector("NexiPin") {
            addStrokePath(2.1f) {
                moveTo(9f, 4f)
                lineTo(15f, 4f)
                lineTo(14f, 9f)
                lineTo(17.5f, 12.5f)
                lineTo(6.5f, 12.5f)
                lineTo(10f, 9f)
                close()
                moveTo(12f, 12.5f)
                lineTo(12f, 20f)
            }
        }
    }

    val PlayFilled: ImageVector by lazy {
        strokeVector("NexiPlayFilled") {
            addFilledPath(Color.White) {
                moveTo(7.5f, 4.5f)
                lineTo(19.5f, 12f)
                lineTo(7.5f, 19.5f)
                close()
            }
        }
    }

    val InfoOutline: ImageVector by lazy {
        strokeVector("NexiInfo") {
            addStrokePath(2.1f) {
                addCircle(12f, 12f, 9f)
                moveTo(12f, 11.2f)
                lineTo(12f, 16.2f)
            }
            addFilledPath(Color.White) {
                addCircle(12f, 8f, 1.15f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        strokeVector("NexiChevronLeft") {
            addStrokePath(2.4f) {
                moveTo(15f, 5f)
                lineTo(8f, 12f)
                lineTo(15f, 19f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeVector("NexiChevronRight") {
            addStrokePath(2.4f) {
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }
        }
    }

    val ArrowRight: ImageVector by lazy {
        strokeVector("NexiArrowRight") {
            addStrokePath(2.2f) {
                moveTo(5f, 12f); lineTo(19f, 12f)
                moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
            }
        }
    }

    val Crown: ImageVector by lazy {
        strokeVector("NexiCrown") {
            addStrokePath(2f) {
                moveTo(3.5f, 18.5f); lineTo(20.5f, 18.5f)
                moveTo(5f, 15f)
                lineTo(3.5f, 7.5f)
                lineTo(8.5f, 11f)
                lineTo(12f, 5f)
                lineTo(15.5f, 11f)
                lineTo(20.5f, 7.5f)
                lineTo(19f, 15f)
                close()
            }
        }
    }

    val Coin: ImageVector by lazy {
        strokeVector("NexiCoin", strokeWidth = 2.0f) {
            addStrokePath(2.0f) {
                // Outer circle
                addCircle(12f, 12f, 8.6f)
                // Dollar vertical stem
                moveTo(12f, 6.8f)
                lineTo(12f, 17.2f)
                // Dollar S-curve
                moveTo(14.5f, 9.2f)
                curveTo(14.2f, 8.1f, 13.3f, 7.6f, 12f, 7.6f)
                curveTo(10.5f, 7.6f, 9.5f, 8.4f, 9.5f, 9.6f)
                curveTo(9.5f, 10.9f, 10.7f, 11.4f, 12f, 11.8f)
                curveTo(13.3f, 12.2f, 14.5f, 12.7f, 14.5f, 14.1f)
                curveTo(14.5f, 15.5f, 13.5f, 16.4f, 12f, 16.4f)
                curveTo(10.6f, 16.4f, 9.7f, 15.8f, 9.4f, 14.7f)
            }
        }
    }

    val Trophy: ImageVector by lazy {
        strokeVector("NexiTrophy") {
            addStrokePath(2f) {
                moveTo(7f, 4f)
                lineTo(17f, 4f)
                lineTo(17f, 10f)
                curveTo(17f, 13.2f, 14.8f, 15f, 12f, 15f)
                curveTo(9.2f, 15f, 7f, 13.2f, 7f, 10f)
                close()
                // Handles
                moveTo(7f, 6f); lineTo(4.5f, 6f); lineTo(4.5f, 9.5f); lineTo(7f, 10.5f)
                moveTo(17f, 6f); lineTo(19.5f, 6f); lineTo(19.5f, 9.5f); lineTo(17f, 10.5f)
                // Base
                moveTo(12f, 15f); lineTo(12f, 19.5f)
                moveTo(8f, 19.5f); lineTo(16f, 19.5f)
            }
        }
    }

    val PlusCircle: ImageVector by lazy {
        strokeVector("NexiPlusCircle") {
            addStrokePath(2f) {
                addCircle(12f, 12f, 9f)
                moveTo(12f, 8.5f); lineTo(12f, 15.5f)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            }
        }
    }

    val Calendar: ImageVector by lazy {
        strokeVector("NexiCalendar") {
            addStrokePath(2f) {
                addRoundRect(3.5f, 5.5f, 20.5f, 20.5f, 2.2f)
                moveTo(8f, 3.5f); lineTo(8f, 7f)
                moveTo(16f, 3.5f); lineTo(16f, 7f)
                moveTo(3.5f, 10f); lineTo(20.5f, 10f)
            }
        }
    }

    val Sort: ImageVector by lazy {
        strokeVector("NexiSort") {
            addStrokePath(2.1f) {
                moveTo(3.5f, 6.5f); lineTo(20.5f, 6.5f)
                moveTo(6.5f, 12f); lineTo(17.5f, 12f)
                moveTo(9.5f, 17.5f); lineTo(14.5f, 17.5f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        strokeVector("NexiGrid") {
            addStrokePath(2f) {
                addRoundRect(4f, 4f, 10f, 10f, 1.8f)
                addRoundRect(14f, 4f, 20f, 10f, 1.8f)
                addRoundRect(4f, 14f, 10f, 20f, 1.8f)
                addRoundRect(14f, 14f, 20f, 20f, 1.8f)
            }
        }
    }

    val Bookmark: ImageVector by lazy {
        strokeVector("NexiBookmark") {
            addStrokePath(2f) {
                moveTo(6f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                lineTo(12f, 16.2f)
                lineTo(6f, 20f)
                close()
            }
        }
    }

    val Download: ImageVector by lazy {
        strokeVector("NexiDownload") {
            addStrokePath(2.1f) {
                moveTo(12f, 4f); lineTo(12f, 15f)
                moveTo(8f, 11.2f); lineTo(12f, 15.2f); lineTo(16f, 11.2f)
                moveTo(4.5f, 17.5f)
                lineTo(4.5f, 19f)
                quadTo(4.5f, 20f, 5.5f, 20f)
                lineTo(18.5f, 20f)
                quadTo(19.5f, 20f, 19.5f, 19f)
                lineTo(19.5f, 17.5f)
            }
        }
    }

    val History: ImageVector by lazy {
        strokeVector("NexiHistory") {
            addStrokePath(2f) {
                addCircle(12f, 12f, 9f)
                moveTo(12f, 7.5f)
                lineTo(12f, 12.2f)
                lineTo(15.2f, 14.2f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        strokeVector("NexiSettings") {
            addStrokePath(2f) {
                addCircle(12f, 12f, 3.2f)
                addCircle(12f, 12f, 7.5f)
                moveTo(12f, 2.5f); lineTo(12f, 4.5f)
                moveTo(12f, 19.5f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(4.5f, 12f)
                moveTo(19.5f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(17.3f, 6.7f)
                moveTo(6.7f, 17.3f); lineTo(5.3f, 18.7f)
            }
        }
    }

    val Radio: ImageVector by lazy {
        strokeVector("NexiRadio") {
            addStrokePath(2f) {
                addCircle(12f, 12f, 2f)
                moveTo(8.2f, 8.2f)
                curveTo(6.2f, 10.2f, 6.2f, 13.8f, 8.2f, 15.8f)
                moveTo(15.8f, 8.2f)
                curveTo(17.8f, 10.2f, 17.8f, 13.8f, 15.8f, 15.8f)
                moveTo(5.4f, 5.4f)
                curveTo(2f, 8.8f, 2f, 15.2f, 5.4f, 18.6f)
                moveTo(18.6f, 5.4f)
                curveTo(22f, 8.8f, 22f, 15.2f, 18.6f, 18.6f)
            }
        }
    }
}
