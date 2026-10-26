package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.ProviderProfileDao
import com.factswallpaper.core.model.ProviderProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomProviderProfileRepository @Inject constructor(
    private val providerProfileDao: ProviderProfileDao,
    private val dispatchers: FactsDispatchers,
) : ProviderProfileRepository {

    override fun observeProfiles(): Flow<List<ProviderProfile>> =
        providerProfileDao.observeAll().map { profiles -> profiles.map { it.toDomain() } }

    override suspend fun getProfiles(): List<ProviderProfile> =
        withContext(dispatchers.io) { providerProfileDao.getAll().map { it.toDomain() } }

    override suspend fun getProfile(profileId: String): ProviderProfile? =
        withContext(dispatchers.io) { providerProfileDao.getById(profileId)?.toDomain() }

    override suspend fun saveProfile(profile: ProviderProfile) {
        withContext(dispatchers.io) { providerProfileDao.upsert(profile.toEntity()) }
    }

    override suspend fun deleteProfile(profileId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                providerProfileDao.deleteById(profileId)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t))
            }
        }

    override suspend fun setDefaultProfile(profileId: String) {
        withContext(dispatchers.io) { providerProfileDao.setDefault(profileId) }
    }
}
