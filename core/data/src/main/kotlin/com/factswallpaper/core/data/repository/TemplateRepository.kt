package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.CardTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun observeTemplates(): Flow<List<CardTemplate>>
    fun observeDefaultTemplate(): Flow<CardTemplate?>
    suspend fun getTemplates(): List<CardTemplate>
    suspend fun getTemplate(templateId: String): CardTemplate?
    suspend fun saveTemplate(template: CardTemplate)
    suspend fun saveTemplates(templates: List<CardTemplate>)
    suspend fun deleteCustomTemplate(templateId: String): AppResult<Unit>
    suspend fun setDefaultTemplate(templateId: String)
}
