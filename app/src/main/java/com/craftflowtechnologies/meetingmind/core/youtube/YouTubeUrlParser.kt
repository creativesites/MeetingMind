package com.craftflowtechnologies.meetingmind.core.youtube

/**
 * Pure-Kotlin YouTube URL recognizer and normalizer.
 *
 * Accepts all standard YouTube link formats (watch, youtu.be short links, shorts, live, embed,
 * mobile `m.youtube.com`, and music `music.youtube.com`) and extracts the 11-character video ID
 * plus any optional start timestamp (`t=125`, `t=2m5s`, `start=60`).
 */
data class ParsedYouTubeUrl(
    val videoId: String,
    val canonicalWatchUrl: String,
    val shortUrl: String,
    val thumbnailUrl: String,
    val startSeconds: Long? = null
) {
    fun urlAtMs(startMs: Long): String {
        val sec = (startMs / 1000L).coerceAtLeast(0L)
        return if (sec > 0L) "$shortUrl?t=${sec}s" else canonicalWatchUrl
    }
}

object YouTubeUrlParser {

    private val VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val URL_IN_TEXT_REGEX = Regex("""https?://[^\s<>"')]+""", RegexOption.IGNORE_CASE)
    private val TIME_HMS_REGEX = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$""", RegexOption.IGNORE_CASE)

    /**
     * Parses a raw URL or shared text snippet (such as `"Watch this sermon https://youtu.be/dQw4w9WgXcQ?si=abc"`)
     * into a [ParsedYouTubeUrl], or returns null when no valid YouTube video URL is present.
     */
    fun parse(rawInput: String?): ParsedYouTubeUrl? {
        val trimmed = rawInput?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val candidateUrl = extractCandidateUrl(trimmed) ?: return null
        val withoutFragment = candidateUrl.substringBefore('#')
        val schemeSplit = withoutFragment.split("://", limit = 2)
        if (schemeSplit.size != 2) return null
        val scheme = schemeSplit[0].lowercase()
        if (scheme != "http" && scheme != "https") return null

        val remainder = schemeSplit[1]
        val hostAndPath = remainder.substringBefore('?')
        val query = remainder.substringAfter('?', "")
        val host = hostAndPath.substringBefore('/').lowercase().removePrefix("www.")
        val path = "/" + hostAndPath.substringAfter('/', "")
        val params = parseQueryParams(query)

        val videoId: String? = when (host) {
            "youtu.be" -> path.trim('/').substringBefore('/').takeIf { VIDEO_ID_REGEX.matches(it) }
            "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> {
                val segments = path.split('/').filter { it.isNotEmpty() }
                when {
                    segments.isEmpty() -> params["v"]?.takeIf { VIDEO_ID_REGEX.matches(it) }
                    segments[0] == "watch" -> params["v"]?.takeIf { VIDEO_ID_REGEX.matches(it) }
                    segments[0] in setOf("shorts", "live", "embed", "v") && segments.size >= 2 ->
                        segments[1].takeIf { VIDEO_ID_REGEX.matches(it) }
                    else -> params["v"]?.takeIf { VIDEO_ID_REGEX.matches(it) }
                }
            }
            else -> null
        }

        if (videoId == null) return null
        val startSeconds = parseStartSeconds(params["t"] ?: params["start"])
        return ParsedYouTubeUrl(
            videoId = videoId,
            canonicalWatchUrl = "https://www.youtube.com/watch?v=$videoId",
            shortUrl = "https://youtu.be/$videoId",
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            startSeconds = startSeconds
        )
    }

    fun isYouTubeUrl(rawInput: String?): Boolean = parse(rawInput) != null

    private fun extractCandidateUrl(text: String): String? {
        val found = URL_IN_TEXT_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ';', ')', ']') }.toList()
        if (found.isNotEmpty()) {
            return found.firstOrNull { u ->
                val lower = u.lowercase()
                "youtube.com" in lower || "youtu.be" in lower
            } ?: found.first()
        }
        val lower = text.lowercase()
        if ((lower.startsWith("youtube.com/") || lower.startsWith("www.youtube.com/") || lower.startsWith("youtu.be/")) && !text.contains(' ')) {
            return "https://$text"
        }
        return null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
        }.toMap()
    }

    internal fun parseStartSeconds(raw: String?): Long? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        clean.toLongOrNull()?.let { return it.takeIf { s -> s > 0L } }
        val match = TIME_HMS_REGEX.matchEntire(clean) ?: return null
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        val total = h * 3600L + m * 60L + s
        return total.takeIf { it > 0L }
    }
}
