package com.factswallpaper.core.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base view model for feature screens. Each screen owns a single immutable
 * `UiState` flow plus this transient snackbar channel, which keeps state
 * restoration deterministic and avoids one-shot events being lost on rotation.
 */
abstract class FeatureViewModel : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    protected fun showMessage(@StringRes textRes: Int, vararg args: Any) {
        _message.value = UiMessage(textRes, args.toList())
    }

    fun onMessageShown() {
        _message.value = null
    }
}
