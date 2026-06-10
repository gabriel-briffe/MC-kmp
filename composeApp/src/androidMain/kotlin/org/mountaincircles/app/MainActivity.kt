package org.mountaincircles.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogConfig
import org.mountaincircles.app.io.initializeAndroidAppStorage
import org.mountaincircles.app.modules.skysight.initializeAndroidSkysight
import org.mountaincircles.app.modules.circles.CirclesFilePickerManager
import org.mountaincircles.app.modules.circles.setCirclesFilePickerManager
import org.mountaincircles.app.modules.airports.CupFilePickerManager
import org.mountaincircles.app.modules.airports.setCupFilePickerManager
import org.mountaincircles.app.permissions.PermissionManager
import org.mountaincircles.app.permissions.setPermissionActivity
import org.mountaincircles.app.permissions.setPermissionManager
import org.mountaincircles.app.ui.MainApp

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for edge-to-edge layout and dark status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Keep screen on for navigation/map app - programmatic approach (more reliable than manifest flag)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Logger.log("STARTUP", LogLevel.INFO, "MainActivity onCreate")

        LogConfig.enableAll()

        initializeAndroidAppStorage(this)

        // Initialize Skysight system
        initializeAndroidSkysight(this)

        // Create and set permission manager EARLY (before STARTED state)
        permissionManager = PermissionManager(this)
        setPermissionManager(permissionManager)
        
        // Set activity for permission manager
        setPermissionActivity(this)
        
        // Create and set circles file picker manager EARLY (before STARTED state)
        val circlesFilePickerManager = CirclesFilePickerManager(this)
        setCirclesFilePickerManager(circlesFilePickerManager)

        // Create and set CUP file picker manager EARLY (before STARTED state)
        val cupFilePickerManager = CupFilePickerManager(this)
        setCupFilePickerManager(cupFilePickerManager)

        setContent {
            MainApp()
        }
    }
    
    override fun onDestroy() {
        // Clear the keep screen on flag to allow normal screen timeout behavior
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
        Logger.log("STARTUP", LogLevel.INFO, "MainActivity onDestroy")
    }
}