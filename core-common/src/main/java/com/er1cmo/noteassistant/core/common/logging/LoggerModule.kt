package com.er1cmo.noteassistant.core.common.logging

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    @Binds
    @Singleton
    abstract fun bindAppLogger(impl: AndroidAppLogger): AppLogger
}
