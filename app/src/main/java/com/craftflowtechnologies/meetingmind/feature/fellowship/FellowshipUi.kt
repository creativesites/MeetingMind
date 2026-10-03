package com.craftflowtechnologies.meetingmind.feature.fellowship

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme

/*
 * The Fellowship design kit.
 *
 * This is the ONE file in the fellowship package allowed to name a literal colour: the dark hero
 * gradient and the three kind tints, which follow the same pattern as the Faith screens' hero
 * cards (Pray with me, Record a sermon) and Start tiles. Everything else in the package reads the
 * theme's role tokens — Ink for text, SurfaceBase/SurfaceSunk/Line for surfaces, FaithGold for the
 * accent — so it is legible in light and in dark. FellowshipSourceLintTest enforces that.
 *
 * Note OnInk is the label colour *on an Ink-filled button*; it is never body text.
 */

/** The three things you can send to your group, with the same tints the Faith Start tiles use. */
internal object FellowshipTints {
    val study = Color(0xFF0F766E)
    val prayer = Color(0xFFDB2777)
    val testimony = Color(0xFFEA580C)
}

private val HeroStart = Color(0xFF0B2A2B)
private val HeroEnd = Color(0xFF1F4D45)
private val HeroGlow = Color(0xFFF6D365)

/** Screen header, laid out exactly like the Faith screen's: back, a serif title, a quiet line under it. */
@Composable
internal fun FellowshipTopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** The small caps label that opens a block of the page. */
@Composable
internal fun FellowshipSectionTitle(text: String, modifier: Modifier = Modifier, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = InkMuted,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 10.dp)
    )
}

/** The dark, warm lead card at the top of the hub, built like Faith's "Record a sermon" card. */
@Composable
internal fun FellowshipHero(title: String, body: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(HeroStart.forTheme(), HeroEnd.forTheme())))) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(170.dp).offset(x = 40.dp)
                    .background(Brush.radialGradient(listOf(HeroGlow.copy(alpha = 0.40f), Color.Transparent)), CircleShape)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Color.White)
                    Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.74f), modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(HeroGlow, FaithGold))),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

/**
 * A wide, tinted action card: what it does, in a sentence, and where it goes. The border, wash and
 * icon take the card's own tint, as the Faith Start tiles do.
 */
@Composable
internal fun FellowshipActionCard(title: String, body: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(22.dp), color = SurfaceBase,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.20f)), shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Box(Modifier.background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.10f), SurfaceBase)))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                    Text(body, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A row for one of the person's own notes, as the Faith lists show them. */
@Composable
internal fun FellowshipNoteRow(title: String, meta: String, icon: ImageVector, isPrivate: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SurfaceBase, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(FaithGoldWash), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = FaithGold, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPrivate) {
                        Icon(Icons.Filled.Lock, contentDescription = "Private note", tint = InkMuted, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(meta, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
        }
    }
}

/** The primary pill: Ink fill with an OnInk label, the app's primary-button convention. */
@Composable
internal fun FellowshipPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = if (enabled) Ink else InkFaint, modifier = modifier) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = OnInk, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnInk)
        }
    }
}

/** The quiet secondary pill: a sunk surface with an Ink label. */
@Composable
internal fun FellowshipSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = modifier) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

/** A small selectable chip, gold when chosen. */
@Composable
internal fun FellowshipChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) FaithGoldWash else SurfaceSunk,
        border = BorderStroke(1.dp, if (selected) FaithGold else Line)
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) FaithGoldInk else InkSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** What to show when there is nothing yet — an honest sentence and a next step, never sample content. */
@Composable
internal fun FellowshipEmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(FaithGoldWash), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = FaithGold, modifier = Modifier.size(30.dp))
        }
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (actionLabel != null) FellowshipPrimaryButton(actionLabel, onAction, modifier = Modifier.padding(top = 22.dp))
    }
}

/** A calm, plain-spoken note about privacy or what happens next. */
@Composable
internal fun FellowshipNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Lock) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = InkMuted, modifier = Modifier.padding(top = 2.dp).size(15.dp))
            Text(text, fontSize = 12.5.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** The entry on the Faith screen: one card, in the style of the cards around it. */
@Composable
fun FellowshipEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(22.dp), color = com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised,
        border = BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(FaithGoldWash), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = FaithGold, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Fellowship", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Send a study guide, prayer card or testimony to your group", fontSize = 12.sp, lineHeight = 16.sp, color = InkMuted)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
        }
    }
}

/** Text-field colours that follow the theme: a quiet border, gold-free, readable text. */
@Composable
internal fun fellowshipFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink, unfocusedTextColor = Ink,
    focusedBorderColor = Accent, unfocusedBorderColor = Line,
    focusedContainerColor = SurfaceSunk, unfocusedContainerColor = SurfaceSunk,
    focusedLabelColor = InkSecondary, unfocusedLabelColor = InkMuted,
    focusedPlaceholderColor = InkMuted, unfocusedPlaceholderColor = InkMuted,
    cursorColor = Accent
)
