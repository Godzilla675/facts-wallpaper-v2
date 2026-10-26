package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.TemplateDao
import com.factswallpaper.core.model.CardTemplate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomTemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val dispatchers: FactsDispatchers,
) : TemplateRepository {

    override fun observeTemplates(): Flow<List<CardTemplate>> =
        templateDao.observeAll().map { templates -> templates.map { it.toDomain() } }

    override fun observeDefaultTemplate(): Flow<CardTemplate?> =
        templateDao.observeDefault().map { it?.toDomain() }

    override suspend fun getTemplate(templateId: String): CardTemplate? =
        withContext(dispatchers.io) { templateDao.getById(templateId)?.toDomain() }

    override suspend fun saveTemplate(template: CardTemplate) {
        withContext(dispatchers.io) { templateDao.upsert(template.toEntity()) }
    }

    override suspend fun saveTemplates(templates: List<CardTemplate>) {
        withContext(dispatchers.io) { templateDao.upsertAll(templates.map { it.toEntity() }) }
    }

    override suspend fun deleteCustomTemplate(templateId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val template = templateDao.getById(templateId)
                ?: return@withContext AppResult.Failure(AppError.NotFound("Template"))
            if (template.builtIn) {
                return@withContext AppResult.Failure(
                    AppError.InvalidOperation("Built-in templates cannot be deleted")
                )
            }
            try {
                templateDao.deleteCustomById(templateId)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t))
            }
        }

    override suspend fun setDefaultTemplate(templateId: String) {
        withContext(dispatchers.io) { templateDao.setDefault(templateId) }
    }
}
