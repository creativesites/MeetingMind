package com.craftflowtechnologies.meetingmind.feature.learn.ui.exam

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.exam.ExamPlanner
import com.craftflowtechnologies.meetingmind.feature.notes.editor.LatexMathParser
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MathExpressionView
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What the exam editor saves. A blank [id] makes a new exam. */
data class ExamDraft(
    val id: String = "",
    val title: String = "",
    val examDateMs: Long = System.currentTimeMillis() + 14 * ExamPlanner.DAY_MS,
    val courseId: String? = null,
    val targetGrade: String = "A",
    val dailyMinutes: Int = 30
)

/** Add or change an exam: its title, its day, its course, the target and the daily time. Sheet, not dialog. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamEditorSheet(
    initial: ExamDraft,
    courses: List<ExamCourseChip>,
    onSave: (ExamDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MM.colors
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var examDateMs by remember { mutableStateOf(initial.examDateMs) }
    var courseId by remember { mutableStateOf(initial.courseId) }
    var grade by remember { mutableStateOf(initial.targetGrade) }
    var minutes by remember { mutableStateOf(initial.dailyMinutes.toString()) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()) }
    val isNew = initial.id.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = MM.radius.sheet,
        modifier = Modifier.testTag("exam_editor")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(if (isNew) "Add an exam" else "Change the exam", style = MM.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Name it, e.g. Midterm 1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("goal_title_input")
            )

            Text("The day", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.inkSecondary)
            Text(dateFormat.format(Date(examDateMs)), style = MM.type.bodyStrong, color = c.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                listOf(7, 14, 30, 60).forEach { days ->
                    MMChip(
                        label = "$days days",
                        selected = false,
                        onClick = { examDateMs = System.currentTimeMillis() + days * ExamPlanner.DAY_MS },
                        modifier = Modifier.testTag("exam_in_$days")
                    )
                }
                MMChip(
                    label = "Pick a day",
                    selected = false,
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = examDateMs }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                examDateMs = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }.timeInMillis
                            },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.testTag("exam_pick_day")
                )
            }

            if (courses.isNotEmpty()) {
                Text("Course", style = MM.type.caption.copy(fontWeight = FontWeight.Bold), color = c.inkSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    MMChip(label = "No course", selected = courseId == null, onClick = { courseId = null })
                    courses.forEach { course ->
                        MMChip(label = course.name, selected = courseId == course.id, onClick = { courseId = course.id }, modifier = Modifier.testTag("exam_editor_course_${course.id}"))
                    }
                }
            }

            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it },
                label = { Text("Target, e.g. A or 90%") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("goal_grade_input")
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Minutes a day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("goal_minutes_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.testTag("exam_editor_cancel"))
                PrimaryButton(
                    text = "Save exam",
                    enabled = title.isNotBlank(),
                    onClick = {
                        onSave(
                            ExamDraft(
                                id = initial.id,
                                title = title.trim(),
                                examDateMs = examDateMs,
                                courseId = courseId,
                                targetGrade = grade.trim().ifBlank { "A" },
                                dailyMinutes = minutes.toIntOrNull()?.coerceIn(5, 300) ?: 30
                            )
                        )
                    },
                    modifier = Modifier.testTag("save_goal_confirm")
                )
            }
        }
    }
}

/**
 * The cram sheet as a page to read: its headings, bullets and formulas, with copy, share and save. The markdown is
 * the one the exam plan made; nothing is typed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CramSheetSheet(
    markdown: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSaveAsNote: () -> Unit
) {
    val c = MM.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = MM.radius.sheet,
        modifier = Modifier.testTag("cram_sheet")
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cram sheet", style = MM.type.title, color = c.ink, modifier = Modifier.semantics { heading() }.weight(1f))
                TextAction(
                    text = if (copied) "Copied" else "Copy",
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("Cram sheet", markdown))
                        copied = true
                    },
                    modifier = Modifier.testTag("cram_copy")
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(MMSize.minTouch)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.inkSecondary)
                }
            }
            HorizontalDivider(color = c.line, modifier = Modifier.padding(top = MM.space.s))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.xl, vertical = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                CramSheetText(markdown)
            }
            HorizontalDivider(color = c.line)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.xl, vertical = MM.space.m),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(text = "Share to a Circle", onClick = onShare, modifier = Modifier.testTag("cram_share_circle_sheet"))
                PrimaryButton(text = "Save as note", onClick = onSaveAsNote, modifier = Modifier.testTag("cram_save_note"))
            }
        }
    }
}

/** Renders the cram sheet's markdown: headings, bullets, rules and $…$ formulas. Plain text lines stay plain. */
@Composable
internal fun CramSheetText(markdown: String) {
    val c = MM.colors
    markdown.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> Spacer(Modifier.height(MM.space.xs))
            line.startsWith("### ") -> Text(line.removePrefix("### "), style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.padding(top = MM.space.xs))
            line.startsWith("## ") -> Text(line.removePrefix("## "), style = MM.type.heading, color = c.ink, modifier = Modifier.padding(top = MM.space.s))
            line.startsWith("# ") -> Text(line.removePrefix("# "), style = MM.type.title, color = c.accent, modifier = Modifier.padding(top = MM.space.m))
            line == "---" || line == "***" -> HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = MM.space.xs))
            isFormula(line) -> FormulaBlock(line.removeSurrounding("$$").removeSurrounding("$").trim())
            line.startsWith("- ") || line.startsWith("* ") -> {
                val body = line.substring(2).trim()
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text("•", style = MM.type.bodyStrong, color = c.accent)
                    if (isFormula(body)) {
                        FormulaBlock(body.removeSurrounding("$$").removeSurrounding("$").trim(), Modifier.weight(1f))
                    } else {
                        Text(body, style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            else -> Text(line, style = MM.type.body, color = c.ink)
        }
    }
}

private fun isFormula(s: String): Boolean =
    (s.startsWith("$$") && s.endsWith("$$") && s.length > 4) || (s.startsWith("$") && s.endsWith("$") && s.length > 2)

@Composable
private fun FormulaBlock(latex: String, modifier: Modifier = Modifier) {
    val c = MM.colors
    val node = remember(latex) { runCatching { LatexMathParser.parse(latex) }.getOrNull() }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MM.radius.small)
            .background(c.surfaceSunk)
            .padding(horizontal = MM.space.m, vertical = MM.space.s)
    ) {
        if (node != null) {
            MathExpressionView(node = node, fontSize = MM.type.body.fontSize, inkColor = c.ink)
        } else {
            Text(latex, style = MM.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink)
        }
    }
}
