package com.a02.draw.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.R
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val onLanguageClick: (String) -> Unit,
) : ListAdapter<LanguageItem, LanguageAdapter.LanguageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder =
        LanguageViewHolder(
            ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LanguageViewHolder(
        private val binding: ItemLanguageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LanguageItem) = with(binding) {
            val languageName = root.context.getString(item.option.nameRes)
            languageFlag.text = item.option.flag
            languageNameView.text = languageName
            languageCard.isChecked = item.isSelected
            selectedIndicator.visibility = if (item.isSelected) View.VISIBLE else View.INVISIBLE
            root.isSelected = item.isSelected
            root.contentDescription = root.context.getString(
                if (item.isSelected) R.string.language_option_selected_a11y
                else R.string.language_option_a11y,
                languageName,
            )
            root.setDebouncedClickListener { onLanguageClick(item.option.languageTag) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<LanguageItem>() {
            override fun areItemsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean =
                oldItem.option.languageTag == newItem.option.languageTag

            override fun areContentsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean =
                oldItem == newItem
        }
    }
}
