package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Dedicated execution and continuity space for Work Circles:
 * - What changed (Standups & updates)
 * - What needs a decision (Pending decisions)
 * - Who owns the next commitment (Action items / Tasks)
 * - The latest meeting brief (Brief card)
 * - Active blockers requiring resolution
 */
@Composable
fun WorkOverviewTab(
    state: CircleHomeUiState,
    now: Long,
    actions: FeedActions,
    modifier: Modifier = Modifier
) {
    val circle = state.circle ?: return
    val c = MM.colors

    val blockers = remember(state.posts) {
        state.posts.filter { it.post.type == PostType.Blocker && !it.post.answered }
    }
    val latestBrief = remember(state.posts) {
        state.posts.firstOrNull { it.post.type == PostType.Brief }
    }
    val decisions = remember(state.posts) {
        state.posts.filter { it.post.type == PostType.Decision }
    }
    val tasks = remember(state.posts) {
        state.posts.filter { it.post.type == PostType.Task }
    }
    val openTasks = remember(tasks) { tasks.filter { !it.post.answered } }
    val completedTasks = remember(tasks) { tasks.filter { it.post.answered } }
    val standups = remember(state.posts) {
        state.posts.filter { it.post.type == PostType.Standup }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        state.failure?.let { f -> item { StatusLine(StatusKind.Warning, f.message) } }

        // Top work hero
        item {
            WorkCircleHero(circle, state, actions)
        }

        // Quick action row for work operations
        item {
            WorkActionRow(circle, actions)
        }

        // 1. Active Blockers (High Priority)
        if (blockers.isNotEmpty()) {
            item {
                SectionHeader("Active Blockers", count = blockers.size)
            }
            items(blockers, key = { "blocker_${it.post.id}" }) { item ->
                BlockerCard(item, circle, state, now, actions)
            }
        }

        // 2. Latest Meeting Brief
        item {
            SectionHeader("Latest Meeting Brief")
        }
        item {
            if (latestBrief != null) {
                BriefCard(latestBrief, circle, state, now, actions)
            } else {
                MMCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = MM.space.s)) {
                        Text("No meeting briefs shared yet", style = MM.type.bodyStrong, color = c.ink)
                        Text(
                            "Meeting briefs shared from meeting wrap-up will appear here to maintain team continuity.",
                            style = MM.type.secondary,
                            color = c.inkSecondary,
                            modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.m)
                        )
                        SecondaryButton("Share a brief", { actions.onStartPost(PostType.Brief) }, leadingIcon = Icons.Rounded.Add)
                    }
                }
            }
        }

        // 3. Open Commitments & Action Items
        item {
            SectionHeader("Commitments & Tasks", count = openTasks.size)
        }
        if (tasks.isEmpty()) {
            item {
                MMCard {
                    Text(
                        "No commitments assigned yet. Tap '+ Action item' to assign the next commitment.",
                        style = MM.type.secondary,
                        color = c.inkSecondary,
                        modifier = Modifier.padding(vertical = MM.space.s)
                    )
                }
            }
        } else {
            if (openTasks.isNotEmpty()) {
                items(openTasks, key = { "task_${it.post.id}" }) { item ->
                    TaskRowCard(item, circle, state, actions)
                }
            }
            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        "${completedTasks.size} completed commitment${if (completedTasks.size == 1) "" else "s"}",
                        style = MM.type.caption,
                        color = c.inkMuted,
                        modifier = Modifier.padding(top = MM.space.xs)
                    )
                }
                items(completedTasks.take(3), key = { "task_done_${it.post.id}" }) { item ->
                    TaskRowCard(item, circle, state, actions)
                }
            }
        }

        // 4. Key Decisions
        if (decisions.isNotEmpty()) {
            item {
                SectionHeader("Recorded Decisions", count = decisions.size)
            }
            items(decisions.take(4), key = { "decision_${it.post.id}" }) { item ->
                DecisionCard(item, circle, state, now, actions)
            }
        }

        // 5. Recent Status & Standup Updates
        if (standups.isNotEmpty()) {
            item {
                SectionHeader("Recent Updates & Standups", count = standups.size)
            }
            items(standups.take(3), key = { "standup_${it.post.id}" }) { item ->
                StandupCard(item, circle, state, now, actions)
            }
        }
    }
}

@Composable
private fun WorkCircleHero(circle: Circle, state: CircleHomeUiState, actions: FeedActions) {
    val canInvite = state.me != null && (state.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CirclesPalette.Accent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(CirclesPalette.CoverStart, CirclesPalette.Accent, CirclesPalette.CoverEnd)
                        )
                    )
            ) {
                if (circle.coverPath != null) {
                    MediaImage(circle.id, circle.coverPath, Modifier.fillMaxSize(), "Cover photo for ${circle.name}")
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    state.members.take(5).forEachIndexed { idx, m ->
                        MemberAvatar(circleId = circle.id, uid = m.uid, name = m.displayName, avatarVersion = m.avatarVersion, size = 32.dp, online = idx < 2)
                    }
                    if (state.members.size > 5) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+${state.members.size - 5}", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.White)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(circle.name, style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = CirclesPalette.White)
                Text(
                    "${circle.groupWord} · ${state.members.size} ${if (state.members.size == 1) "contributor" else "contributors"}",
                    style = MM.type.secondary, color = CirclesPalette.White.copy(alpha = 0.85f)
                )
                if (circle.description.isNotBlank()) {
                    Text(circle.description, style = MM.type.body, color = CirclesPalette.White.copy(alpha = 0.9f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CirclesPalette.White,
                        modifier = Modifier.weight(1f).clickable(onClick = actions.onOpenChat)
                    ) {
                        Row(
                            Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Team Chat",
                                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                                color = CirclesPalette.Accent
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, null, tint = CirclesPalette.Accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (canInvite) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = CirclesPalette.White.copy(alpha = 0.18f),
                            modifier = Modifier.clickable(onClick = actions.onInvite)
                        ) {
                            Row(
                                Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.PersonAdd, null, tint = CirclesPalette.White, modifier = Modifier.size(16.dp))
                                Text("Invite", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkActionRow(circle: Circle, actions: FeedActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        MMChip("+ Action item", selected = false, onClick = { actions.onStartPost(PostType.Task) })
        MMChip("+ Decision", selected = false, onClick = { actions.onStartPost(PostType.Decision) })
        MMChip("+ Meeting brief", selected = false, onClick = { actions.onStartPost(PostType.Brief) })
        MMChip("+ Status update", selected = false, onClick = { actions.onStartPost(PostType.Standup) })
        MMChip("+ Blocker", selected = false, onClick = { actions.onStartPost(PostType.Blocker) })
    }
}

@Composable
private fun BlockerCard(item: FeedPost, circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val who = post.authorName ?: "Team member"
    val authorAvatar = remember(post.authorUid, state.members) {
        state.members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (c.isDark) c.dangerWash else CirclesPalette.RoseBg,
        border = BorderStroke(1.dp, if (c.isDark) c.danger.copy(alpha = 0.4f) else CirclesPalette.RoseInk.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().clickable { actions.onOpenThread(post) }
    ) {
        Column(Modifier.padding(MM.space.m), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorAvatar, size = 32.dp)
                Spacer(Modifier.width(MM.space.s))
                Column(Modifier.weight(1f)) {
                    Text(who, style = MM.type.bodyStrong, color = c.ink)
                    Text(relativeTime(now, post.createdAt), style = MM.type.caption, color = c.inkSecondary)
                }
                PostTypeBadge(PostType.Blocker)
            }
            Text(post.body, style = MM.type.bodyStrong, color = if (c.isDark) c.danger else CirclesPalette.RoseInk)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.clickable { actions.onOpenThread(post) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.inkSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        if (post.counts.comments > 0) "${post.counts.comments} comments" else "Help unblock",
                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.inkSecondary
                    )
                }
                if (item.mine || state.canModerate) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (c.isDark) c.surfaceRaised else CirclesPalette.White,
                        border = BorderStroke(1.dp, c.line),
                        modifier = Modifier.clickable { actions.onMarkAnswered(post) }
                    ) {
                        Text(
                            "Mark resolved",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefCard(item: FeedPost, circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val who = post.authorName ?: "Team member"
    val authorAvatar = remember(post.authorUid, state.members) {
        state.members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    MMCard(onClick = { actions.onOpenThread(post) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorAvatar, size = 36.dp)
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(who, style = MM.type.bodyStrong, color = c.ink)
                Text("Meeting brief · ${relativeTime(now, post.createdAt)}", style = MM.type.caption, color = c.inkSecondary)
            }
            PostTypeBadge(PostType.Brief)
        }
        Text(
            post.body,
            style = MM.type.body,
            color = c.ink,
            maxLines = 5,
            modifier = Modifier.padding(top = MM.space.s)
        )
        HorizontalDivider(Modifier.padding(vertical = MM.space.s), color = c.line)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (post.counts.comments > 0) "${post.counts.comments} notes & replies" else "Open full brief & notes",
                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = CirclesPalette.Accent
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = CirclesPalette.Accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskRowCard(item: FeedPost, circle: Circle, state: CircleHomeUiState, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val who = post.authorName ?: "Member"
    val authorAvatar = remember(post.authorUid, state.members) {
        state.members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    val canToggle = item.mine || state.canModerate

    MMCard(onClick = { actions.onOpenThread(post) }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
            // Checkbox button
            Surface(
                shape = CircleShape,
                color = if (post.answered) CirclesPalette.GreenBg else c.surfaceSunk,
                border = BorderStroke(1.5.dp, if (post.answered) CirclesPalette.GreenInk else c.inkMuted),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = canToggle) { actions.onMarkAnswered(post) }
            ) {
                if (post.answered) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, "Done", tint = CirclesPalette.GreenInk, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    post.body,
                    style = if (post.answered) MM.type.body.copy(color = c.inkMuted) else MM.type.bodyStrong.copy(color = c.ink)
                )
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemberAvatar(circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorAvatar, size = 18.dp)
                    Text(
                        if (item.mine) "You" else who,
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                    if (post.counts.comments > 0) {
                        Text("· ${post.counts.comments} comments", style = MM.type.caption, color = c.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionCard(item: FeedPost, circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val who = post.authorName ?: "Member"
    val authorAvatar = remember(post.authorUid, state.members) {
        state.members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    MMCard(onClick = { actions.onOpenThread(post) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorAvatar, size = 32.dp)
            Spacer(Modifier.width(MM.space.s))
            Column(Modifier.weight(1f)) {
                Text(who, style = MM.type.bodyStrong, color = c.ink)
                Text(relativeTime(now, post.createdAt), style = MM.type.caption, color = c.inkSecondary)
            }
            PostTypeBadge(PostType.Decision)
        }
        Text(post.body, style = MM.type.body, color = c.ink, modifier = Modifier.padding(top = MM.space.s))
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                circle.settings.reactions.forEach { k ->
                    val n = post.counts.reactions[k] ?: 0
                    if (n > 0) {
                        Surface(shape = RoundedCornerShape(999.dp), color = CirclesPalette.AccentSoft) {
                            Text(
                                "${k.emoji} $n",
                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = CirclesPalette.AccentDeep,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Text(
                if (post.counts.comments > 0) "${post.counts.comments} comments" else "Discuss",
                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.inkSecondary,
                modifier = Modifier.clickable { actions.onOpenThread(post) }
            )
        }
    }
}

@Composable
private fun StandupCard(item: FeedPost, circle: Circle, state: CircleHomeUiState, now: Long, actions: FeedActions) {
    val post = item.post
    val c = MM.colors
    val who = post.authorName ?: "Member"
    val authorAvatar = remember(post.authorUid, state.members) {
        state.members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
    }
    MMCard(onClick = { actions.onOpenThread(post) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(circleId = circle.id, uid = post.authorUid, name = who, avatarVersion = authorAvatar, size = 32.dp)
            Spacer(Modifier.width(MM.space.s))
            Column(Modifier.weight(1f)) {
                Text(who, style = MM.type.bodyStrong, color = c.ink)
                Text(relativeTime(now, post.createdAt), style = MM.type.caption, color = c.inkSecondary)
            }
            PostTypeBadge(PostType.Standup)
        }
        Text(post.body, style = MM.type.body, color = c.ink, modifier = Modifier.padding(top = MM.space.s))
    }
}
