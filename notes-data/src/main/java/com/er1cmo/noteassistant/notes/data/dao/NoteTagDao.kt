package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.er1cmo.noteassistant.notes.data.entity.NoteTagCrossRefEntity

@Dao
interface NoteTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(crossRefs: List<NoteTagCrossRefEntity>)
}
