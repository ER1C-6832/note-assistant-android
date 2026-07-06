package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.er1cmo.noteassistant.notes.data.entity.NoteTagCrossRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTagDao {
    @Query("SELECT * FROM note_tag_cross_ref")
    fun observeAll(): Flow<List<NoteTagCrossRefEntity>>

    @Query("SELECT * FROM note_tag_cross_ref WHERE note_id = :noteId")
    fun listForNote(noteId: Long): List<NoteTagCrossRefEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(crossRefs: List<NoteTagCrossRefEntity>)

    @Query("DELETE FROM note_tag_cross_ref WHERE note_id = :noteId")
    fun deleteForNote(noteId: Long): Int

    @Query("DELETE FROM note_tag_cross_ref WHERE tag_id = :tagId")
    fun deleteForTag(tagId: Long): Int
}
