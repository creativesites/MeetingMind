package com.craftflowtechnologies.meetingmind.core.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NoteLinkKind
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Personal Vertical Phase 1 engine:
 * - Starter routine seeding & custom routine creation
 * - Minimum-effort ("low-energy day") scaling and resumable completion state
 * - Guided Journey enrollment with Day 1 "Why" Anchor Note and step completion
 * - Day-Page Journal Stitching (morning intention + evening routine reflection in one daily document)
 * - Private "Reflect in Personal Journal" bi-directional bridge from a Work note
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var noteRepo: NoteRepository
    private lateinit var repo: PersonalRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteRepo = NoteRepository(context, db)
        repo = PersonalRepository(context, db, noteRepo) { 1_763_720_000_000L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun catalogHasAllCuratedStarterContent() {
        assertEquals(12, PersonalGrowthCatalog.routineTemplates.size)
        assertEquals(14, PersonalGrowthCatalog.journeys.size)
        assertEquals(8, PersonalGrowthCatalog.challenges.size)
        assertTrue(PersonalGrowthCatalog.guidedActivities.size >= 12)
    }

    @Test
    fun starterRoutinesSeedOnceAndSupportMinEffortScaling() = runBlocking {
        repo.ensureStarterRoutinesSeeded()
        val seeded = repo.observeActiveRoutines().first()
        assertEquals(2, seeded.size)
        val morning = seeded.first { it.id == "routine_morning_reset" }
        assertTrue(morning.totalMinutes(minEffort = true) <= morning.totalMinutes(minEffort = false))

        // Resumable completion persists step progress
        val started = repo.startOrResumeRoutine(morning.id, minEffortMode = true, epochDay = 20410L)
        repo.saveRoutineProgress(started.copy(currentStepIndex = 2, completedStepIds = listOf("s1", "s2")))
        val resumed = repo.startOrResumeRoutine(morning.id, minEffortMode = false, epochDay = 20410L)
        assertEquals(started.id, resumed.id)
        assertEquals(2, resumed.currentStepIndex)
        assertTrue(resumed.usedMinEffortMode)
    }

    @Test
    fun dayPageStitchingCombinesMorningIntentionAndEveningReflectionIntoOneNote() = runBlocking {
        val day = 20410L
        val plan = repo.setTodayIntention("Stay unhurried and finish the design", epochDay = day)
        assertNotNull(plan.dayPageNoteId)

        val (eveningNoteId, _) = repo.stitchEntryIntoDayPage(
            epochDay = day,
            sectionHeading = "Evening Wind-Down",
            bodyText = "Finished the spec and went for a sunset walk."
        )
        assertEquals(plan.dayPageNoteId, eveningNoteId)

        val doc = noteRepo.getDocument(eveningNoteId)!!
        assertTrue(doc.note.isPrivate)
        assertTrue(doc.note.plainText.contains("Stay unhurried and finish the design"))
        assertTrue(doc.note.plainText.contains("sunset walk"))
    }

    @Test
    fun journeyEnrollmentCreatesWhyAnchorNoteAndAdvancesSteps() = runBlocking {
        val journey = PersonalGrowthCatalog.journeys.first()
        val enrollment = repo.startJourney(journey, userWhyText = "I want to remember my daughter growing up.", epochDay = 20410L)
        assertNotNull(enrollment.anchorWhyNoteId)
        val whyNote = noteRepo.getNote(enrollment.anchorWhyNoteId!!)
        assertTrue(whyNote!!.isPrivate)
        assertTrue(whyNote.plainText.contains("daughter"))

        val afterStep = repo.completeNextEnrollmentStep(
            enrollmentId = enrollment.id,
            usedMinEffort = false,
            reflectionText = "Spoke one honest sentence before coffee.",
            epochDay = 20410L
        )
        assertEquals(1, afterStep?.currentStepOrDay)
    }

    @Test
    fun privateReflectionBridgeLinksBackToWorkNoteAndStaysPrivate() = runBlocking {
        val workNote = noteRepo.createNote(
            workflow = RecordingType.MEETING,
            title = "Q4 Roadmap Review",
            isPrivate = false
        )
        val reflection = repo.createPrivateReflectionForNote(workNote.id)
        assertNotNull(reflection)
        assertTrue(reflection!!.isPrivate)
        assertEquals(RecordingType.JOURNAL, reflection.workflow)

        val links = noteRepo.getLinks(reflection.id)
        assertTrue(links.any { it.toNoteId == workNote.id && it.kind == NoteLinkKind.PERSONAL_REFLECTION })
    }

    @Test
    fun completingJourneyGeneratesGroundedFinaleAnthologyWithUsersOwnWords() = runBlocking {
        val custom = repo.startCustomChallenge(
            title = "3-Day Breathing Sprint",
            durationDays = 3,
            config = CustomChallengeConfig(
                dailyAction = "Take 3 deep breaths",
                minEffortAction = "1 calm breath",
                reflectionPrompt = "How did your chest feel?"
            ),
            epochDay = 20410L
        )
        repo.completeNextEnrollmentStep(custom.id, false, "Felt shoulders drop immediately", 20410L)
        repo.completeNextEnrollmentStep(custom.id, true, "Used gentle breath before meeting", 20411L)
        val finished = repo.completeNextEnrollmentStep(custom.id, false, "Much steadier by day three", 20412L)

        assertNotNull(finished)
        assertEquals(GrowthEnrollmentStatus.COMPLETED, finished!!.status)
        assertNotNull(finished.finaleNoteId)

        val finale = noteRepo.getNote(finished.finaleNoteId!!)
        assertNotNull(finale)
        assertTrue(finale!!.isPrivate)
        assertTrue(finale.plainText.contains("Felt shoulders drop immediately"))
        assertTrue(finale.plainText.contains("Much steadier by day three"))
    }

    @Test
    fun playbookToChallengeIdeaCollisionsAndLifeChaptersWorkOffline() = runBlocking {
        // 1. Playbook -> 7-day Challenge
        val playbook = noteRepo.createNote(
            workflow = RecordingType.LIFE_SKILLS,
            title = "Deep Work Without Burnout",
            initialBlocks = listOf(
                com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                    id = NoteRepository.newId("b"),
                    noteId = "",
                    position = 0,
                    type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.BULLET,
                    content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain("Silence notifications for the first 45 minutes each morning")
                )
            ),
            useTemplate = false
        )
        val challenge = repo.startChallengeFromPlaybook(playbook, durationDays = 7, epochDay = 20410L)
        assertEquals(7, challenge.targetStepsOrDays)
        val cfg = CustomChallengeConfig.fromJson(challenge.customConfigJson)
        assertNotNull(cfg)
        assertTrue(cfg!!.dailyAction.contains("Silence notifications"))

        // 2. Idea Collision Engine
        val ideaA = repo.captureQuickSpark("Builder Dinner Series", "Host monthly dinner conversations with founders and designers")
        val ideaB = repo.captureQuickSpark("Monthly Founders Salon", "Small dinner table for founders to share creative prototypes")
        val collision = PersonalGrowthIntelligence.findBestIdeaCollision(listOf(ideaA, ideaB))
        assertNotNull(collision)
        assertTrue(collision!!.sharedThemes.any { it.equals("Dinner", ignoreCase = true) || it.equals("Founders", ignoreCase = true) })

        // 3. Graduate Idea -> Life Chapter
        val chapter = repo.graduateIdeaToLifeChapter(ideaA.id)
        assertNotNull(chapter)
        assertEquals("Builder Dinner Series", chapter!!.title)
        assertEquals(1, repo.observeLifeChapters().first().size)
    }

    @Test
    fun editAndArchiveRoutineUpdatesFieldsAndHidesFromActiveList() = runBlocking {
        val created = repo.saveCustomRoutine(
            title = "Afternoon Reset",
            whyDescription = "Protect late-day focus",
            timeWindow = RoutineTimeWindow.AFTERNOON,
            anchorCue = "After lunch",
            soundscape = PersonalSoundscape.FOREST,
            steps = listOf(
                RoutineStep(id = "", routineId = "", position = 0, kind = RoutineStepKind.BREATHING, title = "Breathe")
            ),
            daysOfWeekMask = 31, // Mon-Fri
            reminderEnabled = true,
            scheduledMinuteOfDay = 14 * 60
        )
        val edited = repo.saveCustomRoutine(
            title = "Deep Afternoon Reset",
            whyDescription = "Protect late-day focus",
            timeWindow = RoutineTimeWindow.AFTERNOON,
            anchorCue = "After lunch walk",
            soundscape = PersonalSoundscape.RAIN,
            steps = listOf(
                RoutineStep(id = "", routineId = "", position = 0, kind = RoutineStepKind.BREATHING, title = "Breathe"),
                RoutineStep(id = "", routineId = "", position = 1, kind = RoutineStepKind.QUIET_WRITE, title = "Write 1 line")
            ),
            existingId = created.id,
            daysOfWeekMask = 31,
            reminderEnabled = true,
            scheduledMinuteOfDay = 14 * 60 + 30
        )
        assertEquals(created.id, edited.id)
        assertEquals(PersonalSoundscape.RAIN, edited.soundscape)
        assertEquals(2, edited.steps.size)
        assertEquals(14 * 60 + 30, edited.scheduledMinuteOfDay)

        repo.archiveRoutine(edited.id)
        assertTrue(repo.observeActiveRoutines().first().none { it.id == edited.id })
    }

    @Test
    fun enrollmentSupportsPauseResumeAndChapterNotesManagement() = runBlocking {
        val journey = PersonalGrowthCatalog.journeys.first()
        val enrolled = repo.startJourney(journey, userWhyText = "Clarity", epochDay = 20410L)
        val paused = repo.updateEnrollmentStatus(enrolled.id, GrowthEnrollmentStatus.PAUSED)
        assertEquals(GrowthEnrollmentStatus.PAUSED, paused?.status)
        val resumed = repo.updateEnrollmentStatus(enrolled.id, GrowthEnrollmentStatus.ACTIVE)
        assertEquals(GrowthEnrollmentStatus.ACTIVE, resumed?.status)

        val chapter = repo.createLifeChapter("Moving to Lisbon", "A fresh season")
        val spark = repo.captureQuickSpark("Neighborhood cafes", "List of quiet morning cafes")
        repo.assignNoteToChapter(spark.id, chapter.id)
        assertEquals(1, repo.observeNotesInChapter(chapter.id).first().size)

        repo.archiveLifeChapter(chapter.id)
        assertTrue(repo.observeLifeChapters().first().none { it.id == chapter.id })
    }

    @Test
    fun linkedStoryThreadsAreExcludedFromFutureIdeaCollisions() = runBlocking {
        val a = repo.captureQuickSpark("Builder Dinner Series", "Host monthly dinner conversations with founders and designers")
        val b = repo.captureQuickSpark("Monthly Founders Salon", "Small dinner table for founders to share creative prototypes")
        assertNotNull(PersonalGrowthIntelligence.findBestIdeaCollision(listOf(a, b)))

        repo.connectIdeasIntoThread(a.id, b.id)
        val linked = repo.getStoryThreadLinkedPairs()
        assertTrue(PersonalGrowthIntelligence.canonicalPair(a.id, b.id) in linked)
        assertEquals(null, PersonalGrowthIntelligence.findBestIdeaCollision(listOf(a, b), excludedPairs = linked))
    }

    @Test
    fun replyToPastMemoryCreatesPrivateStoryThreadNote() = runBlocking {
        val past = noteRepo.createNote(
            workflow = RecordingType.JOURNAL,
            title = "Letter from 2025",
            isPrivate = true,
            useTemplate = false
        )
        val reply = repo.replyToPastMemory(past.id, "Reading this a year later makes me so grateful we kept going.")
        assertNotNull(reply)
        assertTrue(reply!!.isPrivate)
        assertEquals(RecordingType.JOURNAL, reply.workflow)
        assertTrue(reply.plainText.contains("grateful we kept going"))

        val links = noteRepo.getLinks(reply.id)
        assertTrue(links.any { it.toNoteId == past.id && it.kind == NoteLinkKind.STORY_THREAD })
    }

    @Test
    fun innerCirclePeopleAndGiftMentionRadarSurfaceJournalLines() = runBlocking {
        val person = repo.addInnerCirclePerson("Sarah", "Sister", "Loves ceramics and oat lattes")
        assertEquals("PERSONAL", person.space)
        val people = repo.observeInnerCirclePeople().first()
        assertEquals(1, people.size)

        val (_, _) = repo.stitchEntryIntoDayPage(
            epochDay = 20410L,
            sectionHeading = "Afternoon Walk",
            bodyText = "Coffee with Sarah today — she mentioned wanting a handmade ceramic pour-over dripper."
        )
        val journals = repo.observeJournalNotes().first()
        val mentions = repo.findMentionsForPerson("Sarah", journals)
        assertEquals(1, mentions.size)
        assertTrue(mentions.first().second.contains("ceramic pour-over"))
    }

    @Test
    fun computeLiveSpaceGlanceReflectsActiveRoutinesAndPlan() = runBlocking {
        repo.ensureStarterRoutinesSeeded()
        val glance = repo.computeLiveSpaceGlance()
        assertTrue(glance.isNotBlank())
    }

    @Test
    fun obsidianMarkdownRendererAndDayOneJsonImporterWorkOffline() = runBlocking {
        val sample = noteRepo.createNote(
            workflow = RecordingType.JOURNAL,
            title = "Quiet Sunday Walk",
            isPrivate = true,
            metadata = mapOf(PersonalNoteMeta.MOOD_VIBE to JournalMoodVibe.GRATEFUL.name),
            initialBlocks = listOf(
                com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                    id = NoteRepository.newId("b"),
                    noteId = "",
                    position = 0,
                    type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH,
                    content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain("Felt calm walking by the sea.")
                )
            ),
            useTemplate = false
        )
        val doc = noteRepo.getDocument(sample.id)!!
        val md = PersonalVaultIntegrations.renderObsidianMarkdown(
            note = doc.note,
            blocks = doc.blocks,
            linkedTitles = listOf("Coastal Mornings"),
            chapterTitle = "Cape Town Season"
        )
        assertTrue(md.contains("space: personal"))
        assertTrue(md.contains("mood: grateful"))
        assertTrue(md.contains("chapter: \"Cape Town Season\""))
        assertTrue(md.contains("[[Coastal Mornings]]"))
        assertTrue(PersonalVaultIntegrations.obsidianFileName(doc.note).endsWith(".md"))

        val dayOneJson = """
            {
              "entries": [
                {
                  "text": "# Rainy Morning in Kyoto\nWalked through the cedar path before sunrise.",
                  "creationDate": "2024-05-10T06:30:00Z",
                  "tags": ["travel", "gratitude"],
                  "location": { "placeName": "Philosopher's Path", "localityName": "Kyoto" }
                }
              ]
            }
        """.trimIndent()
        val parsed = PersonalVaultIntegrations.parseDayOneJson(dayOneJson)
        assertEquals(1, parsed.size)
        assertEquals("Rainy Morning in Kyoto", parsed.first().title)
        assertEquals("Philosopher's Path, Kyoto", parsed.first().locationLabel)

        val importedCount = PersonalVaultIntegrations.importDayOneEntries(noteRepo, dayOneJson)
        assertEquals(1, importedCount)
        val allJournals = repo.observeJournalNotes().first()
        assertTrue(allJournals.any { it.title == "Rainy Morning in Kyoto" && it.isPrivate })
    }
}
