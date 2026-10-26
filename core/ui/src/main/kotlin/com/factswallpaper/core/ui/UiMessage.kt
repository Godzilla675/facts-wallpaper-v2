package com.factswallpaper.core.ui

import androidx.annotation.StringRes

/**
 * A user-facing transient message. Feature view models never hold raw display
 * strings: they carry a string resource id (plus optional format args) so all
 * copy remains in `strings.xml` and is localization-ready.
 */
data class UiMessage(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
)
