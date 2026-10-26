package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.SmartViewCounts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomFactRepository @Inject constructor(
    private val factDao: FactDao,
    private val dispatchers: FactsDispatchers,
) : FactRepository {

    override fun observeFacts(filter: FactFilter): Flow<List<Fact>> {
        val documentId = filter.documentId
        val source = if (documentId != null) {
            factDao.observeByDocument(documentId)
        } else {
            factDao.observeAll()
        }
        return source.map { entities ->
            entities.asSequence()
                .map { it.toDomain() }
                .filter(filter::matches)
                .sortedByDescending { it.updatedAtEpochMs }
                .toList()
        }
    }

    override fun observeFact(factId: String): Flow<Fact?> =
        factDao.observeById(factId).map { it?.toDomain() }

    override suspend fun getFact(factId: String): Fact? =
        withContext(dispatchers.io) { factDao.getById(factId)?.toDomain() }

    override suspend fun saveFact(fact: Fact) {
        withContext(dispatchers.io) { factDao.upsertFactWithTags(fact.toEntity(), fact.tags) }
    }

    override suspend fun saveFacts(facts: List<Fact>) {
        withContext(dispatchers.io) {
            factDao.upsertAll(facts.map { it.toEntity() })
        }
    }

    override suspend fun setActive(factId: String, active: Boolean) {
        withContext(dispatchers.io) {
            factDao.setActive(factId, active, System.currentTimeMillis())
        }
    }

    override suspend fun setFavorite(factId: String, favorite: Boolean) {
        withContext(dispatchers.io) {
            factDao.setFavorite(factId, favorite, System.currentTimeMillis())
        }
    }

    override suspend fun deleteFact(factId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                factDao.deleteById(factId)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t))
            }
        }

    override fun observeTags(): Flow<List<FactTag>> =
        factDao.observeTagCounts().map { counts ->
            counts.map { FactTag(tag = it.tag, factCount = it.count) }
        }

    override fun observeSmartViewCounts(): Flow<SmartViewCounts> =
        factDao.observeAll().map { entities ->
            val facts = entities.map { it.toDomain() }
            SmartViewCounts(
                total = facts.size,
                active = facts.count { it.active },
                favorites = facts.count { it.favorite },
                formulas = facts.count {
                    it.hasFormula || it.templateType == CardTemplateType.FORMULA
                },
                diagrams = facts.count { it.hasDiagram },
            )
        }

    override fun observeTotalCount(): Flow<Int> = factDao.observeTotalCount()
}
