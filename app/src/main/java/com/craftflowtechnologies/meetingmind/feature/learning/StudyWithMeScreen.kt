package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Isolated, accessible UI for a deliberately bounded Study With Me plan. */
@Composable
fun StudyWithMeScreen(
    plan: StudyWithMePlan,
    onPractice: (String) -> Unit,
    onTeachBack: (String) -> Unit,
    onExit: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(0) }
    val phases = listOf("Orient", "Recall", "Focus", "Next action", "Complete")
    val phase = phases[step]
    Column(
        Modifier.fillMaxSize().padding(24.dp).semantics { contentDescription = "Study With Me, $phase step" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Study With Me · ${plan.minutes} minutes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$phase · ${step + 1} of ${phases.size}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                when (step) {
                    0 -> Text(plan.orientation)
                    1 -> Text(if (plan.recallActivities.isEmpty()) "No review is due. We can move to one focused challenge." else "Start with a short retrieval attempt. You can pause whenever you need.")
                    2 -> Text(plan.focusConcept?.let { "Optional focused review: ${it.name}. Read the cited definition, then say it back in your own words." } ?: "Choose a concept when you are ready; there is no pressure to fill this time.")
                    3 -> Text(plan.challenge?.let { "One useful next action is ready: ${it.prompt}" } ?: "You have completed the useful work available right now.")
                    else -> Text("Session complete. You completed a bounded study session; your next recommendation will appear when it is due.")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (plan.emptyState != null) Text(plan.emptyState, style = MaterialTheme.typography.bodyMedium)
        if (step == 1 && plan.recallActivities.isNotEmpty()) Button(onClick = { onPractice(plan.recallActivities.first().id) }) { Text("Start recall") }
        if (step == 2 && plan.focusConcept != null) OutlinedButton(onClick = { onTeachBack(plan.focusConcept.id) }) { Text("Try Teach-Back") }
        if (step == 3 && plan.challenge != null) Button(onClick = { onPractice(plan.challenge.id) }) { Text("Try challenge") }
        Spacer(Modifier.height(12.dp))
        if (step < phases.lastIndex) OutlinedButton(onClick = { step++ }) { Text("Continue") }
        OutlinedButton(onClick = onExit, modifier = Modifier.semantics { contentDescription = "Pause or exit Study With Me" }) { Text("Pause and exit") }
    }
}
