package com.autovideo.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IptvScreen(
    state: StreamingUiState,
    onBack: () -> Unit,
    onImportPlaylist: () -> Unit,
    onLoadRemote: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onGroupChange: (String?) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onOpenChannel: (IptvChannel) -> Unit,
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(AutoBackground, Color(0xFF130A20), Color(0xFF081928))))
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeadUnitIconButton(
                Icons.Rounded.ArrowBack,
                "Назад",
                onBack,
                size = 64.dp,
                iconSize = 34.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("IPTV", color = AutoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Без предустановленных каналов · только ваши M3U", color = AutoMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            AppClock(compact = true)
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeadUnitActionButton(
                text = "Импорт M3U",
                icon = Icons.Rounded.FileOpen,
                onClick = onImportPlaylist,
            )
            OutlinedTextField(
                value = remoteUrl,
                onValueChange = { remoteUrl = it.take(2_048) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("HTTPS-адрес плейлиста") },
                colors = iptvTextFieldColors(),
            )
            HeadUnitActionButton(
                text = "Загрузить",
                icon = Icons.Rounded.Wifi,
                onClick = { onLoadRemote(remoteUrl) },
                backgroundColor = AutoBlue,
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            label = { Text("Поиск каналов") },
            colors = iptvTextFieldColors(),
        )

        if (state.groups.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    GroupButton("Все", state.selectedGroup == null) { onGroupChange(null) }
                }
                items(state.groups, key = { it }) { group ->
                    GroupButton(group, state.selectedGroup == group) { onGroupChange(group) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AutoPurple, modifier = Modifier.size(58.dp))
            }
            state.error != null -> IptvMessage(state.error)
            state.channels.isEmpty() -> IptvMessage("Импортируйте локальный M3U или укажите HTTPS-адрес плейлиста")
            state.visibleChannels.isEmpty() -> IptvMessage("Каналы по выбранному фильтру не найдены")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.visibleChannels, key = IptvChannel::streamUrl) { channel ->
                    ChannelCard(
                        channel = channel,
                        favorite = channel.streamUrl in state.favorites,
                        onToggleFavorite = { onToggleFavorite(channel) },
                        onClick = { onOpenChannel(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .headUnitPressable(onClick, shape = RoundedCornerShape(16.dp))
            .background(if (selected) AutoPurple else AutoSurfaceHigh, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = AutoText, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ChannelCard(
    channel: IptvChannel,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(116.dp)
            .headUnitPressable(onClick, shape = RoundedCornerShape(20.dp))
            .background(AutoSurfaceHigh, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(62.dp).background(AutoPink.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.LiveTv, null, tint = AutoPink, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = AutoText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(channel.group ?: "Без группы", color = AutoMuted, fontSize = 12.sp, maxLines = 1)
        }
        HeadUnitIconButton(
            icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Избранное",
            onClick = onToggleFavorite,
            size = 52.dp,
            iconSize = 28.dp,
            backgroundColor = Color.Transparent,
            tint = if (favorite) AutoPink else AutoMuted,
            sound = UiSound.FAVORITE,
        )
    }
}

@Composable
private fun IptvMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = AutoMuted, fontSize = 18.sp)
    }
}

@Composable
private fun iptvTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AutoText,
    unfocusedTextColor = AutoText,
    focusedBorderColor = AutoPurple,
    unfocusedBorderColor = AutoMuted.copy(alpha = 0.5f),
    focusedLabelColor = AutoPurple,
    unfocusedLabelColor = AutoMuted,
    cursorColor = AutoPink,
)
