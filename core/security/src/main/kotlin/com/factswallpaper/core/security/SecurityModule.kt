package com.factswallpaper.core.security

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
    abstract fun bindApiKeyStore(impl: AndroidKeystoreApiKeyStore): ApiKeyStore

    @Binds
    @Singleton
    abstract fun bindSecretCipher(impl: AndroidKeystoreSecretCipher): SecretCipher
}
