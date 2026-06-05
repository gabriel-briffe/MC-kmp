package org.mountaincircles.app.modules.airspace.logic.business

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.logic.AirspaceConstants
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceProgress
import org.mountaincircles.app.utils.ioDispatcher
import org.mountaincircles.app.utils.currentTimeMillis
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Airspace Module Business Logic
 * Extracted business methods for better separation of concerns
 */

/**
 * Refresh airspace data by rescanning
 */
/**
 * Save airspace settings
 */
suspend fun saveAirspaceSettings(module: AirspaceModule) {
}


// Caching removed - MapLibre handles filtering dynamically

/**
 * Public method to rescan for existing airspace data files and log results
 * Similar to circles module's rescanPacks() method
 * Also loads availableTypes for the filter widget
 */
internal suspend fun rescanData(module: AirspaceModule): Boolean {
    try {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Rescanning for existing airspace data files")

        val hasData = module.airspaceStorage.hasAirspaceData()

        if (hasData) {
            Logger.log("AIRSPACE", LogLevel.INFO, "Found existing airspace data file during rescan")

            // Load metadata only to get available types (no feature deserialization)
            val metadataResult = module.airspaceStorage.loadAirspaceMetadata()
            when {
                metadataResult.isSuccess -> {
                    val metadata = metadataResult.getOrThrow()
                    if (metadata != null) {
                        Logger.log("AIRSPACE", LogLevel.INFO, "Loaded airspace metadata with ${metadata.availableTypes.size} available types: ${metadata.availableTypes}")
                        // 🚀 SMART DISABLED TYPES: Clean up disabled types for rescan
                        val currentState = module.currentState
                        val cleanedVisibleTypes = module.cleanupVisibleTypes(currentState.currentVisibleTypes, metadata.availableTypes)

                        // ✅ AUTO-ENABLE ALL TYPES: If no types are visible after cleanup, enable all available types
                        val finalVisibleTypes = if (cleanedVisibleTypes.isEmpty() && metadata.availableTypes.isNotEmpty()) {
                            Logger.log("AIRSPACE", LogLevel.INFO, "No airspace types were visible after rescan - automatically enabling all ${metadata.availableTypes.size} available types")
                            metadata.availableTypes
                        } else {
                            cleanedVisibleTypes
                        }

                        // Update state with available types and final visible types
                        module.updateState { currentState.copy(
                            availableTypes = metadata.availableTypes,
                            currentVisibleTypes = finalVisibleTypes // Use final visible types (may include auto-enabled types)
                            // Note: importedAt is preserved from the copy() call
                        ) }

                        Logger.log("AIRSPACE", LogLevel.INFO, "Rescan visible types cleanup: ${currentState.currentVisibleTypes.size} → ${finalVisibleTypes.size} (removed ${currentState.currentVisibleTypes.size - cleanedVisibleTypes.size} obsolete types${if (finalVisibleTypes != cleanedVisibleTypes) ", auto-enabled ${finalVisibleTypes.size - cleanedVisibleTypes.size} types" else ""})")

                        // Persist the auto-enabled visible types to settings (same as manual toggle)
                        if (finalVisibleTypes.isNotEmpty()) {
                            val visibleTypesString = finalVisibleTypes.joinToString(",")
                            ScopeManager.ioScope.launch {
                                module.settingPersistence.saveString("currentVisibleTypes", visibleTypesString)
                            }
                            Logger.log("AIRSPACE", LogLevel.INFO, "Persisted rescan auto-enabled types to settings: ${finalVisibleTypes.joinToString()}")
                        }
                    }
                }
                else -> {
                    Logger.log("AIRSPACE", LogLevel.WARN, "Failed to load airspace metadata for available types: ${metadataResult.exceptionOrNull()?.message}")
                }
            }
        } else {
            Logger.log("AIRSPACE", LogLevel.INFO, "No existing airspace data found during rescan")
            module.updateState { module.currentState.copy(
                availableTypes = emptySet(),
                importedAt = null
            ) }
        }

        return hasData
    } catch (e: Exception) {
        Logger.log("AIRSPACE", LogLevel.ERROR, "Error during airspace rescan: ${e.message}", e)
        return false
    }
}

/**
 * Import airspace data for selected countries
 */
suspend fun importAirspaceForSelectedCountries(module: AirspaceModule): Result<Unit> {
    val selectedCountries = module.currentState.selectedCountries
    return importAirspaceForCountries(module, selectedCountries)
}

/**
 * Import airspace data for specific countries
 */
suspend fun importAirspaceForCountries(module: AirspaceModule, countryCodes: List<String>): Result<Unit> {
    return try {
        Logger.log("AIRSPACE", LogLevel.INFO, "Starting airspace import for countries: ${countryCodes.joinToString()}")

        // Clear all existing airspace data and cache before downloading new data
        Logger.log("AIRSPACE", LogLevel.INFO, "Clearing all existing airspace data and cache before new import")
        module.airspaceStorage.clearAirspaceData()  // ✅ Clean slate for ALL data and cache files

        // Set downloading state
        module.updateState {
            it.copy(
                isDownloading = true,
                currentProgress = AirspaceProgress(
                    current = 0,
                    total = countryCodes.size, // Total countries
                    status = "Starting airspace import...",
                    percent = 0
                )
            )
        }

        // Track completed countries (like Airports' completedFiles)
        var completedCountries = 0

        val result = module.airspaceDownloadManagerInstance.downloadAirspaceForCountries(countryCodes) { progress ->
            // Handle granular progress for each phase (exactly like Airports)
            when {
                progress.status.contains("Downloading") && progress.status.contains("...") -> {
                    // Extract country name from "Downloading France..."
                    val countryName = progress.status.substringAfter("Downloading ").substringBefore("...")
                    val currentFileNumber = completedCountries + 1
                    val status = "${currentFileNumber}/${countryCodes.size} downloading ${countryName}..."
                    module.updateState { it.copy(
                        currentProgress = AirspaceProgress(
                            current = completedCountries,
                            total = countryCodes.size,
                            status = status,
                            percent = (completedCountries * 100) / countryCodes.size
                        )
                    )}
                }
                progress.status.contains("Parsing") && progress.status.contains("...") -> {
                    // Extract country name from "Parsing France..."
                    val countryName = progress.status.substringAfter("Parsing ").substringBefore("...")
                    val currentFileNumber = completedCountries + 1
                    val status = "${currentFileNumber}/${countryCodes.size} parsing ${countryName}..."
                    module.updateState { it.copy(
                        currentProgress = AirspaceProgress(
                            current = completedCountries,
                            total = countryCodes.size,
                            status = status,
                            percent = (completedCountries * 100) / countryCodes.size
                        )
                    )}
                }
                progress.status.contains("Merging") && progress.status.contains("...") -> {
                    // Extract country name from "Merging France..."
                    val countryName = progress.status.substringAfter("Merging ").substringBefore("...")
                    val currentFileNumber = completedCountries + 1
                    val status = "${currentFileNumber}/${countryCodes.size} merging ${countryName}..."
                    module.updateState { it.copy(
                        currentProgress = AirspaceProgress(
                            current = completedCountries,
                            total = countryCodes.size,
                            status = status,
                            percent = (completedCountries * 100) / countryCodes.size
                        )
                    )}
                }
                progress.status.contains("airspace data processed") -> {
                    completedCountries++
                    val percent = (completedCountries * 100) / countryCodes.size
                    val status = "${completedCountries}/${countryCodes.size} countries completed"
                    module.updateState { it.copy(
                        currentProgress = AirspaceProgress(
                            current = completedCountries,
                            total = countryCodes.size,
                            status = status,
                            percent = percent
                        )
                    )}
                    Logger.log("AIRSPACE_IMPORT", LogLevel.DEBUG, "File completed: ${progress.status} (${completedCountries}/${countryCodes.size})")
                }
            }
        }

            if (result.isSuccess) {
                Logger.log("AIRSPACE", LogLevel.INFO, "Airspace import completed successfully")

                // Load metadata only to get available types (no feature deserialization)
                val metadataResult = module.airspaceStorage.loadAirspaceMetadata()
                val availableTypes = when {
                    metadataResult.isSuccess -> {
                        val metadata = metadataResult.getOrThrow()
                        if (metadata != null) {
                            Logger.log("AIRSPACE", LogLevel.INFO, "Imported airspace data contains ${metadata.availableTypes.size} types: ${metadata.availableTypes}")
                            metadata.availableTypes
                        } else {
                            Logger.log("AIRSPACE", LogLevel.WARN, "Failed to load imported airspace metadata for available types")
                            emptySet<String>()
                        }
                    }
                    else -> {
                        Logger.log("AIRSPACE", LogLevel.WARN, "Failed to load imported airspace metadata: ${metadataResult.exceptionOrNull()?.message}")
                        emptySet<String>()
                    }
                }

                // Layer manager will use pre-warmed cache for new data

                // 🚀 SMART DISABLED TYPES: Clean up disabled types and preserve user preferences
                val currentState = module.currentState
                val cleanedVisibleTypes = module.cleanupVisibleTypes(currentState.currentVisibleTypes, availableTypes)

                // ✅ AUTO-ENABLE ALL TYPES: If no types are visible after cleanup, enable all available types
                val finalVisibleTypes = if (cleanedVisibleTypes.isEmpty() && availableTypes.isNotEmpty()) {
                    Logger.log("AIRSPACE", LogLevel.INFO, "No airspace types were visible after import - automatically enabling all ${availableTypes.size} available types")
                    availableTypes
                } else {
                    cleanedVisibleTypes
                }

                // Update UI state to reflect completion
                module.updateState {
                    it.copy(
                        isDownloading = false,
                        currentProgress = null,  // ✅ Set to null to hide progress UI (matches working modules)
                        hasDataToRender = true,  // Data is now available
                        availableTypes = availableTypes,
                        currentVisibleTypes = finalVisibleTypes, // Use final visible types (may include auto-enabled types)
                        importedAt = currentTimeMillis() // Set import timestamp
                    )
                }

                Logger.log("AIRSPACE", LogLevel.INFO, "Visible types cleanup: ${currentState.currentVisibleTypes.size} → ${finalVisibleTypes.size} (removed ${currentState.currentVisibleTypes.size - cleanedVisibleTypes.size} obsolete types${if (finalVisibleTypes != cleanedVisibleTypes) ", auto-enabled ${finalVisibleTypes.size - cleanedVisibleTypes.size} types" else ""})")

                // Persist the auto-enabled visible types to settings (same as manual toggle)
                if (finalVisibleTypes.isNotEmpty()) {
                    val visibleTypesString = finalVisibleTypes.joinToString(",")
                    ScopeManager.ioScope.launch {
                        module.settingPersistence.saveString("currentVisibleTypes", visibleTypesString)
                    }
                    Logger.log("AIRSPACE", LogLevel.INFO, "Persisted auto-enabled types to settings: ${finalVisibleTypes.joinToString()}")
                }

                // Save the updated settings with the new import timestamp

                // 🚀 TEMPORARILY DISABLED: Cache warming bypassed for MapLibre filtering test
                Logger.log("AIRSPACE", LogLevel.INFO, "🎯 TEST MODE: Skipping cache warming (using MapLibre filtering)")
                // module.businessService.warmFilteredCache()

                // ✅ RESCAN after successful import to update available types and UI
                Logger.log("AIRSPACE", LogLevel.INFO, "Rescanning after successful import")
                rescanData(module)

                Result.success(Unit)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown import error")
            Logger.log("AIRSPACE", LogLevel.ERROR, "Airspace import failed: ${error.message}", error)

            // Update state with error
            module.updateState {
                it.copy(
                    isDownloading = false,
                    currentProgress = null,
                    hasError = true,
                    errorMessage = error.message
                )
            }

            Result.failure(error)
        }
    } catch (e: Exception) {
        Logger.log("AIRSPACE", LogLevel.ERROR, "Unexpected error during airspace import: ${e.message}", e)

        // Update state with error
        module.updateState {
            it.copy(
                isDownloading = false,
                currentProgress = null,
                hasError = true,
                errorMessage = e.message
            )
        }

        Result.failure(e)
    }
}
