package net.sn.fetchplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import net.sn.fetchplayer.R

class NordCrateDiggingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val startTime = System.currentTimeMillis()

    private val nord0 = ContextCompat.getColor(context, R.color.nord0) // #2E3440

    private val bgPaint = Paint().apply {
        color = nord0 // Solid Nord0 Dark Background (#2E3440)
        style = Paint.Style.FILL
    }

    private val layerPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 136, 192, 208) // rgba(136, 192, 208, 0.4) Nord Cyan
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val layerPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 143, 188, 187) // rgba(143, 188, 187, 0.35) Nord Teal
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val layerPaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 129, 161, 193) // rgba(129, 161, 193, 0.3) Nord Soft Blue
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        style = Paint.Style.FILL
        setShadowLayer(6f, 3f, 3f, Color.argb(220, 0, 0, 0))
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

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        // 1. Draw solid Base Nord Dark Background (#2E3440)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Offscreen Layer for SCREEN Blending of the 3 90deg vertical bar layers
        val saveLayer = canvas.saveLayer(0f, 0f, w, h, null)

        // Layer 1: 90deg vertical bars moving horizontally (2.2s loop)
        val offset1 = ((elapsed % 2.2f) / 2.2f) * w * 0.30f
        drawVerticalStripeLayer(canvas, w, h, offset1, layerPaint1, listOf(
            0.0f to 0.40f,
            0.50f to 0.90f
        ))

        // Layer 2: 90deg vertical bars moving horizontally (2.8s loop)
        val offset2 = ((elapsed % 2.8f) / 2.8f) * w * 0.35f
        drawVerticalStripeLayer(canvas, w, h, offset2 - w * 0.15f, layerPaint2, listOf(
            0.0f to 0.25f,
            0.35f to 0.65f,
            0.75f to 1.0f
        ))

        // Layer 3: 90deg vertical bars moving horizontally (3.5s loop)
        val offset3 = ((elapsed % 3.5f) / 3.5f) * w * 0.25f
        drawVerticalStripeLayer(canvas, w, h, offset3 - w * 0.10f, layerPaint3, listOf(
            0.0f to 0.30f,
            0.40f to 0.80f,
            0.90f to 1.0f
        ))

        canvas.restoreToCount(saveLayer)

        // 3. Monospace White Text on the left ("SN MATRIX: CRATE DIGGING.. FLIPPING RECORDS..")
        val responsiveTextSize = Math.max(20f, Math.min(w, h) * 0.045f)
        textPaint.textSize = responsiveTextSize

        val dotCount = ((elapsed * 3.0f).toInt() % 3) + 1
        val dots = ".".repeat(dotCount)

        val textX = w * 0.05f
        val textY1 = h * 0.48f
        val textY2 = textY1 + responsiveTextSize * 1.5f

        canvas.drawText("SN MATRIX: CRATE DIGGING$dots FLIPPING", textX, textY1, textPaint)
        canvas.drawText("RECORDS$dots", textX, textY2, textPaint)

        // 4. Station North Logo in Lower Right Corner
        logoBitmap?.let { bmp ->
            val logoW = Math.min(w, h) * 0.24f
            val logoH = logoW * (bmp.height.toFloat() / bmp.width.toFloat())
            val margin = w * 0.04f
            rectF.set(w - margin - logoW, h - margin - logoH, w - margin, h - margin)

            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = 210
            }
            canvas.drawBitmap(bmp, null, rectF, logoPaint)
        }

        postInvalidateOnAnimation()
    }

    private fun drawVerticalStripeLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        offsetX: Float,
        paint: Paint,
        bars: List<Pair<Float, Float>>
    ) {
        val totalW = w * 1.8f
        val startX = -w * 0.4f + offsetX
        for (bar in bars) {
            val left = startX + bar.first * totalW
            val right = startX + bar.second * totalW
            canvas.drawRect(left, 0f, right, h, paint)
        }
    }
}
