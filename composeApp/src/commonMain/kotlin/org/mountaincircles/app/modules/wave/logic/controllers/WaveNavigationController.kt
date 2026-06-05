package org.mountaincircles.app.modules.wave.logic.controllers

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection

/**
 * Wave Navigation Controller - Circles Pattern
 * Handles all navigation logic for the Wave module
 * Works with WaveModule's direct state management
 */
class WaveNavigationController(private val waveModule: org.mountaincircles.app.modules.wave.WaveModule) {

    private val waveLogic = WaveLogic()

    /**
     * Step to higher/lower pressure level
     */
    suspend fun stepPressure(delta: Int) {
        val currentState = waveModule.moduleState.value
        val newSelection = waveLogic.stepPressure(currentState.selection, delta, currentState.entries)

        if (newSelection != null) {
            waveModule.updateSelection(newSelection)
            Logger.log("WAVE", LogLevel.DEBUG, "Pressure stepped by $delta: ${currentState.selection.pressure} -> ${newSelection.pressure}")
        }
    }

    /**
     * Step to previous/next hour
     */
    suspend fun stepHour(delta: Int) {
        val currentState = waveModule.moduleState.value
        val newSelection = waveLogic.stepHour(currentState.selection, delta, currentState.entries)

        if (newSelection != null) {
            waveModule.updateSelection(newSelection)
            Logger.log("WAVE", LogLevel.DEBUG, "Hour stepped by $delta: ${currentState.selection.hour} -> ${newSelection.hour}")
        }
    }

    /**
     * Jump to current time (nearest available hour) within the currently selected forecast date
     */
    suspend fun navigateToNow() {
        val currentState = waveModule.moduleState.value
        val allEntries = currentState.entries

        if (allEntries.isEmpty()) {
            Logger.log("WAVE", LogLevel.WARN, "No wave entries available for navigate to now")
            return
        }

        // Filter entries to only include the currently selected forecast date
        val currentForecastDate = currentState.selection.forecastDate
        val entries = allEntries.filter { it.forecastDate == currentForecastDate }

        if (entries.isEmpty()) {
            Logger.log("WAVE", LogLevel.WARN, "No entries available for current forecast date ($currentForecastDate) for navigate to now")
            return
        }

        Logger.log("WAVE", LogLevel.INFO, "=== NAVIGATE TO NOW STARTED ===")
        Logger.log("WAVE", LogLevel.INFO, "Current time: ${waveLogic.getCurrentDate()} ${waveLogic.getCurrentHour().toString().padStart(2, '0')}:00")
        Logger.log("WAVE", LogLevel.INFO, "Total entries for current forecast date ($currentForecastDate): ${entries.size}")

        val currentHour = waveLogic.getCurrentHour()
        val currentMinute = waveLogic.getCurrentMinute()
        val currentPressure = currentState.selection.pressure

        // Find the closest entry to current time within the current forecast date
        val closestEntry = entries.minByOrNull { entry ->
            // Calculate actual time difference in minutes
            val entryTimeInMinutes = entry.hour * 60
            val currentTimeInMinutes = currentHour * 60 + currentMinute
            kotlin.math.abs(entryTimeInMinutes - currentTimeInMinutes).toDouble()
        }

        // If multiple pressure levels exist for the closest hour, prefer the currently selected pressure level
        val selectedEntry = if (closestEntry != null) {
            val closestHour = closestEntry.hour
            val entriesForClosestHour = entries.filter { it.hour == closestHour }

            // If multiple pressure levels available for this hour, prefer current pressure level
            if (entriesForClosestHour.size > 1) {
                val preferredEntry = entriesForClosestHour.find { it.pressure == currentPressure }
                preferredEntry ?: closestEntry // Fall back to closestEntry if current pressure not available
            } else {
                closestEntry // Only one entry for this hour, use it
            }
        } else {
            null
        }

        if (selectedEntry != null) {
            val newSelection = WaveSelection(
                forecastDate = selectedEntry.forecastDate,
                targetDate = selectedEntry.targetDate,
                hour = selectedEntry.hour,
                pressure = selectedEntry.pressure
            )
            waveModule.updateSelection(newSelection)
            Logger.log("WAVE", LogLevel.INFO, "=== NAVIGATE TO NOW COMPLETED ===")
            Logger.log("WAVE", LogLevel.INFO, "Selected: ${newSelection.forecastDate} ${newSelection.targetDate} ${newSelection.hour}:00 ${newSelection.pressure}hPa")
        } else {
            Logger.log("WAVE", LogLevel.WARN, "No suitable entry found for navigate to now")
        }
    }

    /**
     * Set forecast date
     */
    suspend fun setForecastDate(forecastDate: String) {
        val currentState = waveModule.moduleState.value
        // Find an entry with the target forecast date
        val matchingEntry = currentState.entries.find { it.forecastDate == forecastDate }

        if (matchingEntry != null) {
            val newSelection = WaveSelection(
                forecastDate = matchingEntry.forecastDate,
                targetDate = matchingEntry.targetDate,
                hour = matchingEntry.hour,
                pressure = matchingEntry.pressure
            )
            waveModule.updateSelection(newSelection)
            Logger.log("WAVE", LogLevel.DEBUG, "Forecast date set to: $forecastDate")
        } else {
            Logger.log("WAVE", LogLevel.WARN, "No entries found for forecast date: $forecastDate")
        }
    }
}