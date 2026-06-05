package org.mountaincircles.app.state

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * State synchronization middleware for the action system
 * Note: Currently unused - StateSynchronizer was removed as it was not functional.
 * The middleware system is not initialized in the app.
 */
class StateSyncMiddleware : EffectMiddleware {

    override val middlewareName: String = "StateSyncMiddleware"

    override suspend fun process(action: StateAction): List<Effect> {
        // Middleware system is not currently used - return empty effects
        Logger.log("StateSyncMiddleware", LogLevel.DEBUG, "Processing action: ${action::class.simpleName} (no-op)")
        return emptyList()
    }
}