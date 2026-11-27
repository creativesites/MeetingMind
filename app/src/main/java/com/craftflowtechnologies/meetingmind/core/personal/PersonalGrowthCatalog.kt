package com.craftflowtechnologies.meetingmind.core.personal

/**
 * Versioned, offline-first curated catalog for the Personal Vertical:
 * - 12 starter Routines (Morning, Afternoon, Evening, Weekly & Creative)
 * - 14 Flagship Guided Journeys (multi-week behavioral transformations)
 * - 8 Focused Challenges (7, 14, 21, and 30 day sprints)
 * - 16 Bundled Guided Coaching Activities (2–5 min scripts voiced via Read-to-Me TTS or run interactively)
 *
 * Stored as immutable Kotlin data so it requires zero network calls, adds negligible APK size,
 * and never triggers database schema migrations when copy is refined or translated.
 */

data class RoutineStepTemplate(
    val idSuffix: String,
    val kind: RoutineStepKind,
    val title: String,
    val minEffortTitle: String,
    val durationSeconds: Int,
    val minEffortDurationSeconds: Int,
    val promptText: String = "",
    val guidedActivityId: String? = null,
    val companionState: String = "IDLE"
)

data class RoutineTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val whyDescription: String,
    val timeWindow: RoutineTimeWindow,
    val defaultMinuteOfDay: Int,
    val daysOfWeekMask: Int = PersonalRoutine.ALL_DAYS_MASK,
    val anchorCue: String,
    val soundscape: PersonalSoundscape = PersonalSoundscape.NONE,
    val steps: List<RoutineStepTemplate>,
    val isDefaultStarter: Boolean = false
)

data class JourneyStepDefinition(
    val index: Int,
    val chapter: Int,
    val chapterTitle: String,
    val title: String,
    val lessonScript: String,
    val microAction: String,
    val minEffortAction: String,
    val reflectionPrompt: String,
    val suggestedRoutineTemplateId: String? = null
)

data class JourneyDefinition(
    val id: String,
    val title: String,
    val tagline: String,
    val introStory: String,
    val durationDays: Int,
    val landscapeTheme: String, // DAWN_TRAIL, QUIET_GARDEN, COASTAL_LIGHT, STARLIT_PEAK
    val recommendedRoutineId: String,
    val whyPrompt: String,
    val steps: List<JourneyStepDefinition>,
    val version: Int = 1
)

data class ChallengeDefinition(
    val id: String,
    val title: String,
    val tagline: String,
    val durationDays: Int,
    val dailyAction: String,
    val minEffortAction: String,
    val reflectionPrompt: String,
    val category: String
)

enum class CoachingCategory(val label: String) {
    MORNING_MOTIVATION("Morning Motivation"),
    EVENING_REFLECTION("Evening Reflection"),
    FOCUS("Focus & Concentration"),
    GRATITUDE("Gratitude"),
    CONFIDENCE("Confidence"),
    SELF_DISCIPLINE("Self-Discipline"),
    CREATIVITY("Creativity"),
    STRESS_MANAGEMENT("Stress Management"),
    DECISION_MAKING("Decision Making"),
    MINDFULNESS("Mindfulness"),
    PERSONAL_GROWTH("Personal Growth")
}

data class GuidedActivityDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: CoachingCategory,
    val durationMinutes: Int,
    val stepKind: RoutineStepKind,
    val spokenScript: String,
    val reflectionPrompt: String? = null
)

object PersonalGrowthCatalog {

    const val CATALOG_VERSION = 1

    // ------------------------------------------------------------------ 12 Starter Routines

    val routineTemplates: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            id = "routine_morning_reset",
            title = "Morning Reset",
            subtitle = "Arrive gently before the noise begins",
            whyDescription = "Starting with one calm breath and a clear intention turns reaction into direction.",
            timeWindow = RoutineTimeWindow.MORNING,
            defaultMinuteOfDay = 7 * 60 + 30,
            anchorCue = "Right after I wake up or pour my first drink",
            soundscape = PersonalSoundscape.DAWN,
            isDefaultStarter = true,
            steps = listOf(
                RoutineStepTemplate("hydrate", RoutineStepKind.CHECK, "Drink a glass of water & stretch", "Take three slow sips of water", 60, 20, companionState = "IDLE"),
                RoutineStepTemplate("breathe", RoutineStepKind.BREATHING, "60-second calm arrival breath", "Three deep breaths", 60, 25, guidedActivityId = "act_morning_breath", companionState = "BREATHING"),
                RoutineStepTemplate("intention", RoutineStepKind.INTENTION, "Set today's intention", "Pick one word for today", 90, 30, promptText = "What matters most for you today, even if the day gets busy?", companionState = "LISTENING"),
                RoutineStepTemplate("priority", RoutineStepKind.CHECK, "Review your one main priority", "Glance at today's first step", 120, 30, companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_evening_winddown",
            title = "Evening Wind-Down",
            subtitle = "Close the day and put down what you carried",
            whyDescription = "Giving your mind a gentle closing ritual improves rest and keeps meaningful memories from slipping away.",
            timeWindow = RoutineTimeWindow.EVENING,
            defaultMinuteOfDay = 21 * 60,
            anchorCue = "When I put my phone on the charger or dim the lights",
            soundscape = PersonalSoundscape.FIREPLACE,
            isDefaultStarter = true,
            steps = listOf(
                RoutineStepTemplate("disconnect", RoutineStepKind.CHECK, "Put away distractions & dim screens", "Silence notifications for 5 minutes", 60, 20, companionState = "COZY"),
                RoutineStepTemplate("relax", RoutineStepKind.BREATHING, "2-minute evening exhale breathing", "Four slow exhales", 120, 30, guidedActivityId = "act_evening_exhale", companionState = "BREATHING"),
                RoutineStepTemplate("moment", RoutineStepKind.MEMORY, "Review one meaningful moment from today", "Recall one small good thing", 60, 20, promptText = "What is one small moment from today worth keeping?", companionState = "COZY"),
                RoutineStepTemplate("reflect", RoutineStepKind.VOICE_REFLECTION, "Record a 2-minute voice reflection", "Speak one honest sentence about today", 120, 30, promptText = "How did today really feel? What went well, and what can you release tonight?", companionState = "LISTENING"),
                RoutineStepTemplate("tomorrow", RoutineStepKind.QUIET_WRITE, "Write one thing to carry into tomorrow", "Jot down tomorrow's first tiny step", 60, 25, promptText = "What is the single kindest or most useful thing you can do for tomorrow's self?", companionState = "COZY")
            )
        ),
        RoutineTemplate(
            id = "routine_daily_reflection",
            title = "Daily Reflection",
            subtitle = "Five quiet minutes to hear yourself think",
            whyDescription = "Experience alone does not teach us; reflected experience does.",
            timeWindow = RoutineTimeWindow.EVENING,
            defaultMinuteOfDay = 20 * 60 + 30,
            anchorCue = "After dinner when the house gets quiet",
            soundscape = PersonalSoundscape.RAIN,
            steps = listOf(
                RoutineStepTemplate("pause", RoutineStepKind.BREATHING, "Arrive in the present", "Two calm breaths", 45, 20, companionState = "BREATHING"),
                RoutineStepTemplate("voice", RoutineStepKind.VOICE_REFLECTION, "Speak freely about your day", "30-second check-in", 180, 30, promptText = "What stood out to you today—an emotion, a conversation, or a surprise?", companionState = "LISTENING"),
                RoutineStepTemplate("lesson", RoutineStepKind.QUIET_WRITE, "Note one takeaway", "Write one line", 90, 30, promptText = "What did today teach you about yourself?", companionState = "COZY")
            )
        ),
        RoutineTemplate(
            id = "routine_gratitude",
            title = "Gratitude Practice",
            subtitle = "Train your attention toward what is good",
            whyDescription = "Naming specific, concrete things you appreciate rewires your baseline attention.",
            timeWindow = RoutineTimeWindow.MORNING,
            defaultMinuteOfDay = 8 * 60,
            anchorCue = "With my morning coffee or tea",
            soundscape = PersonalSoundscape.DAWN,
            steps = listOf(
                RoutineStepTemplate("notice", RoutineStepKind.COACHING_AUDIO, "2-minute gratitude lens", "30-second pause", 120, 30, guidedActivityId = "act_gratitude_lens", companionState = "READING"),
                RoutineStepTemplate("write", RoutineStepKind.QUIET_WRITE, "Name three specific moments or people", "Name one small thing you're thankful for", 90, 30, promptText = "What three ordinary or unexpected things are you genuinely grateful for right now?", companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_reading_time",
            title = "Reading Time",
            subtitle = "Unhurried pages that feed your mind",
            whyDescription = "Even ten pages a day becomes a dozen books a year when attached to a calm window.",
            timeWindow = RoutineTimeWindow.EVENING,
            defaultMinuteOfDay = 21 * 60 + 30,
            anchorCue = "When I sit in my reading chair or get into bed",
            soundscape = PersonalSoundscape.RAIN,
            steps = listOf(
                RoutineStepTemplate("open", RoutineStepKind.TIMER, "Read without multitasking", "Read just 1 page", 1200, 120, companionState = "COZY"),
                RoutineStepTemplate("capture", RoutineStepKind.QUIET_WRITE, "Keep one quote or idea from what you read", "Save one phrase", 90, 30, promptText = "What single line or idea from today's reading do you want to remember?", companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_focus_session",
            title = "Deep Focus Session",
            subtitle = "Clear the desk and enter flow",
            whyDescription = "Separating preparation from execution removes the friction that feeds procrastination.",
            timeWindow = RoutineTimeWindow.AFTERNOON,
            defaultMinuteOfDay = 14 * 60,
            anchorCue = "Before starting my most important block of work",
            soundscape = PersonalSoundscape.FOREST,
            steps = listOf(
                RoutineStepTemplate("clear", RoutineStepKind.CHECK, "Close extra tabs & silence alerts", "Put phone face down", 45, 15, companionState = "IDLE"),
                RoutineStepTemplate("prep", RoutineStepKind.COACHING_AUDIO, "2-minute focus arrival", "Pick your single target", 120, 30, guidedActivityId = "act_focus_prep", companionState = "READING"),
                RoutineStepTemplate("timer", RoutineStepKind.TIMER, "25-minute single-task block", "5-minute 'just start' sprint", 1500, 300, companionState = "IDLE"),
                RoutineStepTemplate("debrief", RoutineStepKind.QUIET_WRITE, "Log what moved forward", "One-line progress note", 60, 20, promptText = "What did you finish or unblock in this session?", companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_movement",
            title = "Exercise & Movement",
            subtitle = "Move your body to clear your mind",
            whyDescription = "Movement shifts state faster than overthinking ever can.",
            timeWindow = RoutineTimeWindow.MORNING,
            defaultMinuteOfDay = 7 * 60,
            anchorCue = "After putting on my shoes",
            steps = listOf(
                RoutineStepTemplate("warmup", RoutineStepKind.CHECK, "Loosen shoulders & take 5 deep breaths", "Stand up and stretch", 90, 30, companionState = "IDLE"),
                RoutineStepTemplate("move", RoutineStepKind.TIMER, "20 minutes of walking, running, or training", "3 minutes of gentle stretching", 1200, 180, companionState = "IDLE"),
                RoutineStepTemplate("feel", RoutineStepKind.VOICE_REFLECTION, "Capture post-movement clarity", "Note how your energy shifted", 60, 20, promptText = "How does your body and mind feel right now compared to before you moved?", companionState = "LISTENING")
            )
        ),
        RoutineTemplate(
            id = "routine_personal_learning",
            title = "Personal Learning Session",
            subtitle = "Apply one real-life skill to your week",
            whyDescription = "Small deliberate practice beats passive consumption.",
            timeWindow = RoutineTimeWindow.AFTERNOON,
            defaultMinuteOfDay = 17 * 60,
            anchorCue = "After wrapping up the main work day",
            steps = listOf(
                RoutineStepTemplate("study", RoutineStepKind.TIMER, "15 minutes with a book, talk, or playbook", "Review 1 retention byte (3 min)", 900, 180, companionState = "READING"),
                RoutineStepTemplate("apply", RoutineStepKind.QUIET_WRITE, "Write one experiment to try this week", "Name one tiny takeaway", 90, 30, promptText = "How can you test what you just learned in real life within 48 hours?", companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_quiet_time",
            title = "Quiet Time & Stillness",
            subtitle = "Unhurried space for prayer, contemplation, or grounding",
            whyDescription = "Carving out stillness anchors your values before the day pulls you in every direction.",
            timeWindow = RoutineTimeWindow.MORNING,
            defaultMinuteOfDay = 6 * 60 + 45,
            anchorCue = "In my quiet corner before checking messages",
            soundscape = PersonalSoundscape.DAWN,
            steps = listOf(
                RoutineStepTemplate("still", RoutineStepKind.BREATHING, "Settle into stillness", "Three quiet breaths", 90, 30, companionState = "BREATHING"),
                RoutineStepTemplate("listen", RoutineStepKind.TIMER, "Read, pray, or sit quietly", "2 minutes of quiet presence", 480, 120, companionState = "COZY"),
                RoutineStepTemplate("keep", RoutineStepKind.QUIET_WRITE, "Keep one word or prayer for the day", "Write one quiet sentence", 90, 30, promptText = "What word, conviction, or prayer do you want to carry with you today?", companionState = "COZY")
            )
        ),
        RoutineTemplate(
            id = "routine_weekly_planning",
            title = "Weekly Planning",
            subtitle = "Design your week on purpose",
            whyDescription = "Ten minutes of honest foresight saves hours of reactive stress.",
            timeWindow = RoutineTimeWindow.MORNING,
            defaultMinuteOfDay = 8 * 60 + 30,
            daysOfWeekMask = 1, // Monday
            anchorCue = "Monday morning with a fresh cup",
            steps = listOf(
                RoutineStepTemplate("review", RoutineStepKind.MEMORY, "Look back at last week's journal highlights", "Recall one win from last week", 180, 45, companionState = "IDLE"),
                RoutineStepTemplate("big3", RoutineStepKind.QUIET_WRITE, "Choose your top 3 personal & life priorities", "Pick 1 non-negotiable for this week", 240, 60, promptText = "If this week goes wonderfully, what three things will you have protected or finished?", companionState = "IDLE"),
                RoutineStepTemplate("protect", RoutineStepKind.CHECK, "Block rest and one joyful moment", "Pick one evening to protect", 120, 30, companionState = "IDLE")
            )
        ),
        RoutineTemplate(
            id = "routine_sunday_reset",
            title = "Sunday Reset",
            subtitle = "Clear the slate and step into Monday calm",
            whyDescription = "Resetting your environment and mind on Sunday evening removes Monday dread.",
            timeWindow = RoutineTimeWindow.EVENING,
            defaultMinuteOfDay = 18 * 60 + 30,
            daysOfWeekMask = 64, // Sunday
            anchorCue = "Late Sunday afternoon before dinner",
            soundscape = PersonalSoundscape.FOREST,
            steps = listOf(
                RoutineStepTemplate("space", RoutineStepKind.CHECK, "Tidy your desk, bag, and tomorrow's clothes", "Clear one surface", 300, 60, companionState = "IDLE"),
                RoutineStepTemplate("dump", RoutineStepKind.VOICE_REFLECTION, "5-minute Sunday brain dump", "1-minute voice clear-out", 300, 60, promptText = "Empty out everything lingering in your head from this past week so you don't carry it into Monday.", companionState = "LISTENING"),
                RoutineStepTemplate("gratitude", RoutineStepKind.QUIET_WRITE, "Name one gift from this week", "One line of thanks", 120, 30, promptText = "What made you smile or feel quietly proud over the past seven days?", companionState = "COZY")
            )
        ),
        RoutineTemplate(
            id = "routine_creative_time",
            title = "Personal Creative Time",
            subtitle = "Make something before you consume",
            whyDescription = "Protecting a small pocket for making—writing, sketching, music, or ideas—keeps life vivid.",
            timeWindow = RoutineTimeWindow.AFTERNOON,
            defaultMinuteOfDay = 16 * 60,
            anchorCue = "When I step away from errands for a breather",
            steps = listOf(
                RoutineStepTemplate("spark", RoutineStepKind.VOICE_REFLECTION, "Speak an unfiltered idea into the Spark Vault", "One 20-second spark", 120, 30, promptText = "What is an idea, story, design, or question you've been itching to explore?", companionState = "LISTENING"),
                RoutineStepTemplate("make", RoutineStepKind.TIMER, "15 minutes of playful creation with zero perfectionism", "3 minutes of rough sketching or drafting", 900, 180, companionState = "IDLE")
            )
        )
    )

    fun routineTemplateById(id: String): RoutineTemplate? = routineTemplates.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ 14 Flagship Guided Journeys

    val journeys: List<JourneyDefinition> = listOf(
        JourneyDefinition(
            id = "journey_consistent_journaling",
            title = "Build a Consistent Journaling Habit",
            tagline = "Find your natural voice and turn small daily reflections into lasting clarity.",
            introStory = "You do not need poetic prose or an hour at a desk to keep a meaningful journal. Over 14 gentle days, you will build a 2-minute voice-or-ink habit anchored to your real life.",
            durationDays = 14,
            landscapeTheme = "QUIET_GARDEN",
            recommendedRoutineId = "routine_daily_reflection",
            whyPrompt = "In your own words, why do you want to keep a record of your life in this season?",
            steps = buildJourneySteps(
                "journey_consistent_journaling",
                listOf(
                    Triple("Finding Your Voice", "One Honest Sentence", "Forget writing essays. Today's only job is capturing a single true sentence—spoken or typed—about what is on your mind right now."),
                    Triple("Finding Your Voice", "Anchor It to a Moment", "Habits stick when they ride on something you already do. Pick your anchor (morning coffee or plugging in your phone) and attach a 60-second check-in."),
                    Triple("Finding Your Voice", "Speak While You Walk", "Voice journaling removes the blank-page freeze. Take a two-minute walk or stand by a window and talk to Zuri as if telling a close friend about your day."),
                    Triple("Finding Your Voice", "The Low-Energy Rule", "Consistency is not perfection; it is refusing to quit on tired days. Practice using the 30-second minimum-effort reflection today."),
                    Triple("Noticing Patterns", "What Gave You Energy?", "Look back at the past 24 hours. Name one moment that gave you energy and one moment that quietly drained it."),
                    Triple("Noticing Patterns", "Keep a Conversation", "Write or speak one quote or insight someone shared with you recently that you don't want to forget."),
                    Triple("Noticing Patterns", "Week 1 Mirror Review", "Re-read your first six entries. Notice one recurring theme or emotion you hadn't seen while living through the week."),
                    Triple("Going Deeper", "The Unfiltered Brain Dump", "Spend three minutes emptying every open loop in your head without editing yourself."),
                    Triple("Going Deeper", "Reflect on a Note", "Open any Work, Learning, or Faith note from this week and tap 'Reflect in Journal' to capture your private perspective."),
                    Triple("Going Deeper", "A Letter to Next Month", "Capture what you hope will feel different or clearer thirty days from now."),
                    Triple("Going Deeper", "Name Your Current Chapter", "If this season of your life were a chapter in a book, what would its title be?"),
                    Triple("Making It Yours", "Celebrate a Small Promise", "Record one quiet promise you kept to yourself this week."),
                    Triple("Making It Yours", "Design Your Weekly Rhythm", "Decide which days of the week feel best for a longer reflection versus a quick voice spark."),
                    Triple("Making It Yours", "Your Journey Anthology", "Revisit your Day 1 'Why'. Record how journaling feels now and keep this milestone in your Memory Vault.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_better_morning",
            title = "Create a Better Morning",
            tagline = "Reclaim your first 15 minutes with hydration, breath, and a calm daily intention.",
            introStory = "How your morning starts quietly shapes your focus and mood for the next twelve hours. We start with one tiny 2-minute anchor and build your Morning Reset step by step.",
            durationDays = 21,
            landscapeTheme = "DAWN_TRAIL",
            recommendedRoutineId = "routine_morning_reset",
            whyPrompt = "What would a calmer, steadier morning change about the rest of your day?",
            steps = buildJourneySteps(
                "journey_better_morning",
                listOf(
                    Triple("Chapter 1 · Wake Gently", "Water Before Screens", "Place a glass of water by your bed tonight. Tomorrow, drink it before opening a single feed or inbox."),
                    Triple("Chapter 1 · Wake Gently", "Three Arrival Breaths", "Before standing up to rush, sit for 60 seconds with the 3D breathing pacer."),
                    Triple("Chapter 1 · Wake Gently", "Protect the First 10 Minutes", "Keep your phone on Do Not Disturb until your Morning Reset finishes."),
                    Triple("Chapter 1 · Wake Gently", "Sunlight & Movement", "Step near a window or outside for two minutes of natural light and a shoulder stretch."),
                    Triple("Chapter 1 · Wake Gently", "Speak Today's Intention", "Instead of checking what everyone else wants first, name one intention in your own voice."),
                    Triple("Chapter 1 · Wake Gently", "The Hard-Morning Rescue", "Practice the 2-minute low-energy Morning Reset so rushed days still start grounded."),
                    Triple("Chapter 1 · Wake Gently", "Week 1 Dawn Check-In", "Review how your mornings felt this week and adjust your start time if needed.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_self_discipline",
            title = "Develop Self-Discipline",
            tagline = "Build quiet self-trust by keeping small, concrete promises every day.",
            introStory = "Real discipline is not harshness—it is self-respect in action. Over 21 days, you will replace willpower battles with frictionless cues and honest reflection.",
            durationDays = 21,
            landscapeTheme = "STARLIT_PEAK",
            recommendedRoutineId = "routine_focus_session",
            whyPrompt = "What is one area of your life where keeping small promises to yourself would change everything?",
            steps = buildJourneySteps(
                "journey_self_discipline",
                listOf(
                    Triple("Chapter 1 · Self-Trust", "Shrink the Promise", "Pick one habit you've struggled to keep and shrink it to 5 minutes so starting is effortless."),
                    Triple("Chapter 1 · Self-Trust", "Design Your Environment", "Change one physical thing in your room tonight to make tomorrow's right choice obvious."),
                    Triple("Chapter 1 · Self-Trust", "The 5-Minute Start Rule", "Tell yourself you only have to do five minutes. Notice how resistance drops once you cross the threshold."),
                    Triple("Chapter 1 · Self-Trust", "Spot Your Friction Trigger", "Voice-journal the exact moment you usually get derailed and design a simple 'If-Then' response."),
                    Triple("Chapter 1 · Self-Trust", "Never Miss Twice Gently", "If you slip, skip the guilt. Reset calmly the very next day."),
                    Triple("Chapter 1 · Self-Trust", "Keep One Hard Promise", "Do the single thing you have been avoiding for 10 focused minutes before lunch."),
                    Triple("Chapter 1 · Self-Trust", "Week 1 Evidence Log", "Record the real evidence from this week that you are someone who follows through.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_rebuild_focus",
            title = "Rebuild Your Focus",
            tagline = "Trade scattered multitasking for calm, single-threaded attention.",
            introStory = "Attention is your most precious personal resource. Over 14 days, you will clear digital noise, practice 25-minute deep blocks, and rediscover the satisfaction of finishing.",
            durationDays = 14,
            landscapeTheme = "COASTAL_LIGHT",
            recommendedRoutineId = "routine_focus_session",
            whyPrompt = "What meaningful work or personal project deserves your undivided attention right now?",
            steps = buildJourneySteps(
                "journey_rebuild_focus",
                listOf(
                    Triple("Clearing the Static", "Audit Your Interruptions", "Notice the top three notifications that broke your train of thought today and silence one."),
                    Triple("Clearing the Static", "One Tab, One Task", "Run a single 15-minute Deep Focus Session with every unrelated tab closed."),
                    Triple("Clearing the Static", "The Parking Lot Spark", "When a random thought interrupts focus, drop it in the Idea Studio in 10 seconds and return."),
                    Triple("Clearing the Static", "Focus Warm-Up Ritual", "Play the 2-minute Focus Arrival coaching activity before starting your main block."),
                    Triple("Clearing the Static", "Finish Before Switching", "Bring one small nagging task all the way to 'done' before touching anything new.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_more_intentional",
            title = "Become More Intentional",
            tagline = "Align your daily calendar with the values you actually care about.",
            introStory = "Busy days can still feel hollow if they belong to everyone except you. Clarify your core values and practice saying a calm no to what doesn't fit.",
            durationDays = 21,
            landscapeTheme = "COASTAL_LIGHT",
            recommendedRoutineId = "routine_weekly_planning",
            whyPrompt = "If you looked back a year from today, what would make you feel you lived this season on purpose?",
            steps = buildJourneySteps(
                "journey_more_intentional",
                listOf(
                    Triple("Compass Check", "Name Three Core Values", "Complete the Values Clarification reflection and pin the three words that matter most this season."),
                    Triple("Compass Check", "The Energy Audit", "Look at yesterday's hours: which hour matched your values best?"),
                    Triple("Compass Check", "One Graceful No", "Identify one low-value obligation you can decline, defer, or simplify this week.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_work_life_balance",
            title = "Improve Your Work-Life Balance",
            tagline = "Build a clean shutdown ritual so rest actually feels like rest.",
            introStory = "When work never officially closes, your mind stays half-clocked-in all evening. Build a reliable boundary between doing and being.",
            durationDays = 14,
            landscapeTheme = "QUIET_GARDEN",
            recommendedRoutineId = "routine_evening_winddown",
            whyPrompt = "Who—or what part of your life—deserves your full presence after work ends?",
            steps = buildJourneySteps(
                "journey_work_life_balance",
                listOf(
                    Triple("The Clean Boundary", "Create Your Shutdown Cue", "Write tomorrow's top task before closing your laptop so your brain stops rehearsing it at dinner."),
                    Triple("The Clean Boundary", "Transition Walk", "Take a 5-minute walk or stretch to physically mark the end of the workday."),
                    Triple("The Clean Boundary", "Unplugged Evening Hour", "Keep one full hour screen-free and capture how your nervous system responds.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_gratitude",
            title = "Develop a Gratitude Practice",
            tagline = "Notice the quiet abundance already woven into ordinary days.",
            introStory = "Gratitude is a trainable lens, not forced positivity. Practice spotting specific, concrete moments of goodness even in complicated seasons.",
            durationDays = 14,
            landscapeTheme = "DAWN_TRAIL",
            recommendedRoutineId = "routine_gratitude",
            whyPrompt = "What good things in your life do you want to notice more deeply instead of rushing past?",
            steps = buildJourneySteps(
                "journey_gratitude",
                listOf(
                    Triple("Seeing Clearly", "Three Tiny Specifics", "Avoid generic lists. Name three tiny, specific sensory details you appreciated today."),
                    Triple("Seeing Clearly", "Tell One Person", "Think of someone who made your week easier and send them a quick thank-you message."),
                    Triple("Seeing Clearly", "Gratitude for Past Self", "Thank yourself for one wise decision you made earlier this month.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_confident_communicator",
            title = "Become a More Confident Communicator",
            tagline = "Think clearly out loud, speak with warmth, and listen without rushing.",
            introStory = "Your voice becomes steadier the more you hear yourself articulate real thoughts. Use MeetingMind's voice-first journal to rehearse clarity every day.",
            durationDays = 21,
            landscapeTheme = "COASTAL_LIGHT",
            recommendedRoutineId = "routine_daily_reflection",
            whyPrompt = "Where do you want your voice to feel calmer, clearer, and more assured?",
            steps = buildJourneySteps(
                "journey_confident_communicator",
                listOf(
                    Triple("Voice & Presence", "The 60-Second Clear Point", "Record a 60-second voice note explaining one idea you care about in three crisp sentences."),
                    Triple("Voice & Presence", "Pause Instead of Filler", "Practice letting a 2-second silence breathe while speaking a journal reflection."),
                    Triple("Voice & Presence", "Listen to Understand", "Reflect on one conversation today where you listened more than you prepared your reply.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_ideas_to_action",
            title = "Turn Ideas into Action",
            tagline = "Move your best sparks out of your head and into real-world experiments.",
            introStory = "Great ideas fade when they stay vague. Over 14 days, pair your Idea Studio with small, low-risk tests that turn curiosity into shipped projects.",
            durationDays = 14,
            landscapeTheme = "STARLIT_PEAK",
            recommendedRoutineId = "routine_creative_time",
            whyPrompt = "Which long-simmering idea would you love to finally bring to life?",
            steps = buildJourneySteps(
                "journey_ideas_to_action",
                listOf(
                    Triple("From Spark to Ship", "Capture Without Judging", "Record three raw ideas into your Idea Studio today—no matter how unfinished."),
                    Triple("From Spark to Ship", "Pick One to Simmer", "Choose one Spark and write the smallest possible 15-minute experiment to test it."),
                    Triple("From Spark to Ship", "Take the First Visible Step", "Spend 15 minutes building the ugly first version and log what you learned.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_reading_habit",
            title = "Build a Reading Habit",
            tagline = "Read five unhurried pages a day and keep the ideas that change you.",
            introStory = "Replace late-night scrolling with a calm reading pocket and a living commonplace book of favorite quotes.",
            durationDays = 21,
            landscapeTheme = "QUIET_GARDEN",
            recommendedRoutineId = "routine_reading_time",
            whyPrompt = "What kinds of books or stories do you miss making time for?",
            steps = buildJourneySteps(
                "journey_reading_habit",
                listOf(
                    Triple("The Reading Pocket", "Put the Book on Your Pillow", "Make your next book physically easier to reach than your phone."),
                    Triple("The Reading Pocket", "Five Pages Minimum", "Read five pages—or just one page on a tired night—and capture one memorable line.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_evening_routine",
            title = "Establish a Consistent Evening Routine",
            tagline = "Unwind your nervous system and wake up genuinely restored.",
            introStory = "Great mornings actually begin the evening before. Build a predictable 8-minute wind-down that signals your mind it is safe to rest.",
            durationDays = 14,
            landscapeTheme = "STARLIT_PEAK",
            recommendedRoutineId = "routine_evening_winddown",
            whyPrompt = "How would better, calmer evenings change your sleep and energy?",
            steps = buildJourneySteps(
                "journey_evening_routine",
                listOf(
                    Triple("The Sunset Anchor", "Set Your Digital Sunset", "Pick a realistic time tonight to dim screens and begin your Evening Wind-Down."),
                    Triple("The Sunset Anchor", "Empty the Mental To-Do List", "Write tomorrow's one priority down so your brain doesn't hold it overnight.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_creativity",
            title = "Become More Creative",
            tagline = "Notice more, connect unexpected dots, and make space for playful experiments.",
            introStory = "Creativity is not a rare talent—it is a habit of paying attention and capturing sparks before they evaporate.",
            durationDays = 14,
            landscapeTheme = "QUIET_GARDEN",
            recommendedRoutineId = "routine_creative_time",
            whyPrompt = "What creative outlet or curiosity makes you feel most alive?",
            steps = buildJourneySteps(
                "journey_creativity",
                listOf(
                    Triple("Notice & Collect", "The Observation Walk", "Take a 10-minute walk and voice-note three interesting details you normally overlook."),
                    Triple("Notice & Collect", "Combine Two Sparks", "Pick two unrelated ideas in your Idea Studio and brainstorm how they could collide.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_personal_learning",
            title = "Develop a Personal Learning Habit",
            tagline = "Turn podcasts, books, and YouTube talks into real-world personal skills.",
            introStory = "Bridge inspiration and action by importing talks that matter to you and practicing one small playbook step each day.",
            durationDays = 14,
            landscapeTheme = "DAWN_TRAIL",
            recommendedRoutineId = "routine_personal_learning",
            whyPrompt = "What practical life skill do you want to master over the next few months?",
            steps = buildJourneySteps(
                "journey_personal_learning",
                listOf(
                    Triple("Active Growth", "Import One Life Skill", "Pick one YouTube talk or article on a skill you want, extract its playbook, and choose Step 1."),
                    Triple("Active Growth", "Practice Before Adding More", "Test one principle in real life today and voice-note what actually happened.")
                )
            )
        ),
        JourneyDefinition(
            id = "journey_digital_life",
            title = "Create a Healthier Digital Life",
            tagline = "Use your phone as a tool for memory and creation, not endless scrolling.",
            introStory = "Reclaim hours every week by replacing reflexive phone checks with intentional pauses and real-world presence.",
            durationDays = 14,
            landscapeTheme = "COASTAL_LIGHT",
            recommendedRoutineId = "routine_evening_winddown",
            whyPrompt = "What would you love to do with the quiet hours you reclaim from mindless scrolling?",
            steps = buildJourneySteps(
                "journey_digital_life",
                listOf(
                    Triple("Digital Calm", "Move One Tempting App", "Take your most distracting app off your home screen for 7 days."),
                    Triple("Digital Calm", "Replace the Scroll Reflex", "When you reach for your phone out of boredom, open Personal and capture a 15-second thought instead.")
                )
            )
        )
    )

    fun journeyById(id: String): JourneyDefinition? = journeys.firstOrNull { it.id == id }

    private fun buildJourneySteps(journeyId: String, specs: List<Triple<String, String, String>>): List<JourneyStepDefinition> =
        specs.mapIndexed { idx, (chapterTitle, title, lesson) ->
            JourneyStepDefinition(
                index = idx,
                chapter = (idx / 7) + 1,
                chapterTitle = chapterTitle,
                title = title,
                lessonScript = lesson,
                microAction = title,
                minEffortAction = "Take 60 seconds to try a tiny version of: $title",
                reflectionPrompt = "What did you notice when you tried '$title' today?"
            )
        }

    // ------------------------------------------------------------------ 8 Curated Challenges

    val challenges: List<ChallengeDefinition> = listOf(
        ChallengeDefinition(
            id = "challenge_7d_gratitude",
            title = "7 Days of Gratitude",
            tagline = "Spot one concrete, specific good thing every single day for a week.",
            durationDays = 7,
            dailyAction = "Record or write 3 specific moments you're grateful for today",
            minEffortAction = "Name 1 tiny good thing from today",
            reflectionPrompt = "What stood out as a quiet gift today?",
            category = "Gratitude"
        ),
        ChallengeDefinition(
            id = "challenge_7d_morning_reflection",
            title = "7 Days of Morning Reflection",
            tagline = "Start every day for a week with a calm 60-second spoken intention.",
            durationDays = 7,
            dailyAction = "Set your morning intention before checking feeds",
            minEffortAction = "Pick one word to anchor today",
            reflectionPrompt = "What kind of energy do you want to bring into today?",
            category = "Morning"
        ),
        ChallengeDefinition(
            id = "challenge_14d_focus",
            title = "14 Days of Better Focus",
            tagline = "Complete one distraction-free 25-minute single-task block each day.",
            durationDays = 14,
            dailyAction = "Finish one 25-minute single-task Focus Session",
            minEffortAction = "Finish one 5-minute 'just start' block",
            reflectionPrompt = "What helped you stay focused—or pulled you away—today?",
            category = "Focus"
        ),
        ChallengeDefinition(
            id = "challenge_14d_ideas",
            title = "14 Days of Capturing Your Ideas",
            tagline = "Catch at least one daily spark in your Idea Studio before it vanishes.",
            durationDays = 14,
            dailyAction = "Capture 1 voice or written idea into your Spark Vault",
            minEffortAction = "Capture 1 one-sentence curiosity",
            reflectionPrompt = "What sparked your curiosity today?",
            category = "Creativity"
        ),
        ChallengeDefinition(
            id = "challenge_21d_journaling",
            title = "21 Days of Consistent Journaling",
            tagline = "Build a lasting habit of keeping your story in your own words.",
            durationDays = 21,
            dailyAction = "Add a 2-minute voice or written reflection to Today's Journal",
            minEffortAction = "Write or speak one honest sentence",
            reflectionPrompt = "What moment from today do you want your future self to remember?",
            category = "Journaling"
        ),
        ChallengeDefinition(
            id = "challenge_30d_reading",
            title = "30 Days of Reading",
            tagline = "Read a few pages every day and save one favorite quote.",
            durationDays = 30,
            dailyAction = "Read for 15 minutes and capture 1 takeaway",
            minEffortAction = "Read 1 single page",
            reflectionPrompt = "What idea from your reading lingered with you today?",
            category = "Growth"
        ),
        ChallengeDefinition(
            id = "challenge_30d_intentional",
            title = "30 Days of Intentional Living",
            tagline = "Align one daily choice with your values and close each evening cleanly.",
            durationDays = 30,
            dailyAction = "Set a morning intention and review it briefly at night",
            minEffortAction = "Pause once at midday to ask: what matters right now?",
            reflectionPrompt = "Where did your actions match your values today?",
            category = "Intentionality"
        ),
        ChallengeDefinition(
            id = "challenge_7d_no_scrolling",
            title = "7 Days Without Unnecessary Scrolling",
            tagline = "Trade late-night doomscrolling for real rest and quiet reflection.",
            durationDays = 7,
            dailyAction = "Keep social feeds closed after 9 PM",
            minEffortAction = "Put your phone down 15 minutes earlier tonight",
            reflectionPrompt = "What did you do with the extra quiet space tonight?",
            category = "Digital Health"
        )
    )

    fun challengeById(id: String): ChallengeDefinition? = challenges.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ Bundled Coaching & Guided Activities

    val guidedActivities: List<GuidedActivityDefinition> = listOf(
        GuidedActivityDefinition(
            id = "act_morning_breath",
            title = "2-Minute Morning Arrival Breath",
            subtitle = "Ground your body before the day accelerates",
            category = CoachingCategory.MORNING_MOTIVATION,
            durationMinutes = 2,
            stepKind = RoutineStepKind.BREATHING,
            spokenScript = "Welcome to your morning. Rest your shoulders away from your ears. Breathe in gently for four counts. Hold softly for four. Exhale slowly for six, letting yesterday stay behind you. You don't have to solve the entire week right now—only meet this next hour with steady presence."
        ),
        GuidedActivityDefinition(
            id = "act_evening_exhale",
            title = "Evening Unwind & Exhale",
            subtitle = "Signal your nervous system that the workday is done",
            category = CoachingCategory.EVENING_REFLECTION,
            durationMinutes = 3,
            stepKind = RoutineStepKind.BREATHING,
            spokenScript = "The day is closing. Whatever went right, let it settle into gratitude. Whatever stayed unfinished, give yourself permission to place it gently on tomorrow's shelf. Breathe in slowly for four counts, and let a long, warm exhale carry the tension out of your jaw and hands."
        ),
        GuidedActivityDefinition(
            id = "act_focus_prep",
            title = "Focus Preparation Session",
            subtitle = "Clear mental clutter and lock onto one target",
            category = CoachingCategory.FOCUS,
            durationMinutes = 2,
            stepKind = RoutineStepKind.COACHING_AUDIO,
            spokenScript = "Take one calm breath and choose a single target for the next twenty-five minutes. Not three tasks—just one. Notice any urge to check messages as simple static passing by. Starting small beats waiting to feel ready. Begin with the very first tiny step."
        ),
        GuidedActivityDefinition(
            id = "act_gratitude_lens",
            title = "3-Minute Gratitude Lens",
            subtitle = "Spot concrete goodness in ordinary moments",
            category = CoachingCategory.GRATITUDE,
            durationMinutes = 3,
            stepKind = RoutineStepKind.COACHING_AUDIO,
            spokenScript = "Our minds naturally scan for problems, so noticing good things takes a gentle pause. Think of one person who showed up for you recently, one comfort you enjoyed today, and one small capability you often take for granted. Let each one land for a full breath.",
            reflectionPrompt = "Which specific moment or person came to mind just now?"
        ),
        GuidedActivityDefinition(
            id = "act_brain_dump",
            title = "5-Minute Guided Brain Dump",
            subtitle = "Empty every open loop out of working memory",
            category = CoachingCategory.STRESS_MANAGEMENT,
            durationMinutes = 5,
            stepKind = RoutineStepKind.VOICE_REFLECTION,
            spokenScript = "When your head feels crowded, trying to organize thoughts mentally only adds pressure. Press record or start writing, and pour out every unfinished task, worry, and half-formed idea without judging the order. Once it is safely kept here, your mind can rest.",
            reflectionPrompt = "Pour out everything currently taking up room in your head."
        ),
        GuidedActivityDefinition(
            id = "act_values_check",
            title = "Values Clarification Exercise",
            subtitle = "Reconnect with what actually matters in this season",
            category = CoachingCategory.PERSONAL_GROWTH,
            durationMinutes = 5,
            stepKind = RoutineStepKind.QUIET_WRITE,
            spokenScript = "Pause and imagine looking back on this month from a year ahead. What three qualities do you want to remember bringing to your relationships, your work, and yourself?",
            reflectionPrompt = "Name 2–3 core values you want guiding your decisions this month, and one small way to live them today."
        ),
        GuidedActivityDefinition(
            id = "act_decision_10_10_10",
            title = "The 10-10-10 Decision Framework",
            subtitle = "Zoom out when a personal choice feels tangled",
            category = CoachingCategory.DECISION_MAKING,
            durationMinutes = 4,
            stepKind = RoutineStepKind.QUIET_WRITE,
            spokenScript = "When a decision feels heavy up close, widen the camera. Ask yourself: how will I feel about this choice in ten minutes, in ten months, and in ten years? Let your longer-term self speak calmly to the short-term noise.",
            reflectionPrompt = "What choice are you weighing? How does it look 10 minutes, 10 months, and 10 years from now?"
        ),
        GuidedActivityDefinition(
            id = "act_confidence_reset",
            title = "2-Minute Confidence Reset",
            subtitle = "Steady your voice before a big conversation",
            category = CoachingCategory.CONFIDENCE,
            durationMinutes = 2,
            stepKind = RoutineStepKind.COACHING_AUDIO,
            spokenScript = "Stand or sit tall and drop your shoulders. Confidence is not pretending you have every answer; it is trusting that you can stay honest, curious, and steady in the room. Remember one hard moment you have already navigated well."
        ),
        GuidedActivityDefinition(
            id = "act_discipline_friction",
            title = "Overcoming Resistance (2-Min Rule)",
            subtitle = "Break procrastination with kindness and tiny momentum",
            category = CoachingCategory.SELF_DISCIPLINE,
            durationMinutes = 2,
            stepKind = RoutineStepKind.COACHING_AUDIO,
            spokenScript = "Procrastination is rarely laziness—it is usually an oversized first step. Shrink what is in front of you until it takes two minutes. Open the document. Put on the shoes. Speak the first sentence. Momentum follows motion."
        ),
        GuidedActivityDefinition(
            id = "act_creative_spark",
            title = "Creative Curiosity Prompt",
            subtitle = "Unlock fresh connections in three minutes",
            category = CoachingCategory.CREATIVITY,
            durationMinutes = 3,
            stepKind = RoutineStepKind.VOICE_REFLECTION,
            spokenScript = "What caught your eye today that most people walked straight past? A phrase, a pattern, a problem worth solving? Capture it raw while it is still fresh.",
            reflectionPrompt = "Describe one intriguing detail, question, or idea you noticed today."
        ),
        GuidedActivityDefinition(
            id = "act_morning_intention",
            title = "Morning Intention Setting",
            subtitle = "Choose one anchor before the rush starts",
            category = CoachingCategory.MORNING_MOTIVATION,
            durationMinutes = 2,
            stepKind = RoutineStepKind.INTENTION,
            spokenScript = "Before your calendar decides your mood, choose one intention for today. Not a twenty-item checklist—just the posture and single focus you want to carry.",
            reflectionPrompt = "Today, I want to move through the day with..."
        ),
        GuidedActivityDefinition(
            id = "act_weekly_reset",
            title = "Guided Weekly Reset",
            subtitle = "Celebrate progress, release slips, and orient next week",
            category = CoachingCategory.PERSONAL_GROWTH,
            durationMinutes = 5,
            stepKind = RoutineStepKind.QUIET_WRITE,
            spokenScript = "Look back over the past seven days with the kindness you would offer a close friend. Name one win, one lesson, and the single priority that would make next week feel peaceful.",
            reflectionPrompt = "1) One win from this week · 2) One lesson kept · 3) Next week's main priority"
        )
    )

    fun activityById(id: String): GuidedActivityDefinition? = guidedActivities.firstOrNull { it.id == id }
}
