package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan

/**
 * The note screen's entrance (§3.4, §3.5): wraps the header block and the body of `MeetingDetail` or
 * `WrapUp` and plays the planned Unveil or nod on them. With a [MomentPlan.Plain] plan (or null while the
 * plan loads) the content shows as it is. A [MomentPlan.FirstKeep] plays in [FirstKeepOverlay] above the
 * note, so here it shows the end state (the note, still).
 *
 * The character (48–64 dp, 40 dp for the nod) stays in the header and top-bar zone, steps back and leaves;
 * it never covers or changes the user's words. The body enters the same quiet way for every variant. Any
 * tap skips (and still reaches what it hit); Back skips while it plays. One polite [announcement]
 * ("{title} is ready."), made only when the plan announces.
 *
 * ```
 * NoteUnveil(plan, form = settings.form, announcement = "$title is ready.", key = "unveil:$noteId", titleWords = title.wordCount()) {
 *     Column {
 *         Column(Modifier.momentLayer(StageTarget.HEADER)) {      // title, meta row, chips
 *             MomentTitle(title, MM.type.display)                  // Inkline writes it; plain otherwise
 *             MetaRow(); Chips()
 *         }
 *         Summary(Modifier.momentLayer(StageTarget.BODY, 0))
 *         KeyPoints(Modifier.momentLayer(StageTarget.BODY, 1))
 *         Actions(Modifier.momentLayer(StageTarget.BODY, 2))
 *     }
 * }
 * ```
 *
 * Work's Wrap-up passes the same plan; the variants already keep the character at ≤ 64 dp in the header
 * zone (pass `form` as usual).
 */
@Composable
fun NoteUnveil(
    plan: MomentPlan?,
    form: CompanionForm?,
    announcement: String,
    modifier: Modifier = Modifier,
    key: String = "unveil",
    titleWords: Int = 5,
    onEnd: () -> Unit = {},
    content: @Composable MomentScope.() -> Unit
) {
    val staged = if (plan is MomentPlan.FirstKeep) null else plan
    MomentStage(
        plan = staged, form = form, announcement = announcement, modifier = modifier, key = key,
        titleWords = titleWords, onEnd = onEnd, content = content
    )
}
