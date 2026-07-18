package com.a02.draw.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.domain.model.Drawing
import com.a02.draw.feature.home.databinding.ItemDrawingBinding
import java.text.DateFormat
import java.util.Date

class DrawingAdapter(
    private val onClick: (Drawing) -> Unit,
) : ListAdapter<Drawing, DrawingAdapter.DrawingViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawingViewHolder {
        val binding = ItemDrawingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DrawingViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: DrawingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DrawingViewHolder(
        private val binding: ItemDrawingBinding,
        private val onClick: (Drawing) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Drawing) = with(binding) {
            drawingTitle.text = item.title
            drawingUpdatedAt.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(item.updatedAtEpochMillis))
            root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Drawing>() {
        override fun areItemsTheSame(oldItem: Drawing, newItem: Drawing): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Drawing, newItem: Drawing): Boolean =
            oldItem == newItem
    }
}
