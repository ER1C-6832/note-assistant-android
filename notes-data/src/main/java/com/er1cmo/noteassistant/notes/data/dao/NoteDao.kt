package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 AND archived = 0 ORDER BY pinned DESC, updated_at DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    fun countNotes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotes(notes: List<NoteEntity>)
}
