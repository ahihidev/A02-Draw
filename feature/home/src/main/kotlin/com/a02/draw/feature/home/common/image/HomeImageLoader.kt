package com.a02.draw.feature.home.common.image

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.a02.draw.core.ui.image.SharedImageBitmapStore
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.feature.home.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.WeakHashMap

class HomeImageLoader {
    private var scope: CoroutineScope? = newScope()
    private val loadedKeys = WeakHashMap<ImageView, String>()

    fun load(
        imageView: ImageView,
        image: ContentImage,
        @DrawableRes fallback: Int,
        retainDrawableWhileLoading: Boolean = false,
    ) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        val localResource = image.localKey?.let(::localAssetDrawable)
        val remoteUrl = image.url?.takeIf { it.startsWith("https://") }
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

            else -> activeScope().launch {
                if (!retainDrawableWhileLoading && previousRequestKey != requestKey) {
                    imageView.setImageDrawable(null)
                }
                val bitmap = SharedImageBitmapStore.loadRemote(remoteUrl)
                if (imageView.tag == remoteUrl) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        loadedKeys[imageView] = remoteUrl
                    } else {
                        imageView.tag = fallback.toString()
                        loadedKeys[imageView] = fallback.toString()
                        imageView.setImageResource(fallback)
                    }
                }
            }
        }
    }

    fun loadUri(
        imageView: ImageView,
        uri: String?,
        @DrawableRes fallback: Int,
    ) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        if (uri.isNullOrBlank()) {
            imageView.tag = fallback.toString()
            imageView.setImageResource(fallback)
            loadedKeys[imageView] = fallback.toString()
            return
        }
        val previousRequestKey = imageView.tag
        if (previousRequestKey != uri) loadedKeys.remove(imageView)
        if (loadedKeys[imageView] == uri && imageView.drawable != null) return
        imageView.tag = uri
        val contentResolver = imageView.context.contentResolver
        SharedImageBitmapStore.getContentUri(uri)?.let {
            imageView.setImageBitmap(it)
            loadedKeys[imageView] = uri
            return
        }
        activeScope().launch {
            if (previousRequestKey != uri) imageView.setImageDrawable(null)
            val bitmap = SharedImageBitmapStore.loadContentUri(contentResolver, uri)
            if (imageView.tag == uri) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    loadedKeys[imageView] = uri
                } else {
                    imageView.tag = fallback.toString()
                    loadedKeys[imageView] = fallback.toString()
                    imageView.setImageResource(fallback)
                }
            }
        }
    }

    fun retain(imageView: ImageView, image: ContentImage) {
        val remoteUrl = image.url?.takeIf { it.startsWith("https://") } ?: return
        if (loadedKeys[imageView] != remoteUrl) return
        val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap ?: return
        SharedImageBitmapStore.putRemote(remoteUrl, bitmap)
    }

    fun retainUri(imageView: ImageView, uri: String?) {
        val value = uri?.takeIf(String::isNotBlank) ?: return
        if (loadedKeys[imageView] != value) return
        val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap ?: return
        SharedImageBitmapStore.putContentUri(value, bitmap)
    }

    fun close() {
        scope?.cancel()
        scope = null
    }

    private fun activeScope(): CoroutineScope = scope?.takeIf { it.isActive }
        ?: newScope().also { scope = it }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @DrawableRes
    private fun localAssetDrawable(key: String): Int? = when (key) {
        "topic_chibi" -> R.drawable.figma_topic_chibi
        "topic_pixel" -> R.drawable.figma_topic_pixel
        "topic_anime" -> R.drawable.figma_topic_anime
        "topic_cartoon" -> R.drawable.figma_topic_cartoon
        "topic_world_cup" -> R.drawable.figma_topic_world_cup
        "topic_bricks", "topic_lego" -> R.drawable.figma_topic_lego
        "topic_animal" -> R.drawable.topic_animal
        "topic_flower" -> R.drawable.topic_flower
        "topic_kids" -> R.drawable.topic_kids
        else -> null
    }
}
