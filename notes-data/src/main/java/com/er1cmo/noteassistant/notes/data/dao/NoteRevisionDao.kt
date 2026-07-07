package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.er1cmo.noteassistant.notes.data.entity.NoteRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRevisionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRevision(revision: NoteRevisionEntity): Long

    @Query("SELECT * FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC")
    fun observeRevisionsForNote(noteId: Long): Flow<List<NoteRevisionEntity>>

    @Query("SELECT * FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC")
    fun listRevisionsForNote(noteId: Long): List<NoteRevisionEntity>

    @Query("SELECT * FROM note_revisions WHERE id = :id LIMIT 1")
    fun getRevisionById(id: Long): NoteRevisionEntity?
}
