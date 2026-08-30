package com.socialfirstaidkit.core.llm.di

import com.socialfirstaidkit.core.llm.api.LlmClient
import com.socialfirstaidkit.core.llm.api.MessageAssistClient
import com.socialfirstaidkit.core.llm.impl.LlmClientImpl
import com.socialfirstaidkit.core.llm.impl.MessageAssistClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBinderModule {

    @Binds
    @Singleton
    abstract fun bindLlmClient(impl: LlmClientImpl): LlmClient

    // Message assist uses the shared LlmClient and routes each function slot independently.
    @Binds
    @Singleton
    abstract fun bindMessageAssistClient(impl: MessageAssistClientImpl): MessageAssistClient
}
