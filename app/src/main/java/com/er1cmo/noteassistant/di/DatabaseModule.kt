package com.er1cmo.noteassistant.di

import android.content.Context
import androidx.room.Room
import com.er1cmo.noteassistant.notes.data.dao.AssistantCommandLogDao
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.dao.NoteRevisionDao
import com.er1cmo.noteassistant.notes.data.dao.NoteTagDao
import com.er1cmo.noteassistant.notes.data.dao.PendingConfirmationDao
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
    ).addMigrations(
        NoteDatabase.MIGRATION_1_2,
        NoteDatabase.MIGRATION_2_3,
    ).build()

    @Provides
    fun provideNoteDao(database: NoteDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: NoteDatabase): TagDao = database.tagDao()

    @Provides
    fun provideNoteTagDao(database: NoteDatabase): NoteTagDao = database.noteTagDao()

    @Provides
    fun provideNoteRevisionDao(database: NoteDatabase): NoteRevisionDao = database.noteRevisionDao()

    @Provides
    fun provideAssistantCommandLogDao(database: NoteDatabase): AssistantCommandLogDao = database.assistantCommandLogDao()

    @Provides
    fun providePendingConfirmationDao(database: NoteDatabase): PendingConfirmationDao = database.pendingConfirmationDao()
}
