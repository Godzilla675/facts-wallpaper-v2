package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.ProviderProfile
import kotlinx.coroutines.flow.Flow

interface ProviderProfileRepository {
    fun observeProfiles(): Flow<List<ProviderProfile>>
    suspend fun getProfiles(): List<ProviderProfile>
    suspend fun getProfile(profileId: String): ProviderProfile?
    suspend fun saveProfile(profile: ProviderProfile)
    suspend fun deleteProfile(profileId: String): AppResult<Unit>
    suspend fun setDefaultProfile(profileId: String)
}
