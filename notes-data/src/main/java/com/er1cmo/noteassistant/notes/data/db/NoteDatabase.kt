package com.er1cmo.noteassistant.notes.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.dao.NoteTagDao
import com.er1cmo.noteassistant.notes.data.dao.TagDao
import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteTagCrossRefEntity
import com.er1cmo.noteassistant.notes.data.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRefEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun noteTagDao(): NoteTagDao
}
