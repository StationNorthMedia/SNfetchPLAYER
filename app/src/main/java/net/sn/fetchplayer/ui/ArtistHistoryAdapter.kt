package net.sn.fetchplayer.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import net.sn.fetchplayer.R
import net.sn.fetchplayer.model.Track

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
        val tvArtistTitle: TextView = view.findViewById(R.id.tvArtistTitle)
        val tvTrackTitle: TextView = view.findViewById(R.id.tvTrackTitle)
        val tvArtistExtract: TextView = view.findViewById(R.id.tvArtistExtract)
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

        // Artist Title
        holder.tvArtistTitle.text = item.displayTitle
        val titleColor = item.textColor ?: ContextCompat.getColor(context, R.color.nord8)
        holder.tvArtistTitle.setTextColor(titleColor)

        // Track Title Subheader (if distinct)
        if (item.track.title.isNotBlank() && item.track.title != item.displayTitle) {
            holder.tvTrackTitle.text = "Track: ${item.track.title}"
            holder.tvTrackTitle.visibility = View.VISIBLE
        } else {
            holder.tvTrackTitle.visibility = View.GONE
        }

        // Bio Extract Text
        holder.tvArtistExtract.text = item.extract
        if (item.textColor != null) {
            holder.tvArtistExtract.setTextColor(item.textColor)
            holder.tvArtistExtract.setTypeface(null, Typeface.BOLD)
        } else {
            holder.tvArtistExtract.setTextColor(ContextCompat.getColor(context, R.color.nord4))
            holder.tvArtistExtract.setTypeface(null, Typeface.NORMAL)
        }

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
