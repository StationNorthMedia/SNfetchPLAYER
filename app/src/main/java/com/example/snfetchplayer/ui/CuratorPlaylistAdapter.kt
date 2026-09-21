package com.example.snfetchplayer.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.snfetchplayer.R
import com.example.snfetchplayer.databinding.ItemCuratorTrackBinding
import com.example.snfetchplayer.manager.PlaylistManager.RawPlaylistItem

class CuratorPlaylistAdapter(
    private val items: MutableList<RawPlaylistItem>,
    private val savedYtIds: MutableSet<String>,
    private var currentlyPlayingYtId: String? = null,
    var isSavedCatalogView: Boolean = false,
    private val onPlayClick: (RawPlaylistItem) -> Unit,
    private val onSaveClick: (RawPlaylistItem) -> Unit,
    private val onEditClick: ((RawPlaylistItem, Int) -> Unit)? = null,
    private val onRemoveClick: (RawPlaylistItem, Int) -> Unit
) : RecyclerView.Adapter<CuratorPlaylistAdapter.CuratorViewHolder>() {

    inner class CuratorViewHolder(val binding: ItemCuratorTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CuratorViewHolder {
        val binding = ItemCuratorTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CuratorViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CuratorViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val binding = holder.binding

        binding.tvIndex.text = "#${position + 1}"
        binding.tvTitle.text = item.title
        binding.tvArtist.text = item.artist
        binding.tvYtId.text = "ID: ${item.youtubeId}"

        val isSaved = savedYtIds.contains(item.youtubeId)
        val isPlaying = item.youtubeId == currentlyPlayingYtId

        // Highlight playing item
        if (isPlaying) {
            binding.cardTrackContainer.strokeColor = ContextCompat.getColor(context, R.color.nord8)
            binding.cardTrackContainer.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            binding.cardTrackContainer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.nord2))
        } else {
            binding.cardTrackContainer.strokeColor = ContextCompat.getColor(context, R.color.nord2)
            binding.cardTrackContainer.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            binding.cardTrackContainer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.nord1))
        }

        if (isSavedCatalogView) {
            // Mode B: Viewing & Editing Saved Catalog JSON
            binding.tvSavedStatusBadge.text = "IN CATALOG ✓"
            binding.tvSavedStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.nord14))
            binding.tvSavedStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.nord0))

            binding.btnSave.text = "Edit"
            binding.btnSave.setIconResource(R.drawable.ic_edit)
            binding.btnSave.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nord8))
            binding.btnSave.setTextColor(ContextCompat.getColor(context, R.color.nord8))

            binding.btnSave.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditClick?.invoke(item, pos)
                }
            }
        } else {
            // Mode A: Viewing & Curating Imported Playlist
            if (isSaved) {
                binding.tvSavedStatusBadge.text = "SAVED ✓"
                binding.tvSavedStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.nord14))
                binding.tvSavedStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.nord0))

                binding.btnSave.text = "Saved"
                binding.btnSave.setIconResource(R.drawable.ic_check)
                binding.btnSave.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nord14))
                binding.btnSave.setTextColor(ContextCompat.getColor(context, R.color.nord14))
            } else {
                binding.tvSavedStatusBadge.text = "NEW"
                binding.tvSavedStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.nord3))
                binding.tvSavedStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.nord6))

                binding.btnSave.text = "Save"
                binding.btnSave.setIconResource(R.drawable.ic_save)
                binding.btnSave.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nord14))
                binding.btnSave.setTextColor(ContextCompat.getColor(context, R.color.nord14))
            }

            binding.btnSave.setOnClickListener { onSaveClick(item) }
        }

        binding.root.setOnClickListener { onPlayClick(item) }
        binding.btnPlay.setOnClickListener { onPlayClick(item) }
        binding.btnRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onRemoveClick(item, pos)
            }
        }
    }

    fun updatePlayingId(youtubeId: String?) {
        this.currentlyPlayingYtId = youtubeId
        notifyDataSetChanged()
    }

    fun markSaved(youtubeId: String) {
        savedYtIds.add(youtubeId)
        notifyDataSetChanged()
    }

    fun markRemoved(youtubeId: String) {
        savedYtIds.remove(youtubeId)
        notifyDataSetChanged()
    }

    fun removeItemAt(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    fun updateItemAt(position: Int, updatedItem: RawPlaylistItem) {
        if (position in 0 until items.size) {
            items[position] = updatedItem
            notifyItemChanged(position)
        }
    }

    fun setItems(newItems: List<RawPlaylistItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<RawPlaylistItem> = items
}
