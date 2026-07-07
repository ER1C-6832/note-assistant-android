package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.er1cmo.noteassistant.notes.data.entity.NoteRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRevisionDao {
    @Query("SELECT * FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteRevisionEntity>>

    @Query("SELECT * FROM note_revisions ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteRevisionEntity>>

    @Query("SELECT * FROM note_revisions WHERE id = :id LIMIT 1")
    fun getRevisionById(id: Long): NoteRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRevision(revision: NoteRevisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRevisions(revisions: List<NoteRevisionEntity>): List<Long>
}
