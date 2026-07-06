package com.er1cmo.noteassistant.di

import com.er1cmo.noteassistant.notes.data.repository.NoteRepositoryImpl
import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository
}
