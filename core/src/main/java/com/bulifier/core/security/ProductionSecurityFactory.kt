package com.bulifier.core.security

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProductionSecurityFactory {
    fun uiVerifier(): UiVerifier
}