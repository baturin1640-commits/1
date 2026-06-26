package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        Text("${files.size} файлов в медиатеке", color = AutoMuted)
        Spacer(Modifier.height(22.dp))

        when {
            loading -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AutoPurple)
            }

            files.isEmpty() -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyMessage, color = AutoMuted, fontSize = 17.sp)
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(files, key = MediaFile::uriString) { file ->
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
            .height(190.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(AutoSurfaceHigh)
            .clickable(onClick = onClick),
    ) {
        VideoThumbnail(
            file = file,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            showPlayButton = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
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
