package com.factswallpaper.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource

/** Presents a [UiMessage] once, then clears it on the view model. */
@Composable
fun UiMessageEffect(
    message: UiMessage?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: () -> Unit,
) {
    val text = message?.let {
        stringResource(it.textRes, *it.args.toTypedArray())
    }
    LaunchedEffect(message) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }
}
