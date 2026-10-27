package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.FactTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FactDao {

    @Query("SELECT * FROM facts ORDER BY createdAtEpochMs DESC")
    abstract fun observeAll(): Flow<List<FactItemEntity>>

    @Query("SELECT * FROM facts WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC")
    abstract fun observeByDocument(documentId: String): Flow<List<FactItemEntity>>

    @Query("SELECT * FROM facts WHERE lessonId = :lessonId ORDER BY createdAtEpochMs ASC")
    abstract fun observeByLesson(lessonId: String): Flow<List<FactItemEntity>>

    @Query("SELECT * FROM facts WHERE active = 1 ORDER BY createdAtEpochMs ASC")
    abstract fun observeAllActive(): Flow<List<FactItemEntity>>

    /** Active facts in canonical sequential order, ignoring document rotation flags. */
    @Query(
        """
        SELECT f.* FROM facts f
        JOIN pdf_documents d ON d.id = f.documentId
        LEFT JOIN lessons l ON l.id = f.lessonId
        WHERE f.active = 1
        ORDER BY d.createdAtEpochMs ASC,
                 CASE WHEN l.sortOrder IS NULL THEN 2147483647 ELSE l.sortOrder END ASC,
                 f.createdAtEpochMs ASC
        """
    )
    abstract fun observeAllActiveOrdered(): Flow<List<FactItemEntity>>

    /**
     * Active facts whose parent document participates in rotation, in the canonical
     * sequential order (document import, lesson order, then fact creation).
     * Collection/folder narrowing is applied by `RotationPoolRepository` using the
     * join-table queries in [CollectionDao].
     */
    @Query(
        """
        SELECT f.* FROM facts f
        JOIN pdf_documents d ON d.id = f.documentId
        LEFT JOIN lessons l ON l.id = f.lessonId
        WHERE f.active = 1 AND d.includedInRotation = 1
        ORDER BY d.createdAtEpochMs ASC,
                 CASE WHEN l.sortOrder IS NULL THEN 2147483647 ELSE l.sortOrder END ASC,
                 f.createdAtEpochMs ASC
        """
    )
    abstract fun observeActiveRotationPool(): Flow<List<FactItemEntity>>

    @Query("SELECT * FROM facts WHERE id = :factId")
    abstract fun observeById(factId: String): Flow<FactItemEntity?>

    @Query("SELECT * FROM facts WHERE id = :factId")
    abstract suspend fun getById(factId: String): FactItemEntity?

    @Query("SELECT COUNT(*) FROM facts")
    abstract fun observeTotalCount(): Flow<Int>

    @Query("SELECT documentId, COUNT(*) AS count FROM facts GROUP BY documentId")
    abstract fun observeFactCountsByDocument(): Flow<List<DocumentFactCount>>

    @Query("SELECT COUNT(*) FROM facts WHERE documentId = :documentId")
    abstract suspend fun countByDocument(documentId: String): Int

    @Query("SELECT verificationStatus, COUNT(*) AS count FROM facts GROUP BY verificationStatus")
    abstract fun observeVerificationCounts(): Flow<List<VerificationStatusCount>>

    @Query("SELECT DISTINCT tag FROM fact_tags ORDER BY tag ASC")
    abstract fun observeAllDistinctTags(): Flow<List<String>>

    @Query("SELECT tag, COUNT(*) AS count FROM fact_tags GROUP BY tag ORDER BY tag ASC")
    abstract fun observeTagCounts(): Flow<List<FactTagCount>>

    @Upsert
    abstract suspend fun upsertAll(facts: List<FactItemEntity>)

    @Upsert
    abstract suspend fun upsert(fact: FactItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTags(tags: List<FactTagEntity>)

    @Query("DELETE FROM fact_tags WHERE factId = :factId")
    abstract suspend fun deleteTagsForFact(factId: String)

    @Query("DELETE FROM facts WHERE id = :factId")
    abstract suspend fun deleteById(factId: String)

    @Query("UPDATE facts SET active = :active, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun setActive(factId: String, active: Boolean, updatedAtEpochMs: Long)

    @Query("UPDATE facts SET favorite = :favorite, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun setFavorite(factId: String, favorite: Boolean, updatedAtEpochMs: Long)

    @Query("UPDATE facts SET reviewGotItCount = reviewGotItCount + 1, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun incrementGotItCount(factId: String, updatedAtEpochMs: Long)

    @Query("UPDATE facts SET reviewAgainCount = reviewAgainCount + 1, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun incrementAgainCount(factId: String, updatedAtEpochMs: Long)

    @Query("UPDATE facts SET reviewGotItCount = reviewGotItCount + 1, nextReviewDueEpochMs = :nextReviewDueEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun recordGotIt(factId: String, nextReviewDueEpochMs: Long, updatedAtEpochMs: Long)

    @Query("UPDATE facts SET reviewAgainCount = reviewAgainCount + 1, nextReviewDueEpochMs = :nextReviewDueEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :factId")
    abstract suspend fun recordAgain(factId: String, nextReviewDueEpochMs: Long, updatedAtEpochMs: Long)

    @Transaction
    open suspend fun upsertFactWithTags(fact: FactItemEntity, tags: List<String>) {
        upsert(fact)
        deleteTagsForFact(fact.id)
        val normalized = tags.map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        insertTags(normalized.map { FactTagEntity(factId = fact.id, tag = it) })
    }
}
