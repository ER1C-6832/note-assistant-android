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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCommandLog(log: AssistantCommandLogEntity): Long

    @Update
    fun updateCommandLog(log: AssistantCommandLogEntity)

    @Query("SELECT * FROM assistant_command_log WHERE id = :id LIMIT 1")
    fun getCommandLogById(id: Long): AssistantCommandLogEntity?

    @Query("SELECT * FROM assistant_command_log ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecentCommandLogs(limit: Int): Flow<List<AssistantCommandLogEntity>>

    @Query("""
        DELETE FROM assistant_command_log
        WHERE id NOT IN (
            SELECT id FROM assistant_command_log
            ORDER BY created_at DESC, id DESC
            LIMIT :keepCount
        )
    """)
    fun pruneKeepingNewest(keepCount: Int): Int
}
