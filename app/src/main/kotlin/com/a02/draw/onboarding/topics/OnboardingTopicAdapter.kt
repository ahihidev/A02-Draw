package com.a02.draw.onboarding.topics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.databinding.ItemOnboardingTopicBinding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.onboarding.common.image.OnboardingImageLoader

data class OnboardingTopicItem(
    val topic: DrawingTopic,
    val isSelected: Boolean,
)

class OnboardingTopicAdapter(
    private val imageLoader: OnboardingImageLoader,
    private val onTopicClick: (String) -> Unit,
) : ListAdapter<OnboardingTopicItem, OnboardingTopicAdapter.TopicViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder =
        TopicViewHolder(
            ItemOnboardingTopicBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TopicViewHolder(
        private val binding: ItemOnboardingTopicBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OnboardingTopicItem, position: Int) {
            binding.topicTitle.text = item.topic.title
            binding.topicCard.isChecked = item.isSelected
            binding.selectedIndicator.visibility = if (item.isSelected) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            imageLoader.load(
                imageView = binding.topicImage,
                image = item.topic.image,
                fallback = topicFallback(position),
            )
            binding.root.setDebouncedClickListener { onTopicClick(item.topic.id) }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<OnboardingTopicItem>() {
            override fun areItemsTheSame(
                oldItem: OnboardingTopicItem,
                newItem: OnboardingTopicItem,
            ): Boolean = oldItem.topic.id == newItem.topic.id

            override fun areContentsTheSame(
                oldItem: OnboardingTopicItem,
                newItem: OnboardingTopicItem,
            ): Boolean = oldItem == newItem
        }

        fun topicFallback(position: Int): Int = when (position % 4) {
            0 -> com.a02.draw.feature.home.R.drawable.topic_chibi
            1 -> com.a02.draw.feature.home.R.drawable.topic_pixel
            2 -> com.a02.draw.feature.home.R.drawable.topic_anime
            else -> com.a02.draw.feature.home.R.drawable.topic_cartoon
        }
    }
}
