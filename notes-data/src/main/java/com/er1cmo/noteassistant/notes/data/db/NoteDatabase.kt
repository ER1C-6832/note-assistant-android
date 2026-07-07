package com.er1cmo.noteassistant.notes.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.er1cmo.noteassistant.notes.data.dao.AssistantCommandLogDao
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.dao.NoteRevisionDao
import com.er1cmo.noteassistant.notes.data.dao.NoteTagDao
import com.er1cmo.noteassistant.notes.data.dao.PendingConfirmationDao
import com.er1cmo.noteassistant.notes.data.dao.TagDao
import com.er1cmo.noteassistant.notes.data.entity.AssistantCommandLogEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteRevisionEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteTagCrossRefEntity
import com.er1cmo.noteassistant.notes.data.entity.PendingConfirmationEntity
import com.er1cmo.noteassistant.notes.data.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRefEntity::class,
        NoteRevisionEntity::class,
        AssistantCommandLogEntity::class,
        PendingConfirmationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun noteTagDao(): NoteTagDao
    abstract fun noteRevisionDao(): NoteRevisionDao
    abstract fun assistantCommandLogDao(): AssistantCommandLogDao
    abstract fun pendingConfirmationDao(): PendingConfirmationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN tag_text TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_revisions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        note_id INTEGER NOT NULL,
                        title_snapshot TEXT NOT NULL,
                        content_snapshot TEXT NOT NULL,
                        tags_snapshot_json TEXT NOT NULL,
                        type_snapshot TEXT NOT NULL,
                        is_done_snapshot INTEGER NOT NULL,
                        pinned_snapshot INTEGER NOT NULL,
                        archived_snapshot INTEGER NOT NULL,
                        deleted_snapshot INTEGER NOT NULL,
                        color_snapshot TEXT,
                        created_at INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        reason TEXT,
                        command_log_id INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_revisions_note_id ON note_revisions(note_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_revisions_command_log_id ON note_revisions(command_log_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_revisions_created_at ON note_revisions(created_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assistant_command_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        conversation_id TEXT,
                        source TEXT NOT NULL,
                        user_text TEXT,
                        recognized_text TEXT,
                        normalized_intent TEXT,
                        tool_name TEXT NOT NULL,
                        arguments_json TEXT NOT NULL,
                        risk_level TEXT NOT NULL,
                        confirmation_status TEXT NOT NULL,
                        result_json TEXT,
                        affected_note_ids_json TEXT,
                        affected_tag_ids_json TEXT,
                        status TEXT NOT NULL,
                        error_code TEXT,
                        error_message TEXT,
                        created_at INTEGER NOT NULL,
                        completed_at INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_command_log_created_at ON assistant_command_log(created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_command_log_tool_name ON assistant_command_log(tool_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_command_log_status ON assistant_command_log(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_command_log_source ON assistant_command_log(source)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_confirmations (
                        confirmation_id TEXT PRIMARY KEY NOT NULL,
                        command_log_id INTEGER NOT NULL,
                        tool_name TEXT NOT NULL,
                        arguments_json TEXT NOT NULL,
                        risk_level TEXT NOT NULL,
                        preview_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_confirmations_command_log_id ON pending_confirmations(command_log_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_confirmations_expires_at ON pending_confirmations(expires_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_confirmations_status ON pending_confirmations(status)")
            }
        }
    }
}
