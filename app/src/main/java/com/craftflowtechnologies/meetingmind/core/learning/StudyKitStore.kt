package com.craftflowtechnologies.meetingmind.core.learning

import android.content.Context
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.OptionWhy
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One versioned JSON file per Learning session, holding its [StudyKit] (the lesson content that
 * does not live in the Learning tables). Lives in `filesDir/study/kits/`. Reading a kit needs no
 * network, so a lesson works offline once it exists.
 *
 * A file with an unknown version, or one that cannot be parsed, reads as absent. The lesson then
 * offers to generate again, rather than showing a half-read kit.
 */
class StudyKitStore(private val directory: File) {

    /** Writes the kit atomically (temp file, then rename). Returns false if the write failed. */
    fun save(kit: StudyKit): Boolean {
        return runCatching {
            directory.mkdirs()
            val target = fileFor(kit.sessionId)
            val temp = File(directory, target.name + ".tmp")
            temp.writeText(encode(kit).toString())
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
            true
        }.getOrDefault(false)
    }

    fun load(sessionId: String): StudyKit? {
        val file = fileFor(sessionId)
        if (!file.exists()) return null
        return runCatching { decode(JSONObject(file.readText())) }.getOrNull()
    }

    fun delete(sessionId: String) {
        fileFor(sessionId).delete()
    }

    internal fun fileFor(sessionId: String): File {
        val safe = sessionId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return File(directory, "$safe.json")
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun forContext(context: Context): StudyKitStore = StudyKitStore(File(context.filesDir, "study/kits"))

        internal fun encode(kit: StudyKit): JSONObject = JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("sessionId", kit.sessionId)
            put("summary", kit.summary)
            put("modelId", kit.modelId)
            put("createdAt", kit.createdAt)
            put("stressed", JSONArray().apply {
                kit.stressed.forEach { m ->
                    put(JSONObject().apply {
                        put("startMs", m.startMs)
                        put("endMs", m.endMs)
                        put("quote", m.quote)
                        put("reason", m.reason.name)
                        put("conceptId", m.conceptId ?: JSONObject.NULL)
                    })
                }
            })
            put("sections", JSONArray().apply {
                kit.sections.forEach { s ->
                    put(JSONObject().apply {
                        put("index", s.index)
                        put("title", s.title)
                        put("body", s.body)
                        put("startMs", s.startMs ?: JSONObject.NULL)
                        put("terms", JSONArray().apply {
                            s.terms.forEach { t ->
                                put(JSONObject().apply {
                                    put("text", t.text)
                                    put("conceptId", t.conceptId)
                                })
                            }
                        })
                        put("quickCheck", s.quickCheck?.let { q ->
                            JSONObject().apply {
                                put("prompt", q.prompt)
                                put("answer", q.answer)
                                put("conceptId", q.conceptId ?: JSONObject.NULL)
                            }
                        } ?: JSONObject.NULL)
                    })
                }
            })
            put("hardToHear", JSONArray().apply {
                kit.hardToHear.forEach { r ->
                    put(JSONObject().apply {
                        put("startMs", r.startMs)
                        put("endMs", r.endMs)
                    })
                }
            })
            put("optionWhys", JSONArray().apply {
                kit.optionWhys.forEach { w ->
                    put(JSONObject().apply {
                        put("activityId", w.activityId)
                        put("optionIndex", w.optionIndex)
                        put("why", w.why)
                    })
                }
            })
            put("rightWhys", JSONObject().apply {
                kit.rightWhys.forEach { (id, why) -> put(id, why) }
            })
        }

        internal fun decode(json: JSONObject): StudyKit? {
            if (json.optInt("version", -1) != SCHEMA_VERSION) return null
            return StudyKit(
                sessionId = json.getString("sessionId"),
                summary = json.getString("summary"),
                stressed = json.getJSONArray("stressed").objects { o ->
                    StressedMoment(
                        startMs = o.getLong("startMs"),
                        endMs = o.getLong("endMs"),
                        quote = o.getString("quote"),
                        reason = StressReason.valueOf(o.getString("reason")),
                        conceptId = o.optStringOrNull("conceptId")
                    )
                },
                sections = json.getJSONArray("sections").objects { o ->
                    LessonSection(
                        index = o.getInt("index"),
                        title = o.getString("title"),
                        body = o.getString("body"),
                        startMs = if (o.isNull("startMs")) null else o.getLong("startMs"),
                        terms = o.getJSONArray("terms").objects { t ->
                            LessonTerm(text = t.getString("text"), conceptId = t.getString("conceptId"))
                        },
                        quickCheck = o.optJSONObject("quickCheck")?.let { q ->
                            QuickCheck(
                                prompt = q.getString("prompt"),
                                answer = q.getString("answer"),
                                conceptId = q.optStringOrNull("conceptId")
                            )
                        }
                    )
                },
                hardToHear = json.getJSONArray("hardToHear").objects { r ->
                    TimeRange(startMs = r.getLong("startMs"), endMs = r.getLong("endMs"))
                },
                optionWhys = json.getJSONArray("optionWhys").objects { w ->
                    OptionWhy(
                        activityId = w.getString("activityId"),
                        optionIndex = w.getInt("optionIndex"),
                        why = w.getString("why")
                    )
                },
                rightWhys = json.getJSONObject("rightWhys").let { obj ->
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                },
                modelId = json.getString("modelId"),
                createdAt = json.getLong("createdAt")
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key) || !has(key)) null else getString(key)

        private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> =
            List(length()) { i -> map(getJSONObject(i)) }
    }
}
