package com.craftflowtechnologies.meetingmind.core.database

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * Builds a database exactly as a past version of the app left it, from Room's exported schema
 * (`app/schemas/…/<version>.json`). Migration tests start from this rather than from today's
 * schema, so they test the real upgrade path.
 */
object ExportedSchema {
    private val dir = File("schemas/com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase")

    fun create(file: File, version: Int) {
        val json = JSONObject(File(dir, "$version.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = json.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
        }
    }
}
