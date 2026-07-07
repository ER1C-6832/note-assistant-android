package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.er1cmo.noteassistant.notes.data.entity.AssistantCommandLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantCommandLogDao {
    @Query("SELECT * FROM assistant_command_log ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AssistantCommandLogEntity>>

    @Query("SELECT * FROM assistant_command_log WHERE id = :id LIMIT 1")
    fun getCommandLogById(id: Long): AssistantCommandLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCommandLog(commandLog: AssistantCommandLogEntity): Long

    @Update
    fun updateCommandLog(commandLog: AssistantCommandLogEntity): Int

    @Query(
        """
        UPDATE assistant_command_log
        SET status = :status,
            confirmation_status = :confirmationStatus,
            result_json = :resultJson,
            affected_note_ids_json = :affectedNoteIdsJson,
            affected_tag_ids_json = :affectedTagIdsJson,
            error_code = :errorCode,
            error_message = :errorMessage,
            completed_at = :completedAt
        WHERE id = :id
        """,
    )
    fun updateCompletion(
        id: Long,
        status: String,
        confirmationStatus: String,
        resultJson: String?,
        affectedNoteIdsJson: String?,
        affectedTagIdsJson: String?,
        errorCode: String?,
        errorMessage: String?,
        completedAt: Long,
    ): Int
}
