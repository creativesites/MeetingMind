package com.example.core.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** The person's photo, or their initials on the look's gradient. */
@Composable
fun Avatar(identity: AppIdentity, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = AppLook.of(identity.look)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(look.heroTop, look.heroBottom)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val path = identity.avatarPath
        if (path != null && File(path).exists()) {
            AsyncImage(model = File(path), contentDescription = "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(identity.initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
        }
    }
}
