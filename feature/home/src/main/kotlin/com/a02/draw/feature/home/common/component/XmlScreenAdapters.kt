package com.a02.draw.feature.home.common.component

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.DrawingLesson
import com.a02.draw.domain.model.LessonCategory
import com.a02.draw.domain.model.TrendingSearch
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.motion.pulse
import com.a02.draw.feature.home.databinding.ItemArtworkBinding
import com.a02.draw.feature.home.databinding.ItemLessonCategoryBinding
import com.a02.draw.feature.home.databinding.ItemSettingBinding
import com.a02.draw.feature.home.databinding.ItemTrendingSearchBinding

class TrendingSearchAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<TrendingSearch, TrendingSearchAdapter.Holder>(TrendingDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemTrendingSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTrendingSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TrendingSearch) {
            binding.title.text = item.title
            binding.image.setImageResource(trendingIcon(bindingAdapterPosition))
            binding.root.setDebouncedClickListener { onClick(item.title) }
        }
    }

    private companion object {
        val TrendingDiff = object : DiffUtil.ItemCallback<TrendingSearch>() {
            override fun areItemsTheSame(oldItem: TrendingSearch, newItem: TrendingSearch) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TrendingSearch, newItem: TrendingSearch) =
                oldItem == newItem
        }

        fun trendingIcon(position: Int) = when (position.mod(4)) {
            0 -> R.drawable.figma_topic_lego
            1 -> R.drawable.figma_topic_anime
            2 -> R.drawable.figma_topic_cartoon
            else -> R.drawable.figma_topic_chibi
        }
    }
}

data class ArtworkRow(
    val artwork: Artwork,
    val isFavorite: Boolean,
    val showFavorite: Boolean,
    val showTitle: Boolean = true,
    val isLocked: Boolean = true,
)

class ArtworkAdapter(
    private val imageLoader: HomeImageLoader,
    private val onArtworkClick: (String) -> Unit,
    private val onFavoriteClick: (String) -> Unit,
) : ListAdapter<ArtworkRow, ArtworkAdapter.Holder>(ArtworkDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemArtworkBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemArtworkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ArtworkRow) {
            val item = row.artwork
            binding.vipBadge.visibility = if (row.isLocked) View.VISIBLE else View.GONE
            binding.title.text = item.title
            binding.title.visibility = if (row.showTitle) View.VISIBLE else View.GONE
            binding.favorite.visibility = if (row.showFavorite) View.VISIBLE else View.GONE
            binding.favorite.isSelected = row.isFavorite
            binding.favorite.contentDescription = binding.root.context.getString(
                if (row.isFavorite) R.string.selected else R.string.not_selected,
            )
            imageLoader.load(binding.image, item.image, R.drawable.topic_chibi)
            binding.root.setDebouncedClickListener {
                imageLoader.retain(binding.image, item.image)
                onArtworkClick(item.id)
            }
            binding.favorite.setDebouncedClickListener {
                binding.favorite.pulse(1.16f)
                onFavoriteClick(item.id)
            }
        }
    }

    private companion object {
        val ArtworkDiff = object : DiffUtil.ItemCallback<ArtworkRow>() {
            override fun areItemsTheSame(oldItem: ArtworkRow, newItem: ArtworkRow) =
                oldItem.artwork.id == newItem.artwork.id

            override fun areContentsTheSame(oldItem: ArtworkRow, newItem: ArtworkRow) =
                oldItem == newItem
        }
    }
}

class CategoryAdapter(
    private val imageLoader: HomeImageLoader,
    private val onClick: (String) -> Unit,
) : ListAdapter<LessonCategory, CategoryAdapter.Holder>(CategoryDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLessonCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemLessonCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LessonCategory) {
            binding.vipBadge.visibility = View.GONE
            binding.title.text = item.title
            binding.subtitle.text = item.difficulty
            binding.meta.text =
                binding.root.context.getString(R.string.lesson_count, item.lessonCount)
            imageLoader.load(binding.image, item.image, R.drawable.topic_chibi)
            binding.root.setDebouncedClickListener { onClick(item.id) }
        }
    }

    private companion object {
        val CategoryDiff = object : DiffUtil.ItemCallback<LessonCategory>() {
            override fun areItemsTheSame(oldItem: LessonCategory, newItem: LessonCategory) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LessonCategory, newItem: LessonCategory) =
                oldItem == newItem
        }
    }
}

data class LessonRow(
    val lesson: DrawingLesson,
    val completedSteps: Int,
    val isLocked: Boolean = true,
)

class LessonAdapter(
    private val imageLoader: HomeImageLoader,
    private val onClick: (String) -> Unit,
) : ListAdapter<LessonRow, LessonAdapter.Holder>(LessonDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLessonCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemLessonCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LessonRow) {
            val lesson = row.lesson
            binding.vipBadge.visibility = if (row.isLocked) View.VISIBLE else View.GONE
            binding.title.text = lesson.title
            binding.subtitle.text = lesson.minutes?.let {
                binding.root.context.getString(R.string.lesson_minutes, it)
            } ?: binding.root.context.getString(R.string.lesson_guided_steps, lesson.totalSteps)
            binding.meta.text = binding.root.context.getString(
                R.string.lesson_steps_complete,
                row.completedSteps.coerceIn(0, lesson.totalSteps),
                lesson.totalSteps,
            )
            imageLoader.load(binding.image, lesson.image, R.drawable.topic_chibi)
            binding.root.setDebouncedClickListener {
                imageLoader.retain(binding.image, lesson.image)
                onClick(lesson.id)
            }
        }
    }

    private companion object {
        val LessonDiff = object : DiffUtil.ItemCallback<LessonRow>() {
            override fun areItemsTheSame(oldItem: LessonRow, newItem: LessonRow) =
                oldItem.lesson.id == newItem.lesson.id

            override fun areContentsTheSame(oldItem: LessonRow, newItem: LessonRow) =
                oldItem == newItem
        }
    }
}

class SettingAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<AppSettingItem, SettingAdapter.Holder>(SettingDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSettingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppSettingItem) {
            binding.title.text = item.title
            binding.icon.setImageResource(settingIcon(item.id))
            binding.root.setDebouncedClickListener { onClick(item.id) }
        }
    }

    private companion object {
        val SettingDiff = object : DiffUtil.ItemCallback<AppSettingItem>() {
            override fun areItemsTheSame(oldItem: AppSettingItem, newItem: AppSettingItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AppSettingItem, newItem: AppSettingItem) =
                oldItem == newItem
        }

        fun settingIcon(id: String) = when (id) {
            "subscription" -> R.drawable.icon_setting_premium
            "help" -> R.drawable.icon_setting_help_figma
            "privacy" -> R.drawable.icon_setting_privacy_figma
            "terms" -> R.drawable.icon_setting_terms_figma
            "share" -> R.drawable.icon_setting_share_figma
            "rate" -> R.drawable.icon_setting_rate_figma
            "feedback" -> R.drawable.icon_setting_feedback_figma
            "update" -> R.drawable.icon_setting_update_figma
            else -> R.drawable.icon_setting_help_figma
        }
    }
}

data class ProfileDrawingRow(
    val id: Long,
    val title: String,
    val mediaUri: String?,
    val fallbackImage: ContentImage?,
)

class ProfileDrawingAdapter(
    private val imageLoader: HomeImageLoader,
    private val onClick: (Long) -> Unit,
) : ListAdapter<ProfileDrawingRow, ProfileDrawingAdapter.Holder>(DrawingDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemArtworkBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemArtworkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProfileDrawingRow) {
            binding.title.text = item.title
            binding.favorite.visibility = View.GONE
            when {
                item.mediaUri != null -> imageLoader.loadUri(
                    binding.image,
                    item.mediaUri,
                    R.drawable.complete_art,
                )

                item.fallbackImage != null -> imageLoader.load(
                    binding.image,
                    item.fallbackImage,
                    R.drawable.complete_art,
                )

                else -> binding.image.setImageResource(R.drawable.complete_art)
            }
            binding.root.setDebouncedClickListener {
                when {
                    item.mediaUri != null -> imageLoader.retainUri(binding.image, item.mediaUri)
                    item.fallbackImage != null -> imageLoader.retain(
                        binding.image,
                        item.fallbackImage
                    )
                }
                onClick(item.id)
            }
        }
    }

    private companion object {
        val DrawingDiff = object : DiffUtil.ItemCallback<ProfileDrawingRow>() {
            override fun areItemsTheSame(oldItem: ProfileDrawingRow, newItem: ProfileDrawingRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ProfileDrawingRow,
                newItem: ProfileDrawingRow
            ) =
                oldItem == newItem
        }
    }
}
