package com.er1cmo.noteassistant.assistant.runtime.di

import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.controller.LocalAssistantController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantRuntimeModule {
    @Binds
    @Singleton
    abstract fun bindAssistantController(impl: LocalAssistantController): AssistantController
}
