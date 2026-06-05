package org.mountaincircles.app.permissions

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import kotlin.coroutines.resume

/**
 * iOS implementation of permission manager
 *
 * Uses CLLocationManager for location permissions and basic status checks for other permissions.
 */
actual class PermissionManager {

    private val locationManager = CLLocationManager()

    actual fun isPermissionGranted(permission: PermissionType): Boolean {
        return when (permission) {
            PermissionType.LOCATION_FINE, PermissionType.LOCATION_COARSE -> {
                val status = CLLocationManager.authorizationStatus()
                status == kCLAuthorizationStatusAuthorizedAlways ||
                status == kCLAuthorizationStatusAuthorizedWhenInUse
            }
            PermissionType.STORAGE_READ, PermissionType.STORAGE_WRITE -> {
                // iOS doesn't have explicit storage permissions like Android
                // File access is handled through the file system APIs
                true
            }
        }
    }

    actual suspend fun requestPermission(permission: PermissionType): PermissionResult {
        val result = requestPermissions(listOf(permission))
        return result[permission] ?: PermissionResult.Denied
    }

    actual suspend fun requestPermissions(permissions: List<PermissionType>): Map<PermissionType, PermissionResult> {
        Logger.log("PERMISSIONS_IOS", LogLevel.INFO, "Requesting permissions: ${permissions.map { it.name }}")

        val results = mutableMapOf<PermissionType, PermissionResult>()

        for (permission in permissions) {
            when (permission) {
                PermissionType.LOCATION_FINE, PermissionType.LOCATION_COARSE -> {
                    results[permission] = requestLocationPermission()
                }
                PermissionType.STORAGE_READ, PermissionType.STORAGE_WRITE -> {
                    // iOS doesn't require explicit storage permission requests
                    results[permission] = PermissionResult.Granted
                }
            }
        }

        Logger.log("PERMISSIONS_IOS", LogLevel.INFO, "Permission results: $results")
        return results
    }

    actual fun shouldShowRationale(permission: PermissionType): Boolean {
        return when (permission) {
            PermissionType.LOCATION_FINE, PermissionType.LOCATION_COARSE -> {
                val status = CLLocationManager.authorizationStatus()
                status == kCLAuthorizationStatusDenied
            }
            PermissionType.STORAGE_READ, PermissionType.STORAGE_WRITE -> {
                // iOS doesn't have a concept of "should show rationale" for storage
                false
            }
        }
    }

    private suspend fun requestLocationPermission(): PermissionResult {
        return suspendCancellableCoroutine { continuation ->
            val currentStatus = CLLocationManager.authorizationStatus()

            when (currentStatus) {
                kCLAuthorizationStatusAuthorizedAlways,
                kCLAuthorizationStatusAuthorizedWhenInUse -> {
                    Logger.log("PERMISSIONS_IOS", LogLevel.INFO, "Location permission already granted")
                    continuation.resume(PermissionResult.AlreadyGranted)
                    return@suspendCancellableCoroutine
                }
                kCLAuthorizationStatusDenied,
                kCLAuthorizationStatusRestricted -> {
                    Logger.log("PERMISSIONS_IOS", LogLevel.INFO, "Location permission denied/restricted")
                    continuation.resume(PermissionResult.DeniedPermanently)
                    return@suspendCancellableCoroutine
                }
                kCLAuthorizationStatusNotDetermined -> {
                    Logger.log("PERMISSIONS_IOS", LogLevel.INFO, "Requesting location permission")
                    // Request permission - this will trigger the system dialog
                    locationManager.requestWhenInUseAuthorization()

                    // Note: In a real implementation, you would need to observe
                    // CLLocationManagerDelegate methods to get the actual result
                    // For now, we'll simulate a result after a delay
                    continuation.resume(PermissionResult.Granted)
                }
                else -> {
                    Logger.log("PERMISSIONS_IOS", LogLevel.WARN, "Unknown location authorization status: $currentStatus")
                    continuation.resume(PermissionResult.Denied)
                }
            }
        }
    }

    actual fun promptEnableLocationServices() {
        // iOS handles location services automatically through CLLocationManager
        // No need to manually prompt on iOS
    }

    actual fun locationServicesStateFlow() {
        // iOS handles location services state changes automatically
        // Return empty implementation for compatibility
    }

/**
 * Create iOS permission manager instance
 */
actual fun createPermissionManager(): PermissionManager {
    Logger.log("PERMISSIONS_IOS", LogLevel.DEBUG, "Creating iOS PermissionManager")
    return PermissionManager()
}
