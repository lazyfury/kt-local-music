package com.example.myapplication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TrackListItem(
    track: AudioTrack,
    onClick: () -> Unit,
    onAddToPlaylist: ((AudioTrack) -> Unit)? = null,
    isInNowPlaying: Boolean = false,
    onRemoveFromPlaylist: ((AudioTrack) -> Unit)? = null,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val favManager = remember { FavoriteManager(context) }
    var showFavDialog by remember { mutableStateOf(false) }
    var isFav by remember(track.id) { mutableStateOf(favManager.isTrackInAnyList(track.id)) }
    // 播放列表指示与移除/添加操作由外部传入
    

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "正在播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} · ${track.album} · ${formatTime((track.durationMs / 1000).toInt())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isInNowPlaying) {
            Row(verticalAlignment = Alignment.CenterVertically){
                Text(
                    text = "已在播放列表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                if (onRemoveFromPlaylist != null) {
                    TextButton(onClick = { onRemoveFromPlaylist(track) }) {
                        Text(text = "移除")
                    }
                }
            }
            
        } else if (onAddToPlaylist != null) {
            TextButton(onClick = { onAddToPlaylist(track) }) {
                Text(text = "添加到播放列表")
            }
        }

        IconButton(onClick = { showFavDialog = true }) {
            Icon(
                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFav) "已收藏" else "收藏",
                tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showFavDialog) {
        FavoriteSelectorDialog(
            trackId = track.id,
            onDismiss = { showFavDialog = false },
            onApplied = {
                // 只要加入任意列表则视为已收藏
                isFav = favManager.isTrackInAnyList(track.id)
            }
        )
    }
}