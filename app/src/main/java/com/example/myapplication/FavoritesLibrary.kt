package com.example.myapplication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 收藏页面：展示所有收藏列表；点击某个列表后展示该列表内的歌曲。
 */
@Composable
fun FavoritesLibrary(controller: PlaybackController) {
    val context = LocalContext.current
    val manager = remember { FavoriteManager(context) }
    val repo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()
    val nowRepo = remember { NowPlayingRepository(context) }

    // 初始化 now-playing（从数据库恢复）
    LaunchedEffect(Unit) {
        val savedNow = nowRepo.loadNowPlaying()
        if (savedNow.isNotEmpty()) {
            controller.setPlaylist(savedNow)
        }
    }

    var listNames by remember { mutableStateOf(manager.getAllLists().toList()) }
    var selectedList by remember { mutableStateOf<String?>(null) }
    var allTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var listTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }

    LaunchedEffect(Unit) {
        // 预加载数据库中所有曲目，用于按 id 过滤
        allTracks = repo.loadPlaylist()
    }

    LaunchedEffect(selectedList, listNames, allTracks) {
        selectedList?.let { name ->
            val ids = manager.getListTrackIds(name)
            listTracks = allTracks.filter { it.id in ids }
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedList != null) {
                IconButton(onClick = { selectedList = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = selectedList ?: "收藏列表", style = MaterialTheme.typography.titleMedium)
                val subtitle = if (selectedList == null) "${listNames.size} 个列表" else "${listTracks.size} 首"
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectedList != null) {
                androidx.compose.material3.Button(onClick = {
                    controller.addToPlaylist(listTracks)
                    scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                }, enabled = listTracks.isNotEmpty()) {
                    androidx.compose.material3.Text(text = "添加到播放列表（全部）")
                }
            }
        }

        if (selectedList == null) {
            if (listNames.isEmpty()) {
                Text(text = "暂无收藏列表", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(listNames) { name ->
                        val count = manager.getListTrackIds(name).size
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 打开新的 Activity 展示该收藏列表的歌曲
                                    val intent = android.content.Intent(context, FavoriteTracksActivity::class.java)
                                    intent.putExtra("favorite_list_name", name)
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "$count 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Divider()
                    }
                }
            }
        } else {
            if (listTracks.isEmpty()) {
                Text(text = "该列表暂无歌曲或数据库未同步", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(listTracks) { t ->
                        val inNow = remember(controller.playlistVersion, t.id) { controller.getPlaylist().any { it.id == t.id } }
                        TrackListItem(
                            track = t,
                            onClick = {
                                controller.play(t)
                                scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                            },
                            onAddToPlaylist = { at ->
                                controller.addToPlaylist(at)
                                scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                            },
                            isInNowPlaying = inNow,
                            onRemoveFromPlaylist = { at ->
                                controller.removeFromPlaylist(at)
                                scope.launch { nowRepo.saveNowPlaying(controller.getPlaylist()) }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}