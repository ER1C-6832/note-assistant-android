package com.er1cmo.noteassistant.assistant.wakeword.di

import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceController
import com.er1cmo.noteassistant.core.common.audio.WakeWordAudioGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WakeWordAudioGateModule {
    @Binds
    @Singleton
    abstract fun bindWakeWordAudioGate(impl: WakeWordServiceController): WakeWordAudioGate
}
