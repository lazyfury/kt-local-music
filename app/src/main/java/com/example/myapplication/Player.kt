package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Player(controller: PlaybackController){
    val track = controller.currentTrack
    val isPlaying = controller.isPlaying
    val progress = controller.progress
    val durationSec = (controller.durationMs / 1000)

    // 收藏相关状态
    val context = androidx.compose.ui.platform.LocalContext.current
    val favManager = remember { FavoriteManager(context) }
    val nowRepo = remember { NowPlayingRepository(context) }
    val scope = rememberCoroutineScope()
    var showFavDialog by remember { mutableStateOf(false) }
    var isFav by remember(track?.id) { mutableStateOf(track?.let { favManager.isTrackInAnyList(it.id) } ?: false) }
    // 播放列表弹层
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var nowPlaylist by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    // 依赖控制器的可组合状态，使用轻量级进度刷新以保持UI平滑
    LaunchedEffect(isPlaying, track) {
        while (controller.isPlaying) {
            controller.updateProgress()
            delay(500)
        }
    }

    Column(
        modifier = androidx.compose.ui.Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 封面占位
        Card(shape = RoundedCornerShape(16.dp)) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Album art",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.size(48.dp)
                )
            }
        }

        // 标题与歌手
        Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            Text(
                text = track?.title ?: "示例歌曲标题",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track?.let { "${it.artist} · ${it.album}" } ?: "演唱者 · 专辑名",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 进度条（拖动时只更新本地值，结束后再发送 seek，避免频繁 seek 导致暂停/归零）
        Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            var sliderPosition by remember(progress) { mutableStateOf(progress) }
            var isDragging by remember { mutableStateOf(false) }

            LaunchedEffect(progress, isDragging) {
                if (!isDragging) sliderPosition = progress
            }

            Slider(
                value = sliderPosition,
                onValueChange = {
                    isDragging = true
                    controller.setUserSeeking(true)
                    sliderPosition = it
                    controller.seekToFraction(sliderPosition)
                },
                onValueChangeFinished = {
                    controller.seekToFraction(sliderPosition)
                    controller.setUserSeeking(false)
                    isDragging = false
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val displayedCurrentSec = (durationSec * (if (isDragging) sliderPosition else progress)).toInt()
                Text(text = formatTime(displayedCurrentSec), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = formatTime(durationSec), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 控制按钮
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.prev() }) {
                Icon(imageVector = Icons.Default.KeyboardArrowLeft, contentDescription = "上一曲")
            }

            IconButton(onClick = { controller.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Menu else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = { controller.next() }) {
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "下一曲")
            }

            IconButton(onClick = { if (track != null) showFavDialog = true }) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFav) "已收藏" else "收藏",
                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = { showPlaylistSheet = true }) {
                Text(text = "播放列表")
            }
        }

        // 播放模式切换（顺序 / 随机）
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "播放模式", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Switch(
                checked = controller.shuffleEnabled,
                onCheckedChange = { enabled ->
                    controller.setShuffleMode(enabled)
                }
            )
        }
    }

    if (showFavDialog && track != null) {
        FavoriteSelectorDialog(
            trackId = track.id,
            onDismiss = { showFavDialog = false },
            onApplied = {
                isFav = favManager.isTrackInAnyList(track.id)
            }
        )
    }

    if (showPlaylistSheet) {
        ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
            Column(modifier = androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "播放列表", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            controller.shufflePlaylist()
                            scope.launch {
                                // 将最新播放列表持久化到“正在播放”并刷新展示
                                nowRepo.saveNowPlaying(controller.getPlaylist())
                                nowPlaylist = nowRepo.loadNowPlaying()
                            }
                        }) { Text(text = "随机排序") }
                        TextButton(onClick = {
                            controller.clearPlaylist()
                            scope.launch {
                                nowRepo.saveNowPlaying(controller.getPlaylist())
                                nowPlaylist = nowRepo.loadNowPlaying()
                            }
                        }) { Text(text = "清空") }
                    }
                }
                LaunchedEffect(showPlaylistSheet) {
                    if (showPlaylistSheet) {
                        // 打开弹层时，用控制器中的播放列表同步到“正在播放”表
                        val current = controller.getPlaylist()
                        scope.launch {
                            if (current.isNotEmpty()) {
                                nowRepo.saveNowPlaying(current)
                            }
                            nowPlaylist = nowRepo.loadNowPlaying()
                        }
                    }
                }
                val playlist = nowPlaylist
                if (playlist.isEmpty()) {
                    Text(text = "播放列表为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(playlist) { t ->
                            val isPlayingItem = remember(controller.currentTrack?.id, t.id) { controller.currentTrack?.id == t.id }
                            TrackListItem(
                                track = t,
                                onClick = { controller.playIfInPlaylist(t);  },
                                isPlaying = isPlayingItem
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

// 待播队列相关 UI 已移除

fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

@Preview
@Composable
fun PlayerPreview(){
    val context = LocalContext.current
    val controller = remember { PlaybackController(context) }
    MyApplicationTheme {
        Player(controller)
    }
}

// 删除占位符UI，统一依赖PlaybackController状态