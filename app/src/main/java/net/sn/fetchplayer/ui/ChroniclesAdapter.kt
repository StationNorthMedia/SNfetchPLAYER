package net.sn.fetchplayer.ui

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.sn.fetchplayer.R
import net.sn.fetchplayer.databinding.ItemChroniclesCardBinding
import net.sn.fetchplayer.model.ChronicleCardItem
import net.sn.fetchplayer.util.AppLogger
import java.io.InputStream

class ChroniclesAdapter(
    private val items: List<ChronicleCardItem>,
    private val onPlayPauseClick: (ChronicleCardItem) -> Unit,
    private val onSeek: (ChronicleCardItem, Float) -> Unit
) : RecyclerView.Adapter<ChroniclesAdapter.ChronicleViewHolder>() {

    companion object {
        private const val PAYLOAD_PROGRESS = "PAYLOAD_PROGRESS"
    }

    private var activeTrackId: Int = -1
    private var isPlaying: Boolean = false
    private var currentProgressPercent: Float = 0f
    private var currentTimeMs: Long = 0L
    private var durationMs: Long = 0L

    fun setActiveState(trackId: Int, playing: Boolean, progressPercent: Float, currentMs: Long, totalMs: Long) {
        val prevActive = activeTrackId
        val trackChanged = (prevActive != trackId)
        activeTrackId = trackId
        isPlaying = playing
        currentProgressPercent = progressPercent
        currentTimeMs = currentMs
        durationMs = totalMs

        if (prevActive != -1 && trackChanged) {
            val prevIndex = items.indexOfFirst { it.id == prevActive }
            if (prevIndex != -1) notifyItemChanged(prevIndex)
        }

        val currIndex = items.indexOfFirst { it.id == trackId }
        if (currIndex != -1) {
            if (trackChanged) {
                notifyItemChanged(currIndex)
            } else {
                notifyItemChanged(currIndex, PAYLOAD_PROGRESS)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChronicleViewHolder {
        val binding = ItemChroniclesCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChronicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChronicleViewHolder, position: Int) {
        val item = items[position]
        val prevItem = if (position > 0) items[position - 1] else null
        holder.bind(item, prevItem)
    }

    override fun onBindViewHolder(holder: ChronicleViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            holder.updateProgressOnly(isPlaying, currentProgressPercent, currentTimeMs, durationMs)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ChronicleViewHolder(val binding: ItemChroniclesCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChronicleCardItem, prevItem: ChronicleCardItem?) {
            val context = binding.root.context

            // Chapter Header
            if (item.chapter.isNotEmpty() && (prevItem == null || prevItem.chapter != item.chapter)) {
                binding.tvChapterHeader.visibility = View.VISIBLE
                binding.tvChapterHeader.text = item.chapter
            } else {
                binding.tvChapterHeader.visibility = View.GONE
            }

            // Paragraph Image
            if (item.image.isNotEmpty()) {
                binding.ivParagraphImage.visibility = View.VISIBLE
                try {
                    val stream: InputStream = context.assets.open("comic/" + item.image)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    binding.ivParagraphImage.setImageBitmap(bitmap)
                    stream.close()
                } catch (e: Exception) {
                    AppLogger.w("ChroniclesAdapter", "Could not load image asset comic/${item.image}: ${e.message}")
                    binding.ivParagraphImage.visibility = View.GONE
                }
            } else {
                binding.ivParagraphImage.visibility = View.GONE
            }

            // Story Text
            binding.tvParagraphText.text = item.text

            // Audio Label
            binding.tvAudioLabel.text = "🎧 AUDIO #${item.id}"

            val nord2 = ContextCompat.getColor(context, R.color.nord2)
            val nord8 = ContextCompat.getColor(context, R.color.nord8)
            val nord11 = ContextCompat.getColor(context, R.color.nord11)

            val isActive = (item.id == activeTrackId)

            if (isActive) {
                binding.cardParagraph.setStrokeColor(nord11)
                binding.cardParagraph.setStrokeWidth(4)
                binding.btnPlayItem.text = if (isPlaying) "⏸" else "▶"
                binding.btnPlayItem.setStrokeColor(ColorStateList.valueOf(nord11))
                binding.btnPlayItem.setTextColor(nord11)
                binding.sbProgressItem.progress = (currentProgressPercent * 100).toInt()
                binding.tvTimeItem.text = formatTime(currentTimeMs, durationMs)
            } else {
                binding.cardParagraph.setStrokeColor(nord2)
                binding.cardParagraph.setStrokeWidth(2)
                binding.btnPlayItem.text = "▶"
                binding.btnPlayItem.setStrokeColor(ColorStateList.valueOf(nord8))
                binding.btnPlayItem.setTextColor(nord8)
                binding.sbProgressItem.progress = 0
                binding.tvTimeItem.text = "0:00 / 0:00"
            }

            binding.btnPlayItem.setOnClickListener {
                onPlayPauseClick(item)
            }

            binding.sbProgressItem.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        onSeek(item, progress / 100f)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        fun updateProgressOnly(playing: Boolean, progressPercent: Float, currentMs: Long, totalMs: Long) {
            val context = binding.root.context
            val nord11 = ContextCompat.getColor(context, R.color.nord11)
            binding.cardParagraph.setStrokeColor(nord11)
            binding.cardParagraph.setStrokeWidth(4)
            binding.btnPlayItem.text = if (playing) "⏸" else "▶"
            binding.btnPlayItem.setStrokeColor(ColorStateList.valueOf(nord11))
            binding.btnPlayItem.setTextColor(nord11)
            binding.sbProgressItem.progress = (progressPercent * 100).toInt()
            binding.tvTimeItem.text = formatTime(currentMs, totalMs)
        }

        private fun formatTime(currentMs: Long, totalMs: Long): String {
            val curSec = (currentMs / 1000).toInt()
            val totSec = (totalMs / 1000).toInt()
            val curMin = curSec / 60
            val curRem = curSec % 60
            val totMin = totSec / 60
            val totRem = totSec % 60
            return String.format("%d:%02d / %d:%02d", curMin, curRem, totMin, totRem)
        }
    }
}
