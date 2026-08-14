package com.a02.draw.feature.home.screen.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.databinding.ItemHomeTopicBinding
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader

class HomeTopicAdapter(
    private val imageLoader: HomeImageLoader,
    private val onTopicClick: (String) -> Unit,
) : ListAdapter<DrawingTopic, HomeTopicAdapter.TopicViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder =
        TopicViewHolder(
            ItemHomeTopicBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TopicViewHolder(
        private val binding: ItemHomeTopicBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(topic: DrawingTopic, position: Int) {
            binding.topicTitle.text = topic.title
            imageLoader.load(binding.topicImage, topic.image, topicFallback(position))
            binding.root.setDebouncedClickListener { onTopicClick(topic.id) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<DrawingTopic>() {
            override fun areItemsTheSame(oldItem: DrawingTopic, newItem: DrawingTopic): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DrawingTopic, newItem: DrawingTopic): Boolean =
                oldItem == newItem
        }

        fun topicFallback(position: Int): Int = when (position % 6) {
            0 -> R.drawable.topic_chibi
            1 -> R.drawable.topic_pixel
            2 -> R.drawable.topic_anime
            3 -> R.drawable.topic_cartoon
            4 -> R.drawable.figma_topic_world_cup
            else -> R.drawable.figma_topic_lego
        }
    }
}
