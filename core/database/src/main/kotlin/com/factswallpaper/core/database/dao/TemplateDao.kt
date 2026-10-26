package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TemplateDao {

    @Query("SELECT * FROM templates ORDER BY builtIn DESC, name ASC")
    abstract fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY builtIn DESC, name ASC")
    abstract suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    abstract suspend fun getById(id: String): TemplateEntity?

    @Query("SELECT * FROM templates WHERE isDefault = 1 LIMIT 1")
    abstract fun observeDefault(): Flow<TemplateEntity?>

    @Upsert
    abstract suspend fun upsertAll(templates: List<TemplateEntity>)

    @Upsert
    abstract suspend fun upsert(template: TemplateEntity)

    /** Built-in templates can never be deleted; the guard lives in SQL itself. */
    @Query("DELETE FROM templates WHERE id = :id AND builtIn = 0")
    abstract suspend fun deleteCustomById(id: String)

    @Query("UPDATE templates SET isDefault = 0")
    abstract suspend fun clearDefault()

    @Query("UPDATE templates SET isDefault = 1 WHERE id = :id")
    abstract suspend fun markDefault(id: String)

    @Transaction
    open suspend fun setDefault(id: String) {
        clearDefault()
        markDefault(id)
    }
}
