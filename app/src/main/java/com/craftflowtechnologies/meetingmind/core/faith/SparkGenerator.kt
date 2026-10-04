package com.craftflowtechnologies.meetingmind.core.faith

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

/**
 * Generates relatable, deep, funny, fire, and authentic shareable content.
 * Supports online generation via Gemini with strict scripture validation,
 * as well as a rich offline curated catalog (40+ shareable gems).
 */
class SparkGenerator(
    private val context: Context,
    private val scriptureService: ScriptureService = ScriptureService(context)
) {

    suspend fun isAiAvailable(): Boolean {
        return runCatching {
            UserPreferencesManager(context).preferencesFlow.first().processingProfile == ProcessingProfile.INTERNET &&
                    GeminiCredentialStore(context).getApiKey() != null
        }.getOrDefault(false)
    }

    suspend fun generate(
        vibe: SparkVibe,
        format: SparkFormat,
        faithLevel: FaithLevel,
        moment: SparkMoment? = null,
        customTopic: String? = null
    ): List<SparkPiece> {
        if (isAiAvailable()) {
            val aiResult = generateWithGemini(vibe, format, faithLevel, moment, customTopic)
            if (aiResult is AiResult.Success && aiResult.value.isNotEmpty()) {
                return aiResult.value
            }
        }
        return getOfflineCurated(vibe, format, faithLevel, moment, customTopic)
    }

    suspend fun remix(piece: SparkPiece, action: RemixAction): SparkPiece {
        if (isAiAvailable()) {
            val result = remixWithGemini(piece, action)
            if (result is AiResult.Success) {
                return result.value
            }
        }
        return offlineRemixFallback(piece, action)
    }

    private suspend fun generateWithGemini(
        vibe: SparkVibe,
        format: SparkFormat,
        faithLevel: FaithLevel,
        moment: SparkMoment?,
        customTopic: String?
    ): AiResult<List<SparkPiece>> {
        val topicContext = buildString {
            if (!customTopic.isNullOrBlank()) append("Topic/Need: \"$customTopic\". ")
            if (moment != null) append("Context/Moment: ${moment.label} (${moment.promptHint}). ")
        }

        val faithGuideline = when (faithLevel) {
            FaithLevel.NONE -> "NO RELIGIOUS WORDS OR SCRIPTURE. Write universal human wisdom, sharp humor, psychological depth or high-agency truth. Do NOT include verseRef or verseText."
            FaithLevel.SUBTLE -> "Grounded in timeless spiritual wisdom, but keep tone relatable, honest and modern. If a verse fits naturally, provide verseRef (e.g. Proverbs 16:3); if not, leave null."
            FaithLevel.CLEAR -> "Include a powerful, complementary Scripture reference (verseRef, e.g. Isaiah 40:31) and actual verse text (verseText) that anchors the message."
        }

        val prompt = """
            You are a sharp, creative, modern writer known for creating viral, deeply relatable, shareable quotes and observations for social media status & stories.
            Tone / Vibe: ${vibe.label} (${vibe.description}).
            Format: ${format.label} (${format.promptDescription}).
            $topicContext
            Faith Guideline: $faithGuideline

            Rules:
            1. Never be preachy, condescending, or cliché. Avoid dry church jargon or guilt trips.
            2. For Funny: Use dry irony, relatable daily observations, self-awareness, clean wit.
            3. For Fire: Unapologetic high agency, discipline, push through excuses.
            4. For Deep: Perspective shifts that make people re-read and reflect.
            5. Return exactly 3 distinct, fresh variations.

            Return ONLY a valid JSON object matching this schema:
            {
              "pieces": [
                {
                  "text": "The main shareable quote/story/takeaway",
                  "verseRef": "Book Chapter:Verse or null",
                  "verseText": "Exact verse text or null"
                },
                {
                  "text": "Variation 2",
                  "verseRef": "Book Chapter:Verse or null",
                  "verseText": "Exact verse text or null"
                },
                {
                  "text": "Variation 3",
                  "verseRef": "Book Chapter:Verse or null",
                  "verseText": "Exact verse text or null"
                }
              ]
            }
            Do not output Markdown fences or additional text.
        """.trimIndent()

        val transport = GeminiHttpTransport(GeminiCredentialStore(context))
        val response = transport.execute(
            GeminiRequest(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = "You write modern, punchy, shareable insights and reflections.",
                prompt = prompt,
                timeoutMs = 40_000L
            )
        )

        return when (response) {
            is AiResult.Success -> {
                try {
                    val rawJson = response.value.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val obj = JSONObject(rawJson)
                    val array = obj.getJSONArray("pieces")
                    val results = mutableListOf<SparkPiece>()

                    for (i in 0 until minOf(3, array.length())) {
                        val item = array.getJSONObject(i)
                        val text = item.getString("text").trim()
                        val rawRef = if (item.has("verseRef") && !item.isNull("verseRef")) item.getString("verseRef").trim() else null
                        var validatedRef: String? = null
                        var validatedText: String? = null

                        if (!rawRef.isNullOrBlank() && faithLevel != FaithLevel.NONE) {
                            val parsed = ScriptureReferenceParser.parse(rawRef)
                            if (parsed != null) {
                                val passage = scriptureService.passage(parsed)
                                if (passage is PassageResult.Found) {
                                    validatedRef = parsed.display()
                                    validatedText = passage.passage.text
                                }
                            }
                        }

                        results.add(
                            SparkPiece(
                                text = text,
                                verseRef = validatedRef,
                                verseText = validatedText,
                                vibe = vibe,
                                format = format
                            )
                        )
                    }

                    if (results.isNotEmpty()) AiResult.Success(results)
                    else AiResult.Failed("No pieces in model response")
                } catch (e: Exception) {
                    AiResult.Failed("JSON parse error: ${e.message}")
                }
            }
            is AiResult.Failed -> AiResult.Failed(response.message)
            is AiResult.ModelUnavailable -> AiResult.ModelUnavailable(response.modelId, response.message)
            is AiResult.DeviceUnsupported -> AiResult.DeviceUnsupported(response.reason)
            is AiResult.InsufficientMemory -> AiResult.InsufficientMemory(response.requiredMb, response.availableMb)
            is AiResult.InsufficientStorage -> AiResult.InsufficientStorage(response.requiredMb, response.availableMb)
        }
    }

    private suspend fun remixWithGemini(piece: SparkPiece, action: RemixAction): AiResult<SparkPiece> {
        val prompt = """
            Take this existing shareable quote/post:
            "${piece.text}"
            
            Current vibe: ${piece.vibe.label}
            Current format: ${piece.format.label}
            
            Remix instruction: ${action.instruction}
            
            Return ONLY a valid JSON object:
            {
              "text": "The remixed version",
              "verseRef": ${if (action == RemixAction.REMOVE_VERSE) "null" else if (piece.verseRef != null) "\"${piece.verseRef}\"" else "null"}
            }
        """.trimIndent()

        val transport = GeminiHttpTransport(GeminiCredentialStore(context))
        val response = transport.execute(
            GeminiRequest(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = "You remix and improve short shareable quotes.",
                prompt = prompt,
                timeoutMs = 30_000L
            )
        )

        return when (response) {
            is AiResult.Success -> {
                try {
                    val raw = response.value.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val obj = JSONObject(raw)
                    val newText = obj.getString("text").trim()
                    val ref = if (obj.has("verseRef") && !obj.isNull("verseRef")) obj.getString("verseRef") else null
                    AiResult.Success(
                        piece.withTextUpdate(newText).copy(
                            verseRef = if (action == RemixAction.REMOVE_VERSE) null else (ref ?: piece.verseRef),
                            verseText = if (action == RemixAction.REMOVE_VERSE) null else piece.verseText
                        )
                    )
                } catch (e: Exception) {
                    AiResult.Failed("Remix parse error: ${e.message}")
                }
            }
            else -> AiResult.Failed("Remix unavailable")
        }
    }

    private fun offlineRemixFallback(piece: SparkPiece, action: RemixAction): SparkPiece {
        val remixed = when (action) {
            RemixAction.SHORTER -> piece.text.substringBefore("—").substringBefore(";").trim()
            RemixAction.BOLDER -> piece.text.replace("Maybe", "Stop waiting:").replace("try to", "must")
            RemixAction.FUNNIER -> "${piece.text} (And yes, I needed to hear this too)."
            RemixAction.CALMER -> "Take a breath. ${piece.text}"
            RemixAction.DEEPER -> "Real shift: ${piece.text}"
            RemixAction.REMOVE_VERSE -> return piece.withTextUpdate(piece.text).copy(verseRef = null, verseText = null)
            RemixAction.ADD_VERSE -> return piece.withTextUpdate(piece.text).copy(verseRef = "Proverbs 3:5-6", verseText = "Trust in the Lord with all your heart, and lean not on your own understanding.")
        }
        return piece.withTextUpdate(remixed)
    }

    fun getOfflineCurated(
        vibe: SparkVibe,
        format: SparkFormat,
        faithLevel: FaithLevel,
        moment: SparkMoment? = null,
        customTopic: String? = null
    ): List<SparkPiece> {
        val pool = curatedCatalog.filter { item ->
            val vibeMatch = item.vibe == vibe
            val faithMatch = when (faithLevel) {
                FaithLevel.NONE -> item.verseRef == null
                FaithLevel.CLEAR -> item.verseRef != null
                FaithLevel.SUBTLE -> true
            }
            vibeMatch && faithMatch
        }

        val candidates = if (pool.size >= 3) pool else curatedCatalog.filter {
            if (faithLevel == FaithLevel.NONE) it.verseRef == null else true
        }

        return candidates.shuffled().take(3).map {
            it.copy(id = UUID.randomUUID().toString())
        }
    }

    companion object {
        val curatedCatalog = listOf(
            // FUNNY & WITTY
            SparkPiece(
                text = "I prayed for patience, and God immediately gave me a slow cashier, a traffic jam, and a 1% battery warning.",
                verseRef = "James 1:3-4",
                verseText = "The testing of your faith produces perseverance.",
                vibe = SparkVibe.FUNNY,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "You think you're exhausted from hard work. Actually, you're exhausted from keeping 47 tabs open in your brain about conversations that haven't even happened.",
                vibe = SparkVibe.FUNNY,
                format = SparkFormat.PERSPECTIVE_FLIP
            ),
            SparkPiece(
                text = "God, please give me the confidence of a middle schooler who just learned what sarcasm is.",
                vibe = SparkVibe.FUNNY,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Reminder: Your phone has 'Do Not Disturb' mode. So does your peace of mind. Use it.",
                vibe = SparkVibe.FUNNY,
                format = SparkFormat.HOT_TAKE
            ),
            SparkPiece(
                text = "You don't have to attend every fight you're invited to. Some invitations are best left on read.",
                verseRef = "Proverbs 17:14",
                verseText = "Starting a quarrel is like breaching a dam; so drop the matter before a dispute breaks out.",
                vibe = SparkVibe.FUNNY,
                format = SparkFormat.ONE_LINER
            ),

            // FIRE & DRIVE
            SparkPiece(
                text = "Nobody is coming to do your pushups for you. Discomfort today is the interest you pay on tomorrow’s freedom.",
                vibe = SparkVibe.FIRE,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Stop asking for lighter loads. Ask for broader shoulders and stronger discipline.",
                verseRef = "Joshua 1:9",
                verseText = "Be strong and courageous. Do not be afraid; do not be discouraged.",
                vibe = SparkVibe.FIRE,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "You don't lack motivation. You lack momentum. Do one hard thing for 120 seconds, and watch the paralysis vanish.",
                vibe = SparkVibe.FIRE,
                format = SparkFormat.HOT_TAKE
            ),
            SparkPiece(
                text = "The giant in your way isn't there to stop you. It was put there to show everyone what God is capable of doing through you.",
                verseRef = "1 Samuel 17:47",
                verseText = "The battle is the Lord’s.",
                vibe = SparkVibe.FIRE,
                format = SparkFormat.PERSPECTIVE_FLIP
            ),
            SparkPiece(
                text = "A year from now you will wish you had started today. Kill hesitation before it kills your potential.",
                vibe = SparkVibe.FIRE,
                format = SparkFormat.ONE_LINER
            ),

            // DEEP & THOUGHTFUL
            SparkPiece(
                text = "You thought the delay was a denial. Actually, seasons of silence aren't empty—they're where your roots grow deep enough to hold what's coming next.",
                verseRef = "Isaiah 40:31",
                verseText = "Those who wait on the Lord shall renew their strength.",
                vibe = SparkVibe.DEEP,
                format = SparkFormat.PERSPECTIVE_FLIP
            ),
            SparkPiece(
                text = "Be careful what you pray for God to remove. Sometimes the storm you're fighting is the very thing steering you away from disaster.",
                vibe = SparkVibe.DEEP,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "We judge ourselves by our intentions, but the world experiences us through our attention. Give the people you love your presence, not your leftover battery.",
                vibe = SparkVibe.DEEP,
                format = SparkFormat.HOT_TAKE
            ),
            SparkPiece(
                text = "The seed doesn't know it's being planted; it only knows it's being buried in the dark. Trust the hand that placed you here.",
                verseRef = "John 12:24",
                verseText = "Unless a grain of wheat falls into the earth and dies, it remains alone; but if it dies, it bears much fruit.",
                vibe = SparkVibe.DEEP,
                format = SparkFormat.MINI_STORY
            ),
            SparkPiece(
                text = "Quiet confidence doesn't need to win every argument in the room. True strength rests in knowing who you are when the door closes.",
                vibe = SparkVibe.DEEP,
                format = SparkFormat.ONE_LINER
            ),

            // REAL & RAW
            SparkPiece(
                text = "You don't need a five-year plan for today. You just need to make the next faithful choice right in front of you.",
                verseRef = "Matthew 6:34",
                verseText = "Do not worry about tomorrow, for tomorrow will worry about itself.",
                vibe = SparkVibe.REAL,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "It is okay to be a masterpiece and a work in progress at the exact same time. Stop apologizing for taking up space while you heal.",
                vibe = SparkVibe.REAL,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "The bravest sentence you can say this week is: 'I don't have this all figured out, but I'm still showing up.'",
                vibe = SparkVibe.REAL,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "You survived every single bad day that felt like the end of the world. Your track record of getting back up is 100%.",
                vibe = SparkVibe.REAL,
                format = SparkFormat.HOT_TAKE
            ),

            // CALM & PEACE
            SparkPiece(
                text = "Peace is not the absence of deadlines; it's remembering whose hands hold the clock.",
                verseRef = "Philippians 4:7",
                verseText = "And the peace of God, which transcends all understanding, will guard your hearts and your minds.",
                vibe = SparkVibe.CALM,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Unclench your jaw. Drop your shoulders from your ears. Take a full breath. God has not abdicated the throne.",
                vibe = SparkVibe.CALM,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Not everything that calls for your attention deserves your anxiety. Let what is outside your control rest.",
                vibe = SparkVibe.CALM,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "He leads beside still waters not because the river stops moving, but because the shepherd is between you and the current.",
                verseRef = "Psalm 23:2",
                verseText = "He leads me beside quiet waters.",
                vibe = SparkVibe.CALM,
                format = SparkFormat.VERSE_TWIST
            ),

            // JOY & GRATITUDE
            SparkPiece(
                text = "Don't let the things you are still waiting for blind you to the prayers you are currently standing inside of.",
                verseRef = "Psalm 103:2",
                verseText = "Praise the Lord, my soul, and forget not all his benefits.",
                vibe = SparkVibe.GRATEFUL,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Celebrate the quiet wins nobody clapped for: the temper you kept, the boundary you honored, the morning you got back up.",
                vibe = SparkVibe.GRATEFUL,
                format = SparkFormat.HOT_TAKE
            ),
            SparkPiece(
                text = "Gratitude doesn't just change your day—it rewires what your eyes are capable of noticing.",
                vibe = SparkVibe.GRATEFUL,
                format = SparkFormat.ONE_LINER
            ),
            SparkPiece(
                text = "Waking up with breath in your lungs is God saying: 'I still have work for you to do today.'",
                verseRef = "Lamentations 3:22-23",
                verseText = "His compassions never fail; they are new every morning.",
                vibe = SparkVibe.GRATEFUL,
                format = SparkFormat.ONE_LINER
            )
        )
    }
}
