package com.bulifier.hilt

import com.bulifier.core.security.CoreUiVerifier
import com.bulifier.core.security.UiVerifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindUiActionsVerifier(impl: CoreUiVerifier): UiVerifier

}
