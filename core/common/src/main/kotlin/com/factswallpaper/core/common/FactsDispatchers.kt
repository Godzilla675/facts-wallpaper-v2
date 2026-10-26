package com.factswallpaper.core.common

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** Injected coroutine dispatchers so repository and worker code stays testable. */
class FactsDispatchers @Inject constructor(
    @IoDispatcher val io: CoroutineDispatcher,
    @DefaultDispatcher val default: CoroutineDispatcher,
    @MainDispatcher val main: CoroutineDispatcher,
)
