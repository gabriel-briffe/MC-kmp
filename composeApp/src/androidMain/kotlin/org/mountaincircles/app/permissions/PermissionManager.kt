package org.mountaincircles.app.permissions

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlin.coroutines.resume

/**
 * Android implementation of permission manager
 */
actual class PermissionManager(private val activity: ComponentActivity) {
    
    private var currentCallback: ((Map<String, Boolean>) -> Unit)? = null
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> = 
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            currentCallback?.invoke(permissions)
            currentCallback = null
        }
    
    actual fun isPermissionGranted(permission: PermissionType): Boolean {
        val androidPermission = mapToAndroidPermission(permission)
        return ContextCompat.checkSelfPermission(activity, androidPermission) == PackageManager.PERMISSION_GRANTED
    }

    actual fun isLocationServicesEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    
    actual suspend fun requestPermission(permission: PermissionType): PermissionResult {
        val result = requestPermissions(listOf(permission))
        return result[permission] ?: PermissionResult.Denied
    }
    
    actual suspend fun requestPermissions(permissions: List<PermissionType>): Map<PermissionType, PermissionResult> {
        Logger.log("PERMISSIONS", LogLevel.INFO, "Requesting permissions: ${permissions.map { it.name }}")
        
        val androidPermissions = permissions.map { mapToAndroidPermission(it) }.toTypedArray()
        val permissionTypeMap = permissions.associateBy { mapToAndroidPermission(it) }
        
        // Check if all permissions are already granted
        val alreadyGranted = androidPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (alreadyGranted) {
            Logger.log("PERMISSIONS", LogLevel.INFO, "All permissions already granted")
            return permissions.associateWith { PermissionResult.AlreadyGranted }
        }
        
        return suspendCancellableCoroutine { continuation ->
            currentCallback = { results ->
                Logger.log("PERMISSIONS", LogLevel.INFO, "Permission results received: $results")
                
                val mappedResults = mutableMapOf<PermissionType, PermissionResult>()
                
                for ((androidPermission, granted) in results) {
                    val permissionType = permissionTypeMap[androidPermission]
                    if (permissionType != null) {
                        val result = when {
                            granted -> PermissionResult.Granted
                            shouldShowRationale(permissionType) -> PermissionResult.Denied
                            else -> PermissionResult.DeniedPermanently
                        }
                        mappedResults[permissionType] = result
                        
                        Logger.log("PERMISSIONS", LogLevel.INFO, 
                            "Permission ${permissionType.name}: ${result::class.simpleName}")
                    }
                }
                
                continuation.resume(mappedResults)
            }
            
            try {
                permissionLauncher.launch(androidPermissions)
            } catch (e: Exception) {
                Logger.log("PERMISSIONS", LogLevel.ERROR, "Failed to launch permission request: ${e.message}", e)
                val errorResults = permissions.associateWith { PermissionResult.Denied }
                continuation.resume(errorResults)
            }
        }
    }
    
    actual fun shouldShowRationale(permission: PermissionType): Boolean {
        val androidPermission = mapToAndroidPermission(permission)
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, androidPermission)
    }

    actual fun promptEnableLocationServices() {
        Logger.log("PERMISSIONS", LogLevel.INFO, "Prompting user to enable location services")
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Logger.log("PERMISSIONS", LogLevel.ERROR, "Failed to open location settings: ${e.message}", e)
        }
    }

    /**
     * Get a Flow that emits when location services state changes
     */
    actual fun locationServicesStateFlow(): kotlinx.coroutines.flow.Flow<Boolean> = callbackFlow {
        var lastKnownState: Boolean? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    val currentState = isLocationServicesEnabled()
                    // Only emit if the state actually changed
                    if (lastKnownState != currentState) {
                        lastKnownState = currentState
                        Logger.log("PERMISSIONS", LogLevel.INFO, "Location services state actually changed: $currentState")
                        trySend(currentState)
                    }
                }
            }
        }

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        activity.registerReceiver(receiver, filter)

        // Send initial state
        val initialState = isLocationServicesEnabled()
        lastKnownState = initialState
        trySend(initialState)
        Logger.log("PERMISSIONS", LogLevel.INFO, "Location services flow initialized with state: $initialState")

        awaitClose {
            try {
                activity.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Logger.log("PERMISSIONS", LogLevel.WARN, "Failed to unregister location services receiver: ${e.message}")
            }
        }
    }
    
    private fun mapToAndroidPermission(permission: PermissionType): String {
        return when (permission) {
            PermissionType.LOCATION_FINE -> Manifest.permission.ACCESS_FINE_LOCATION
            PermissionType.LOCATION_COARSE -> Manifest.permission.ACCESS_COARSE_LOCATION
            PermissionType.STORAGE_READ -> Manifest.permission.READ_EXTERNAL_STORAGE
            PermissionType.STORAGE_WRITE -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }
}

/**
 * Static holder for the activity context and permission manager
 */
private var currentActivity: ComponentActivity? = null
private var currentPermissionManager: PermissionManager? = null

fun setPermissionActivity(activity: ComponentActivity) {
    currentActivity = activity
    Logger.log("PERMISSIONS", LogLevel.DEBUG, "Permission activity set")
}

fun setPermissionManager(manager: PermissionManager) {
    currentPermissionManager = manager
    Logger.log("PERMISSIONS", LogLevel.DEBUG, "Permission manager set")
}

fun getCurrentActivity(): ComponentActivity? {
    return currentActivity
}

actual fun createPermissionManager(): PermissionManager {
    // First try to get the static instance
    currentPermissionManager?.let { return it }
    
    // Fallback to creating a new one
    val activity = currentActivity 
        ?: throw IllegalStateException("Activity not set. Call setPermissionActivity() in MainActivity.onCreate()")
    
    Logger.log("PERMISSIONS", LogLevel.DEBUG, "Creating Android PermissionManager")
    return PermissionManager(activity)
}
