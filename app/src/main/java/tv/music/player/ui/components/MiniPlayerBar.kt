package tv.music.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.library.LibraryStore
import tv.music.player.core.library.libraryKey
import tv.music.player.core.lyrics.LyricLine
import tv.music.player.core.lyrics.Lyrics
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.lyrics.LyricsState
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvFonts
import tv.music.player.core.theme.TvType

/**
 * 宽版底部播放条，对齐 Daily Music 的横屏控制台：
 * 旋转圆形封面、逐字歌词、上一首 / 播放 / 下一首 / 收藏；不占进度条，看进度去播放页。
 */
@Composable
fun MiniPlayerBar(
    playback: PlaybackController,
    lyricsRepository: LyricsRepository,
    library: LibraryStore,
    offsetMs: Int,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val accent = LocalTvAccent.current
    val group = rememberTvFocusGroup("mini")
    val isFavorite = favorites.any { it.libraryKey() == snapshot.track.libraryKey() }
    var lyricsState by remember(snapshot.track.id) { mutableStateOf<LyricsState?>(null) }

    LaunchedEffect(snapshot.track.id) {
        lyricsState = lyricsRepository.load(snapshot.track)
    }

    val lyrics = (lyricsState as? LyricsState.Ready)?.lyrics
    val plot = lyrics?.let { currentMiniLine(it, snapshot.positionMs, offsetMs) }
    val isLive = snapshot.track.usesPlayer
    val spin = remember { Animatable(0f) }
    LaunchedEffect(snapshot.isPlaying) {
        if (!snapshot.isPlaying) return@LaunchedEffect
        while (true) {
            spin.animateTo(spin.value + 360f, tween(18_000, easing = LinearEasing))
            spin.snapTo(spin.value % 360f)
        }
    }

    val shape = RoundedCornerShape(26.dp)
    val progress = if (snapshot.durationMs > 0L) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else 0f
    TvSurface(
        modifier = modifier.fillMaxWidth().height(80.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape),
        shape = shape,
        baseColor = Color(0xFF10131C).copy(alpha = 0.94f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSurface(
                modifier = Modifier.size(54.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                shape = CircleShape,
                baseColor = Color.Transparent,
                focusedScale = 1.1f,
                glowSize = 10.dp,
                group = group,
                focusKey = "mini:info",
                onClick = onOpenPlayer,
            ) {
                TrackArtwork(
                    track = snapshot.track,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { rotationZ = if (snapshot.isPlaying) spin.value else spin.value % 360f },
                    circular = true,
                )
            }
            Spacer(Modifier.width(12.dp))
            TvSurface(
                modifier = Modifier.width(154.dp),
                shape = RoundedCornerShape(10.dp),
                baseColor = Color.Transparent,
                focusedScale = 1.0f,
                glowSize = 0.dp,
                showBorder = false,
                group = group,
                focusKey = "mini:title",
                onClick = onOpenPlayer,
            ) {
                Column {
                    Text(
                        text = if (isLive) "正在播放" else "待播放",
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                        ),
                        color = if (isLive) accent.copy(alpha = 0.78f) else TvColors.TextTertiary,
                    )
                    Text(
                        text = snapshot.track.title,
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 14.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TvColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snapshot.track.artist,
                        style = TextStyle(
                            fontFamily = TvFonts.family,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        ),
                        color = TvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.width(1.dp).height(34.dp)
                    .background(TvColors.Divider)
            )
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = plot?.text.orEmpty(),
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
                    label = "mini-lyric",
                ) { text ->
                    if (text.isBlank()) {
                        Text(
                            text = when {
                                snapshot.track.isLocal -> "本地歌曲"
                                lyricsState is LyricsState.Loading -> "歌词加载中…"
                                else -> "纯音乐，请欣赏"
                            },
                            style = TvType.body,
                            color = TvColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        BasicText(
                            text = text,
                            style = TextStyle(
                                fontFamily = TvFonts.family,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                brush = if (plot != null) {
                                    miniSweepBrush(
                                        line = plot,
                                        position = snapshot.positionMs + offsetMs,
                                        sung = accent,
                                        dim = TvColors.TextSecondary,
                                    )
                                } else {
                                    SolidColor(TvColors.TextSecondary)
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniControl(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    group = group,
                    focusKey = "mini:prev",
                    onClick = playback::previous,
                )
                MiniControl(
                    icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                    group = group,
                    focusKey = "mini:toggle",
                    primary = true,
                    onClick = playback::toggle,
                )
                MiniControl(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    group = group,
                    focusKey = "mini:next",
                    onClick = playback::next,
                )
                MiniControl(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    group = group,
                    focusKey = "mini:favorite",
                    tint = if (isFavorite) Color(0xFFFF7A8C) else TvColors.TextPrimary,
                    onClick = { library.toggleFavorite(snapshot.track) },
                )
            }
        }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(accent.copy(alpha = 0.9f))
                )
            }
        }
    }
}

@Composable
private fun MiniControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    group: TvFocusGroup,
    focusKey: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    tint: Color = TvColors.TextPrimary,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = modifier.size(if (primary) 46.dp else 38.dp),
        shape = CircleShape,
        baseColor = if (primary) accent.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.06f),
        focusedScale = 1.12f,
        glowSize = 10.dp,
        focusedBorderColor = if (primary) Color.White.copy(alpha = 0.92f) else null,
        focusedGlowColor = if (primary) Color.White.copy(alpha = 0.35f) else null,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvIcon(
                icon,
                Modifier.size(if (primary) 22.dp else 18.dp),
                tint = if (primary) Color(0xFF08090E) else tint,
                contentDescription = contentDescription,
            )
        }
    }
}

private fun currentMiniLine(lyrics: Lyrics, positionMs: Long, offsetMs: Int): LyricLine? {
    if (lyrics.lines.isEmpty()) return null
    val index = lyrics.indexAt(positionMs, offsetMs.toLong()).coerceAtLeast(0)
    return lyrics.lines.getOrNull(index)
}

private fun miniSweepBrush(line: LyricLine, position: Long, sung: Color, dim: Color): Brush {
    val total = line.text.length
    if (total <= 0) return SolidColor(dim)
    val p = when {
        position <= line.timeMs -> 0f
        line.words.isEmpty() -> {
            val duration = line.durationMs.takeIf { it > 0L } ?: 4_000L
            ((position - line.timeMs).toFloat() / duration).coerceIn(0f, 1f)
        }
        else -> {
            var charsBefore = 0
            var result = 1f
            for (word in line.words) {
                val end = word.timeMs + word.durationMs
                if (position < word.timeMs) {
                    result = charsBefore.toFloat() / total
                    break
                }
                if (position <= end) {
                    val fraction = if (word.durationMs > 0L) {
                        ((position - word.timeMs).toFloat() / word.durationMs).coerceIn(0f, 1f)
                    } else 1f
                    result = (charsBefore + word.text.length * fraction) / total
                    break
                }
                charsBefore += word.text.length
            }
            result
        }
    }.coerceIn(0f, 1f)
    val lead = 0.045f
    val a = (p - lead).coerceIn(0f, 1f)
    val b = (p + lead * 0.4f).coerceIn(0f, 1f)
    return Brush.horizontalGradient(0f to sung, a to sung, b to dim, 1f to dim)
}

@Composable
fun MiniPlayerSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.height(20.dp))
}