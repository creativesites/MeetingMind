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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.ui.text.style.TextOverflow
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
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xl),
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        // The circle at a glance, in words: no stat boxes.
        item(key = "summary") {
            Text(
                listOf(
                    if (state.members.size == 1) "1 member" else "${state.members.size} members",
                    "$activeContributors active",
                    if (state.posts.size == 1) "1 post" else "${state.posts.size} posts"
                ).joinToString(" · "),
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
        }
        if (me != null) item(key = "me") {
            val myCount = postsByUid[me.uid]?.size ?: 0
            MMCard(contentPadding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = MM.space.l, end = MM.space.s, top = MM.space.l, bottom = MM.space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    MemberAvatar(circle.id, me.uid, me.displayName, me.avatarVersion, size = 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(me.displayName, style = MM.type.heading, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            memberLine(me, myCount, me.uid == topContributorUid, null, circle.isWork),
                            style = MM.type.caption, color = MM.colors.inkMuted
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = MM.space.s, end = MM.space.s, bottom = MM.space.xs)) {
                    TextAction(if (me.avatarVersion != null) "Change photo" else "Add a photo", {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    if (me.avatarVersion != null) TextAction("Remove photo", actions.onRemovePhoto)
                }
            }
        }
        if (canInvite) item(key = "invite") {
            MMCard(onClick = actions.onInvite, contentPadding = MM.space.l) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    Box(Modifier.size(40.dp).background(MM.colors.accentWash, MM.radius.pill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.iconSmall))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Invite people", style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text("Share a code or a link", style = MM.type.caption, color = MM.colors.inkMuted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkFaint, modifier = Modifier.size(MMSize.iconSmall))
                }
            }
        }
        if (state.members.size > 6) item(key = "search") {
            CircleField(
                value = query,
                onValueChange = { query = it.take(60) },
                label = "Search members",
                placeholder = "A name",
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted) }
            )
        }
        if (state.isAdmin && state.joinRequests.isNotEmpty()) {
            item(key = "requests") {
                Column {
                    CirclesSectionTitle("Waiting to join · ${state.joinRequests.size}")
                    MMCard(contentPadding = 0.dp) {
                        state.joinRequests.forEachIndexed { idx, r ->
                            if (idx > 0) CirclesRowDivider()
                            Box(Modifier.padding(horizontal = MM.space.s)) { JoinRequestRow(r, actions) }
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text("No one matches \"${query.trim()}\".", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        } else {
            listOf("Leaders" to leaders, "Members" to regulars).forEach { (title, group) ->
                if (group.isNotEmpty()) item(key = "group_$title") {
                    Column {
                        CirclesSectionTitle("$title · ${group.size}")
                        MMCard(contentPadding = 0.dp) {
                            group.forEachIndexed { i, m ->
                                if (i > 0) CirclesRowDivider(startInset = MM.space.l + 40.dp + MM.space.m)
                                MemberCard(circle.id, m, me, actions, postsByUid[m.uid]?.size ?: 0, m.uid == topContributorUid, isWork = circle.isWork)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One quiet line under a member's name: role, title, the External tag in work circles, when they joined, and their
 * contribution (kept while founder decision D3 is paused).
 */
private fun memberLine(m: Member, postCount: Int, isTop: Boolean, joined: String?, isWork: Boolean): String = listOfNotNull(
    m.role.label.takeIf { m.role != Role.Member },
    m.title?.takeIf { it.isNotBlank() },
    "External".takeIf { isWork && m.external },
    joined?.let { "Joined $it" },
    postCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "post" else "posts"}" },
    "Top contributor".takeIf { isTop }
).ifEmpty { listOf(m.role.label) }.joinToString(" · ")

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

    Row(
        Modifier.fillMaxWidth().clickable { actions.onMemberClick(m) }.heightIn(min = 64.dp)
            .padding(start = MM.space.l, end = MM.space.xs, top = MM.space.s, bottom = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        MemberAvatar(circleId, m.uid, m.displayName, m.avatarVersion, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) "${m.displayName} (you)" else m.displayName,
                style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                memberLine(m, postCount, isTopContributor, joined, isWork),
                style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (canChangeRole || canRemove || canEditTitle) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "Actions for ${m.displayName}", tint = MM.colors.inkMuted) }
                DropdownMenu(menu, { menu = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
                    if (canEditTitle) Item("Set title") { menu = false; actions.onSetTitle(m) }
                    if (canChangeRole && m.role != Role.Admin) Item("Make admin") { menu = false; actions.onSetRole(m, Role.Admin) }
                    if (canChangeRole && m.role != Role.Moderator) Item("Make moderator") { menu = false; actions.onSetRole(m, Role.Moderator) }
                    if (canChangeRole && m.role != Role.Member) Item("Make a regular member") { menu = false; actions.onSetRole(m, Role.Member) }
                    if (canChangeRole) Item("Make owner") { menu = false; actions.onSetRole(m, Role.Owner) }
                    if (canRemove) Item("Remove from circle") { menu = false; actions.onRemove(m) }
                }
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkFaint, modifier = Modifier.size(MMSize.iconSmall).padding(end = MM.space.s))
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
