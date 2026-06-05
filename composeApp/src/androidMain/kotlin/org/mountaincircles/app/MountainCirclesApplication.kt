package org.mountaincircles.app

import android.app.Application
import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.io.setGlobalFileManager
import org.mountaincircles.app.network.AndroidNetworkConnectivityMonitor
import org.mountaincircles.app.io.setGlobalNetworkMonitor
import org.mountaincircles.app.persistence.initializeDataStore

/**
 * Application entry point. Runs whenever the process starts (app open or widget update).
 * Initializes FileManager, NetworkMonitor, and DataStore here so they are available
 * when the app is not running and the user refreshes a widget.
 */
class MountainCirclesApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val fileManager = FileManager()
        fileManager.initialize(this)
        setGlobalFileManager(fileManager)

        setGlobalNetworkMonitor(AndroidNetworkConnectivityMonitor(this))
        initializeDataStore(this)
    }
}
