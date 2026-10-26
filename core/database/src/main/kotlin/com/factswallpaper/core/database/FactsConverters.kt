package com.factswallpaper.core.database

import androidx.room.TypeConverter
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.VerificationStatus

/**
 * Room stores enum-like values as their stable names (the schema in the spec is
 * explicit about this). Converters are used by aggregate projection queries so
 * typed counters do not have to re-parse strings at every call site.
 */
class FactsConverters {

    @TypeConverter
    fun pdfRouteToString(value: PdfProcessingRoute?): String? = value?.name

    @TypeConverter
    fun stringToPdfRoute(value: String?): PdfProcessingRoute? =
        value?.let { runCatching { PdfProcessingRoute.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun cardTemplateTypeToString(value: CardTemplateType?): String? = value?.name

    @TypeConverter
    fun stringToCardTemplateType(value: String?): CardTemplateType? =
        value?.let { runCatching { CardTemplateType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun verificationStatusToString(value: VerificationStatus?): String? = value?.name

    @TypeConverter
    fun stringToVerificationStatus(value: String?): VerificationStatus? =
        value?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
}
