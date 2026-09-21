package net.sn.fetchplayer.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import net.sn.fetchplayer.R
import net.sn.fetchplayer.databinding.DialogChroniclesEbookBinding
import net.sn.fetchplayer.manager.QueenQuotesManager

class ChroniclesEbookDialog(context: Context) : Dialog(context) {

    private lateinit var binding: DialogChroniclesEbookBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogChroniclesEbookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        binding.btnCloseEbook.setOnClickListener {
            dismiss()
        }

        val rawText = QueenQuotesManager.getChroniclesText(context)
        val formattedSsb = formatChroniclesSsb(context, rawText)
        binding.tvEbookContent.text = formattedSsb
    }

    private fun formatChroniclesSsb(context: Context, raw: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val lines = raw.split("\n")

        val nord15 = ContextCompat.getColor(context, R.color.nord15)
        val nord8 = ContextCompat.getColor(context, R.color.nord8)
        val nord4 = ContextCompat.getColor(context, R.color.nord4)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Chapter", ignoreCase = true) || trimmed.startsWith("THE MANJARO", ignoreCase = true)) {
                val start = ssb.length
                ssb.append(trimmed).append("\n\n")
                val end = ssb.length
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(nord15), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(RelativeSizeSpan(1.3f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (trimmed.startsWith("Volume", ignoreCase = true) || trimmed.contains("//")) {
                val start = ssb.length
                ssb.append(trimmed).append("\n\n")
                val end = ssb.length
                ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(nord8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val start = ssb.length
                ssb.append(line).append("\n")
                val end = ssb.length
                ssb.setSpan(ForegroundColorSpan(nord4), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return ssb
    }
}
