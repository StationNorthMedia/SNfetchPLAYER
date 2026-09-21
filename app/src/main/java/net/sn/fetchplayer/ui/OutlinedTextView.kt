package net.sn.fetchplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import net.sn.fetchplayer.R

class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeColor: Int = ContextCompat.getColor(context, R.color.nord0)
    private var strokeWidthPx: Float = 4f

    init {
        val density = context.resources.displayMetrics.density
        strokeWidthPx = 3.5f * density
        strokeColor = ContextCompat.getColor(context, R.color.nord0)
    }

    fun setStrokeColor(color: Int) {
        strokeColor = color
        invalidate()
    }

    fun setStrokeWidthDp(widthDp: Float) {
        val density = context.resources.displayMetrics.density
        strokeWidthPx = widthDp * density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val originalTextColor = currentTextColor

        // Draw outer dark stroke outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidthPx
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeMiter = 10f
        setTextColor(strokeColor)
        super.onDraw(canvas)

        // Draw inner fill text
        paint.style = Paint.Style.FILL
        setTextColor(originalTextColor)
        super.onDraw(canvas)
    }
}
