package com.craftflowtechnologies.meetingmind.core.circles

import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import org.json.JSONObject

/**
 * Strong-typed events for end-to-end encrypted Private Circles.
 * Every event is serialized to JSON before AES-256-GCM encryption.
 * The Firestore relay only ever sees the ciphertext, IV, uid, and server timestamp.
 */
sealed class CircleEvent {
    abstract val eventId: String
    abstract val circleId: String
    abstract val authorUid: String
    abstract val timestamp: Long
    abstract val type: String

    fun toJsonString(): String = toJson().toString()

    abstract fun toJson(): JSONObject

    data class MemberJoined(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val displayName: String,
        val role: MemberRole = MemberRole.MEMBER
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("displayName", displayName)
            put("role", role.name)
        }

        companion object {
            const val TYPE = "member_joined"
            fun fromJson(json: JSONObject): MemberJoined = MemberJoined(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                displayName = json.getString("displayName"),
                role = json.optString("role", "").let { r ->
                    runCatching { MemberRole.valueOf(r) }.getOrDefault(MemberRole.MEMBER)
                }
            )
        }
    }

    data class MemberLeft(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
        }

        companion object {
            const val TYPE = "member_left"
            fun fromJson(json: JSONObject): MemberLeft = MemberLeft(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp")
            )
        }
    }

    data class PrayerPosted(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val prayerId: String,
        val authorName: String,
        val requestText: String,
        val isUrgent: Boolean
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("prayerId", prayerId)
            put("authorName", authorName)
            put("requestText", requestText)
            put("isUrgent", isUrgent)
        }

        companion object {
            const val TYPE = "prayer_posted"
            fun fromJson(json: JSONObject): PrayerPosted = PrayerPosted(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                prayerId = json.getString("prayerId"),
                authorName = json.getString("authorName"),
                requestText = json.getString("requestText"),
                isUrgent = json.optBoolean("isUrgent", false)
            )
        }
    }

    data class PrayerPrayed(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val prayerId: String
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("prayerId", prayerId)
        }

        companion object {
            const val TYPE = "prayer_prayed"
            fun fromJson(json: JSONObject): PrayerPrayed = PrayerPrayed(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                prayerId = json.getString("prayerId")
            )
        }
    }

    data class PrayerAnswered(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val prayerId: String
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("prayerId", prayerId)
        }

        companion object {
            const val TYPE = "prayer_answered"
            fun fromJson(json: JSONObject): PrayerAnswered = PrayerAnswered(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                prayerId = json.getString("prayerId")
            )
        }
    }

    data class TestimonyPosted(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val testimonyId: String,
        val authorName: String,
        val title: String,
        val storyText: String,
        val scriptureRef: String?,
        val prayerRequestId: String?
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("testimonyId", testimonyId)
            put("authorName", authorName)
            put("title", title)
            put("storyText", storyText)
            put("scriptureRef", scriptureRef ?: JSONObject.NULL)
            put("prayerRequestId", prayerRequestId ?: JSONObject.NULL)
        }

        companion object {
            const val TYPE = "testimony_posted"
            fun fromJson(json: JSONObject): TestimonyPosted = TestimonyPosted(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                testimonyId = json.getString("testimonyId"),
                authorName = json.getString("authorName"),
                title = json.getString("title"),
                storyText = json.getString("storyText"),
                scriptureRef = json.optString("scriptureRef").takeIf { it.isNotBlank() && it != "null" },
                prayerRequestId = json.optString("prayerRequestId").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    data class TestimonyAmen(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val testimonyId: String
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("testimonyId", testimonyId)
        }

        companion object {
            const val TYPE = "testimony_amen"
            fun fromJson(json: JSONObject): TestimonyAmen = TestimonyAmen(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                testimonyId = json.getString("testimonyId")
            )
        }
    }

    data class SermonShared(
        override val eventId: String,
        override val circleId: String,
        override val authorUid: String,
        override val timestamp: Long,
        val sermonId: String,
        val authorName: String,
        val title: String,
        val preacher: String?,
        val scripturePassage: String?,
        val sermonDate: String?,
        val discussionGuideJson: String,
        val transcriptSummary: String,
        val audioDurationSec: Long
    ) : CircleEvent() {
        override val type: String = TYPE

        override fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("eventId", eventId)
            put("circleId", circleId)
            put("authorUid", authorUid)
            put("timestamp", timestamp)
            put("sermonId", sermonId)
            put("authorName", authorName)
            put("title", title)
            put("preacher", preacher ?: JSONObject.NULL)
            put("scripturePassage", scripturePassage ?: JSONObject.NULL)
            put("sermonDate", sermonDate ?: JSONObject.NULL)
            put("discussionGuideJson", discussionGuideJson)
            put("transcriptSummary", transcriptSummary)
            put("audioDurationSec", audioDurationSec)
        }

        companion object {
            const val TYPE = "sermon_shared"
            fun fromJson(json: JSONObject): SermonShared = SermonShared(
                eventId = json.getString("eventId"),
                circleId = json.getString("circleId"),
                authorUid = json.getString("authorUid"),
                timestamp = json.getLong("timestamp"),
                sermonId = json.getString("sermonId"),
                authorName = json.getString("authorName"),
                title = json.getString("title"),
                preacher = json.optString("preacher").takeIf { it.isNotBlank() && it != "null" },
                scripturePassage = json.optString("scripturePassage").takeIf { it.isNotBlank() && it != "null" },
                sermonDate = json.optString("sermonDate").takeIf { it.isNotBlank() && it != "null" },
                discussionGuideJson = json.optString("discussionGuideJson", ""),
                transcriptSummary = json.optString("transcriptSummary", ""),
                audioDurationSec = json.optLong("audioDurationSec", 0L)
            )
        }
    }

    companion object {
        fun fromJsonString(jsonString: String): CircleEvent? = runCatching {
            val json = JSONObject(jsonString)
            when (json.optString("type")) {
                MemberJoined.TYPE -> MemberJoined.fromJson(json)
                MemberLeft.TYPE -> MemberLeft.fromJson(json)
                PrayerPosted.TYPE -> PrayerPosted.fromJson(json)
                PrayerPrayed.TYPE -> PrayerPrayed.fromJson(json)
                PrayerAnswered.TYPE -> PrayerAnswered.fromJson(json)
                TestimonyPosted.TYPE -> TestimonyPosted.fromJson(json)
                TestimonyAmen.TYPE -> TestimonyAmen.fromJson(json)
                SermonShared.TYPE -> SermonShared.fromJson(json)
                else -> null
            }
        }.getOrNull()
    }
}
