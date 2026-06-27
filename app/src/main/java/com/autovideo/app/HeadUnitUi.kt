package com.autovideo.app

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Modifier.headUnitPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    sound: UiSound = UiSound.BUTTON,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val soundPlayer = LocalUiSoundPlayer.current
    var lastClickMs by remember { mutableLongStateOf(0L) }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) 90 else 180,
            easing = FastOutSlowInEasing,
        ),
        label = "headUnitPressScale",
    )

    return this
        .scale(scale)
        .clip(shape)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickMs >= 180L) {
                    lastClickMs = now
                    soundPlayer?.play(sound)
                    onClick()
                }
            },
        )
}

@Composable
fun HeadUnitIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    iconSize: Dp = 48.dp,
    backgroundColor: Color = AutoSurfaceHigh,
    tint: Color = Color.White,
    enabled: Boolean = true,
    sound: UiSound = UiSound.BUTTON,
) {
    Box(
        modifier = modifier
            .size(size)
            .headUnitPressable(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(26.dp),
                sound = sound,
            )
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun HeadUnitActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AutoPurple,
    sound: UiSound = UiSound.BUTTON,
) {
    Row(
        modifier = modifier
            .headUnitPressable(
                onClick = onClick,
                shape = RoundedCornerShape(24.dp),
                sound = sound,
            )
            .background(backgroundColor)
            .padding(horizontal = 30.dp, vertical = 21.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.width(15.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
