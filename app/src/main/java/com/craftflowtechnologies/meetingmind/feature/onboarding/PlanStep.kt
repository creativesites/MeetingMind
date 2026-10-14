package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalPlans
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.time.LocalDate

/** One row of the "Choose how you read" step: a plan with a taste of its first entry, or "Write one with AI". */
data class PlanChoice(
    val id: String,
    val title: String,
    val byline: String,
    /** The first entry's heading or opening words, and a few lines of it. Null for the AI row. */
    val previewTitle: String? = null,
    val previewText: String? = null,
    val recommended: Boolean = false
) {
    companion object {
        /** The id of the "Write one with AI, any time" row (it sets the source to AI; the plan stays as the fallback). */
        const val AI = "ai"

        /** A plan's row, with the first entry of the year as its preview. */
        fun of(classics: ClassicDevotionals, recommended: Boolean): PlanChoice {
            val plan = classics.plan
            val first = classics.forDate(LocalDate.of(2026, 1, 1))
            val head = first?.heading?.takeIf { it.isNotBlank() } ?: first?.referenceText?.takeIf { it.isNotBlank() }
            val text = first?.keyText?.takeIf { it.isNotBlank() } ?: first?.paragraphs?.firstOrNull()
            return PlanChoice(
                plan.id, plan.title, "${plan.author} · ${plan.blurb}",
                previewTitle = head,
                previewText = text?.let { if (it.length > 150) it.take(147).substringBeforeLast(' ') + "…" else it },
                recommended = recommended
            )
        }

        fun ai() = PlanChoice(
            AI, "Write one with AI, any time", "A fresh devotional each day, written with AI and labelled as such. " +
                "Needs Internet mode or the offline pack. ${DevotionalPlans.default.title} fills in until then."
        )
    }
}

/**
 * Onboarding: "Choose how you read". Every bundled plan, the recommended one first and already chosen, each with a
 * preview of its first entry, then "Write one with AI, any time". It can be skipped: skipping keeps the recommended
 * plan, and the choice can be changed later in the devotional's settings.
 */
@Composable
fun PlanStep(choices: List<PlanChoice>, selectedId: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Choose how you read", style = MM.type.title, color = FixedWhite, modifier = Modifier.padding(top = MM.space.m).testTag("onboarding_plan_title"))
        Text(
            "A devotional is ready every day, even offline. Pick a book to read through the year, or have one written with AI. You can change this any time.",
            style = MM.type.body, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.l)
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            choices.forEach { c -> PlanRow(c, c.id == selectedId) { onSelect(c.id) } }
        }
        Text(
            "Not sure? Skip this and you'll read ${DevotionalPlans.default.title}.",
            style = MM.type.caption, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = MM.space.l, bottom = MM.space.l)
        )
    }
}

@Composable
private fun PlanRow(c: PlanChoice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MM.radius.card,
        color = if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
        border = if (selected) BorderStroke(MMSize.hairline, Brand.Cyan) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).testTag("onboarding_plan_${c.id}")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(MM.space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, style = MM.type.heading, color = FixedWhite, modifier = Modifier.weight(1f))
                if (c.recommended) Text("Recommended", style = MM.type.caption, color = Brand.Cyan)
            }
            Text(c.byline, style = MM.type.secondary, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = MM.space.xs))
            if (c.previewText != null) {
                Column(Modifier.padding(top = MM.space.m)) {
                    c.previewTitle?.let { Text(it, style = MM.type.caption, color = Brand.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(c.previewText, style = MM.type.secondary, color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                }
            }
        }
    }
}
