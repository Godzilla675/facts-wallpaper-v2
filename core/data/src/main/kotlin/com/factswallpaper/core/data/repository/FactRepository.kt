package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.SmartViewCounts
import kotlinx.coroutines.flow.Flow

interface FactRepository {
    fun observeFacts(filter: FactFilter = FactFilter()): Flow<List<Fact>>
    fun observeFact(factId: String): Flow<Fact?>
    suspend fun getFact(factId: String): Fact?
    suspend fun saveFact(fact: Fact)
    suspend fun saveFacts(facts: List<Fact>)
    suspend fun setActive(factId: String, active: Boolean)
    suspend fun setFavorite(factId: String, favorite: Boolean)
    suspend fun recordReview(factId: String, gotIt: Boolean) {}
    suspend fun deleteFact(factId: String): AppResult<Unit>
    fun observeTags(): Flow<List<FactTag>>
    fun observeSmartViewCounts(): Flow<SmartViewCounts>
    fun observeTotalCount(): Flow<Int>
}
