package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun MediaLibraryScreen(
    title: String,
    files: List<MediaFile>,
    loading: Boolean,
    emptyMessage: String,
    onPlay: (MediaFile) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("${files.size} файлов на подключённых носителях", color = AutoMuted)
        Spacer(Modifier.height(22.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple)
            }

            files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = AutoMuted, fontSize = 17.sp)
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(files, key = { it.uriString }) { file ->
                    MediaFileCard(file = file, onClick = { onPlay(file) })
                }
            }
        }
    }
}

@Composable
fun MediaFileCard(file: MediaFile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(166.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(AutoSurfaceHigh)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    if (file.isVideo) {
                        Brush.linearGradient(listOf(Color(0xFF32185F), Color(0xFF0D2135)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF4B173C), Color(0xFF161126)))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (file.isVideo) Icons.Rounded.VideoFile else Icons.Rounded.Audiotrack,
                contentDescription = null,
                tint = if (file.isVideo) AutoPurple else AutoPink,
                modifier = Modifier.size(38.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC05040A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Воспроизвести", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${file.folderName} · ${formatSize(file.sizeBytes)}",
                    color = AutoMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.1f ГБ", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.0f МБ", mb)
    }
}
