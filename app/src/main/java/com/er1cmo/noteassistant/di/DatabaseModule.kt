package com.er1cmo.noteassistant.di

import android.content.Context
import androidx.room.Room
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.dao.NoteTagDao
import com.er1cmo.noteassistant.notes.data.dao.TagDao
import com.er1cmo.noteassistant.notes.data.db.NoteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideNoteDatabase(@ApplicationContext context: Context): NoteDatabase = Room.databaseBuilder(
        context,
        NoteDatabase::class.java,
        "note_assistant.db",
    ).build()

    @Provides
    fun provideNoteDao(database: NoteDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: NoteDatabase): TagDao = database.tagDao()

    @Provides
    fun provideNoteTagDao(database: NoteDatabase): NoteTagDao = database.noteTagDao()
}
