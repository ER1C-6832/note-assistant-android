package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.er1cmo.noteassistant.notes.data.entity.PendingConfirmationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingConfirmationDao {
    @Query("SELECT * FROM pending_confirmations WHERE status = 'pending' AND expires_at > :nowMillis ORDER BY created_at DESC")
    fun observePending(nowMillis: Long): Flow<List<PendingConfirmationEntity>>

    @Query("SELECT * FROM pending_confirmations WHERE confirmation_id = :confirmationId LIMIT 1")
    fun getById(confirmationId: String): PendingConfirmationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPendingConfirmation(pendingConfirmation: PendingConfirmationEntity)

    @Query("UPDATE pending_confirmations SET status = :status WHERE confirmation_id = :confirmationId")
    fun updateStatus(confirmationId: String, status: String): Int

    @Query("UPDATE pending_confirmations SET status = 'expired' WHERE status = 'pending' AND expires_at <= :nowMillis")
    fun markExpired(nowMillis: Long): Int
}
