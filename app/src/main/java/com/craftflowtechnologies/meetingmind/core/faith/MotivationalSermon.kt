package com.craftflowtechnologies.meetingmind.core.faith

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

/**
 * A structured, uplifting motivational message and sermonette.
 * Designed to be read in 60-90 seconds and shared as high-impact 9:16 stories.
 */
data class MotivationalSermon(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val theme: String,
    val scriptureReference: String,
    val scriptureText: String,
    val keyQuote: String,
    val points: List<String>,
    val declaration: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Converts this motivational sermon into a 9:16 vertical Story share request.
     */
    fun toStoryShareRequest(): ShareRequest {
        return ShareRequest(
            content = ShareCardContent(
                eyebrow = title,
                text = keyQuote,
                reference = scriptureReference,
                attribution = "MeetingMind Daily Inspiration",
                quoted = true
            ),
            theme = "$theme, $scriptureReference, $title",
            caption = "$title\n\n“$keyQuote”\n\n— $scriptureReference\n\nDaily Declaration:\n$declaration"
        )
    }
}

/**
 * Standard inspirational themes supported for instant generation.
 */
enum class SermonTheme(val label: String, val promptHint: String) {
    HOPE("Hope & Renewal", "hope in difficult seasons and spiritual renewal"),
    STRENGTH("Strength & Courage", "overcoming adversity with unwavering courage"),
    PEACE("Peace Over Anxiety", "finding divine peace in the midst of turmoil"),
    PURPOSE("Purpose & Calling", "discovering your divine assignment and walking boldly"),
    FAITH("Faith Over Fear", "trusting God beyond visible circumstances"),
    GRACE("Grace for the Grind", "grace, patience, and perseverance in daily work"),
    VICTORY("Overcoming & Victory", "triumphing over doubt, fear, and past setbacks"),
    GRATITUDE("Joy & Gratitude", "living from a posture of thankfulness and praise")
}

/**
 * Generator that creates motivational sermons via Gemini AI or a rich curated catalog.
 */
class MotivationalSermonGenerator(private val context: Context) {

    /**
     * Curated offline library ensuring instant, reliable inspiration even without connectivity.
     */
    private val curatedSermons = listOf(
        MotivationalSermon(
            title = "Unshakeable in the Storm",
            theme = SermonTheme.HOPE.label,
            scriptureReference = "Isaiah 40:31",
            scriptureText = "Those who wait on the LORD shall renew their strength; they shall mount up with wings like eagles.",
            keyQuote = "Your current struggle is not your final destination. Waiting on God is never wasted time—it is divine renewal.",
            points = listOf(
                "Shift your gaze from the wind to the One who commands the storm.",
                "Patience is not passive waiting; it is expectant trust in His timing.",
                "When you feel exhausted, heaven's strength is already being dispatched to you."
            ),
            declaration = "Today, I exchange my exhaustion for God's infinite strength. I will soar above every obstacle."
        ),
        MotivationalSermon(
            title = "Courage to Take the Step",
            theme = SermonTheme.STRENGTH.label,
            scriptureReference = "Joshua 1:9",
            scriptureText = "Be strong and of good courage; do not be afraid, nor be dismayed, for the LORD your God is with you wherever you go.",
            keyQuote = "Courage is not the absence of fear; it is the holy conviction that God's presence is greater than any challenge ahead.",
            points = listOf(
                "You do not walk into tomorrow alone—God is already standing in your future.",
                "Break out of hesitation; faith moves forward even when trembling.",
                "Every giant in your way is an invitation to witness God's deliverance."
            ),
            declaration = "I step forward boldly today, anchored by the truth that God is with me in every room I enter."
        ),
        MotivationalSermon(
            title = "The Peace That Transcends",
            theme = SermonTheme.PEACE.label,
            scriptureReference = "Philippians 4:6-7",
            scriptureText = "Be anxious for nothing, but in everything by prayer and supplication, with thanksgiving, let your requests be made known to God.",
            keyQuote = "Peace does not mean a life free of noise; it means having a quiet, guarded heart in the center of the storm.",
            points = listOf(
                "Trade your worries for prayers; anxiety cannot survive in an atmosphere of thanksgiving.",
                "Guard your mind against worst-case narratives by fixing your focus on His faithfulness.",
                "God's peace is not an emotion; it is an active garrison protecting your soul."
            ),
            declaration = "I cast all my cares upon the Lord today. His supernatural peace guards my heart and mind."
        ),
        MotivationalSermon(
            title = "Walking in Your Assignment",
            theme = SermonTheme.PURPOSE.label,
            scriptureReference = "Jeremiah 29:11",
            scriptureText = "For I know the plans I have for you, declares the LORD, plans to prosper you and not to harm you, plans to give you hope and a future.",
            keyQuote = "You were not created to simply survive the day; you were crafted intentionally for a purpose that outlasts your doubts.",
            points = listOf(
                "Your gifts are not accidental—they are tools tailored for your calling.",
                "Never let temporary delays trick you into believing God has changed His mind about you.",
                "Small acts of faithfulness today unlock the great doors of tomorrow."
            ),
            declaration = "I am aligned with God's purpose. My steps are ordered, and my future is filled with divine hope."
        ),
        MotivationalSermon(
            title = "Faith Beyond What You See",
            theme = SermonTheme.FAITH.label,
            scriptureReference = "2 Corinthians 5:7",
            scriptureText = "For we walk by faith, not by sight.",
            keyQuote = "Sight counts the odds; faith counts on the God who created the universe. Trust what He spoke over what you see.",
            points = listOf(
                "Circumstances report the facts, but God's word declares the ultimate truth.",
                "Step out in obedience even when the entire path isn't lit yet.",
                "A mustard seed of real faith outweighs a mountain of human despair."
            ),
            declaration = "I choose faith over fear today. I walk by faith and expect God's goodness in every situation."
        ),
        MotivationalSermon(
            title = "Grace for the Daily Grind",
            theme = SermonTheme.GRACE.label,
            scriptureReference = "2 Corinthians 12:9",
            scriptureText = "My grace is sufficient for you, for My strength is made perfect in weakness.",
            keyQuote = "You don't need to have it all together. God does His most magnificent work through human weakness surrendered to Him.",
            points = listOf(
                "Release the burden of perfection; His grace covers your shortcomings.",
                "Show yourself the same patience and compassion God extends to you every morning.",
                "When your own reserves run dry, divine sufficiency begins."
            ),
            declaration = "God's grace is sufficient for me today. His strength carries me through every task and meeting."
        ),
        MotivationalSermon(
            title = "More Than Conquerors",
            theme = SermonTheme.VICTORY.label,
            scriptureReference = "Romans 8:37",
            scriptureText = "Yet in all these things we are more than conquerors through Him who loved us.",
            keyQuote = "The battle you are facing is not fought to achieve victory, but from a posture of victory already won at the Cross.",
            points = listOf(
                "No setback has the power to cancel the destiny God wrote for you.",
                "Hold your head high; the Lord is fighting for you while you stay steadfast.",
                "Every test is becoming your testimony."
            ),
            declaration = "I am more than a conqueror through Christ. No weapon formed against my peace or purpose will prosper."
        ),
        MotivationalSermon(
            title = "The Power of a Thankful Heart",
            theme = SermonTheme.GRATITUDE.label,
            scriptureReference = "Psalm 100:4",
            scriptureText = "Enter into His gates with thanksgiving, and into His courts with praise. Be thankful to Him, and bless His name.",
            keyQuote = "Gratitude unlocks the fullness of life. It turns what we have into enough, and transforms ordinary days into sacred moments.",
            points = listOf(
                "Gratitude shifts your perspective from what is lacking to what is abundantly provided.",
                "A thankful spirit is the greatest antidote to bitterness and weariness.",
                "Celebrate the small wins today—they are evidence of God's quiet faithfulness."
            ),
            declaration = "My heart overflows with gratitude today. I will praise the Lord for His goodness in every moment."
        )
    )

    /**
     * Checks if Gemini cloud generation is active and available.
     */
    suspend fun isAiAvailable(): Boolean {
        return UserPreferencesManager(context).preferencesFlow.first().processingProfile == ProcessingProfile.INTERNET &&
                GeminiCredentialStore(context).getApiKey() != null
    }

    /**
     * Generates a motivational sermon for the requested theme or custom topic.
     * Uses Gemini when available, or instantly returns an inspirational curated sermon.
     */
    suspend fun generate(themeText: String): MotivationalSermon {
        if (isAiAvailable()) {
            val aiResult = generateFromGemini(themeText)
            if (aiResult is AiResult.Success) {
                return aiResult.value
            }
        }
        return getCuratedOrFallback(themeText)
    }

    private suspend fun generateFromGemini(topic: String): AiResult<MotivationalSermon> {
        val prompt = """
            You are a pastoral, inspiring motivational communicator. Write an uplifting, high-impact mini-sermon / motivational message.
            Topic or need: "$topic"
            
            Return ONLY a valid JSON object with the following fields:
            {
              "title": "Short, powerful title (3-5 words)",
              "theme": "$topic",
              "scriptureReference": "Book Chapter:Verse (e.g. Joshua 1:9)",
              "scriptureText": "The actual verse text",
              "keyQuote": "A punchy, memorable 1-2 sentence quote designed for a social media Story or status card",
              "points": [
                "1. First inspiring takeaway (1 sentence)",
                "2. Second inspiring takeaway (1 sentence)",
                "3. Third inspiring takeaway (1 sentence)"
              ],
              "declaration": "A 1-sentence first-person declaration/prayer of faith"
            }
            Do not include Markdown backticks or any other text outside the JSON.
        """.trimIndent()

        val transport = GeminiHttpTransport(GeminiCredentialStore(context))
        val response = transport.execute(
            GeminiRequest(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = "You are an inspiring, uplifting pastoral communicator.",
                prompt = prompt,
                timeoutMs = 45_000L
            )
        )

        return when (response) {
            is AiResult.Success -> {
                try {
                    val rawJson = response.value.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val obj = JSONObject(rawJson)
                    val pointsArray = obj.getJSONArray("points")
                    val pointsList = mutableListOf<String>()
                    for (i in 0 until pointsArray.length()) {
                        pointsList.add(pointsArray.getString(i))
                    }
                    AiResult.Success(
                        MotivationalSermon(
                            title = obj.getString("title"),
                            theme = topic,
                            scriptureReference = obj.getString("scriptureReference"),
                            scriptureText = obj.getString("scriptureText"),
                            keyQuote = obj.getString("keyQuote"),
                            points = pointsList,
                            declaration = obj.getString("declaration")
                        )
                    )
                } catch (e: Exception) {
                    AiResult.Failed("Parse failure: ${e.message}")
                }
            }
            is AiResult.Failed -> AiResult.Failed(response.message)
            is AiResult.ModelUnavailable -> AiResult.ModelUnavailable(response.modelId, response.message)
            is AiResult.DeviceUnsupported -> AiResult.DeviceUnsupported(response.reason)
            is AiResult.InsufficientMemory -> AiResult.InsufficientMemory(response.requiredMb, response.availableMb)
            is AiResult.InsufficientStorage -> AiResult.InsufficientStorage(response.requiredMb, response.availableMb)
        }
    }

    /**
     * Finds a matching curated sermon or picks a relevant one.
     */
    fun getCuratedOrFallback(themeText: String): MotivationalSermon {
        val lower = themeText.lowercase()
        val match = curatedSermons.firstOrNull { sermon ->
            lower.contains(sermon.theme.lowercase()) ||
            sermon.title.lowercase().contains(lower) ||
            sermon.points.any { it.lowercase().contains(lower) }
        }
        return match ?: curatedSermons.random()
    }

    fun allCurated(): List<MotivationalSermon> = curatedSermons
}
