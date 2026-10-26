package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LessonDao {

    @Query("SELECT * FROM lessons WHERE documentId = :documentId ORDER BY sortOrder ASC")
    abstract fun observeByDocument(documentId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE documentId = :documentId ORDER BY sortOrder ASC")
    abstract suspend fun getByDocument(documentId: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    abstract fun observeById(lessonId: String): Flow<LessonEntity?>

    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    abstract suspend fun getById(lessonId: String): LessonEntity?

    @Query("SELECT documentId, COUNT(*) AS count FROM lessons GROUP BY documentId")
    abstract fun observeLessonCounts(): Flow<List<DocumentLessonCount>>

    @Query("SELECT COUNT(*) FROM lessons WHERE documentId = :documentId")
    abstract suspend fun countByDocument(documentId: String): Int

    @Upsert
    abstract suspend fun upsert(lesson: LessonEntity)

    @Upsert
    abstract suspend fun upsertAll(lessons: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE id = :lessonId")
    abstract suspend fun deleteById(lessonId: String)

    @Query("DELETE FROM lessons WHERE documentId = :documentId")
    abstract suspend fun deleteByDocument(documentId: String)

    @Query("UPDATE lessons SET selected = :selected WHERE id = :lessonId")
    abstract suspend fun updateSelection(lessonId: String, selected: Boolean)

    @Query("UPDATE lessons SET selected = :selected WHERE documentId = :documentId")
    abstract suspend fun updateAllSelections(documentId: String, selected: Boolean)

    @Transaction
    open suspend fun replaceDocumentLessons(documentId: String, lessons: List<LessonEntity>) {
        deleteByDocument(documentId)
        upsertAll(lessons)
    }
}
