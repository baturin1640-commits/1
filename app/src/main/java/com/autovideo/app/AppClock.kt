package com.autovideo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AppClock(modifier: Modifier = Modifier, compact: Boolean = false) {
    var time by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            time = formatClock()
        }
    }

    Row(
        modifier = modifier
            .background(Color(0xCC111018), RoundedCornerShape(18.dp))
            .padding(
                horizontal = if (compact) 12.dp else 15.dp,
                vertical = if (compact) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = AutoMuted,
            modifier = Modifier.padding(end = 7.dp),
        )
        Text(
            text = time,
            color = AutoText,
            fontSize = if (compact) 17.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
