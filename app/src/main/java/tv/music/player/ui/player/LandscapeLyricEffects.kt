package tv.music.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.music.player.core.lyrics.LyricLine
import tv.music.player.core.lyrics.Lyrics
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.SpectrumTap
import tv.music.player.core.theme.CoverArt
import tv.music.player.core.theme.TvFonts
import kotlin.math.abs
import kotlin.math.exp

internal data class EffectPalette(
    val glow: Color,
    val base: Color,
    val wave: Color,
    val waveIn: Color,
    val watermark: Color,
)

internal fun effectPalette(accent: Color): EffectPalette = EffectPalette(
    glow = accent,
    base = lerp(accent, Color(0xFF080B14), 0.72f),
    wave = lerp(accent, Color.White, 0.12f),
    waveIn = lerp(accent, Color.White, 0.46f),
    watermark = accent.copy(alpha = 0.20f),
)

private fun lineAt(lyrics: Lyrics, position: Long): LyricLine? {
    if (lyrics.lines.isEmpty()) return null
    return lyrics.lines.getOrNull(lyrics.indexAt(position).coerceAtLeast(0))
}

private fun lineDuration(lyrics: Lyrics, index: Int): Long {
    val line = lyrics.lines.getOrNull(index) ?: return 1L
    if (line.durationMs > 0L) return line.durationMs
    val next = lyrics.lines.getOrNull(index + 1)?.timeMs
    return if (next != null && next > line.timeMs) {
        (next - line.timeMs).coerceIn(1_200L, 8_000L)
    } else {
        4_000L
    }
}

private fun effectSweep(line: LyricLine?, position: Long, durationMs: Long): Float {
    if (line == null) return 0f
    val total = line.text.length
    if (total <= 0) return 0f
    if (position <= line.timeMs) return 0f
    if (line.words.isEmpty()) {
        return ((position - line.timeMs).toFloat() / durationMs.coerceAtLeast(1L))
            .coerceIn(0f, 1f)
    }
    var charsBefore = 0
    for (word in line.words) {
        val end = word.timeMs + word.durationMs
        if (position < word.timeMs) return charsBefore.toFloat() / total
        if (position <= end) {
            val fraction = if (word.durationMs > 0L) {
                ((position - word.timeMs).toFloat() / word.durationMs).coerceIn(0f, 1f)
            } else 1f
            return (charsBefore + word.text.length * fraction) / total
        }
        charsBefore += word.text.length
    }
    return 1f
}

private fun tokenSweepBrush(
    line: LyricLine?,
    position: Long,
    tokens: List<String>,
    index: Int,
    durationMs: Long,
    sungColor: Color,
    dimColor: Color,
): Brush {
    if (line == null) return SolidColor(sungColor)
    // 没有逐字时间轴（LRC 行级歌词）时按整行时长匀速扫过，保证卡拉OK效果不缺失。
    val total = tokens.sumOf { it.length }.coerceAtLeast(1)
    val progress = effectSweep(line, position, durationMs)
    var before = 0
    for (i in 0 until index) before += tokens[i].length
    val length = tokens[index].length.coerceAtLeast(1)
    val start = before.toFloat() / total
    val end = (before + length).toFloat() / total
    val local = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return when {
        local <= 0f -> SolidColor(dimColor)
        local >= 1f -> SolidColor(sungColor)
        else -> Brush.horizontalGradient(
            0f to sungColor,
            (local - 0.04f).coerceAtLeast(0f) to sungColor,
            (local + 0.08f).coerceAtMost(1f) to dimColor,
            1f to dimColor,
        )
    }
}

private fun isCjk(c: Char): Boolean =
    c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0x3040..0x30FF

private fun tokenizeLyric(text: String): List<String> {
    val out = ArrayList<String>(text.length)
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            out += buffer.toString()
            buffer.clear()
        }
    }
    for (ch in text) {
        when {
            ch == ' ' -> {
                flush()
                out += "\u00A0"
            }
            isCjk(ch) -> {
                flush()
                out += ch.toString()
            }
            else -> buffer.append(ch)
        }
    }
    flush()
    return out
}

private fun watermarkOf(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    val first = trimmed.first()
    return if (first.code < 0x80) {
        trimmed.takeWhile { it.code < 0x80 && !it.isWhitespace() }.ifEmpty { first.toString() }
    } else {
        first.toString()
    }
}

private fun wordPulse(line: LyricLine?, position: Long): Float {
    if (line == null || line.words.isEmpty()) return 0f
    var last = -1L
    for (word in line.words) {
        if (word.timeMs <= position && word.timeMs > last) last = word.timeMs
    }
    if (last < 0L) return 0f
    val dt = (position - last).toFloat()
    return if (dt > 900f) 0f else exp(-dt / 260f)
}

/** 动感涟漪：模糊封面取色 + 向左/右推出的同心光带 + 逐字汇合歌词。 */
@Composable
internal fun WaveLyricsEffect(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    track: DemoTrack,
    coverImage: CoverArt,
) {
    val accent = coverImage.accent
    val palette = remember(accent) { effectPalette(accent) }
    val position = positionMs + offsetMs
    var phase by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var mid by remember { mutableFloatStateOf(0f) }
    var high by remember { mutableFloatStateOf(0f) }
    val latestPosition = rememberUpdatedState(position)
    val latestLyrics = rememberUpdatedState(lyrics)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val pulse = wordPulse(lineAt(latestLyrics.value, latestPosition.value), latestPosition.value)
                bass = SpectrumTap.bass
                mid = SpectrumTap.mid
                high = SpectrumTap.high
                // 0.18 圈/秒是最低频率保底：静音 / 暂停时也保持呼吸，绝不停死；
                // 人声 mid 权重最大，涟漪跟着咬字起伏，bass 只负责加重鼓点。
                val speed = (0.18f + 0.26f * bass + 0.42f * mid + 0.22f * pulse)
                    .coerceAtMost(0.85f)
                phase = (phase + dt * speed) % 1f
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to lerp(accent, Color(0xFF080B14), 0.58f),
                    1f to lerp(accent, Color(0xFF080B14), 0.68f),
                ),
            )
        )
        Canvas(
            Modifier.fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawWaveField(phase, bass, mid, high, palette)
        }
        val line = lineAt(lyrics, position)
        val text = line?.text?.takeIf { it.isNotBlank() } ?: track.title
        WaveWatermark(text, w, h, palette)
        WaveLyricText(lyrics, position, line, text, palette, w, h)
    }
}

@Composable
private fun BoxScope.WaveWatermark(
    text: String,
    w: Float,
    h: Float,
    palette: EffectPalette,
) {
    val mark = remember(text) { watermarkOf(text) }
    if (mark.isEmpty()) return
    val density = LocalDensity.current
    val sizePx = minOf(h * 0.95f, w * 0.34f / mark.length * 1.05f)
    // 模糊半径随字号缩放：固定 9.dp 在笔画密的字（那/繁）上会把字缝糊成一团。
    val blurDp = with(density) { (sizePx * 0.010f).toDp() }.coerceIn(1.dp, 4.dp)
    BasicText(
        text = mark,
        style = TextStyle(
            fontSize = with(density) { sizePx.toSp() },
            fontWeight = FontWeight.Black,
            color = palette.watermark,
            fontFamily = TvFonts.family,
        ),
        modifier = Modifier.align(Alignment.Center).blur(blurDp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WaveLyricText(
    lyrics: Lyrics,
    position: Long,
    line: LyricLine?,
    fallback: String,
    palette: EffectPalette,
    w: Float,
    h: Float,
) {
    val text = line?.text?.takeIf { it.isNotBlank() } ?: fallback
    val tokens = remember(text) { tokenizeLyric(text) }
    val charCount = remember(text) { text.count { !it.isWhitespace() }.coerceAtLeast(1) }
    val density = LocalDensity.current
    val fontSize = remember(charCount, w, h) {
        val px = minOf(h * 0.165f, maxOf(h * 0.052f, w * 0.66f / (charCount * 1.03f))) * 0.67f
        with(density) { px.toSp() }
    }
    val enter = remember { Animatable(1f) }
    LaunchedEffect(text) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(920, easing = LinearEasing))
    }
    val index = lyrics.lines.indexOf(line).coerceAtLeast(0)
    val duration = lineDuration(lyrics, index)
    FlowRow(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.68f),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        val middle = (tokens.size - 1) / 2f
        tokens.forEachIndexed { tokenIndex, token ->
            val side = if (tokenIndex <= middle) -1f else 1f
            val delay = if (middle < 0.5f) 0f else (abs(tokenIndex - middle) / middle) * 0.26f
            BasicText(
                text = token,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.28f,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    brush = tokenSweepBrush(
                        line, position, tokens, tokenIndex, duration,
                        sungColor = Color.White,
                        dimColor = Color.White.copy(alpha = 0.46f),
                    ),
                ),
                modifier = Modifier.graphicsLayer {
                    val p = ((enter.value - delay) / 0.46f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - p) * (1f - p) * (1f - p)
                    translationX = side * (1f - eased) * w * 0.10f
                    alpha = 0.03f + 0.97f * eased
                },
            )
        }
    }
}

private const val RING_SLOTS = 6
private const val RING_BASE = 5

private fun DrawScope.drawWaveField(
    phase: Float,
    bass: Float,
    mid: Float,
    high: Float,
    palette: EffectPalette,
) {
    val w = size.width
    val h = size.height
    val band = w * 0.075f * (1f + 0.60f * mid)
    val maxR = w * 0.40f
    // 亮度保底 0.60：静音段涟漪依旧可见，人声 / 低频一来再增亮。
    val loud = 0.60f + 0.55f * bass + 0.30f * mid
    val extra = ((bass - 0.55f) / 0.35f).coerceIn(0f, 1f)
    val sources = listOf(Offset(-w * 0.02f, h * 0.5f), Offset(w * 1.02f, h * 0.5f))
    for (center in sources) {
        for (i in 0 until RING_SLOTS) {
            val offset = if (i < RING_BASE) i / RING_BASE.toFloat() else 0.1f
            val gate = if (i < RING_BASE) 1f else extra
            if (gate <= 0.01f) continue
            val p = (phase + offset) % 1f
            val radius = maxR * p
            val fadeIn = (p / 0.22f).coerceAtMost(1f)
            val fadeOut = 1f - ((p - 0.62f) / 0.38f).coerceIn(0f, 1f)
            val alpha = 0.78f * loud * gate * fadeIn * fadeIn * fadeOut * fadeOut
            if (alpha <= 0.004f) continue
            val ringVar = (if (i % 2 == 0) 1f else 0.78f) * (0.80f + 0.40f * high)
            val outer = radius + band * 0.5f
            if (outer <= 1f) continue
            val innerEdge = ((radius - band * 0.5f) / outer).coerceIn(0.0001f, 0.98f)
            val peak = (radius / outer).coerceIn(innerEdge + 0.002f, 0.985f)
            val tail = (peak + (1f - peak) * 0.55f).coerceIn(peak + 0.002f, 0.999f)
            val brush = Brush.radialGradient(
                0f to Color.Transparent,
                innerEdge to Color.Transparent,
                (innerEdge + (peak - innerEdge) * 0.55f) to palette.wave.copy(alpha = alpha * 0.42f * ringVar),
                peak to palette.waveIn.copy(alpha = alpha * ringVar),
                tail to palette.wave.copy(alpha = alpha * 0.40f * ringVar),
                1f to Color.Transparent,
                center = center,
                radius = outer,
            )
            drawCircle(brush = brush, radius = outer, center = center)
        }
    }
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0f),
            0.26f to Color.White.copy(alpha = 0.45f),
            0.50f to Color.White,
            0.74f to Color.White.copy(alpha = 0.45f),
            1f to Color.White.copy(alpha = 0f),
        ),
        blendMode = BlendMode.DstIn,
    )
}

/** 简约：模糊封面 + 描边歌名，两组歌词左右错落，逐字点亮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SimpleLyricsEffect(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    track: DemoTrack,
    coverImage: CoverArt,
) {
    val position = positionMs + offsetMs
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        Image(
            bitmap = coverImage.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(16.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.50f),
                    0.40f to Color.Black.copy(alpha = 0.34f),
                    1f to Color.Black.copy(alpha = 0.74f),
                )
            )
        )
        val anchor = lyrics.indexAt(position).coerceAtLeast(0)
        val pairStart = (anchor / 2) * 2
        Column(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = with(density) { (h * 0.14f).toDp() }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedText(
                text = track.title,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = with(density) { (h * 0.155f).toSp() },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = Color.Transparent,
                strokeWidth = 3f,
                strokeColor = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
            Spacer(Modifier.height(with(density) { (h * 0.016f).toDp() }))
            Text(
                text = track.artist.uppercase(),
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = with(density) { (h * 0.019f).toSp() },
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.60f),
                ),
                maxLines = 1,
            )
        }
        val fontSize = with(density) { (h * 0.055f).toSp() }
        val gap = with(density) { (h * 0.042f).toDp() }
        AnimatedContent(
            targetState = pairStart,
            modifier = Modifier.align(Alignment.TopCenter)
                .fillMaxWidth(0.85f)
                .offset(y = with(density) { (h * 0.585f).toDp() }),
            transitionSpec = {
                (fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 5 }) togetherWith
                    (fadeOut(tween(280)) + slideOutVertically(tween(280)) { -it / 5 })
            },
            label = "simple-pair",
        ) { start ->
            Column(Modifier.fillMaxWidth()) {
                SimpleLyricLine(lyrics, start, position, fontSize, alignEnd = false)
                Spacer(Modifier.height(gap))
                SimpleLyricLine(lyrics, start + 1, position, fontSize, alignEnd = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimpleLyricLine(
    lyrics: Lyrics,
    index: Int,
    position: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    alignEnd: Boolean,
) {
    val line = lyrics.lines.getOrNull(index) ?: return
    if (line.text.isBlank()) return
    val tokens = remember(line.text) { tokenizeLyric(line.text) }
    val duration = lineDuration(lyrics, index)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        tokens.forEachIndexed { tokenIndex, token ->
            OutlinedText(
                text = token,
                style = TextStyle(
                    fontFamily = TvFonts.family,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.26f,
                    fontWeight = FontWeight.Bold,
                ),
                brush = tokenSweepBrush(
                    line, position, tokens, tokenIndex, duration,
                    sungColor = Color.White,
                    dimColor = Color.White.copy(alpha = 0.55f),
                ),
                strokeWidth = 5f,
                strokeColor = Color.Black.copy(alpha = 0.90f),
            )
        }
    }
}

@Composable
private fun OutlinedText(
    text: String,
    style: TextStyle,
    color: Color = Color.White,
    brush: Brush? = null,
    strokeWidth: Float,
    strokeColor: Color,
    maxLines: Int = Int.MAX_VALUE,
) {
    Box {
        BasicText(
            text = text,
            style = style.copy(
                color = strokeColor,
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = text,
            style = if (brush != null) style.copy(brush = brush) else style.copy(color = color),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 精简时钟：模糊封面 + 大字时间，只做背景板。 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun ClockLyricsEffect(track: DemoTrack, coverImage: CoverArt) {
    val palette = remember(coverImage.accent) { effectPalette(coverImage.accent) }
    var clock by remember { mutableStateOf(currentClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClockText()
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        Image(
            bitmap = coverImage.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(26.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to palette.glow.copy(alpha = 0.42f),
                    0.45f to palette.base.copy(alpha = 0.34f),
                    1f to Color.Black.copy(alpha = 0.62f),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.34f),
                    0.45f to Color.Black.copy(alpha = 0.18f),
                    1f to Color.Black.copy(alpha = 0.44f),
                )
            )
        )
        BasicText(
            text = clock,
            style = TextStyle(
                fontFamily = TvFonts.family,
                fontSize = with(LocalDensity.current) { (h * 0.26f).toSp() },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        )
    }
}

private fun currentClockText(): String {
    val now = java.util.Calendar.getInstance()
    fun two(value: Int) = if (value < 10) "0$value" else "$value"
    return "${two(now.get(java.util.Calendar.HOUR_OF_DAY))}:${two(now.get(java.util.Calendar.MINUTE))}:${two(now.get(java.util.Calendar.SECOND))}"
}
