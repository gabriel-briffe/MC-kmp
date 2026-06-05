package org.mountaincircles.app.permissions

import kotlinx.coroutines.flow.Flow

/**
 * Permission types that can be requested
 */
enum class PermissionType {
    LOCATION_FINE,
    LOCATION_COARSE,
    STORAGE_READ,
    STORAGE_WRITE
}

/**
 * Permission request result
 */
sealed class PermissionResult {
    object Granted : PermissionResult()
    object Denied : PermissionResult()
    object DeniedPermanently : PermissionResult()
    object AlreadyGranted : PermissionResult()
}

/**
 * Cross-platform permission manager interface
 */
expect class PermissionManager {
    /**
     * Check if a permission is currently granted
     */
    fun isPermissionGranted(permission: PermissionType): Boolean

    /**
     * Check if location services are enabled on the device
     */
    fun isLocationServicesEnabled(): Boolean

    /**
     * Request a single permission
     */
    suspend fun requestPermission(permission: PermissionType): PermissionResult

    /**
     * Request multiple permissions
     */
    suspend fun requestPermissions(permissions: List<PermissionType>): Map<PermissionType, PermissionResult>

    /**
     * Check if we should show rationale for a permission
     */
    fun shouldShowRationale(permission: PermissionType): Boolean

    /**
     * Prompt user to enable location services (Android only)
     */
    fun promptEnableLocationServices()

    /**
     * Get a reactive flow for location services state changes (Android only)
     */
    fun locationServicesStateFlow(): kotlinx.coroutines.flow.Flow<Boolean>
}

/**
 * Platform-specific function to create permission manager
 */
expect fun createPermissionManager(): PermissionManager
