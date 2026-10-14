package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ui.theme.Brand

/**
 * The app's launcher icon, drawn in Compose so it stays sharp at any size: the icon's dark tile
 * ([R.color.ic_launcher_background], the same colour the launcher uses) with the brand mark on it.
 * The launcher's own PNG is only 192px and its adaptive-icon XML can't be loaded as a painter.
 */
@Composable
fun AppIconTile(size: Dp = 112.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.23f)
    Box(
        modifier
            .size(size)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Brand.Indigo, spotColor = Brand.Violet)
            .clip(shape)
            .background(colorResource(R.color.ic_launcher_background))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center
    ) {
        // The mark is 512x441, so keep its proportions.
        Image(
            painterResource(R.drawable.brand_mark),
            contentDescription = "MeetingMind",
            modifier = Modifier.size(width = size * 0.62f, height = size * 0.62f * 441f / 512f)
        )
    }
}
