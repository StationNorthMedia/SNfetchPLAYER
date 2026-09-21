package com.example.snfetchplayer.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

class FloatingTextMarginSpan(
    private val marginPx: Int,
    private val lineCount: Int
) : LeadingMarginSpan.LeadingMarginSpan2 {

    override fun getLeadingMargin(first: Boolean): Int {
        return if (first) marginPx else 0
    }

    override fun drawLeadingMargin(
        c: Canvas?,
        p: Paint?,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence?,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?
    ) {
        // Margin offset only, no custom drawing
    }

    override fun getLeadingMarginLineCount(): Int {
        return lineCount
    }
}
