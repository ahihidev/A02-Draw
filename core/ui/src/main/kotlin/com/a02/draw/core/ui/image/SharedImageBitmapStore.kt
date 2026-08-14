package com.a02.draw.core.ui.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.net.HttpURLConnection
import java.net.URL

object SharedImageBitmapStore {
    private const val NETWORK_TIMEOUT_MILLIS = 8_000
    private const val MAX_BITMAP_DIMENSION = 1_024
    private const val BYTES_PER_KILOBYTE = 1_024
    private const val CACHE_MEMORY_DIVISOR = 8
    private const val MAX_CACHE_SIZE_KILOBYTES = 48 * 1_024
    private val cacheSizeKilobytes = (
            Runtime.getRuntime().maxMemory() / BYTES_PER_KILOBYTE / CACHE_MEMORY_DIVISOR
            ).toInt().coerceAtMost(MAX_CACHE_SIZE_KILOBYTES)
    private val cache = object : LruCache<String, Bitmap>(cacheSizeKilobytes) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / BYTES_PER_KILOBYTE).coerceAtLeast(1)
    }
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private val requests = mutableMapOf<String, Deferred<Bitmap?>>()

    fun getRemote(url: String): Bitmap? = cache.get(remoteKey(url))

    fun getContentUri(uri: String): Bitmap? = cache.get(contentUriKey(uri))

    fun putRemote(url: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(remoteKey(url), bitmap)
    }

    fun putContentUri(uri: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(contentUriKey(uri), bitmap)
    }

    suspend fun loadRemote(url: String): Bitmap? = load(remoteKey(url)) {
        val connection = URL(url).openConnection().apply {
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            useCaches = true
        }
        try {
            connection.getInputStream().use(BitmapFactory::decodeStream)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    suspend fun loadContentUri(contentResolver: ContentResolver, uri: String): Bitmap? =
        load(contentUriKey(uri)) {
            contentResolver.openInputStream(uri.toUri())?.use(BitmapFactory::decodeStream)
        }

    private suspend fun load(key: String, decode: () -> Bitmap?): Bitmap? {
        cache.get(key)?.let { return it }
        val request = synchronized(requestLock) {
            requests[key] ?: requestScope.async {
                runCatching { decode()?.constrained() }.getOrNull()?.also {
                    cache.put(key, it)
                }
            }.also { deferred ->
                requests[key] = deferred
                deferred.invokeOnCompletion {
                    synchronized(requestLock) {
                        if (requests[key] === deferred) requests.remove(key)
                    }
                }
            }
        }
        return request.await()
    }

    private fun Bitmap.constrained(): Bitmap {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= MAX_BITMAP_DIMENSION) return this
        val factor = MAX_BITMAP_DIMENSION.toFloat() / largestDimension
        val resized = scale(
            width = (width * factor).toInt().coerceAtLeast(1),
            height = (height * factor).toInt().coerceAtLeast(1),
        )
        if (resized !== this) recycle()
        return resized
    }

    private fun remoteKey(url: String) = "remote:$url"

    private fun contentUriKey(uri: String) = "content:$uri"
}
