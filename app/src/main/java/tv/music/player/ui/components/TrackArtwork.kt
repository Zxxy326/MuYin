package tv.music.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.theme.rememberCoverArt

@Composable
fun TrackArtwork(
    track: DemoTrack,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    circular: Boolean = false,
) {
    val cover = rememberCoverArt(track.artworkUrl)
    Image(
        bitmap = cover.image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(if (circular) CircleShape else RoundedCornerShape(corner)),
    )
}