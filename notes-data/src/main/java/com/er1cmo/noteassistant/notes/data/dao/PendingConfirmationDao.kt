package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.er1cmo.noteassistant.notes.data.entity.PendingConfirmationEntity

@Dao
interface PendingConfirmationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPendingConfirmation(pendingConfirmation: PendingConfirmationEntity)

    @Update
    fun updatePendingConfirmation(pendingConfirmation: PendingConfirmationEntity)

    @Query("SELECT * FROM pending_confirmations WHERE confirmation_id = :confirmationId LIMIT 1")
    fun getPendingConfirmationById(confirmationId: String): PendingConfirmationEntity?

    @Query("SELECT * FROM pending_confirmations WHERE (:onlyPending = 0 OR status = 'pending') ORDER BY created_at DESC LIMIT :limit")
    fun listPendingConfirmations(limit: Int, onlyPending: Boolean): List<PendingConfirmationEntity>

    @Query("UPDATE pending_confirmations SET status = 'expired' WHERE status = 'pending' AND expires_at <= :nowMillis")
    fun markExpired(nowMillis: Long): Int
}
