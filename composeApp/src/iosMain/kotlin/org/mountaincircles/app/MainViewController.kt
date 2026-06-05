package org.mountaincircles.app

import androidx.compose.ui.window.ComposeUIViewController
import org.mountaincircles.app.logger.LogConfig
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.MainApp
import platform.UIKit.UIViewController

/**
 * iOS main application entry point
 *
 * This creates a UIViewController that hosts the Compose UI.
 * The UIViewController will be used by the iOS app to display the Compose content.
 */
fun MainViewController(): UIViewController {
    Logger.log("STARTUP_IOS", LogLevel.INFO, "Creating MainViewController")

    // Configure logging for production
    // LogConfig.enableNone()

    // Alternative: Enable all logs for development debugging
    LogConfig.enableAll()

    // Return Compose UIViewController with our MainApp composable
    return ComposeUIViewController {
        MainApp()
    }
}
