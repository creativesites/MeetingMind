package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.ui.theme.MM

class MemberActions(
    val onInvite: () -> Unit = {},
    val onSetRole: (Member, Role) -> Unit = { _, _ -> },
    val onRemove: (Member) -> Unit = {}
) { companion object { val None = MemberActions() } }

@Composable
fun MembersTab(state: CircleHomeUiState, actions: MemberActions, modifier: Modifier = Modifier) {
    val circle = state.circle ?: return
    val me = state.me
    val canInvite = me != null && (me.role.isAdmin || circle.settings.whoCanInvite == WhoCanInvite.Members)
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        item { SectionHeader("Members", count = state.members.size) }
        if (canInvite) item { SecondaryButton("Invite people", actions.onInvite, leadingIcon = Icons.Rounded.PersonAdd) }
        item {
            MMCard {
                state.members.forEachIndexed { i, m ->
                    MemberRow(m, me, actions)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(m: Member, me: Member?, actions: MemberActions) {
    var menu by remember { mutableStateOf(false) }
    val isMe = m.uid == me?.uid
    // Same rules as the Worker: owners manage roles; admins remove plain members; nobody removes the owner.
    val canChangeRole = me?.role == Role.Owner && !isMe
    val canRemove = !isMe && me?.role?.isAdmin == true && m.role != Role.Owner && (m.role != Role.Admin || me.role == Role.Owner)
    ListRow(
        title = if (isMe) "${m.displayName} (you)" else m.displayName,
        subtitle = m.role.label,
        leading = { InitialsAvatar(m.displayName) },
        trailing = if (canChangeRole || canRemove) { {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${m.displayName}", tint = MM.colors.inkSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = MM.colors.surfaceRaised) {
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
