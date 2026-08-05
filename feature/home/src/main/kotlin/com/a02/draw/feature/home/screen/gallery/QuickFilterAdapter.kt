package com.a02.draw.feature.home.screen.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.model.GalleryFilter
import com.a02.draw.feature.home.common.motion.pulse
import com.a02.draw.feature.home.databinding.ItemFilterChipBinding

data class QuickFilterItem(val filter: GalleryFilter, val isSelected: Boolean)

class QuickFilterAdapter(
    private val onClick: (GalleryFilter) -> Unit,
) : ListAdapter<QuickFilterItem, QuickFilterAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemFilterChipBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QuickFilterItem) {
            binding.root.text = item.filter.label()
            binding.root.isSelected = item.isSelected
            binding.root.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.isSelected) R.color.home_purple else R.color.home_navy,
                ),
            )
            binding.root.setOnClickListener {
                binding.root.pulse(1.05f)
                onClick(item.filter)
            }
        }

        private fun GalleryFilter.label() = binding.root.context.getString(
            when (this) {
                GalleryFilter.SAVED -> R.string.saved
                GalleryFilter.ALL -> R.string.all
                GalleryFilter.EASY -> R.string.easy
                GalleryFilter.JUJUTSU_KAISEN -> R.string.jujutsu_kaisen
                GalleryFilter.ONE_PIECE -> R.string.one_piece
                GalleryFilter.DORAEMON -> R.string.doraemon
            },
        )
    }

    private object Diff : DiffUtil.ItemCallback<QuickFilterItem>() {
        override fun areItemsTheSame(oldItem: QuickFilterItem, newItem: QuickFilterItem) =
            oldItem.filter == newItem.filter

        override fun areContentsTheSame(oldItem: QuickFilterItem, newItem: QuickFilterItem) =
            oldItem == newItem
    }
}
