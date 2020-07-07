package com.what3words.voiceapidemo

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.card_suggestion.view.*

class SuggestionsAdapter : RecyclerView.Adapter<SuggestionsAdapter.SuggestionHolder>() {

    inner class SuggestionHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val words: TextView = view.textWords
        private val country: ImageView = view.imageCountry

        fun setUpView(suggestion: Suggestion) {
            words.text = suggestion.words
            Glide.with(country.context)
                .load(Uri.parse("https://www.countryflags.io/${suggestion.country}/flat/64.png"))
                .into(country)
        }
    }

    private var suggestions: List<Suggestion> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionHolder {
        return SuggestionHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.card_suggestion,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return suggestions.size
    }

    override fun onBindViewHolder(holder: SuggestionHolder, position: Int) {
        holder.setUpView(suggestions[position])
    }

    fun setData(newData: List<Suggestion>) {
        suggestions = newData
        notifyDataSetChanged()
    }
}