package com.example.snfetchplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.snfetchplayer.R
import com.example.snfetchplayer.data.LexiconSearchSuggestion

class LexiconSearchDropdownAdapter(
    private var suggestions: List<LexiconSearchSuggestion>,
    private val onSuggestionClick: (LexiconSearchSuggestion) -> Unit
) : RecyclerView.Adapter<LexiconSearchDropdownAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDropdownTitle: TextView = view.findViewById(R.id.tvDropdownTitle)
        val tvDropdownBadge: TextView = view.findViewById(R.id.tvDropdownBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lexicon_dropdown_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.tvDropdownTitle.text = suggestion.title
        holder.tvDropdownBadge.text = suggestion.badge

        holder.itemView.setOnClickListener {
            onSuggestionClick(suggestion)
        }
    }

    override fun getItemCount(): Int = suggestions.size

    fun updateData(newSuggestions: List<LexiconSearchSuggestion>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }
}
