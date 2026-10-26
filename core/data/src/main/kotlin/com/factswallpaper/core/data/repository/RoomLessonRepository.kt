package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.LessonDao
import com.factswallpaper.core.model.Lesson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomLessonRepository @Inject constructor(
    private val lessonDao: LessonDao,
    private val dispatchers: FactsDispatchers,
) : LessonRepository {

    override fun observeLessons(documentId: String): Flow<List<Lesson>> =
        lessonDao.observeByDocument(documentId).map { lessons -> lessons.map { it.toDomain() } }

    override fun observeLesson(lessonId: String): Flow<Lesson?> =
        lessonDao.observeById(lessonId).map { it?.toDomain() }

    override suspend fun getLessons(documentId: String): List<Lesson> =
        withContext(dispatchers.io) { lessonDao.getByDocument(documentId).map { it.toDomain() } }

    override suspend fun replaceLessons(documentId: String, lessons: List<Lesson>) {
        withContext(dispatchers.io) {
            lessonDao.replaceDocumentLessons(documentId, lessons.map { it.toEntity() })
        }
    }

    override suspend fun upsert(lesson: Lesson) {
        withContext(dispatchers.io) { lessonDao.upsert(lesson.toEntity()) }
    }

    override suspend fun delete(lessonId: String) {
        withContext(dispatchers.io) { lessonDao.deleteById(lessonId) }
    }

    override suspend fun setSelected(lessonId: String, selected: Boolean) {
        withContext(dispatchers.io) { lessonDao.updateSelection(lessonId, selected) }
    }

    override suspend fun setAllSelected(documentId: String, selected: Boolean) {
        withContext(dispatchers.io) { lessonDao.updateAllSelections(documentId, selected) }
    }
}
