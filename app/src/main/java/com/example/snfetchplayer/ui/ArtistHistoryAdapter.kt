package com.example.snfetchplayer.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.snfetchplayer.R
import com.example.snfetchplayer.model.Track
import com.google.android.material.imageview.ShapeableImageView

data class ArtistHistoryItem(
    val track: Track,
    val displayTitle: String,
    val imageUrl: String?,
    var imageBitmap: android.graphics.Bitmap?,
    val extract: String,
    val textColor: Int? = null
)

class ArtistHistoryAdapter(
    private val context: Context,
    private val items: MutableList<ArtistHistoryItem> = mutableListOf(),
    private val onItemClick: (Track) -> Unit
) : RecyclerView.Adapter<ArtistHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgArtistPhoto: ShapeableImageView = view.findViewById(R.id.imgArtistPhoto)
        val tvArtistInfoText: TextView = view.findViewById(R.id.tvArtistInfoText)
        val dividerView: View = view.findViewById(R.id.viewDivider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_artist_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        if (item.imageBitmap != null) {
            holder.imgArtistPhoto.setImageBitmap(item.imageBitmap)
        } else {
            holder.imgArtistPhoto.setImageResource(R.drawable.sn_logo)
        }

        val density = context.resources.displayMetrics.density
        val imgWidthPx = (85 * density + 10 * density).toInt()

        val ssb = SpannableStringBuilder()

        // Artist Title (Bold + Nord8 Cyan)
        val titleStart = ssb.length
        ssb.append(item.displayTitle).append("\n")
        val titleEnd = ssb.length
        ssb.setSpan(StyleSpan(Typeface.BOLD), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val headerColor = if (item.textColor != null) item.textColor else ContextCompat.getColor(context, R.color.nord8)
        ssb.setSpan(ForegroundColorSpan(headerColor), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(1.25f), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Track Title Sub-Header if available
        if (item.track.title.isNotBlank() && item.track.title != item.displayTitle) {
            val subStart = ssb.length
            ssb.append("Track: ").append(item.track.title).append("\n")
            val subEnd = ssb.length
            ssb.setSpan(StyleSpan(Typeface.ITALIC), subStart, subEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.nord6)), subStart, subEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(1.05f), subStart, subEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Bio Extract Text (Styled with ambient color if present)
        val extractStart = ssb.length
        ssb.append(item.extract)
        val extractEnd = ssb.length
        if (item.textColor != null) {
            ssb.setSpan(ForegroundColorSpan(item.textColor), extractStart, extractEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), extractStart, extractEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            ssb.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.nord4)), extractStart, extractEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Floating Text Margin Span
        ssb.setSpan(FloatingTextMarginSpan(imgWidthPx, 6), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        holder.tvArtistInfoText.text = ssb
        holder.dividerView.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            onItemClick(item.track)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<ArtistHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addOrUpdateItem(newItem: ArtistHistoryItem) {
        val existingIndex = items.indexOfFirst { it.track.id == newItem.track.id || (it.track.youtubeId != null && it.track.youtubeId == newItem.track.youtubeId) }
        if (existingIndex >= 0) {
            items[existingIndex] = newItem
            notifyItemChanged(existingIndex)
        } else {
            items.add(0, newItem) // Add to top of history
            notifyItemInserted(0)
        }
    }
}
