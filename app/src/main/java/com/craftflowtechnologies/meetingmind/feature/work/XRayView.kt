package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.XRayCluster
import com.craftflowtechnologies.meetingmind.core.work.XRayGraph
import com.craftflowtechnologies.meetingmind.core.work.XRayNode
import com.craftflowtechnologies.meetingmind.core.work.XRayNodeType
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

@Composable
fun XRayView(
    graph: XRayGraph,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<XRayNode?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Masthead
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.08f))
                .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Professional X-Ray",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent
                    )
                }

                Text(
                    text = graph.summary,
                    fontSize = 13.sp,
                    color = Ink,
                    lineHeight = 18.sp
                )

                if (graph.citedQuotes.isNotEmpty()) {
                    Text(
                        text = "${graph.citedQuotes.size} cited source quotes in context pack",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }
            }
        }

        // Entity Clusters
        graph.clusters.forEach { cluster ->
            ClusterSection(
                cluster = cluster,
                onNodeClick = { selectedNode = it }
            )
        }
    }

    selectedNode?.let { node ->
        AlertDialog(
            onDismissRequest = { selectedNode = null },
            title = {
                Text(
                    text = node.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    node.subtitle?.let {
                        Text(text = it, fontSize = 13.sp, color = InkSecondary)
                    }
                    node.citationQuote?.let { quote ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceSunk)
                                .padding(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.FormatQuote,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "\"$quote\"",
                                    fontSize = 12.sp,
                                    color = Ink
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNode = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterSection(
    cluster: XRayCluster,
    onNodeClick: (XRayNode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = cluster.label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = InkMuted
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cluster.nodes.forEach { node ->
                XRayNodeChip(node = node, onClick = { onNodeClick(node) })
            }
        }
    }
}

@Composable
private fun XRayNodeChip(
    node: XRayNode,
    onClick: () -> Unit
) {
    val chipBorderColor = when (node.type) {
        XRayNodeType.DECISION -> Accent.copy(alpha = 0.4f)
        XRayNodeType.RISK -> Danger.copy(alpha = 0.4f)
        else -> LineSoft
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, chipBorderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = node.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1
                )
                if (node.citationQuote != null) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Cited quote",
                        tint = Accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            node.subtitle?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    color = InkSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
