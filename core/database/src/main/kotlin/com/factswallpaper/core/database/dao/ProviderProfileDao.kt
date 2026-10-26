package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.ProviderProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProviderProfileDao {

    @Query("SELECT * FROM provider_profiles ORDER BY isDefault DESC, name ASC")
    abstract fun observeAll(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles ORDER BY isDefault DESC, name ASC")
    abstract suspend fun getAll(): List<ProviderProfileEntity>

    @Query("SELECT * FROM provider_profiles WHERE id = :id")
    abstract suspend fun getById(id: String): ProviderProfileEntity?

    @Upsert
    abstract suspend fun upsert(profile: ProviderProfileEntity)

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("UPDATE provider_profiles SET isDefault = 0")
    abstract suspend fun clearDefault()

    @Query("UPDATE provider_profiles SET isDefault = 1 WHERE id = :id")
    abstract suspend fun markDefault(id: String)

    @Transaction
    open suspend fun setDefault(id: String) {
        clearDefault()
        markDefault(id)
    }
}
