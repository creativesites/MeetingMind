package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.KanbanBoardData
import com.craftflowtechnologies.meetingmind.core.work.KanbanCard
import com.craftflowtechnologies.meetingmind.core.work.KanbanColumnType
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceTrack

@Composable
fun KanbanBoard(
    boardData: KanbanBoardData,
    onMoveCard: (KanbanCard, KanbanColumnType) -> Unit,
    onCardClick: (KanbanCard) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            KanbanColumn(
                type = KanbanColumnType.TO_DO,
                cards = boardData.toDo,
                onMoveCard = onMoveCard,
                onCardClick = onCardClick
            )
        }
        item {
            KanbanColumn(
                type = KanbanColumnType.IN_PROGRESS,
                cards = boardData.inProgress,
                onMoveCard = onMoveCard,
                onCardClick = onCardClick
            )
        }
        item {
            KanbanColumn(
                type = KanbanColumnType.DONE,
                cards = boardData.done,
                onMoveCard = onMoveCard,
                onCardClick = onCardClick
            )
        }
    }
}

@Composable
private fun KanbanColumn(
    type: KanbanColumnType,
    cards: List<KanbanCard>,
    onMoveCard: (KanbanCard, KanbanColumnType) -> Unit,
    onCardClick: (KanbanCard) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSunk)
            .border(1.dp, LineSoft, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = type.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceTrack)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${cards.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkSecondary
                )
            }
        }

        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No items",
                    fontSize = 12.sp,
                    color = InkMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { card ->
                    KanbanCardItem(
                        card = card,
                        currentColumn = type,
                        onMoveCard = onMoveCard,
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanCardItem(
    card: KanbanCard,
    currentColumn: KanbanColumnType,
    onMoveCard: (KanbanCard, KanbanColumnType) -> Unit,
    onClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBase)
            .border(1.dp, LineSoft, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("kanban_card_${card.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = card.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Move",
                            tint = InkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(SurfaceRaised)
                    ) {
                        KanbanColumnType.entries.filter { it != currentColumn }.forEach { targetCol ->
                            DropdownMenuItem(
                                text = { Text("Move to ${targetCol.label}", color = Ink, fontSize = 13.sp) },
                                onClick = {
                                    menuOpen = false
                                    onMoveCard(card, targetCol)
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                card.counterpartyName?.let { name ->
                    Text(
                        text = name,
                        fontSize = 11.sp,
                        color = InkSecondary,
                        maxLines = 1
                    )
                }
                card.dueAt?.let { due ->
                    Text(
                        text = "• " + Pulse.shortDate(due),
                        fontSize = 11.sp,
                        color = if (card.isOverdue(System.currentTimeMillis())) Danger else InkMuted
                    )
                }
                card.severity?.let { sev ->
                    Text(
                        text = "• $sev",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sev == "HIGH") Danger else Accent
                    )
                }
            }
        }
    }
}
