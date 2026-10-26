package com.factswallpaper.core.common

/**
 * Port for requesting a proactive card pre-render pass (§F14).
 * Enqueues rendering of cards for the active rotation pool.
 */
interface CardPreRenderNotifier {
    fun enqueue()
}
