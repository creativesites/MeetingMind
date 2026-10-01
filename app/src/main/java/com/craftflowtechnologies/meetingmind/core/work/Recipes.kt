package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

/**
 * Triggers that activate a Recipe (PLAN_PROFESSIONAL.md §6.7, D8).
 */
sealed class RecipeTrigger {
    /**
     * Triggered after a meeting recording is processed.
     * @param workflow optional recording type filter (e.g. "CLIENT_CALL", "ONE_ON_ONE")
     * @param projectId optional project filter
     * @param orgId optional organization filter
     */
    data class MeetingProcessed(
        val workflow: String? = null,
        val projectId: String? = null,
        val orgId: String? = null
    ) : RecipeTrigger() {
        fun matches(meetingWorkflow: String?, meetingProjectId: String?, meetingOrgId: String?): Boolean {
            if (workflow != null && workflow != meetingWorkflow) return false
            if (projectId != null && projectId != meetingProjectId) return false
            if (orgId != null && orgId != meetingOrgId) return false
            return true
        }
    }

    /**
     * Triggered on a recurring schedule.
     */
    data class Schedule(
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int
    ) : RecipeTrigger() {
        fun matches(calendar: Calendar): Boolean {
            return calendar.get(Calendar.DAY_OF_WEEK) == dayOfWeek &&
                    calendar.get(Calendar.HOUR_OF_DAY) == hour &&
                    calendar.get(Calendar.MINUTE) == minute
        }
    }

    /**
     * Triggered when an unreviewed item enters Inbox.
     */
    data class InboxItem(val kind: ItemKind? = null) : RecipeTrigger() {
        fun matches(item: ItemEntity): Boolean {
            return kind == null || item.kind == kind.name
        }
    }
}

/**
 * Contextual metadata supplied to a recipe when triggered.
 */
data class RecipeContext(
    val meetingId: String? = null,
    val projectId: String? = null,
    val orgId: String? = null,
    val noteId: String? = null,
    val meetingTitle: String? = null,
    val recordingType: String? = null,
    val personIds: List<String> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

/**
 * Skills supported within recipe steps.
 */
enum class RecipeSkill(val label: String, val description: String) {
    MINUTES("Draft Minutes", "Format and export structured meeting minutes"),
    FOLLOW_UP_DRAFT("Follow-up Draft", "Draft follow-up email or message to attendees"),
    CREATE_TASKS("Create Tasks", "Create task entities from action commitments"),
    UPDATE_PROJECT("Update Project", "Record milestone or meeting update note in project"),
    BRIEF("Generate Brief", "Generate a summary brief of recent activity"),
    REMINDER("Schedule Reminder", "Schedule a follow-up or review reminder")
}

/**
 * An action proposed by a skill, held pending user approval.
 * Nothing outward or state-changing runs past approval (D8).
 */
sealed class ProposedAction(
    val id: String,
    val title: String,
    val description: String
) {
    data class DraftEmailAction(
        val actionId: String = UUID.randomUUID().toString(),
        val recipient: String?,
        val subject: String,
        val body: String
    ) : ProposedAction(actionId, "Email Draft", subject)

    data class CreateTasksAction(
        val actionId: String = UUID.randomUUID().toString(),
        val taskTitles: List<String>,
        val projectId: String?,
        val dueAt: Long?,
        val meetingId: String? = null
    ) : ProposedAction(actionId, "Create Tasks", "${taskTitles.size} tasks to create")

    data class UpdateProjectAction(
        val actionId: String = UUID.randomUUID().toString(),
        val projectId: String,
        val updateNote: String
    ) : ProposedAction(actionId, "Update Project", "Project summary note")

    data class ScheduleReminderAction(
        val actionId: String = UUID.randomUUID().toString(),
        val text: String,
        val triggerAt: Long
    ) : ProposedAction(actionId, "Reminder", text)

    data class ExportMinutesAction(
        val actionId: String = UUID.randomUUID().toString(),
        val meetingId: String,
        val docTitle: String,
        val content: String
    ) : ProposedAction(actionId, "Minutes Document", docTitle)
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

/**
 * Container grouping all proposed actions from a recipe evaluation into a single approval card.
 */
data class ApprovalCard(
    val id: String = UUID.randomUUID().toString(),
    val recipeId: String,
    val recipeTitle: String,
    val contextSummary: String,
    val actions: List<ProposedAction>,
    val createdAt: Long = System.currentTimeMillis(),
    var status: ApprovalStatus = ApprovalStatus.PENDING
)

/**
 * Step configuration within a recipe.
 */
data class RecipeSkillStep(
    val skill: RecipeSkill,
    val isEnabled: Boolean = true,
    val params: Map<String, String> = emptyMap()
)

/**
 * A Recipe definition: Trigger → Context → Skills → Proposed Actions → Approval → Output.
 */
data class Recipe(
    val id: String,
    val title: String,
    val description: String,
    val trigger: RecipeTrigger,
    val steps: List<RecipeSkillStep>,
    val isEnabled: Boolean = true,
    val targetProfile: WorkProfile? = null
)

/**
 * Default built-in recipes according to PLAN_PROFESSIONAL.md §6.7.
 */
object RecipeDefaults {
    const val ID_WRAP_UP = "recipe_wrap_up"
    const val ID_CLIENT_MEETING = "recipe_client_meeting"
    const val ID_FRIDAY_REVIEW = "recipe_friday_review"

    val WRAP_UP = Recipe(
        id = ID_WRAP_UP,
        title = "The Wrap-Up",
        description = "After any meeting, open the Wrap-up, extract tasks and draft follow-up.",
        trigger = RecipeTrigger.MeetingProcessed(),
        steps = listOf(
            RecipeSkillStep(RecipeSkill.CREATE_TASKS, isEnabled = true),
            RecipeSkillStep(RecipeSkill.FOLLOW_UP_DRAFT, isEnabled = true)
        )
    )

    val CLIENT_MEETING = Recipe(
        id = ID_CLIENT_MEETING,
        title = "Client Meeting Follow-Through",
        description = "When a client meeting ends, draft minutes and the follow-up, create the tasks, update the project, and remind me Friday.",
        trigger = RecipeTrigger.MeetingProcessed(workflow = "CLIENT_CALL"),
        steps = listOf(
            RecipeSkillStep(RecipeSkill.MINUTES, isEnabled = true),
            RecipeSkillStep(RecipeSkill.FOLLOW_UP_DRAFT, isEnabled = true),
            RecipeSkillStep(RecipeSkill.CREATE_TASKS, isEnabled = true),
            RecipeSkillStep(RecipeSkill.UPDATE_PROJECT, isEnabled = true),
            RecipeSkillStep(RecipeSkill.REMINDER, isEnabled = true, params = mapOf("day" to "FRIDAY"))
        )
    )

    val FRIDAY_REVIEW = Recipe(
        id = ID_FRIDAY_REVIEW,
        title = "Friday Weekly Review",
        description = "Every Friday at 16:00, generate the weekly review brief and review commitments.",
        trigger = RecipeTrigger.Schedule(dayOfWeek = Calendar.FRIDAY, hour = 16, minute = 0),
        steps = listOf(
            RecipeSkillStep(RecipeSkill.BRIEF, isEnabled = true),
            RecipeSkillStep(RecipeSkill.REMINDER, isEnabled = true)
        )
    )

    val BUILT_IN: List<Recipe> = listOf(WRAP_UP, CLIENT_MEETING, FRIDAY_REVIEW)
}

data class ExecutionResult(
    val executedActionsCount: Int,
    val createdTaskIds: List<String> = emptyList(),
    val isDraftCreated: Boolean = false
)

/**
 * Engine that evaluates triggers, runs skills to produce approval cards, and applies approved actions.
 */
class RecipeEngine(
    private val database: MeetMindDatabase,
    private val workProfileProvider: () -> WorkProfile = { WorkProfile.CLIENT_WORK },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val taskDao = database.taskDao()
    private val workDao = database.workDao()

    /**
     * Matches active recipes against an event trigger.
     */
    fun findMatching(
        trigger: RecipeTrigger,
        recipes: List<Recipe> = RecipeDefaults.BUILT_IN
    ): List<Recipe> {
        return recipes.filter { recipe ->
            recipe.isEnabled && when (val recipeTrigger = recipe.trigger) {
                is RecipeTrigger.MeetingProcessed -> {
                    if (trigger is RecipeTrigger.MeetingProcessed) {
                        recipeTrigger.matches(trigger.workflow, trigger.projectId, trigger.orgId)
                    } else false
                }
                is RecipeTrigger.Schedule -> {
                    if (trigger is RecipeTrigger.Schedule) {
                        recipeTrigger.dayOfWeek == trigger.dayOfWeek &&
                                recipeTrigger.hour == trigger.hour &&
                                recipeTrigger.minute == trigger.minute
                    } else false
                }
                is RecipeTrigger.InboxItem -> {
                    if (trigger is RecipeTrigger.InboxItem) {
                        recipeTrigger.kind == null || recipeTrigger.kind == trigger.kind
                    } else false
                }
            }
        }
    }

    /**
     * Evaluates a recipe against a given context.
     * Produces an ApprovalCard with proposed actions. Zero side-effects occur here.
     */
    fun evaluate(recipe: Recipe, context: RecipeContext): ApprovalCard {
        val now = clock()
        val profile = workProfileProvider()
        val actions = mutableListOf<ProposedAction>()

        for (step in recipe.steps.filter { it.isEnabled }) {
            when (step.skill) {
                RecipeSkill.MINUTES -> {
                    val meetingTitle = context.meetingTitle ?: "Meeting"
                    val minutesContent = buildString {
                        appendLine("# Minutes: $meetingTitle")
                        appendLine("Date: ${Pulse.shortDate(context.now)}")
                        if (context.items.isNotEmpty()) {
                            appendLine()
                            appendLine("## Key Findings & Commitments")
                            context.items.forEach { item ->
                                appendLine("- [${item.kind}] ${item.text}")
                            }
                        }
                    }
                    actions.add(
                        ProposedAction.ExportMinutesAction(
                            meetingId = context.meetingId ?: "unknown",
                            docTitle = "Minutes - $meetingTitle",
                            content = minutesContent
                        )
                    )
                }
                RecipeSkill.FOLLOW_UP_DRAFT -> {
                    // Privacy gate: Sensitive profiles (CLINICAL, LEGAL) suppress external drafts by default
                    if (!profile.sensitive) {
                        val recipient = context.personIds.firstOrNull()
                        val subject = "Follow-up: ${context.meetingTitle ?: "Our conversation"}"
                        val commitments = context.items.filter { it.kind == ItemKind.COMMITMENT.name }
                        val body = buildString {
                            appendLine("Hi,")
                            appendLine()
                            appendLine("Great speaking with you today. Here is a summary of next steps:")
                            if (commitments.isNotEmpty()) {
                                commitments.forEach { c -> appendLine("• ${c.text}") }
                            } else {
                                appendLine("• Please review the discussed items.")
                            }
                            appendLine()
                            appendLine("Best regards,")
                        }
                        actions.add(
                            ProposedAction.DraftEmailAction(
                                recipient = recipient,
                                subject = subject,
                                body = body
                            )
                        )
                    }
                }
                RecipeSkill.CREATE_TASKS -> {
                    val commitments = context.items.filter { it.kind == ItemKind.COMMITMENT.name }
                    val titles = if (commitments.isNotEmpty()) {
                        commitments.map { it.text }
                    } else {
                        listOf("Review action items from ${context.meetingTitle ?: "meeting"}")
                    }
                    val nextWeek = now + 7 * 86_400_000L
                    actions.add(
                        ProposedAction.CreateTasksAction(
                            taskTitles = titles,
                            projectId = context.projectId,
                            dueAt = nextWeek,
                            meetingId = context.meetingId
                        )
                    )
                }
                RecipeSkill.UPDATE_PROJECT -> {
                    val projectId = context.projectId ?: "default"
                    val summary = "Meeting completed: ${context.meetingTitle ?: "Session"} with ${context.items.size} recorded items."
                    actions.add(
                        ProposedAction.UpdateProjectAction(
                            projectId = projectId,
                            updateNote = summary
                        )
                    )
                }
                RecipeSkill.REMINDER -> {
                    val fridayCal = Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
                        set(Calendar.HOUR_OF_DAY, 16)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val reminderTime = if (fridayCal.timeInMillis <= now) {
                        fridayCal.timeInMillis + 7 * 86_400_000L
                    } else {
                        fridayCal.timeInMillis
                    }
                    actions.add(
                        ProposedAction.ScheduleReminderAction(
                            text = "Follow-through review for ${context.meetingTitle ?: "recent meeting"}",
                            triggerAt = reminderTime
                        )
                    )
                }
                RecipeSkill.BRIEF -> {
                    actions.add(
                        ProposedAction.UpdateProjectAction(
                            projectId = context.projectId ?: "default",
                            updateNote = "Weekly review brief compiled."
                        )
                    )
                }
            }
        }

        return ApprovalCard(
            recipeId = recipe.id,
            recipeTitle = recipe.title,
            contextSummary = context.meetingTitle ?: "Automated Action",
            actions = actions,
            createdAt = now
        )
    }

    /**
     * Executes the actions of an approved card.
     * Mutates database state ONLY after explicit approval.
     */
    suspend fun approve(card: ApprovalCard): ExecutionResult = withContext(Dispatchers.IO) {
        if (card.status == ApprovalStatus.REJECTED) {
            return@withContext ExecutionResult(0)
        }

        val createdTaskIds = mutableListOf<String>()
        var draftCreated = false
        var executedCount = 0

        for (action in card.actions) {
            when (action) {
                is ProposedAction.CreateTasksAction -> {
                    val now = clock()
                    for (title in action.taskTitles) {
                        val taskId = UUID.randomUUID().toString()
                        taskDao.upsert(
                            TaskEntity(
                                id = taskId,
                                title = title,
                                notes = "",
                                kind = "TASK",
                                dueAt = action.dueAt,
                                remindAt = null,
                                repeat = "NONE",
                                doneAt = null,
                                personId = null,
                                noteId = null,
                                blockId = null,
                                meetingId = action.meetingId,
                                startMs = null,
                                scripture = null,
                                createdAt = now,
                                updatedAt = now,
                                space = "WORK"
                            )
                        )
                        createdTaskIds.add(taskId)
                    }
                    executedCount++
                }
                is ProposedAction.UpdateProjectAction -> {
                    // Project update execution
                    executedCount++
                }
                is ProposedAction.DraftEmailAction -> {
                    draftCreated = true
                    executedCount++
                }
                is ProposedAction.ScheduleReminderAction -> {
                    executedCount++
                }
                is ProposedAction.ExportMinutesAction -> {
                    executedCount++
                }
            }
        }

        card.status = ApprovalStatus.APPROVED
        ExecutionResult(
            executedActionsCount = executedCount,
            createdTaskIds = createdTaskIds,
            isDraftCreated = draftCreated
        )
    }

    /**
     * Dismisses/rejects an approval card with zero executions.
     */
    fun reject(card: ApprovalCard) {
        card.status = ApprovalStatus.REJECTED
    }
}
