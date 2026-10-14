package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechRole
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechSection
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechUnit
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A text model that rewrites for the ear. Null means it couldn't answer; the original is then read as written. */
fun interface RewriteModel {
    suspend fun rewrite(prompt: String): String?
}

enum class ChangeKind { REWRITTEN, SPOKEN_FORM, AS_WRITTEN }

/** What Smart Read did with one stretch of text, for the "See what changed" view. */
data class ScriptChange(val before: String, val after: String, val kind: ChangeKind, val reason: String? = null)

/** Rewrites kept so a second listen costs nothing. Stored with the note's reading, so it goes when the reading goes. */
class ScriptStore(private val file: File) {
    @Synchronized fun get(key: String): ScriptChange? = read()[key]

    @Synchronized fun put(key: String, change: ScriptChange) {
        val all = read().toMutableMap()
        all[key] = change
        file.parentFile?.mkdirs()
        val json = JSONObject()
        all.forEach { (k, c) ->
            json.put(k, JSONObject().put("b", c.before).put("a", c.after).put("k", c.kind.name).put("r", c.reason ?: JSONObject.NULL))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    @Synchronized fun all(): List<ScriptChange> = read().values.toList()

    private fun read(): Map<String, ScriptChange> = runCatching {
        if (!file.exists()) return emptyMap()
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { k ->
            val o = json.getJSONObject(k)
            ScriptChange(o.getString("b"), o.getString("a"), ChangeKind.valueOf(o.getString("k")), if (o.isNull("r")) null else o.getString("r"))
        }
    }.getOrDefault(emptyMap())
}

/**
 * Smart Read for one part: rewrites the plain prose of a note so it flows when heard, and nothing else.
 * Scripture, quotes, transcript, prayers, the person's own writing, private text and anything containing
 * a quotation are never sent anywhere and never changed. A rewrite must pass [ScriptValidator]; if it
 * can't, the original wording is read, and the change record says so. With no model (offline, or the
 * cloud isn't allowed) only [SpokenForm] is applied.
 */
class SmartShaper(
    private val model: RewriteModel?,
    private val store: ScriptStore,
    /** Text of the study's scripture passages, to recognise quotations of them. */
    private val passages: List<String> = emptyList()
) {
    private val eligible = setOf(SpeechRole.COMMENTARY, SpeechRole.OBSERVATION, SpeechRole.INTERPRETATION, SpeechRole.APPLICATION, SpeechRole.OTHER)

    suspend fun shape(part: SpeechSection): SpeechSection = part.copy(units = part.units.map { shape(it) })

    private suspend fun shape(unit: SpeechUnit): SpeechUnit {
        if (unit.role !in eligible || unit.locked || unit.isPrivate || unit.unavailable || unit.text.length < MIN_CHARS) return unit
        val original = unit.text
        if (QuotationDetector.containsQuotation(original, passages)) {
            return unit.also { remember(key(unit), ScriptChange(original, original, ChangeKind.AS_WRITTEN, "contains a quotation")) }
        }
        val key = key(unit)
        val limits = if (unit.role == SpeechRole.OTHER) ScriptValidator.Limits.GENERAL else ScriptValidator.Limits.STUDY
        store.get(key)?.let { saved ->
            if (saved.kind != ChangeKind.AS_WRITTEN && saved.before == original && ScriptValidator.check(original, saved.after, limits) == null) return unit.copy(text = saved.after)
            if (saved.kind == ChangeKind.AS_WRITTEN && model != null) return unit // already tried and failed: don't ask again
        }
        if (model == null) {
            val spoken = SpokenForm.apply(original)
            val ok = spoken != original && ScriptValidator.check(original, spoken, ScriptValidator.Limits.GENERAL) == null
            if (ok) { remember(key, ScriptChange(original, spoken, ChangeKind.SPOKEN_FORM)); return unit.copy(text = spoken) }
            return unit
        }
        var reason: String? = "no answer"
        for (attempt in 1..2) {
            val raw = model.rewrite(prompt(original, attempt)) ?: continue
            val cleaned = clean(raw)
            reason = ScriptValidator.check(original, cleaned, limits)
            if (reason == null) {
                remember(key, ScriptChange(original, cleaned, ChangeKind.REWRITTEN))
                return unit.copy(text = cleaned)
            }
        }
        remember(key, ScriptChange(original, original, ChangeKind.AS_WRITTEN, reason))
        return unit
    }

    private fun remember(key: String, change: ScriptChange) = runCatching { store.put(key, change) }.let { }

    private fun key(unit: SpeechUnit): String = sha("v$PROMPT_VERSION|${unit.role}|${unit.text}")

    private fun clean(raw: String): String = raw.trim().removeSurrounding("```").trim().removeSurrounding("\"").trim()

    private fun prompt(text: String, attempt: Int): String = buildString {
        appendLine("Rewrite the passage below so it sounds natural when read aloud.")
        appendLine("Rules, all of them strict:")
        appendLine("- Keep every fact, name, number, date and Bible reference exactly as written.")
        appendLine("- Do not add, remove or soften any claim. Keep every \"not\", \"never\", \"may\", \"might\" and \"perhaps\" as it is.")
        appendLine("- Do not add quotations, Bible verses, examples, advice or conclusions.")
        appendLine("- Use short, plain sentences. Spell out symbols and abbreviations. No lists, headings or markdown.")
        appendLine("- Keep roughly the same length.")
        if (attempt > 1) appendLine("- Your last answer broke a rule. Change as little as possible: only smooth the wording.")
        appendLine("Reply with the rewritten passage only.")
        appendLine()
        append(text)
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    companion object {
        const val PROMPT_VERSION = 1
        /** Part of the fingerprint of Smart Read audio: a new version re-makes it. */
        const val SCRIPT_VERSION = 100 + PROMPT_VERSION
        const val MIN_CHARS = 30
    }
}
