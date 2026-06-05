package org.mountaincircles.app.modules.skysight.logic.controllers

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.settings.SettingPersistence

/**
 * Controller for Skysight authentication operations
 */
object SkysightAuthController {

    /**
     * Perform login with email and password
     */
    suspend fun login(
        module: SkysightModule,
        apiManager: org.mountaincircles.app.modules.skysight.logic.SkysightApiManager,
        settingPersistence: SettingPersistence,
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            Logger.log("SKYSIGHT", LogLevel.INFO, "Attempting login for email: $email")

            // Set logging in state
            module.updateState { it.copy(isLoggingIn = true, hasError = false, errorMessage = null) }

            // Make HTTP request via API manager
            val response = apiManager.login(email, password)

            when {
                response.isSuccess -> {
                    val loginData = response.getOrThrow()
                    Logger.log("SKYSIGHT", LogLevel.INFO, "Login successful for $email, API key: ${loginData.key.take(10)}...")

                    // Save login data to state and persistence (no default region - user must choose)
                    module.updateState { it.copy(
                        email = email,
                        password = password,
                        isLoggedIn = true,
                        isLoggingIn = false,
                        hasDataToRender = true, // Enable top menu button and submenu
                        allowedRegions = loginData.allowed_regions,
                        apiKey = loginData.key, // Cache API key
                        apiKeyValidUntil = loginData.valid_until // Cache expiration timestamp
                        // selectedRegion remains empty - user must choose
                    )}

                    // Persist login data (excluding sensitive API key and validity)
                    settingPersistence.saveString("email", email)
                    settingPersistence.saveString("password", password)
                    settingPersistence.saveBoolean("isLoggedIn", true)
                    settingPersistence.saveString("allowedRegions", loginData.allowed_regions.joinToString(","))
                    // selectedRegion remains empty in persistence until user chooses

                    Result.success(Unit)
                }
                else -> {
                    val error = response.exceptionOrNull() ?: Exception("Unknown login error")
                    Logger.log("SKYSIGHT", LogLevel.ERROR, "Login failed: ${error.message}", error)

                    module.updateState { it.copy(
                        isLoggingIn = false,
                        hasError = true,
                        errorMessage = error.message ?: "Login failed"
                    )}

                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.ERROR, "Login exception: ${e.message}", e)
            module.updateState { it.copy(
                isLoggingIn = false,
                hasError = true,
                errorMessage = e.message ?: "Login failed"
            )}
            Result.failure(e)
        }
    }

    /**
     * Perform logout and clear user data
     */
    suspend fun logout(
        module: SkysightModule,
        settingPersistence: org.mountaincircles.app.settings.SettingPersistence
    ) {
        org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.INFO, "Logging out user: ${module.currentState.email}")

        // Clear login data from state (including cached API key)
        module.updateState { it.copy(
            isLoggedIn = false,
            hasDataToRender = false, // Disable top menu button and submenu
            allowedRegions = emptyList(),
            selectedRegion = "", // Clear selected region
            availableLayers = emptyList(), // Clear available layers
            isLoadingLayers = false,
            hasError = false,
            errorMessage = null,
            apiKey = null, // Clear cached API key
            apiKeyValidUntil = null // Clear API key expiration
        )}

        // Clear persisted login data (excluding API key and validUntil)
        settingPersistence.saveBoolean("isLoggedIn", false)
        settingPersistence.saveString("allowedRegions", "")
        settingPersistence.saveString("selectedRegion", "")
        // availableLayers are not persisted as full objects
    }

    /**
     * Update user email
     */
    suspend fun updateEmail(
        module: SkysightModule,
        settingPersistence: org.mountaincircles.app.settings.SettingPersistence,
        email: String
    ) {
        module.updateState { it.copy(email = email) }
        settingPersistence.saveString("email", email)
    }

    /**
     * Update user password
     */
    suspend fun updatePassword(
        module: SkysightModule,
        settingPersistence: org.mountaincircles.app.settings.SettingPersistence,
        password: String
    ) {
        module.updateState { it.copy(password = password) }
        settingPersistence.saveString("password", password)
    }
}