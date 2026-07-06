package com.er1cmo.noteassistant.di

import com.er1cmo.noteassistant.core.common.time.SystemTimeProvider
import com.er1cmo.noteassistant.core.common.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
