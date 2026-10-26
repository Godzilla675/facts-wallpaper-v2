package com.factswallpaper.core.data.repository

import com.factswallpaper.core.model.Lesson
import kotlinx.coroutines.flow.Flow

interface LessonRepository {
    fun observeLessons(documentId: String): Flow<List<Lesson>>
    fun observeLesson(lessonId: String): Flow<Lesson?>
    suspend fun getLessons(documentId: String): List<Lesson>
    suspend fun replaceLessons(documentId: String, lessons: List<Lesson>)
    suspend fun upsert(lesson: Lesson)
    suspend fun delete(lessonId: String)
    suspend fun setSelected(lessonId: String, selected: Boolean)
    suspend fun setAllSelected(documentId: String, selected: Boolean)
}
