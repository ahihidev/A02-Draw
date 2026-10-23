package com.a02.draw.feature.home.common.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.feature.home.screen.emojimix.EmojiCompositeSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DefaultReferenceImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ReferenceImageStore {
    override suspend fun importHttpsImage(url: String, filePrefix: String): ImportedReference =
        withContext(dispatchers.io) {
            val bytes = downloadImage(url)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "The selected file is not a supported image"
            }
            require(bitmap.width > 0 && bitmap.height > 0) { "The selected image is empty" }
            require(bitmap.width.toLong() * bitmap.height <= MAX_PIXELS) { "The selected image is too large" }
            val constrained = bitmap.constrain(MAX_EDGE)
            try {
                savePng(constrained, filePrefix)
            } finally {
                if (constrained !== bitmap) constrained.recycle()
                bitmap.recycle()
            }
        }

    override suspend fun createEmojiComposite(emojis: List<String>): ImportedReference =
        withContext(dispatchers.default) {
            require(emojis.size == COMPOSITE_EMOJI_COUNT && emojis.distinct().size == emojis.size) {
                "Select three different emoji"
            }
            val bitmap = createBitmap(EmojiCompositeSpec.SIZE, EmojiCompositeSpec.SIZE)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = EMOJI_TEXT_SIZE
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setShadowLayer(24f, 0f, 20f, 0x42000000)
            }
            emojis.zip(EmojiCompositeSpec.centers).forEach { (emoji, center) ->
                val baseline = center.second.toFloat() - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(emoji, center.first.toFloat(), baseline, paint)
            }
            try {
                withContext(dispatchers.io) { savePng(bitmap, "emoji-mix-3") }
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun copy(sourceUri: String, destinationUri: String) =
        withContext(dispatchers.io) {
            val resolver = context.contentResolver
            resolver.openInputStream(sourceUri.toUri()).use { input ->
                requireNotNull(input) { "Cannot read generated image" }
                resolver.openOutputStream(destinationUri.toUri(), "w").use { output ->
                    requireNotNull(output) { "Cannot open destination" }
                    input.copyTo(output)
                }
            }
            Unit
        }

    private fun downloadImage(initialUrl: String): ByteArray {
        var current = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(
                current.protocol.equals(
                    "https",
                    ignoreCase = true
                )
            ) { "Only HTTPS images are allowed" }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "image/*")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) { "Too many redirects" }
                    current = URL(current, requireNotNull(connection.getHeaderField("Location")))
                    return@repeat
                }
                require(code in 200..299) { "Image request failed ($code)" }
                val contentType = connection.contentType.orEmpty().substringBefore(';').lowercase()
                require(contentType.startsWith("image/")) { "The selected URL is not an image" }
                val contentLength = connection.contentLengthLong
                require(contentLength < 0 || contentLength <= MAX_DOWNLOAD_BYTES) { "The image is too large" }
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_DOWNLOAD_BYTES) { "The image is too large" }
                        output.write(buffer, 0, count)
                    }
                }
                return output.toByteArray()
            } finally {
                connection.disconnect()
            }
        }
        error("Image redirect failed")
    }

    private fun savePng(bitmap: Bitmap, prefix: String): ImportedReference {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, Environment.DIRECTORY_PICTURES)
        val directory = File(root, DIRECTORY_NAME)
        check(directory.exists() || directory.mkdirs()) { "Cannot create image directory" }
        val safePrefix = prefix.lowercase().replace(Regex("[^a-z0-9-]"), "-").take(32)
        val file = File(directory, "$safePrefix-${UUID.randomUUID()}.png")
        file.outputStream().buffered().use { output ->
            check(
                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    PNG_QUALITY,
                    output
                )
            ) { "Cannot save image" }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return ImportedReference(uri.toString(), displayName = file.name)
    }

    private fun Bitmap.constrain(maxEdge: Int): Bitmap {
        val currentMax = maxOf(width, height)
        if (currentMax <= maxEdge) return this
        val scale = maxEdge.toFloat() / currentMax
        return scale((width * scale).toInt(), (height * scale).toInt())
    }

    private companion object {
        const val DIRECTORY_NAME = "emoji-mix"
        const val COMPOSITE_EMOJI_COUNT = 3
        const val EMOJI_TEXT_SIZE = 410f
        const val MAX_EDGE = 2048
        const val MAX_PIXELS = 50_000_000L
        const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
        const val MAX_REDIRECTS = 5
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 20_000
        const val PNG_QUALITY = 100
        const val USER_AGENT = "A02Draw/1.0"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
