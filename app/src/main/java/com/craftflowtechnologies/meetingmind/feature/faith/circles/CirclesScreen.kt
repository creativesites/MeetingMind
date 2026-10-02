package com.craftflowtechnologies.meetingmind.feature.faith.circles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme

@Composable
fun CirclesScreen(
    viewModel: CirclesViewModel,
    onNavigateBack: () -> Unit,
    onOpenCircle: (String) -> Unit
) {
    val circles by viewModel.circles.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SurfaceBase.forTheme(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBase.forTheme())
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnInk.forTheme()
                        )
                    }
                    Text(
                        text = "Fellowship Circles",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OnInk.forTheme(),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = FaithGoldInk.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "E2EE",
                                tint = FaithGoldInk,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "E2EE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FaithGoldInk
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // E2EE & Privacy Trust Banner
            item {
                Surface(
                    color = OnInk.forTheme().copy(alpha = 0.04f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Line.forTheme())
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = FaithGoldInk.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = FaithGoldInk,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private & Sealed Fellowship",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = OnInk.forTheme()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Circles (5–15 members) share prayer requests, praise testimonies, and sermon guides with end-to-end encryption. Your personal devotionals and confessions never leave this phone.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = OnInk.forTheme().copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            // Action Tiles: Create & Join
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(text = "New Circle", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, FaithGoldInk)
                    ) {
                        Icon(imageVector = Icons.Filled.GroupAdd, contentDescription = null, tint = FaithGoldInk)
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Join Circle", color = FaithGoldInk, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Circles List Header
            item {
                Text(
                    text = "YOUR CIRCLES (${circles.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = OnInk.forTheme().copy(alpha = 0.6f)
                )
            }

            if (circles.isEmpty()) {
                item {
                    Surface(
                        color = OnInk.forTheme().copy(alpha = 0.02f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Line.forTheme())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🕊️", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No fellowship circles yet",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = OnInk.forTheme()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Create a circle for your small group or join one with an invite link.",
                                fontSize = 12.sp,
                                color = OnInk.forTheme().copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                items(circles) { circle ->
                    CircleCard(circle = circle, onClick = { onOpenCircle(circle.id) })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCircleDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, myName, emoji ->
                showCreateDialog = false
                viewModel.createCircle(name, desc, myName, emoji) { created ->
                    onOpenCircle(created.id)
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinCircleDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { inviteCode, myName ->
                showJoinDialog = false
                viewModel.joinCircle(inviteCode, myName) { joined ->
                    joined?.let { onOpenCircle(it.id) }
                }
            }
        )
    }
}

@Composable
private fun CircleCard(
    circle: Circle,
    onClick: () -> Unit
) {
    Surface(
        color = SurfaceBase.forTheme(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line.forTheme()),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = FaithGoldInk.copy(alpha = 0.14f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = circle.avatarEmoji, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = circle.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = OnInk.forTheme()
                )
                if (circle.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = circle.description,
                        fontSize = 12.sp,
                        color = OnInk.forTheme().copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${circle.memberCount} members",
                        fontSize = 11.sp,
                        color = FaithGoldInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "• End-to-End Encrypted",
                        fontSize = 11.sp,
                        color = OnInk.forTheme().copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCircleDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, myName: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("Me") }
    var emoji by remember { mutableStateOf("🕊️") }

    val emojis = listOf("🕊️", "📖", "🙏", "⛪", "✝️", "🌱")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create Fellowship Circle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "A private, encrypted group (5–15 members) for your Bible study or prayer team.",
                    fontSize = 12.sp,
                    color = OnInk.forTheme().copy(alpha = 0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.forEach { e ->
                        Surface(
                            shape = CircleShape,
                            color = if (emoji == e) FaithGoldInk.copy(alpha = 0.25f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (emoji == e) FaithGoldInk else Line.forTheme()),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { emoji = e }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = e, fontSize = 18.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Circle Name (e.g. Tuesday Bible Study)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    label = { Text("Your Display Name in this Circle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, desc, myName, emoji) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun JoinCircleDialog(
    onDismiss: () -> Unit,
    onJoin: (inviteCode: String, myName: String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("Me") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Join Fellowship Circle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste the invite link or code shared by your circle leader. The encryption key is included to grant you zero-knowledge access.",
                    fontSize = 12.sp,
                    color = OnInk.forTheme().copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text("Invite Link or Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (inviteCode.isNotBlank()) onJoin(inviteCode, myName) },
                enabled = inviteCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FaithGoldInk)
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
