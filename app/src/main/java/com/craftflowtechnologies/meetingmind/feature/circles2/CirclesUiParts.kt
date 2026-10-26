package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.ui.theme.CirclesFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlin.math.abs

/**
 * Color tokens from the MeetingMind Circles Redesign specification (`MeetingMind Circles Redesign.html`).
 * Works harmoniously alongside [MM.colors].
 */
object CirclesPalette {
    val White = CirclesFixedColors.White
    val Accent = CirclesFixedColors.Accent
    val AccentSoft = CirclesFixedColors.AccentSoft
    val AccentDeep = CirclesFixedColors.AccentDeep
    val CoverStart = CirclesFixedColors.CoverStart
    val CoverEnd = CirclesFixedColors.CoverEnd
    val WarmBg = CirclesFixedColors.WarmBg
    val WarmBorder = CirclesFixedColors.WarmBorder
    val WarmInk = CirclesFixedColors.WarmInk
    val GreenBg = CirclesFixedColors.GreenBg
    val GreenInk = CirclesFixedColors.GreenInk
    val OnlineDot = CirclesFixedColors.OnlineDot
    val RoseBg = CirclesFixedColors.RoseBg
    val RoseInk = CirclesFixedColors.RoseInk

    val AvatarPairs: List<Pair<Color, Color>> = CirclesFixedColors.AvatarPairs

    fun avatarColors(seed: String): Pair<Color, Color> {
        if (seed.isBlank()) return AvatarPairs.last()
        val idx = abs(seed.trim().lowercase().hashCode()) % AvatarPairs.size
        return AvatarPairs[idx]
    }
}

/** A text field in tokens: sunk fill, small radius, accent line when focused. */
@Composable
fun CircleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) 8 else 1,
    singleLine: Boolean = maxLines == 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val c = MM.colors
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
        label = { Text(label, style = MM.type.caption) },
        placeholder = placeholder?.let { { Text(it, style = MM.type.body, color = c.inkMuted) } },
        leadingIcon = leadingIcon,
        textStyle = MM.type.body.copy(color = c.ink), shape = RoundedCornerShape(14.dp),
        minLines = minLines, maxLines = maxLines, singleLine = singleLine, keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surfaceSunk, unfocusedContainerColor = c.surfaceSunk, disabledContainerColor = c.surfaceSunk,
            focusedIndicatorColor = CirclesPalette.Accent, unfocusedIndicatorColor = c.line,
            focusedLabelColor = CirclesPalette.Accent, unfocusedLabelColor = c.inkSecondary, cursorColor = CirclesPalette.Accent,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink
        )
    )
}

/** Sheet surface (DESIGN_SYSTEM section 7): raised fill, top card radius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m), content = content
        )
    }
}

/** Initials in a deterministically-colored pastel circle, with optional online status dot. */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = MMSize.minTouch,
    online: Boolean = false
) {
    val initials = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    val (bg, fg) = CirclesPalette.avatarColors(name)
    Box(modifier.size(size).clearAndSetSemantics { }) {
        Box(
            Modifier.matchParentSize().background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = if (size <= 32.dp) 11.sp else 13.sp),
                color = fg
            )
        }
        if (online) {
            Box(
                Modifier
                    .size(11.dp)
                    .align(Alignment.BottomEnd)
                    .background(CirclesPalette.OnlineDot, CircleShape)
                    .border(2.dp, MM.colors.surface, CircleShape)
            )
        }
    }
}

/** Role pill badge from the Members redesign: dark pill for Owner, soft violet pill for Admin/Moderator, subtle pill for Member. */
@Composable
fun RoleTag(role: Role, modifier: Modifier = Modifier) {
    val (bg, fg) = when (role) {
        Role.Owner -> MM.colors.ink to MM.colors.surface
        Role.Admin, Role.Moderator -> CirclesPalette.AccentSoft to CirclesPalette.AccentDeep
        Role.Member -> MM.colors.surfaceSunk to MM.colors.inkSecondary
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg, modifier = modifier) {
        Text(
            role.label,
            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Warm amber title pill badge (e.g., "Pastor", "Youth lead", "Treasurer"). */
@Composable
fun TitleTag(title: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(999.dp), color = CirclesPalette.WarmBg, modifier = modifier) {
        Text(
            title,
            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = CirclesPalette.WarmInk,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Post type pill badge on feed cards ("Prayer", "Testimony", "Free-form", etc.). */
@Composable
fun PostTypeBadge(type: PostType, modifier: Modifier = Modifier) {
    val (bg, fg) = when (type) {
        PostType.Prayer -> CirclesPalette.WarmBg to CirclesPalette.WarmInk
        PostType.Testimony, PostType.Achievement -> CirclesPalette.GreenBg to CirclesPalette.GreenInk
        PostType.Task -> CirclesPalette.AccentSoft to CirclesPalette.AccentDeep
        PostType.Decision -> CirclesPalette.RoseBg to CirclesPalette.RoseInk
        PostType.Brief -> CirclesPalette.WarmBg to CirclesPalette.WarmInk
        PostType.Standup -> CirclesPalette.GreenBg to CirclesPalette.GreenInk
        PostType.Blocker -> CirclesPalette.RoseBg to CirclesPalette.RoseInk
        else -> CirclesPalette.AccentSoft to CirclesPalette.AccentDeep
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg, modifier = modifier) {
        Text(
            type.label,
            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** A quiet label row used above groups of fields. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MM.type.caption, color = MM.colors.inkSecondary, modifier = modifier.padding(top = MM.space.xs))
}

@Composable
fun RowSpaced(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** "just now", "5m", "3h", "2d", then a date. Pure, so tests pass [now]. */
fun relativeTime(now: Long, then: Long): String {
    if (then <= 0L) return ""
    val s = (now - then) / 1000
    return when {
        s < 60 -> "just now"
        s < 3_600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3_600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(then))
    }
}

/**
 * Member details bottom sheet showing avatar, display name, role explanation, title,
 * joined date, message shortcut, and moderation options.
 */
@Composable
fun MemberInfoSheet(
    member: com.craftflowtechnologies.meetingmind.core.circles2.Member,
    isMe: Boolean,
    me: com.craftflowtechnologies.meetingmind.core.circles2.Member?,
    onDismiss: () -> Unit,
    onMessage: (com.craftflowtechnologies.meetingmind.core.circles2.Member) -> Unit,
    onSetTitle: ((com.craftflowtechnologies.meetingmind.core.circles2.Member) -> Unit)? = null,
    onSetRole: ((com.craftflowtechnologies.meetingmind.core.circles2.Member, Role) -> Unit)? = null,
    onRemove: ((com.craftflowtechnologies.meetingmind.core.circles2.Member) -> Unit)? = null
) {
    val isOnline = isMe || member.lastReadAt > 0L || member.role == Role.Owner
    val canChangeRole = me?.role == Role.Owner && !isMe
    val canRemove = !isMe && me?.role?.isAdmin == true && member.role != Role.Owner && (member.role != Role.Admin || me.role == Role.Owner)
    val canEditTitle = me?.role?.isAdmin == true
    val roleDesc = when (member.role) {
        Role.Owner -> "Circle Creator · Has full administrative control over this circle and its leadership."
        Role.Admin -> "Admin · Can invite, approve posts, and manage circle settings."
        Role.Moderator -> "Moderator · Helps keep conversations welcoming, safe, and focused."
        Role.Member -> "Member · Participates in feeds, group chat, and shared moments."
    }

    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            InitialsAvatar(member.displayName, size = 64.dp, online = isOnline)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    if (isMe) "${member.displayName} (You)" else member.displayName,
                    style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    member.title?.takeIf { it.isNotBlank() }?.let { TitleTag(it) }
                    RoleTag(member.role)
                }
            }
        }

        com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard {
            Text(roleDesc, style = MM.type.secondary, color = MM.colors.inkSecondary)
            if (member.joinedAt > 0L) {
                val joined = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(member.joinedAt))
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = MM.space.xs),
                    color = MM.colors.line
                )
                Text("Member since $joined", style = MM.type.caption, color = MM.colors.inkMuted)
            }
        }

        if (!isMe) {
            com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton(
                "Message in chat",
                { onMessage(member) },
                leadingIcon = Icons.Rounded.ChatBubbleOutline,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (canEditTitle || canChangeRole || canRemove) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canEditTitle && onSetTitle != null) {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton(
                        "Set title",
                        { onSetTitle(member) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canRemove && onRemove != null) {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton(
                        "Remove",
                        { onRemove(member) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton(
            "Close",
            onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
