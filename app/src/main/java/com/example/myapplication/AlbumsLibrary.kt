package com.example.myapplication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.myapplication.db.AlbumEntry
import kotlinx.coroutines.launch
import android.content.Intent

@Composable
fun AlbumsLibrary(controller: PlaybackController) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<AlbumEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        albums = repo.loadAlbums()
    }

    Column {
        Text(text = "专辑列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                query = q
                scope.launch { albums = repo.searchAlbums(q) }
            },
            label = { Text(text = "搜索专辑（数据库）") },
            placeholder = { Text(text = "输入专辑关键词") },
            singleLine = true,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider()

        if (albums.isEmpty()) {
            Text(
                text = "暂无专辑或数据库未同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)) {
                items(albums) { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(context, AlbumTracksActivity::class.java)
                                intent.putExtra("album", a.album)
                                context.startActivity(intent)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = a.album, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "${a.count} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Divider()
                }
            }
        }
    }
}