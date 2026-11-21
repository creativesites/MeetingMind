package com.craftflowtechnologies.meetingmind.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalPlans
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
    val c = MM.colors
    Column(Modifier.fillMaxWidth()) {
        Text("Choose how you read", style = MM.type.display, color = c.ink, modifier = Modifier.padding(top = MM.space.l).testTag("onboarding_plan_title"))
        Text(
            "A devotional is ready every day, even offline. Pick a book to read through the year, or have one written with AI. You can change this any time.",
            style = MM.type.body, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.xl)
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            choices.forEach { choice -> PlanRow(choice, choice.id == selectedId) { onSelect(choice.id) } }
        }
        Text(
            "Not sure? Skip this and you'll read ${DevotionalPlans.default.title}.",
            style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = MM.space.l, bottom = MM.space.l)
        )
    }
}

@Composable
private fun PlanRow(choice: PlanChoice, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch)
            .choiceCard(c, MM.radius.card, selected, MM.elevation.floating)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(MM.space.l).testTag("onboarding_plan_${choice.id}"),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(choice.title, style = MM.type.heading, color = c.ink, modifier = Modifier.weight(1f, fill = false))
                if (choice.recommended) {
                    Text(
                        "Recommended", style = MM.type.caption, color = c.accent,
                        modifier = Modifier.padding(start = MM.space.s).clip(MM.radius.pill).background(c.accentWash)
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
            }
            Text(choice.byline, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
            if (choice.previewText != null) {
                Column(
                    Modifier.padding(top = MM.space.m).fillMaxWidth().clip(MM.radius.small).background(c.surfaceSunk).padding(MM.space.m)
                ) {
                    choice.previewTitle?.let { Text(it, style = MM.type.caption, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(choice.previewText, style = MM.type.secondary, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                }
            }
        }
        ChoiceMark(selected, Modifier.padding(start = MM.space.m))
    }
}
