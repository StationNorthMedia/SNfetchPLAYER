package net.sn.fetchplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import net.sn.fetchplayer.R
import kotlin.math.min
import kotlin.math.sin

class NordCrateDiggingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val startTime = System.currentTimeMillis()

    private val nord0 = ContextCompat.getColor(context, R.color.nord0)
    private val nord1 = ContextCompat.getColor(context, R.color.nord1)
    private val nord2 = ContextCompat.getColor(context, R.color.nord2)
    private val nord4 = ContextCompat.getColor(context, R.color.nord4)
    private val nord6 = ContextCompat.getColor(context, R.color.nord6)
    private val nord7 = ContextCompat.getColor(context, R.color.nord7)
    private val nord8 = ContextCompat.getColor(context, R.color.nord8)
    private val nord9 = ContextCompat.getColor(context, R.color.nord9)

    private val bgPaint = Paint().apply {
        color = 0xF21D212A.toInt() // 95% opaque Nord0
        style = Paint.Style.FILL
    }

    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord1
        style = Paint.Style.FILL
    }

    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord2
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord8
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord7
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord6
        textSize = 36f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = nord8
        textSize = 24f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
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
        val cy = h * 0.40f
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        // 1. Semi-translucent Nord Dark Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. KINETIC MULTI-LAYER SHIMMER BANDS (from tv.css #video-overlay)
        drawShimmerBand(canvas, w, h, elapsed, 2.2f, 0.40f, nord8)
        drawShimmerBand(canvas, w, h, elapsed + 1.2f, 2.8f, 0.35f, nord7)
        drawShimmerBand(canvas, w, h, elapsed + 2.1f, 3.5f, 0.30f, nord9)

        // 3. ROTATING VINYL / QUEEN LOGO BADGE IN CENTER
        val vinylRadius = min(w, h) * 0.18f
        val rotationAngle = (elapsed * 90f) % 360f

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // Vinyl disc body
        canvas.drawCircle(cx, cy, vinylRadius, vinylPaint)

        // Vinyl concentric grooves
        for (r in listOf(0.85f, 0.70f, 0.55f)) {
            canvas.drawCircle(cx, cy, vinylRadius * r, groovePaint)
        }

        // Center vinyl label circle
        val centerLabelRadius = vinylRadius * 0.38f
        canvas.drawCircle(cx, cy, centerLabelRadius, centerLabelPaint)

        // Center logo inside vinyl label
        logoBitmap?.let { bmp ->
            val imgW = centerLabelRadius * 1.3f
            val imgH = imgW * (bmp.height.toFloat() / bmp.width.toFloat())
            rectF.set(cx - imgW / 2f, cy - imgH / 2f, cx + imgW / 2f, cy + imgH / 2f)
            canvas.drawBitmap(bmp, null, rectF, null)
        }

        // Outer neon border ring
        canvas.restore()
        borderPaint.alpha = (180 + 75 * sin(elapsed * 5.0)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, vinylRadius + 4f, borderPaint)

        // 4. DIGGING TEXT & PULSING DOTS
        val textY = cy + vinylRadius + min(w, h) * 0.12f
        val dotCount = ((elapsed * 3.0f).toInt() % 4)
        val dots = ".".repeat(dotCount)

        val responsiveTextSize = min(w, h) * 0.050f
        textPaint.textSize = responsiveTextSize
        subTextPaint.textSize = responsiveTextSize * 0.60f

        canvas.drawText("CRATE DIGGING$dots", cx, textY, textPaint)
        canvas.drawText("STATION NORTH // AUDIO & TV REALM", cx, textY + responsiveTextSize * 1.4f, subTextPaint)

        postInvalidateOnAnimation()
    }

    private fun drawShimmerBand(
        canvas: Canvas,
        w: Float,
        h: Float,
        elapsed: Float,
        period: Float,
        alphaPercent: Float,
        colorInt: Int
    ) {
        val progress = (elapsed % period) / period
        val offsetX = (progress * w * 1.4f) - (w * 0.2f)
        val bandWidth = w * 0.5f

        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        val alphaInt = (alphaPercent * 255).toInt().coerceIn(0, 255)

        val cStart = (alphaInt shl 24) or (r shl 16) or (g shl 8) or b
        val cTransparent = 0x00000000

        val shader = LinearGradient(
            offsetX, 0f, offsetX + bandWidth, h,
            intArrayOf(cTransparent, cStart, cTransparent),
            floatArrayOf(0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        stripePaint.shader = shader
        canvas.drawRect(0f, 0f, w, h, stripePaint)
        stripePaint.shader = null
    }
}
