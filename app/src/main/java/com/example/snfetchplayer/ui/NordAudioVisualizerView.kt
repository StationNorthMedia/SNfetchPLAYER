package com.example.snfetchplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class NordAudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numBars = 36
    private val barHeights = FloatArray(numBars) { 0.15f }
    private val targetHeights = FloatArray(numBars) { 0.15f }
    private val peakHeights = FloatArray(numBars) { 0.15f }
    private val peakVelocities = FloatArray(numBars) { 0.0f }
    private var maxObservedMag = 35.0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val baseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4488C0D0.toInt()
    }

    private var isPlaying = false
    private var phase = 0f
    private var lastFftTime = 0L

    private val barRect = RectF()
    private val peakRect = RectF()

    // Nord Palette Colors (Frost & Aurora)
    private val nord10 = 0xFF5E81AC.toInt() // Deep Blue
    private val nord9  = 0xFF81A1C1.toInt() // Ice Blue
    private val nord8  = 0xFF88C0D0.toInt() // Frost Cyan
    private val nord7  = 0xFF8FBCBB.toInt() // Teal
    private val nord14 = 0xFFA3BE8C.toInt() // Green
    private val nord13 = 0xFFEBCB8B.toInt() // Yellow
    private val nord12 = 0xFFD08770.toInt() // Orange
    private val nord11 = 0xFFBF616A.toInt() // Red
    private val nord15 = 0xFFB48EAD.toInt() // Purple

    private val gradientColors = intArrayOf(
        nord10, nord9, nord8, nord7, nord14, nord13, nord12, nord11
    )
    private val gradientPositions = floatArrayOf(
        0.0f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 0.9f, 1.0f
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            val shader = LinearGradient(
                0f, h.toFloat(), 0f, 0f,
                gradientColors,
                gradientPositions,
                Shader.TileMode.CLAMP
            )
            barPaint.shader = shader
        }
    }

    interface OnBassPulseListener {
        fun onBassPulse(amplitude: Float)
    }

    private var bassPulseListener: OnBassPulseListener? = null

    fun setOnBassPulseListener(listener: OnBassPulseListener?) {
        this.bassPulseListener = listener
    }

    fun setAudioPlaying(playing: Boolean) {
        if (isPlaying != playing) {
            isPlaying = playing
            invalidate()
        }
    }

    /**
     * Process raw Android FFT byte array using Logarithmic Frequency Binning,
     * Dynamic Automatic Gain Control (AGC), and Balanced Amplitude Normalization.
     */
    fun updateFft(fft: ByteArray) {
        if (!isPlaying || fft.size < 8) return
        lastFftTime = System.currentTimeMillis()

        val n = fft.size
        val sampleRate = 44100 // Standard Audio sampling rate
        val minFreq = 40.0     // Sub-bass 40Hz
        val maxFreq = 14000.0  // High treble 14kHz

        val numBins = n / 2
        val binWidth = sampleRate.toDouble() / n

        var maxFrameMag = 0f

        for (i in 0 until numBars) {
            // Logarithmic frequency bounds for bar i
            val fLow = minFreq * (maxFreq / minFreq).pow(i.toDouble() / numBars)
            val fHigh = minFreq * (maxFreq / minFreq).pow((i + 1).toDouble() / numBars)

            var kStart = (fLow / binWidth).toInt().coerceIn(1, numBins - 1)
            var kEnd = (fHigh / binWidth).toInt().coerceIn(1, numBins - 1)
            if (kEnd < kStart) kEnd = kStart

            var sumMag = 0f
            var count = 0
            for (k in kStart..kEnd) {
                val rIndex = k * 2
                val iIndex = k * 2 + 1
                if (iIndex < fft.size) {
                    val r = fft[rIndex].toFloat()
                    val im = fft[iIndex].toFloat()
                    val mag = hypot(r, im)
                    sumMag += mag
                    count++
                }
            }

            val avgMag = if (count > 0) sumMag / count else 0f
            if (avgMag > maxFrameMag) {
                maxFrameMag = avgMag
            }

            // Gentle frequency weighting to balance bass and high-end presence
            val freqWeight = 0.85f + (i.toFloat() / numBars.toFloat()) * 0.45f

            // Dynamic gain normalization (prevents sticking to 100% max ceiling)
            val normMag = if (maxObservedMag > 0.1f) (avgMag / maxObservedMag) * freqWeight else 0f
            val normalized = (normMag * 0.78f).coerceIn(0.08f, 0.82f)

            targetHeights[i] = normalized
        }

        // Smooth Automatic Gain Control (AGC) tracking
        if (maxFrameMag > maxObservedMag) {
            maxObservedMag += (maxFrameMag - maxObservedMag) * 0.25f
        } else {
            maxObservedMag = Math.max(25f, maxObservedMag * 0.992f)
        }

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val gap = 6f
        val totalGaps = (numBars - 1) * gap
        val barWidth = (width - totalGaps) / numBars

        val now = System.currentTimeMillis()
        val useSyntheticWave = isPlaying && (now - lastFftTime > 600)

        if (useSyntheticWave) {
            phase += 0.12f
            for (i in 0 until numBars) {
                val bassPulse = sin(phase * 1.1f) * 0.28f
                val midPulse = sin(phase * 0.75f + i * 0.32f) * 0.20f
                val treblePulse = sin(phase * 1.6f + i * 0.55f) * 0.15f
                val freqWeight = (1.0f - (i.toFloat() / numBars.toFloat()) * 0.35f)
                val base = 0.15f + (bassPulse + midPulse + treblePulse) * freqWeight
                targetHeights[i] = base.coerceIn(0.08f, 0.72f)
            }
        }

        // Draw glowing baseline at bottom
        canvas.drawRect(0f, height - 3f, width, height, baseLinePaint)

        val peakCapHeight = (barWidth * 0.8f).coerceIn(4f, 12f)

        for (i in 0 until numBars) {
            if (isPlaying) {
                barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.35f
            } else {
                barHeights[i] += (0.06f - barHeights[i]) * 0.15f
            }

            // Peak cap physics (gravity & bounce)
            if (barHeights[i] > peakHeights[i]) {
                peakHeights[i] = barHeights[i]
                peakVelocities[i] = 0.004f
            } else {
                peakVelocities[i] += 0.007f // Gravity
                peakHeights[i] = (peakHeights[i] - peakVelocities[i]).coerceAtLeast(barHeights[i])
            }

            val minBarHeight = 10f
            val maxBarHeight = height - peakCapHeight - 4f
            val actualBarH = Math.max(minBarHeight, barHeights[i] * maxBarHeight)
            val left = i * (barWidth + gap)
            val bottom = height - 2f
            val top = bottom - actualBarH
            val right = left + barWidth

            // Draw Bar
            barRect.set(left, top, right, bottom)
            val cornerRadius = barWidth / 2f
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)

            // Draw Floating Peak Cap
            val peakH = Math.max(actualBarH + peakCapHeight + 2f, peakHeights[i] * maxBarHeight + peakCapHeight)
            val peakTop = (bottom - peakH).coerceAtLeast(0f)
            val peakBottom = peakTop + peakCapHeight

            peakRect.set(left, peakTop, right, peakBottom)

            // Color peak cap dynamically based on frequency bar position
            val peakColor = when {
                i < numBars * 0.3 -> nord8  // Bass: Cyan
                i < numBars * 0.6 -> nord14 // Mids: Green
                i < numBars * 0.8 -> nord13 // High-Mids: Yellow
                else -> nord11               // Treble: Aurora Red
            }
            peakPaint.color = peakColor

            canvas.drawRoundRect(peakRect, cornerRadius, cornerRadius, peakPaint)
        }

        if (isPlaying) {
            val bassEnergy = ((barHeights[0] + barHeights[1] + barHeights[2] + barHeights[3]) / 4f).coerceIn(0f, 1f)
            bassPulseListener?.onBassPulse(bassEnergy)
        }

        if (isPlaying || barHeights.any { it > 0.08f }) {
            postInvalidateOnAnimation()
        }
    }
}
