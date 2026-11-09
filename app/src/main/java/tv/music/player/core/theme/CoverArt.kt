package tv.music.player.core.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.music.player.core.lyrics.EmbeddedTags
import tv.music.player.core.playback.DemoTrack
import java.util.concurrent.TimeUnit

class CoverArt(val image: ImageBitmap, val accent: Color)

/** 封面缺失时由音源 JS pic 动作补全（对齐上游 v1.7.2）。 */
val LocalTrackPicProvider = compositionLocalOf<(suspend (DemoTrack) -> String?)?> { null }

private val coverCache = object : LruCache<String, CoverArt>(16) {}
private val fallbackCoverArt: CoverArt by lazy { buildCoverArt() }
private val coverClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .build()

@Composable
fun rememberCoverArt(url: String? = null): CoverArt {
    val fallback = fallbackCoverArt
    val context = LocalContext.current.applicationContext
    var art by remember(url) { mutableStateOf(url?.let(coverCache::get) ?: fallback) }
    LaunchedEffect(url) {
        val target = url?.trim().orEmpty()
        if (target.isBlank()) {
            art = fallback
            return@LaunchedEffect
        }
        coverCache.get(target)?.let {
            art = it
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { loadCover(context, target) }
        if (loaded != null) {
            coverCache.put(target, loaded)
            art = loaded
        }
    }
    return art
}

@Composable
fun rememberTrackCoverArt(track: DemoTrack): CoverArt {
    val provider = LocalTrackPicProvider.current
    var url by remember(track.id, track.artworkUrl) { mutableStateOf(track.artworkUrl) }
    LaunchedEffect(track.id, track.artworkUrl) {
        if (url.isBlank() && provider != null) {
            val pic = withContext(Dispatchers.IO) { runCatching { provider.invoke(track) }.getOrNull() }
            if (!pic.isNullOrBlank()) url = pic
        }
    }
    return rememberCoverArt(url)
}

private fun loadCover(context: Context, url: String): CoverArt? = runCatching {
    val decoded = if (url.startsWith("content://") || url.startsWith("file://")) {
        decodeLocal(context, url)
    } else {
        decodeHttp(url)
    } ?: return null
    val maxDim = 960
    val bitmap = if (decoded.width > maxDim || decoded.height > maxDim) {
        val ratio = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }
    val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    val palette = Palette.from(scaled).maximumColorCount(16).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
    val accent = normalizeAccent(Color(swatch?.rgb ?: 0xFF35D6C4.toInt()))
    CoverArt(bitmap.asImageBitmap(), accent)
}.getOrNull()

private fun decodeHttp(url: String): Bitmap? {
    for (candidate in coverMirrors(url)) {
        val bitmap = runCatching {
            val request = Request.Builder()
                .url(candidate)
                .header("User-Agent", "TVMusic/1.0")
                .build()
            coverClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                if (bytes.size < 256 || bytes.size > 12 * 1024 * 1024) return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
        if (bitmap != null) return bitmap
    }
    return null
}

private fun coverMirrors(raw: String): List<String> {
    val url = raw.trim()
    if (url.isBlank()) return emptyList()
    val https = if (url.startsWith("http://")) {
        "https://" + url.removePrefix("http://")
    } else {
        url
    }
    val mirrors = linkedSetOf(https)
    if (https.contains("y.gtimg.cn/")) {
        mirrors += https.replace("y.gtimg.cn", "y.qq.com")
    }
    return mirrors.toList()
}

private fun decodeLocal(context: Context, url: String): Bitmap? {
    val uri = Uri.parse(url)
    val embedded = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
    if (embedded != null) return embedded
    val tagged = runCatching {
        EmbeddedTags.read(context, url).picture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }.getOrNull()
    if (tagged != null) return tagged
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return runCatching {
            context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        }.getOrNull()
    }
    return null
}

private fun buildCoverArt(): CoverArt {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        intArrayOf(0xFF24184E.toInt(), 0xFF4B2A7A.toInt(), 0xFF0E5F5C.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

    paint.shader = null
    paint.color = 0x33FFFFFF
    canvas.drawCircle(size * 0.23f, size * 0.22f, size * 0.30f, paint)
    paint.color = 0x22FFB25C
    canvas.drawCircle(size * 0.80f, size * 0.78f, size * 0.28f, paint)

    val cx = size * 0.62f
    val cy = size * 0.58f
    paint.style = Paint.Style.FILL
    paint.color = 0xFF0B0D12.toInt()
    canvas.drawCircle(cx, cy, size * 0.31f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    for (i in 0..5) {
        paint.color = ColorUtils.setAlphaComponent(0xFF35D6C4.toInt(), 150 - i * 20)
        canvas.drawCircle(cx, cy, size * (0.285f - i * 0.045f), paint)
    }
    paint.style = Paint.Style.FILL
    paint.color = 0xFF35D6C4.toInt()
    canvas.drawCircle(cx, cy, size * 0.055f, paint)
    paint.color = 0xFF0B0D12.toInt()
    canvas.drawCircle(cx, cy, size * 0.016f, paint)

    val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    val palette = Palette.from(scaled).maximumColorCount(12).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
    val accent = normalizeAccent(Color(swatch?.rgb ?: 0xFF35D6C4.toInt()))

    return CoverArt(bitmap.asImageBitmap(), accent)
}

private fun normalizeAccent(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}
