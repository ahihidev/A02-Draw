package com.a02.draw.feature.home.screen.emojimix

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.feature.home.databinding.ItemEmojiOptionBinding

class EmojiOptionAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<EmojiOption, EmojiOptionAdapter.Holder>(Diff) {
    private var selected: Set<String> = emptySet()

    fun setSelected(value: List<String>) {
        selected = value.toSet()
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemEmojiOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads)
        else holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemEmojiOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(option: EmojiOption) {
            binding.emoji.text = option.emoji
            binding.root.isChecked = option.emoji in selected
            binding.root.contentDescription = option.label
            binding.root.setOnClickListener { onClick(option.emoji) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<EmojiOption>() {
        override fun areItemsTheSame(oldItem: EmojiOption, newItem: EmojiOption) =
            oldItem.emoji == newItem.emoji

        override fun areContentsTheSame(oldItem: EmojiOption, newItem: EmojiOption) =
            oldItem == newItem
    }

    private companion object {
        const val SELECTION_PAYLOAD = "selection"
    }
}
