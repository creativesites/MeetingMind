package com.craftflowtechnologies.meetingmind.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.ApprovalCard
import com.craftflowtechnologies.meetingmind.core.work.ProposedAction
import com.craftflowtechnologies.meetingmind.core.work.Recipe
import com.craftflowtechnologies.meetingmind.core.work.RecipeSkill
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.Primary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("Recipes & Automation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBase,
                    titleContentColor = Ink
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Description
            item {
                Text(
                    text = "Automate common follow-through workflows across your meetings, tasks, and reviews. Every action requires your approval before anything is sent or updated.",
                    fontSize = 14.sp,
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            }

            // Pending Approvals Section (if any)
            if (uiState.pendingApprovals.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Approvals",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }
                items(uiState.pendingApprovals, key = { it.id }) { card ->
                    ApprovalCardView(
                        card = card,
                        onApprove = { viewModel.approveCard(card) },
                        onReject = { viewModel.rejectCard(card) }
                    )
                }
            }

            // Active Recipes Section
            item {
                Text(
                    text = "Active Recipes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            items(uiState.recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onToggleRecipe = { enabled -> viewModel.toggleRecipe(recipe.id, enabled) },
                    onToggleStep = { skill, enabled -> viewModel.toggleStep(recipe.id, skill, enabled) }
                )
            }
        }
    }
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    onToggleRecipe: (Boolean) -> Unit,
    onToggleStep: (RecipeSkill, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LineSoft))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = recipe.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            text = "${recipe.steps.count { it.isEnabled }} steps configured",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                }
                Switch(
                    checked = recipe.isEnabled,
                    onCheckedChange = onToggleRecipe,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recipe.description,
                fontSize = 13.sp,
                color = InkSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide steps" else "Customize steps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recipe.steps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceBase, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.skill.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Ink
                                )
                                Text(
                                    text = step.skill.description,
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                            Switch(
                                checked = step.isEnabled && recipe.isEnabled,
                                onCheckedChange = { onToggleStep(step.skill, it) },
                                enabled = recipe.isEnabled,
                                modifier = Modifier.size(36.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApprovalCardView(
    card: ApprovalCard,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Primary))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.recipeTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Awaiting Approval",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                }
            }

            Text(
                text = "Context: ${card.contextSummary}",
                fontSize = 13.sp,
                color = InkSecondary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceBase)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                card.actions.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Column {
                            Text(
                                text = action.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink
                            )
                            Text(
                                text = action.description,
                                fontSize = 12.sp,
                                color = InkMuted
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = InkSecondary)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Dismiss")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Approve All")
                }
            }
        }
    }
}
