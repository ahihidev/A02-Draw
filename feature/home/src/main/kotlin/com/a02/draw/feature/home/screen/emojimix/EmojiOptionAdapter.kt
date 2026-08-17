package com.a02.draw.feature.home.screen.emojimix

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.feature.home.databinding.ItemEmojiOptionBinding

data class EmojiOptionItem(
    val option: EmojiOption,
    val isSelected: Boolean,
    val isEnabled: Boolean,
)

class EmojiOptionAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<EmojiOptionItem, EmojiOptionAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemEmojiOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        val selectionPayload = payloads.lastOrNull() as? SelectionPayload
        if (selectionPayload == null) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindSelection(selectionPayload)
        }
    }

    inner class Holder(private val binding: ItemEmojiOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: EmojiOptionItem) {
            val option = item.option
            binding.emoji.text = option.emoji
            binding.root.contentDescription = option.label
            binding.root.setOnClickListener { onClick(option.emoji) }
            bindSelection(SelectionPayload(item.isSelected, item.isEnabled))
        }

        fun bindSelection(payload: SelectionPayload) {
            binding.root.isChecked = payload.isSelected
            binding.root.isEnabled = payload.isEnabled
            binding.root.alpha = if (payload.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        }
    }

    data class SelectionPayload(
        val isSelected: Boolean,
        val isEnabled: Boolean,
    )

    private object Diff : DiffUtil.ItemCallback<EmojiOptionItem>() {
        override fun areItemsTheSame(oldItem: EmojiOptionItem, newItem: EmojiOptionItem) =
            oldItem.option.emoji == newItem.option.emoji

        override fun areContentsTheSame(oldItem: EmojiOptionItem, newItem: EmojiOptionItem) =
            oldItem == newItem

        override fun getChangePayload(oldItem: EmojiOptionItem, newItem: EmojiOptionItem): Any? =
            if (oldItem.option == newItem.option) {
                SelectionPayload(newItem.isSelected, newItem.isEnabled)
            } else {
                null
            }
    }

    private companion object {
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.35f
    }
}
