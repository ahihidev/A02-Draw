package com.a02.draw.onboarding.common.image

import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.a02.draw.core.ui.image.SharedImageBitmapStore
import com.a02.draw.domain.model.ContentImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.WeakHashMap

class OnboardingImageLoader {
    private var scope: CoroutineScope? = newScope()
    private val loadedKeys = WeakHashMap<ImageView, String>()

    fun load(
        imageView: ImageView,
        image: ContentImage?,
        @DrawableRes fallback: Int,
    ) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        val localResource = image?.localKey?.let(::localAssetDrawable)
        val remoteUrl = image?.url?.takeIf { it.startsWith("https://") }
        val cachedRemote = remoteUrl?.let(SharedImageBitmapStore::getRemote)
        val requestKey = remoteUrl ?: localResource?.toString() ?: fallback.toString()
        val previousRequestKey = imageView.tag
        if (previousRequestKey != requestKey) loadedKeys.remove(imageView)
        if (loadedKeys[imageView] == requestKey && imageView.drawable != null) return
        imageView.tag = requestKey

        when {
            localResource != null -> {
                imageView.setImageResource(localResource)
                loadedKeys[imageView] = requestKey
            }

            remoteUrl == null -> {
                imageView.setImageResource(fallback)
                loadedKeys[imageView] = requestKey
            }

            cachedRemote != null -> {
                imageView.setImageBitmap(cachedRemote)
                loadedKeys[imageView] = requestKey
            }

            else -> loadRemote(imageView, remoteUrl, fallback, previousRequestKey)
        }
    }

    fun close() {
        scope?.cancel()
        scope = null
    }

    private fun loadRemote(
        imageView: ImageView,
        url: String,
        @DrawableRes fallback: Int,
        previousRequestKey: Any?,
    ): Job = activeScope().launch {
        if (previousRequestKey != url) imageView.setImageDrawable(null)
        val bitmap = SharedImageBitmapStore.loadRemote(url)
        if (imageView.tag == url) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                loadedKeys[imageView] = url
            } else {
                imageView.tag = fallback.toString()
                loadedKeys[imageView] = fallback.toString()
                imageView.setImageResource(fallback)
            }
        }
    }

    private fun activeScope(): CoroutineScope = scope?.takeIf { it.isActive }
        ?: newScope().also { scope = it }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @DrawableRes
    private fun localAssetDrawable(key: String): Int? = when (key) {
        "topic_chibi" -> com.a02.draw.feature.home.R.drawable.figma_topic_chibi
        "topic_pixel" -> com.a02.draw.feature.home.R.drawable.figma_topic_pixel
        "topic_anime" -> com.a02.draw.feature.home.R.drawable.figma_topic_anime
        "topic_cartoon" -> com.a02.draw.feature.home.R.drawable.figma_topic_cartoon
        "topic_world_cup" -> com.a02.draw.feature.home.R.drawable.figma_topic_world_cup
        "topic_bricks", "topic_lego" -> com.a02.draw.feature.home.R.drawable.figma_topic_lego
        "topic_animal" -> com.a02.draw.feature.home.R.drawable.topic_animal
        "topic_flower" -> com.a02.draw.feature.home.R.drawable.topic_flower
        "topic_kids" -> com.a02.draw.feature.home.R.drawable.topic_kids
        else -> null
    }
}
