package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

class MemberActions(
    val onInvite: () -> Unit = {},
    val onSetRole: (Member, Role) -> Unit = { _, _ -> },
    val onRemove: (Member) -> Unit = {},
    /** Opens the title editor for this member. Admins only. */
    val onSetTitle: (Member) -> Unit = {},
    /** Let a waiting person in (true) or turn them away (false). Admins only. */
    val onDecideJoin: (JoinRequest, Boolean) -> Unit = { _, _ -> }
) { companion object { val None = MemberActions() } }

@Composable
fun MembersTab(state: CircleHomeUiState, actions: MemberActions, modifier: Modifier = Modifier) {
    val circle = state.circle ?: return
    val me = state.me
    val canInvite = me != null && (me.role.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    var query by remember { mutableStateOf("") }
    val shown = state.members.filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        item { SectionHeader("Members", count = state.members.size) }
        if (canInvite) item {
            MMCard {
                Text("Invite people", style = MM.type.heading, color = MM.colors.ink)
                Text("Share a code or a link. Admins can let people in one at a time.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                SecondaryButton("Invite people", actions.onInvite, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.PersonAdd)
            }
        }
        item { CircleField(query, { query = it.take(60) }, "Search members") }
        if (state.isAdmin && state.joinRequests.isNotEmpty()) {
            item { SectionHeader("Waiting to join", count = state.joinRequests.size) }
            item {
                MMCard {
                    state.joinRequests.forEach { r -> JoinRequestRow(r, actions) }
                }
            }
        }
        item {
            MMCard {
                if (shown.isEmpty()) Text("No one matches \"${query.trim()}\".", style = MM.type.secondary, color = MM.colors.inkSecondary)
                shown.forEach { m -> MemberRow(m, me, actions) }
            }
        }
    }
}

@Composable
private fun JoinRequestRow(r: JoinRequest, actions: MemberActions) {
    ListRow(
        title = r.displayName,
        subtitle = "Asked to join",
        trailing = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextAction("Decline", { actions.onDecideJoin(r, false) })
                PrimaryButton("Let in", { actions.onDecideJoin(r, true) })
            }
        }
    )
}

@Composable
private fun MemberRow(m: Member, me: Member?, actions: MemberActions) {
    var menu by remember { mutableStateOf(false) }
    val isMe = m.uid == me?.uid
    // Same rules as the Worker: owners manage roles; admins remove plain members; nobody removes the owner.
    val canChangeRole = me?.role == Role.Owner && !isMe
    val canRemove = !isMe && me?.role?.isAdmin == true && m.role != Role.Owner && (m.role != Role.Admin || me.role == Role.Owner)
    // A title is a label such as "Pastor". It sits beside the role, which is what sets permissions.
    val subtitle = listOfNotNull(m.title, m.role.label).joinToString(" · ")
    val canEditTitle = me?.role?.isAdmin == true
    ListRow(
        title = if (isMe) "${m.displayName} (you)" else m.displayName,
        subtitle = subtitle,
        leading = { InitialsAvatar(m.displayName) },
        trailing = if (canChangeRole || canRemove || canEditTitle) { {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${m.displayName}", tint = MM.colors.inkSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = MM.colors.surfaceRaised) {
                    if (canEditTitle) Item("Set title") { menu = false; actions.onSetTitle(m) }
                    if (canChangeRole && m.role == Role.Member) Item("Make admin") { menu = false; actions.onSetRole(m, Role.Admin) }
                    if (canChangeRole && m.role == Role.Admin) Item("Make a regular member") { menu = false; actions.onSetRole(m, Role.Member) }
                    if (canChangeRole) Item("Make owner") { menu = false; actions.onSetRole(m, Role.Owner) }
                    if (canRemove) Item("Remove from circle") { menu = false; actions.onRemove(m) }
                }
            }
        } } else null
    )
}

@Composable
private fun Item(text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text, style = MM.type.body, color = MM.colors.ink) }, onClick = onClick)
