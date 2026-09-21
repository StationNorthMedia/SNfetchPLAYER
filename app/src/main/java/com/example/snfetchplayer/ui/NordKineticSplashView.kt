package com.example.snfetchplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.snfetchplayer.R

class NordKineticSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val startTime = System.currentTimeMillis()

    private val nord0 = ContextCompat.getColor(context, R.color.nord0)
    private val nord1 = ContextCompat.getColor(context, R.color.nord1)
    private val nord2 = ContextCompat.getColor(context, R.color.nord2)
    private val nord3 = ContextCompat.getColor(context, R.color.nord3)
    private val nord8 = ContextCompat.getColor(context, R.color.nord8)

    private val bgPaint = Paint().apply {
        color = nord0
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = nord8
    }

    private val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord0
        style = Paint.Style.FILL
    }

    private val logoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord8
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val logoBitmap: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(resources, R.drawable.sn_logo)
        } catch (e: Exception) {
            null
        }
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = Math.min(w, h) * 0.42f

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        // 1. Kinetic Rings (6 rings scaling continuously)
        val numRings = 6
        for (i in 0 until numRings) {
            val delay = i * 0.7f
            val phase = (elapsed + delay) % 4.0f
            val progress = phase / 4.0f // 0.0 to 1.0

            val radius = maxRadius * (0.25f + progress * 0.75f)
            val alpha = ((1.0f - progress) * 225).toInt().coerceIn(0, 255)

            ringPaint.color = if (i % 2 == 0) nord8 else nord2
            ringPaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }

        // 2. Shockwave Rings (Expanding outward)
        val numShockwaves = 3
        for (i in 0 until numShockwaves) {
            val delay = i * 0.4f
            val phase = (elapsed + delay) % 2.5f
            val progress = phase / 2.5f

            val radius = maxRadius * (0.4f + progress * 0.9f)
            val alpha = ((1.0f - progress) * 255).toInt().coerceIn(0, 255)

            shockwavePaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, shockwavePaint)
        }

        // 3. Center Logo Container with Flare Pulse
        val centerSize = Math.min(w, h) * 0.32f
        val logoPulse = 1.0f + 0.05f * Math.sin(elapsed * 4.0).toFloat()
        val currentLogoSize = centerSize * logoPulse

        rectF.set(
            cx - currentLogoSize / 2f,
            cy - currentLogoSize / 2f,
            cx + currentLogoSize / 2f,
            cy + currentLogoSize / 2f
        )

        // Draw pulsing outer glow border
        logoBorderPaint.alpha = (180 + 75 * Math.sin(elapsed * 5.0)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, currentLogoSize / 2f + 6f, logoBorderPaint)

        // Draw center logo circle container
        canvas.drawCircle(cx, cy, currentLogoSize / 2f, logoBgPaint)

        // Draw logo image inside center circle
        logoBitmap?.let { bmp ->
            val imgW = currentLogoSize * 0.75f
            val imgH = imgW * (bmp.height.toFloat() / bmp.width.toFloat())
            val dstRect = RectF(
                cx - imgW / 2f,
                cy - imgH / 2f,
                cx + imgW / 2f,
                cy + imgH / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, null)
        }

        postInvalidateOnAnimation()
    }
}
