package net.sn.fetchplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.sn.fetchplayer.R
import net.sn.fetchplayer.data.LexiconArtist

class LexiconDialogAdapter(
    private var artists: List<LexiconArtist>,
    private val onArtistClick: (LexiconArtist) -> Unit
) : RecyclerView.Adapter<LexiconDialogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvArtistName: TextView = view.findViewById(R.id.tvArtistName)
        val tvArtistQid: TextView = view.findViewById(R.id.tvArtistQid)
        val tvArtistBioSnippet: TextView = view.findViewById(R.id.tvArtistBioSnippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lexicon_artist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = artists[position]
        holder.tvArtistName.text = artist.name
        holder.tvArtistQid.text = if (artist.qid.isNotEmpty()) artist.qid else ""
        holder.tvArtistBioSnippet.text = artist.bio

        holder.itemView.setOnClickListener {
            onArtistClick(artist)
        }
    }

    override fun getItemCount(): Int = artists.size

    fun updateData(newArtists: List<LexiconArtist>) {
        artists = newArtists
        notifyDataSetChanged()
    }
}
