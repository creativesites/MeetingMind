package com.example.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.work.DueDates
import com.example.core.work.ItemKind
import com.example.core.work.ItemSource
import com.example.core.work.ItemStatus
import com.example.core.work.SUBTYPE_FOLLOW_UP

/**
 * 14 → 15: the work schema (docs/PLAN_PROFESSIONAL.md §10).
 *
 * - `action_items`, `decisions`, `questions` and `follow_ups` become rows of one `items` table,
 *   attached to the recording's note, with free-text deadlines read into real days.
 * - `people` and `note_people` are added, empty: People is built from history after upgrade.
 * - Speakers can point at a person; notebooks gain a kind (PROJECT) and properties.
 *
 * Old findings are marked reviewed, so nobody upgrades into a pile of things to review.
 */
object WorkSchema {

    internal val CREATE_SQL: List<String> = listOf(
        """CREATE TABLE IF NOT EXISTS `items` (`id` TEXT NOT NULL, `meetingId` TEXT, `noteId` TEXT, `kind` TEXT NOT NULL,
            `subtype` TEXT, `text` TEXT NOT NULL, `status` TEXT NOT NULL, `ownerSpeakerId` TEXT, `ownerPersonId` TEXT,
            `ownerName` TEXT, `dueAt` INTEGER, `dueText` TEXT, `answer` TEXT, `answeredAt` INTEGER, `projectId` TEXT,
            `source` TEXT NOT NULL, `confidence` REAL, `reviewed` INTEGER NOT NULL, `sourceSegmentIdsJson` TEXT NOT NULL,
            `sourceStartMs` INTEGER, `supersededById` TEXT, `metadataJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`),
            FOREIGN KEY(`meetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        "CREATE INDEX IF NOT EXISTS `index_items_meetingId` ON `items` (`meetingId`)",
        "CREATE INDEX IF NOT EXISTS `index_items_noteId` ON `items` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_items_kind_status` ON `items` (`kind`, `status`)",
        "CREATE INDEX IF NOT EXISTS `index_items_ownerPersonId` ON `items` (`ownerPersonId`)",
        "CREATE INDEX IF NOT EXISTS `index_items_dueAt` ON `items` (`dueAt`)",
        """CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL,
            `aliasesJson` TEXT NOT NULL, `emailsJson` TEXT NOT NULL, `phonesJson` TEXT NOT NULL, `orgId` TEXT, `role` TEXT,
            `preferredChannel` TEXT, `isSelf` INTEGER NOT NULL, `notes` TEXT, `confidential` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastSeenAt` INTEGER, PRIMARY KEY(`id`))""",
        "CREATE INDEX IF NOT EXISTS `index_people_name` ON `people` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_people_orgId` ON `people` (`orgId`)",
        "CREATE INDEX IF NOT EXISTS `index_people_kind` ON `people` (`kind`)",
        """CREATE TABLE IF NOT EXISTS `note_people` (`noteId` TEXT NOT NULL, `personId` TEXT NOT NULL, `role` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL, PRIMARY KEY(`noteId`, `personId`, `role`),
            FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
            FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        "CREATE INDEX IF NOT EXISTS `index_note_people_personId` ON `note_people` (`personId`)"
    )

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CREATE_SQL.forEach { db.execSQL(it) }
            // Each step checks first, so a migration interrupted part way can run again.
            if (!hasColumn(db, "speakers", "personId")) db.execSQL("ALTER TABLE speakers ADD COLUMN personId TEXT")
            if (!hasColumn(db, "notebooks", "kind")) db.execSQL("ALTER TABLE notebooks ADD COLUMN kind TEXT NOT NULL DEFAULT 'NOTEBOOK'")
            if (!hasColumn(db, "notebooks", "propertiesJson")) db.execSQL("ALTER TABLE notebooks ADD COLUMN propertiesJson TEXT NOT NULL DEFAULT '{}'")
            copyFindings(db)
            OLD_TABLES.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        }
    }

    private val OLD_TABLES = listOf("action_items", "decisions", "questions", "follow_ups")

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndex("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == column }
        }

    private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf<Any>(table)).use { it.moveToFirst() }

    private class Row(val c: android.database.Cursor) {
        fun s(col: String): String? = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
        fun l(col: String): Long? = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
        fun f(col: String): Float? = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getFloat(it) }
        fun b(col: String): Boolean = (l(col) ?: 0L) != 0L
    }

    private fun copyFindings(db: SupportSQLiteDatabase) {
        fun each(sql: String, block: (Row) -> ContentValues) {
            val table = OLD_TABLES.first { sql.contains("FROM $it ") }
            if (!hasTable(db, table)) return
            db.query(sql).use { c ->
                val row = Row(c)
                while (c.moveToNext()) {
                    val values = block(row)
                    values.put("sourceStartMs", firstSegmentStart(db, row.s("sourceSegmentIdsJson")))
                    db.insert("items", SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
        }
        val join = "JOIN meetings m ON m.id = t.meetingId"
        val cols = "t.*, m.noteId AS mNoteId, m.createdAt AS mCreatedAt"

        each("SELECT $cols FROM action_items t $join") { r ->
            base(r, ItemKind.TASK, text = r.s("task").orEmpty()).apply {
                put("status", (if (r.b("isCompleted")) ItemStatus.DONE else ItemStatus.OPEN).name)
                put("ownerSpeakerId", r.s("assigneeSpeakerId"))
                put("ownerName", r.s("assigneeName"))
                putDue(r.s("deadline"), r.l("mCreatedAt"))
                put("confidence", r.f("confidence"))
            }
        }
        each("SELECT $cols FROM decisions t $join") { r ->
            base(r, ItemKind.DECISION, text = r.s("text").orEmpty()).apply {
                put("subtype", r.s("type"))
                put("confidence", r.f("confidence"))
            }
        }
        each("SELECT $cols FROM questions t $join") { r ->
            base(r, ItemKind.QUESTION, text = r.s("text").orEmpty()).apply {
                put("status", (if (r.b("resolved")) ItemStatus.ANSWERED else ItemStatus.OPEN).name)
                put("ownerSpeakerId", r.s("askedBySpeakerId"))
                put("answer", r.s("answer"))
            }
        }
        each("SELECT $cols FROM follow_ups t $join") { r ->
            base(r, ItemKind.TASK, text = r.s("description").orEmpty()).apply {
                put("subtype", SUBTYPE_FOLLOW_UP)
                put("ownerSpeakerId", r.s("ownerSpeakerId"))
                putDue(r.s("deadline"), r.l("mCreatedAt"))
            }
        }
    }

    private fun base(r: Row, kind: ItemKind, text: String): ContentValues {
        val created = r.l("mCreatedAt") ?: System.currentTimeMillis()
        return ContentValues().apply {
            put("id", r.s("id"))
            put("meetingId", r.s("meetingId"))
            put("noteId", r.s("mNoteId"))
            put("kind", kind.name)
            put("text", text)
            put("status", ItemStatus.OPEN.name)
            put("source", ItemSource.AI.name)
            put("reviewed", 1)
            put("sourceSegmentIdsJson", r.s("sourceSegmentIdsJson") ?: "[]")
            put("metadataJson", "{}")
            put("createdAt", created)
            put("updatedAt", created)
        }
    }

    private fun ContentValues.putDue(deadline: String?, reference: Long?) {
        put("dueText", deadline)
        put("dueAt", DueDates.parse(deadline, reference ?: System.currentTimeMillis()))
    }

    private fun firstSegmentStart(db: SupportSQLiteDatabase, idsJson: String?): Long? {
        val first = runCatching { org.json.JSONArray(idsJson ?: "[]").optString(0) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return db.query("SELECT startMs FROM transcript_segments WHERE id = ?", arrayOf<Any>(first)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    }
}
