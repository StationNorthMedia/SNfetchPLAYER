package net.sn.fetchplayer.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

enum class SlantSide {
    LEFT,
    RIGHT
}

class SlantedBackgroundDrawable(
    private val fillColor: Int,
    private val strokeColor: Int? = null,
    private val strokeWidthPx: Float = 0f,
    private val slantWidthDp: Float = 14f,
    private val density: Float = 1.0f,
    private val slantSide: SlantSide = SlantSide.RIGHT,
    private val slantInverted: Boolean = true
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }

    private val strokePaint = strokeColor?.let {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = it
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
    }

    private val path = Path()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val slant = slantWidthDp * density

        if (w <= 0 || h <= 0) return

        path.reset()
        if (slantSide == SlantSide.RIGHT) {
            if (!slantInverted) {
                // Slant on right edge (\): (0,0) -> (w - slant, 0) -> (w, h) -> (0, h) -> Z
                path.moveTo(0f, 0f)
                path.lineTo((w - slant).coerceAtLeast(0f), 0f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
            } else {
                // Inverted slant on right edge (/): (0,0) -> (w, 0) -> (w - slant, h) -> (0, h) -> Z
                path.moveTo(0f, 0f)
                path.lineTo(w, 0f)
                path.lineTo((w - slant).coerceAtLeast(0f), h)
                path.lineTo(0f, h)
            }
            path.close()
        } else {
            if (!slantInverted) {
                // Slant on left edge (\): (slant, 0) -> (w, 0) -> (w, h) -> (0, h) -> Z
                path.moveTo(slant.coerceAtMost(w), 0f)
                path.lineTo(w, 0f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
            } else {
                // Inverted slant on left edge (/): (0, 0) -> (w, 0) -> (w, h) -> (slant, h) -> Z
                path.moveTo(0f, 0f)
                path.lineTo(w, 0f)
                path.lineTo(w, h)
                path.lineTo(slant.coerceAtMost(w), h)
            }
            path.close()
        }

        canvas.drawPath(path, fillPaint)
        strokePaint?.let { canvas.drawPath(path, it) }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
