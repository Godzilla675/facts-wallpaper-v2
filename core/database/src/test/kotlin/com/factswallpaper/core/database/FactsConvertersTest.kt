package com.factswallpaper.core.database

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FactsConvertersTest {

    private val converters = FactsConverters()

    @Test
    fun `pdf route round trips and rejects unknown values`() {
        PdfProcessingRoute.entries.forEach { route ->
            val stored = converters.pdfRouteToString(route)
            assertThat(stored).isEqualTo(route.name)
            assertThat(converters.stringToPdfRoute(stored)).isEqualTo(route)
        }
        assertThat(converters.stringToPdfRoute("nonsense")).isNull()
        assertThat(converters.stringToPdfRoute(null)).isNull()
        assertThat(converters.pdfRouteToString(null)).isNull()
    }

    @Test
    fun `template type round trips and rejects unknown values`() {
        CardTemplateType.entries.forEach { type ->
            val stored = converters.cardTemplateTypeToString(type)
            assertThat(stored).isEqualTo(type.name)
            assertThat(converters.stringToCardTemplateType(stored)).isEqualTo(type)
        }
        assertThat(converters.stringToCardTemplateType("UNKNOWN")).isNull()
    }

    @Test
    fun `verification status round trips and rejects unknown values`() {
        VerificationStatus.entries.forEach { status ->
            val stored = converters.verificationStatusToString(status)
            assertThat(stored).isEqualTo(status.name)
            assertThat(converters.stringToVerificationStatus(stored)).isEqualTo(status)
        }
        assertThat(converters.stringToVerificationStatus("MAYBE")).isNull()
    }
}
