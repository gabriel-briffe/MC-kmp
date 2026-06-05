package org.mountaincircles.app.network

import kotlinx.coroutines.flow.Flow

/**
 * Common interface for network connectivity monitoring
 */
interface NetworkMonitor {
    /**
     * Flow that emits true when network is available, false when unavailable
     */
    val isNetworkAvailable: Flow<Boolean>

    /**
     * Check if network is currently validated for data transmission
     * Returns true only when network has internet capability AND has been validated
     * Default implementation returns true (for platforms without validation check)
     */
    fun isNetworkCurrentlyValidated(): Boolean = true
}