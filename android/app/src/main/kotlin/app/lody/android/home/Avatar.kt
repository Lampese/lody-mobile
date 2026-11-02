package app.lody.android.home

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Display-only avatar images (user avatar, workspace logo): one HTTPS GET per URL, bounded and
 * downsampled, cached in memory. No credentials, cookies or redirects off HTTPS are involved,
 * and a failure simply leaves the monogram in place.
 */
object AvatarImages {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val TARGET_PX = 128
    private val cache = LruCache<String, ImageBitmap>(48)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = true
            connection.setRequestProperty("Accept", "image/*")
            try {
                // HttpURLConnection only follows same-protocol redirects, so HTTPS stays HTTPS.
                require(connection.responseCode == 200 && connection.url.protocol == "https")
                val bytes = connection.inputStream.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        buffer.write(chunk, 0, read)
                        require(buffer.size() <= MAX_BYTES)
                    }
                    buffer.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
                    ?.also { cache.put(url, it) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/** A real avatar when [url] loads, otherwise the [fallback] initial on a neutral fill. */
@Composable
fun Avatar(url: String?, fallback: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    val colors = LodyTheme.colors
    var image by remember(url) { mutableStateOf(url?.let(AvatarImages::cached)) }
    LaunchedEffect(url) { if (url != null && image == null) image = AvatarImages.load(url) }
    Box(modifier.size(size).clip(shape).background(colors.fillSecondary), contentAlignment = Alignment.Center) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(fallback.take(1).uppercase(), style = LodyTheme.typography.subhead, color = colors.textPrimary)
        }
    }
}

/** Workspaces read as rounded squares (web's workspace avatar); people as circles. */
val WorkspaceAvatarShape: Shape = RoundedCornerShape(LodyRadius.md)
