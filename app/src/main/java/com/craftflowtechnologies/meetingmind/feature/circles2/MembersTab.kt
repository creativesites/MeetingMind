package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.JoinRequest
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

class MemberActions(
    val onInvite: () -> Unit = {},
    val onSetRole: (Member, Role) -> Unit = { _, _ -> },
    val onRemove: (Member) -> Unit = {},
    /** Opens the title editor for this member. Admins only. */
    val onSetTitle: (Member) -> Unit = {},
    /** Let a waiting person in (true) or turn them away (false). Admins only. */
    val onDecideJoin: (JoinRequest, Boolean) -> Unit = { _, _ -> },
    /** View profile / info for this member. */
    val onMemberClick: (Member) -> Unit = {},
    val onUpdatePhoto: (PickedFile) -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    /** Marks a member as outside the organisation (work circles). Owners and admins only. */
    val onSetExternal: (Member, Boolean) -> Unit = { _, _ -> }
) { companion object { val None = MemberActions() } }

@Composable
fun MembersTab(state: CircleHomeUiState, actions: MemberActions, modifier: Modifier = Modifier) {
    val circle = state.circle ?: return
    val me = state.me
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val f = readPickedAsync(context, uri)
                if (f != null) {
                    actions.onUpdatePhoto(f)
                }
            }
        }
    }
    val canInvite = me != null && (me.role.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    var query by remember { mutableStateOf("") }
    val postsByUid = remember(state.posts) {
        state.posts.mapNotNull { fp -> fp.post.authorUid?.let { it to fp.post } }.groupBy({ it.first }, { it.second })
    }
    val activeContributors = remember(postsByUid) { postsByUid.keys.size }
    val topContributorUid = remember(postsByUid) {
        postsByUid.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }?.key
    }
    val shown = state.members.filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) }
    val leaders = shown.filter { it.role.canModerate }
    val regulars = shown.filter { !it.role.canModerate }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item {
            Surface(
                shape = MM.radius.card,
                color = MM.colors.surfaceSunk,
                border = BorderStroke(MMSize.hairline, MM.colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.m, vertical = MM.space.s),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.members.size}", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                        Text("members", style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                    Box(Modifier.width(MMSize.hairline).height(20.dp).background(MM.colors.line))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$activeContributors", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                        Text("active", style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                    Box(Modifier.width(MMSize.hairline).height(20.dp).background(MM.colors.line))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.posts.size}", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                        Text("shared posts", style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                }
            }
        }
        if (me != null) item {
            val myCount = postsByUid[me.uid]?.size ?: 0
            val isMyTop = me.uid == topContributorUid
            MMCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    MemberAvatar(circle.id, me.uid, me.displayName, me.avatarVersion, size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        Text("Your profile", style = MM.type.caption, color = MM.colors.inkSecondary)
                        Text(
                            "${me.displayName} (You)",
                            style = MM.type.heading,
                            color = MM.colors.ink
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            me.title?.takeIf { it.isNotBlank() }?.let { TitleTag(it) }
                            RoleTag(me.role)
                        }
                        if (myCount > 0 || isMyTop) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                if (myCount > 0) {
                                    Surface(
                                        shape = MM.radius.pill,
                                        color = MM.colors.surfaceSunk,
                                        border = BorderStroke(MMSize.hairline, MM.colors.line)
                                    ) {
                                        Text(
                                            "$myCount ${if (myCount == 1) "post" else "posts"}",
                                            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                            color = MM.colors.inkSecondary,
                                            modifier = Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2)
                                        )
                                    }
                                }
                                if (isMyTop) {
                                    Surface(
                                        shape = MM.radius.pill,
                                        color = MM.colors.accentWash,
                                        border = BorderStroke(MMSize.hairline, MM.colors.accent.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = MM.colors.accent,
                                                modifier = Modifier.size(MMSize.iconSmall - MM.space.s)
                                            )
                                            Text(
                                                "Top contributor",
                                                style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                                color = MM.colors.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.xs),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        "Update photo",
                        { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (me.avatarVersion != null) {
                        TextAction("Remove", actions.onRemovePhoto)
                    }
                }
            }
        }
        if (canInvite) item {
            MMCard {
                Text("Invite people", style = MM.type.heading, color = MM.colors.ink)
                Text("Share a code or a link. Admins can let people in one at a time.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                SecondaryButton("Invite people", actions.onInvite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.PersonAdd)
            }
        }
        item {
            CircleField(
                value = query,
                onValueChange = { query = it.take(60) },
                label = "Search members",
                placeholder = "Search ${state.members.size} members…",
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted) }
            )
        }
        if (state.isAdmin && state.joinRequests.isNotEmpty()) {
            item { SectionHeader("Waiting to join", count = state.joinRequests.size) }
            item {
                MMCard {
                    state.joinRequests.forEachIndexed { idx, r ->
                        if (idx > 0) HorizontalDivider(color = MM.colors.line)
                        JoinRequestRow(r, actions)
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                MMCard {
                    Text("No one matches \"${query.trim()}\".", style = MM.type.secondary, color = MM.colors.inkSecondary)
                }
            }
        } else {
            if (leaders.isNotEmpty()) {
                item { SectionHeader("Owner and leaders", count = leaders.size) }
                items(leaders, key = { it.uid }) { m ->
                    val count = postsByUid[m.uid]?.size ?: 0
                    val isTop = m.uid == topContributorUid
                    MemberCard(circle.id, m, me, actions, count, isTop, isWork = circle.isWork)
                }
            }
            if (regulars.isNotEmpty()) {
                item { SectionHeader("Members", count = regulars.size) }
                items(regulars, key = { it.uid }) { m ->
                    val count = postsByUid[m.uid]?.size ?: 0
                    val isTop = m.uid == topContributorUid
                    MemberCard(circle.id, m, me, actions, count, isTop, isWork = circle.isWork)
                }
            }
        }
    }
}

@Composable
private fun JoinRequestRow(r: JoinRequest, actions: MemberActions) {
    ListRow(
        title = r.displayName,
        subtitle = "Asked to join",
        leading = { InitialsAvatar(r.displayName, size = 42.dp) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                TextAction("Decline", { actions.onDecideJoin(r, false) })
                PrimaryButton("Let in", { actions.onDecideJoin(r, true) })
            }
        }
    )
}

@Composable
fun MemberRow(
    circleId: String,
    m: Member,
    me: Member?,
    actions: MemberActions,
    postCount: Int = 0,
    isTopContributor: Boolean = false,
    isWork: Boolean = false
) {
    MemberCard(circleId, m, me, actions, postCount, isTopContributor, isWork)
}

@Composable
private fun MemberCard(
    circleId: String,
    m: Member,
    me: Member?,
    actions: MemberActions,
    postCount: Int = 0,
    isTopContributor: Boolean = false,
    isWork: Boolean = false
) {
    var menu by remember { mutableStateOf(false) }
    val isMe = m.uid == me?.uid
    val canChangeRole = me?.role == Role.Owner && !isMe
    val canRemove = !isMe && me?.role?.isAdmin == true && m.role != Role.Owner && (m.role != Role.Admin || me.role == Role.Owner)
    val canEditTitle = me?.role?.isAdmin == true
    val joined = remember(m.joinedAt) {
        if (m.joinedAt > 0L) {
            java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(m.joinedAt))
        } else null
    }

    MMCard(onClick = { actions.onMemberClick(m) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            MemberAvatar(circleId, m.uid, m.displayName, m.avatarVersion, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Text(
                        if (isMe) "${m.displayName} (You)" else m.displayName,
                        style = MM.type.bodyStrong,
                        color = MM.colors.ink
                    )
                    m.title?.takeIf { it.isNotBlank() }?.let { TitleTag(it) }
                    if (m.role != Role.Member) RoleTag(m.role)
                    if (isWork && m.external) ExternalTag()
                }
                Text(
                    if (joined != null) "Joined $joined · ${m.role.label}" else m.role.label,
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
                if (postCount > 0 || isTopContributor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        if (postCount > 0) {
                            Surface(
                                shape = MM.radius.pill,
                                color = MM.colors.surfaceSunk,
                                border = BorderStroke(MMSize.hairline, MM.colors.line)
                            ) {
                                Text(
                                    "$postCount ${if (postCount == 1) "post" else "posts"}",
                                    style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                    color = MM.colors.inkSecondary,
                                    modifier = Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2)
                                )
                            }
                        }
                        if (isTopContributor) {
                            Surface(
                                shape = MM.radius.pill,
                                color = MM.colors.accentWash,
                                border = BorderStroke(MMSize.hairline, MM.colors.accent.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs / 2)
                                ) {
                                    Icon(
                                        Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = MM.colors.accent,
                                        modifier = Modifier.size(MMSize.iconSmall - MM.space.s)
                                    )
                                    Text(
                                        "Top contributor",
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                        color = MM.colors.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (canChangeRole || canRemove || canEditTitle) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${m.displayName}", tint = MM.colors.inkSecondary) }
                    DropdownMenu(menu, { menu = false }, containerColor = MM.colors.surfaceRaised) {
                        if (canEditTitle) Item("Set title") { menu = false; actions.onSetTitle(m) }
                        if (canChangeRole && m.role != Role.Admin) Item("Make admin") { menu = false; actions.onSetRole(m, Role.Admin) }
                        if (canChangeRole && m.role != Role.Moderator) Item("Make moderator") { menu = false; actions.onSetRole(m, Role.Moderator) }
                        if (canChangeRole && m.role != Role.Member) Item("Make a regular member") { menu = false; actions.onSetRole(m, Role.Member) }
                        if (canChangeRole) Item("Make owner") { menu = false; actions.onSetRole(m, Role.Owner) }
                        if (canRemove) Item("Remove from circle") { menu = false; actions.onRemove(m) }
                    }
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Small tag on a work-circle member who is outside the organisation. */
@Composable
private fun ExternalTag() {
    Surface(
        shape = MM.radius.pill,
        color = MM.colors.surfaceSunk,
        border = BorderStroke(MMSize.hairline, MM.colors.line)
    ) {
        Text(
            "External",
            style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
            color = MM.colors.inkSecondary,
            modifier = Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs / 2)
        )
    }
}

@Composable
private fun Item(text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text, style = MM.type.body, color = MM.colors.ink) }, onClick = onClick)
