package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.CreateCardDao
import com.craftflowtechnologies.meetingmind.core.database.CreateCardEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Turns cards into rows and back. Everything that is not a plain column is JSON; unknown or missing values fall back to defaults, so a row never fails to open. */
object CreateCardCodec {

    fun toEntity(card: CreateCard): CreateCardEntity = CreateCardEntity(
        id = card.id,
        source = card.source.name,
        sourceText = card.sourceText.take(CreateCard.MAX_SOURCE),
        sourceRef = card.sourceRef,
        vibe = card.vibe.name,
        text = card.text,
        scriptureRef = card.scriptureRef,
        scriptureVersionId = card.scriptureVersionId,
        format = card.format.name,
        designJson = designToJson(card.design).toString(),
        versionsJson = versionsToJson(card.past).toString(),
        pinned = card.pinned,
        createdAt = card.createdAt,
        updatedAt = card.updatedAt
    )

    fun fromEntity(e: CreateCardEntity): CreateCard = CreateCard(
        id = e.id,
        source = CreateSourceKind.entries.firstOrNull { it.name == e.source } ?: CreateSourceKind.FREE_PROMPT,
        sourceText = e.sourceText,
        sourceRef = e.sourceRef,
        vibe = CreateVibe.parse(e.vibe) ?: CreateVibe.PEACEFUL,
        current = CardVersion(e.text, e.scriptureRef, "Saved"),
        past = versionsFromJson(e.versionsJson),
        scriptureVersionId = e.scriptureVersionId,
        format = CreateFormat.entries.firstOrNull { it.name == e.format } ?: CreateFormat.STORY,
        design = designFromJson(e.designJson),
        pinned = e.pinned,
        createdAt = e.createdAt,
        updatedAt = e.updatedAt
    )

    fun designToJson(d: CreateDesign): JSONObject = JSONObject().apply {
        when (val b = d.background) {
            is CreateBackground.Pack -> { put("bg", "pack"); put("bgId", b.id) }
            is CreateBackground.Photo -> { put("bg", "photo"); put("bgPath", b.path) }
        }
        put("font", d.fontPair.name)
        put("centered", d.centered)
        put("companion", d.includeCompanion)
        put("watermark", d.watermark)
        put("scale", d.textScale.toDouble())
    }

    fun designFromJson(json: String): CreateDesign = runCatching {
        val o = JSONObject(json)
        val bg = if (o.optString("bg") == "photo" && o.optString("bgPath").isNotBlank()) CreateBackground.Photo(o.getString("bgPath"))
        else CreateBackground.Pack(o.optString("bgId", "dawn").ifBlank { "dawn" })
        CreateDesign(
            background = bg,
            fontPair = CreateFontPair.entries.firstOrNull { it.name == o.optString("font") } ?: CreateFontPair.CLASSIC,
            centered = o.optBoolean("centered", true),
            includeCompanion = o.optBoolean("companion", false),
            watermark = o.optBoolean("watermark", false),
            textScale = o.optDouble("scale", 1.0).toFloat().coerceIn(0.7f, 1.4f)
        )
    }.getOrDefault(CreateDesign())

    fun versionsToJson(v: List<CardVersion>): JSONArray = JSONArray().also { arr ->
        v.forEach { arr.put(JSONObject().put("text", it.text).put("ref", it.scriptureRef ?: JSONObject.NULL).put("label", it.label)) }
    }

    fun versionsFromJson(json: String): List<CardVersion> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CardVersion(o.optString("text"), o.optString("ref").takeIf { it.isNotBlank() && !o.isNull("ref") }, o.optString("label", "Earlier"))
        }
    }.getOrDefault(emptyList())
}

/**
 * "My creations". Every card the person makes is saved here so it can be re-edited, re-shared or
 * pinned. Thumbnails are small PNGs in the app's own storage; the card's scripture text is never saved.
 */
class CreateGallery(private val dao: CreateCardDao, private val thumbDir: File? = null) {
    constructor(context: Context) : this(
        MeetMindDatabase.getInstance(context).createCardDao(),
        File(context.filesDir, "creations")
    )

    val all: Flow<List<CreateCard>> get() = dao.observeAll().map { rows -> rows.map(CreateCardCodec::fromEntity) }
    fun recent(limit: Int): Flow<List<CreateCard>> = dao.observeRecent(limit).map { rows -> rows.map(CreateCardCodec::fromEntity) }
    val count: Flow<Int> get() = dao.observeCount()

    suspend fun get(id: String): CreateCard? = dao.get(id)?.let(CreateCardCodec::fromEntity)

    /** Saves [card] (a blank card is not worth keeping: nothing is saved). Returns whether it saved. */
    suspend fun save(card: CreateCard): Boolean {
        if (card.text.isBlank() && card.scriptureRef.isNullOrBlank()) return false
        dao.upsert(CreateCardCodec.toEntity(card.copy(updatedAt = System.currentTimeMillis())))
        return true
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: String) {
        dao.delete(id)
        thumbFile(id)?.delete()
        CreateLocks.forget(id)
    }

    fun thumbFile(id: String): File? = thumbDir?.let { File(it, "$id.png") }

    /** Stores the small preview the gallery shows. Best effort. */
    fun saveThumb(id: String, bitmap: android.graphics.Bitmap) {
        val f = thumbFile(id) ?: return
        runCatching { f.parentFile?.mkdirs(); f.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) } }
    }
}
