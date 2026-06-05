package org.mountaincircles.app.modules.skysight.logic.initialization

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.SkysightStorage
import org.mountaincircles.app.modules.skysight.overlay.ui.SkysightParameterOverlay
import org.mountaincircles.app.modules.skysight.settings.registerSkysightSettings
import org.mountaincircles.app.modules.skysight.settings.logic.SkysightSettingsProvider
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry

/**
 * Skysight Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the skysight module - load persisted settings
 */
suspend fun SkysightModule.initializeSkysight() {
    try {
        Logger.log("SKYSIGHT", LogLevel.INFO, "Initializing Skysight module with persisted settings")

        // Load persisted settings (always update state with persisted values)
        val persistedTimeStr = settingPersistence.getString("currentTime", "12:00") ?: "12:00"
        val persistedTime = try {
            val parts = persistedTimeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 12
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            org.mountaincircles.app.modules.skysight.logic.data.TimePair(hour, minute)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.WARN, "Failed to parse persisted time '$persistedTimeStr', using default", e)
            org.mountaincircles.app.modules.skysight.logic.data.TimePair.DEFAULT
        }
        val persistedLabelsVisible = settingPersistence.getBoolean("isLabelsVisible", true)
        val persistedLayerOpacity = settingPersistence.getFloat("layerOpacity", 0.75f)
        val persistedLabelSize = settingPersistence.getFloat("labelSize", 12.0f)
        val persistedWaveFilterMin = settingPersistence.getFloat("waveFilterMin", -0.5f)
        val persistedWaveFilterMax = settingPersistence.getFloat("waveFilterMax", 0.5f)
        val persistedWblmaxminFilterMin = settingPersistence.getFloat("wblmaxminFilterMin", -0.1f)
        val persistedWblmaxminFilterMax = settingPersistence.getFloat("wblmaxminFilterMax", 0.1f)
        val persistedImportTimeStart = settingPersistence.getFloat("importTimeStart", 14f)
        val persistedImportTimeEnd = settingPersistence.getFloat("importTimeEnd", 40f)
        val persistedLayersToImportStr = settingPersistence.getString("layersToImport", "") ?: ""
        val persistedLayersToImport = if (persistedLayersToImportStr.isNotEmpty()) {
            persistedLayersToImportStr.split(",").toSet()
        } else {
            emptySet<String>()
        }
        // downloadingLayers is runtime state, always starts empty
        val persistedEmail = settingPersistence.getString("email", "") ?: ""
        val persistedPassword = settingPersistence.getString("password", "") ?: ""
        val persistedIsLoggedIn = settingPersistence.getBoolean("isLoggedIn", false)

        // Update state with persisted values
        updateState {
            it.copy(
                currentTime = persistedTime,
                isLabelsVisible = persistedLabelsVisible,
                layerOpacity = persistedLayerOpacity,
                labelSize = persistedLabelSize,
                waveFilterMin = persistedWaveFilterMin,
                waveFilterMax = persistedWaveFilterMax,
                wblmaxminFilterMin = persistedWblmaxminFilterMin,
                wblmaxminFilterMax = persistedWblmaxminFilterMax,
                importTimeStart = persistedImportTimeStart,
                importTimeEnd = persistedImportTimeEnd,
                layersToImport = persistedLayersToImport,
                downloadingLayers = emptySet(), // Runtime state, always starts empty
                email = persistedEmail,
                password = persistedPassword,
                isLoggedIn = persistedIsLoggedIn
            )
        }

        Logger.log("SKYSIGHT", LogLevel.INFO, "Loaded persisted settings: email=${persistedEmail.take(3)}..., loggedIn=$persistedIsLoggedIn, time=${persistedTime.display}")

        // Clean up old realtime tiles (satellite and rain from yesterday UTC or older)
        Logger.log("SKYSIGHT", LogLevel.INFO, "Cleaning up old realtime tiles during initialization")
        val storage = SkysightStorage(
            fileManager = org.mountaincircles.app.io.getGlobalFileManager(),
            downloadManager = org.mountaincircles.app.network.createDownloadManager()
        )
        val cleanedCount = storage.cleanupOldRealtimeTiles()
        Logger.log("SKYSIGHT", LogLevel.INFO, "Cleaned up $cleanedCount old realtime tiles")

        // Register parameter overlay
        Logger.log("SKYSIGHT_OVERLAY", LogLevel.INFO, "Registering Skysight parameter overlay")
        MapOverlayRegistry.register(moduleId, SkysightParameterOverlay())
        Logger.log("SKYSIGHT_OVERLAY", LogLevel.INFO, "Skysight parameter overlay registered successfully")

        // Register settings using the standardized pattern
        registerModuleSettings(
            settingsRegistration = { registerSkysightSettings(this) },
            metadataProvider = SkysightSettingsProvider(this)
        )

    } catch (e: Exception) {
        Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to initialize Skysight module: ${e.message}", e)
        // Continue with default values
    }
}