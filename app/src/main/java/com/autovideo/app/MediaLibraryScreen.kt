package com.autovideo.app

import androidx.compose.foundation.background
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
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 22.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, color = AutoText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("${files.size} файлов в медиатеке", color = AutoMuted, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock()
        }
        Spacer(Modifier.height(24.dp))

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
                Text(emptyMessage, color = AutoMuted, fontSize = 19.sp)
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
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
            .height(232.dp)
            .headUnitPressable(onClick = onClick, shape = RoundedCornerShape(22.dp))
            .background(AutoSurfaceHigh),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = AutoText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${file.folderName} · ${formatSize(file.sizeBytes)}",
                    color = AutoMuted,
                    fontSize = 12.sp,
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
