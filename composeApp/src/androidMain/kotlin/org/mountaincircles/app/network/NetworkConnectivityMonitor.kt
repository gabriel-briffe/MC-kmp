package org.mountaincircles.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Android implementation of network connectivity monitoring
 */
class AndroidNetworkConnectivityMonitor(
    private val context: Context
) : org.mountaincircles.app.network.NetworkMonitor {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Flow that emits true when network is available and validated, false when unavailable
     */
    override val isNetworkAvailable: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Logger.log("NETWORK", LogLevel.INFO, "Network became available")
                // Don't automatically send true - wait for validation
            }

            override fun onLost(network: Network) {
                Logger.log("NETWORK", LogLevel.INFO, "Network connection lost")
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Logger.log("NETWORK", LogLevel.DEBUG, "Network capabilities changed - hasInternet: $hasInternet, validated: $isValidated")

                // Only consider network available when both internet capability AND validation are present
                val isAvailable = hasInternet && isValidated
                trySend(isAvailable)

                if (isAvailable) {
                    Logger.log("NETWORK", LogLevel.INFO, "Network validated and ready for data transmission")
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Send initial validated state (don't assume available just because network exists)
        val initialState = isNetworkCurrentlyValidated()
        Logger.log("NETWORK", LogLevel.INFO, "Initial validated network state: $initialState")
        trySend(initialState)

        awaitClose {
            Logger.log("NETWORK", LogLevel.DEBUG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    /**
     * Check current network validation status synchronously
     * Returns true only when network has internet capability AND has been validated
     */
    override fun isNetworkCurrentlyValidated(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return hasInternet && isValidated
    }
}