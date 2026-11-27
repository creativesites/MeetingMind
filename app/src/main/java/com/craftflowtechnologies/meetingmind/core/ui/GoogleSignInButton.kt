package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Google's own button colours (Google Identity branding guidelines, "Sign in with Google"): the light button is
 * white with a #747775 outline and #1F1F1F text, the dark one #131314 with a #8E918F outline and #E3E3E3 text.
 * These are brand colours, not theme colours: they must not follow the app accent, so they live here (counted in
 * color-literal-baseline.txt) and the four-colour G lives in res/drawable/ic_google_g.xml.
 */
private object GoogleBrand {
    val LightFill = Color(0xFFFFFFFF)
    val LightStroke = Color(0xFF747775)
    val LightText = Color(0xFF1F1F1F)
    val DarkFill = Color(0xFF131314)
    val DarkStroke = Color(0xFF8E918F)
    val DarkText = Color(0xFFE3E3E3)
}

/**
 * The branded "Sign in with Google" button: the multicolour G on a white pill (light theme) or a #131314 pill (dark),
 * Roboto Medium label, 12 dp start and end padding, 10 dp between the mark and the label, 40 dp tall inside a 48 dp
 * touch target. [busy] keeps the shape and swaps the label for "Opening Google…" with a small spinner.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Sign in with Google",
    busy: Boolean = false,
    enabled: Boolean = true
) {
    val dark = MM.colors.isDark
    val fill = if (dark) GoogleBrand.DarkFill else GoogleBrand.LightFill
    val stroke = if (dark) GoogleBrand.DarkStroke else GoogleBrand.LightStroke
    val ink = if (dark) GoogleBrand.DarkText else GoogleBrand.LightText
    val label = if (busy) "Opening Google…" else text
    Box(modifier.heightIn(min = MMSize.minTouch), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled && !busy,
            shape = MM.radius.pill,
            color = fill,
            border = BorderStroke(MMSize.hairline, stroke),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
        ) {
            Row(
                Modifier.padding(PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (busy) {
                    // A full 18 dp ring: a faint track in the label colour with the moving arc on top, so the busy
                    // state reads at a glance. Reduced motion shows the ring still, three-quarters drawn.
                    if (MM.motion.reduced) {
                        CircularProgressIndicator(
                            progress = { 0.75f }, color = ink, trackColor = ink.copy(alpha = 0.24f),
                            strokeWidth = 2.dp, strokeCap = StrokeCap.Round, modifier = Modifier.size(18.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            color = ink, trackColor = ink.copy(alpha = 0.24f),
                            strokeWidth = 2.dp, strokeCap = StrokeCap.Round, modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MM.type.body.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
