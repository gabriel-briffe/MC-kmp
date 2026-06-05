package org.mountaincircles.app.modules.wave.logic.controllers

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.logic.data.WaveEntry
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection

/**
 * Wave logic for navigation and selection operations
 * 
 * Handles:
 * - Time navigation (hour stepping)
 * - Altitude navigation (pressure level stepping) 
 * - Navigation validation
 * - Current selection updates
 */
class WaveLogic {
    
    companion object {
        private val VALID_HOURS = listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
        private val VALID_PRESSURES = listOf(500, 600, 700, 800, 900, 1000)
    }

    /**
     * Get current date in YYYY-MM-DD format (UTC)
     */
    fun getCurrentDate(): String {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date
        return today.toString() // Formats as yyyy-MM-dd
    }

    /**
     * Get current hour (0-23) in UTC
     */
    fun getCurrentHour(): Int {
        val now = Clock.System.now()
        return now.toLocalDateTime(TimeZone.UTC).hour
    }

    /**
     * Get current minute (0-59) in UTC
     */
    fun getCurrentMinute(): Int {
        val now = Clock.System.now()
        return now.toLocalDateTime(TimeZone.UTC).minute
    }
    
    /**
     * Calculate navigation capabilities for current selection
     * With cross-date navigation, time buttons are always enabled when entries are available
     */
    fun calculateNavigationCapabilities(
        selection: WaveSelection,
        availableEntries: List<WaveEntry>
    ): NavigationCapabilities {
        
        val currentHourIndex = VALID_HOURS.indexOf(selection.hour)
        val currentPressureIndex = VALID_PRESSURES.indexOf(selection.pressure)
        
        // Time navigation: Check if we can step within current target date OR navigate to adjacent target dates
        val availableTargetDates = availableEntries
            .map { it.targetDate }
            .distinct()
            .filter { it.isNotBlank() }
            .sorted()
        
        val currentTargetIndex = availableTargetDates.indexOf(selection.targetDate)
        
        val canPrevHour = availableEntries.isNotEmpty() && (
            // Can step within current target date
            (currentHourIndex > 0 && hasEntryForSelection(
                selection.copy(hour = VALID_HOURS[currentHourIndex - 1]),
                availableEntries
            )) ||
            // Or can navigate to an earlier target date (no wrapping)
            (currentTargetIndex > 0)
        )
        
        val canNextHour = availableEntries.isNotEmpty() && (
            // Can step within current target date
            (currentHourIndex < VALID_HOURS.size - 1 && hasEntryForSelection(
                selection.copy(hour = VALID_HOURS[currentHourIndex + 1]),
                availableEntries
            )) ||
            // Or can navigate to a later target date (no wrapping)
            (currentTargetIndex >= 0 && currentTargetIndex < availableTargetDates.size - 1)
        )
        
        // Pressure navigation remains within current target date only
        val canPressureDown = currentPressureIndex > 0 && hasEntryForSelection(
            selection.copy(pressure = VALID_PRESSURES[currentPressureIndex - 1]),
            availableEntries
        )
        
        val canPressureUp = currentPressureIndex < VALID_PRESSURES.size - 1 && hasEntryForSelection(
            selection.copy(pressure = VALID_PRESSURES[currentPressureIndex + 1]),
            availableEntries
        )
        
        return NavigationCapabilities(
            canPrevHour = canPrevHour,
            canNextHour = canNextHour,
            canPressureDown = canPressureDown,
            canPressureUp = canPressureUp
        )
    }
    
    /**
     * Step hour by delta (-1 for previous, +1 for next)
     * When reaching end of time range for current target date, jumps to next/prev target date
     */
    fun stepHour(
        currentSelection: WaveSelection,
        delta: Int,
        availableEntries: List<WaveEntry>
    ): WaveSelection? {
        val currentIndex = VALID_HOURS.indexOf(currentSelection.hour)
        if (currentIndex == -1) {
            Logger.log("WAVE_LOGIC", LogLevel.WARN, "Invalid current hour: ${currentSelection.hour}")
            return null
        }
        
        val newIndex = currentIndex + delta
        
        // First try stepping within current target date
        if (newIndex >= 0 && newIndex < VALID_HOURS.size) {
            val newHour = VALID_HOURS[newIndex]
            val newSelection = currentSelection.copy(hour = newHour)
            
            if (hasEntryForSelection(newSelection, availableEntries)) {
                return newSelection
            }
        }
        
        // If we're here, we need to jump to a different target date
        Logger.log("WAVE_LOGIC", LogLevel.DEBUG, "Time step out of range for current target date, attempting cross-date navigation")
        
        // Get all available target dates, sorted
        val availableTargetDates = availableEntries
            .map { it.targetDate }
            .distinct()
            .filter { it.isNotBlank() }
            .sorted()
        
        if (availableTargetDates.size <= 1) {
            Logger.log("WAVE_LOGIC", LogLevel.DEBUG, "Only one target date available, cannot cross-navigate")
            return null
        }
        
        val currentTargetIndex = availableTargetDates.indexOf(currentSelection.targetDate)
        if (currentTargetIndex == -1) {
            Logger.log("WAVE_LOGIC", LogLevel.WARN, "Current target date not found in available dates: ${currentSelection.targetDate}")
            return null
        }
        
        // Determine the next target date (no wrapping - linear navigation only)
        val nextTargetIndex = if (delta > 0) {
            // Moving forward in time - go to next target date if available
            currentTargetIndex + 1
        } else {
            // Moving backward in time - go to previous target date if available
            currentTargetIndex - 1
        }
        
        // Check if we can navigate to the next target date
        if (nextTargetIndex < 0 || nextTargetIndex >= availableTargetDates.size) {
            Logger.log("WAVE_LOGIC", LogLevel.DEBUG, "No more target dates available in direction (delta=$delta)")
            return null
        }
        
        val nextTargetDate = availableTargetDates[nextTargetIndex]
        Logger.log("WAVE_LOGIC", LogLevel.INFO, "Cross-date navigation: ${currentSelection.targetDate} -> $nextTargetDate")
        
        // Find the best hour for the new target date
        val targetHour = if (delta > 0) {
            // Going forward - start with first available hour for new target date
            findFirstAvailableHour(nextTargetDate, currentSelection.pressure, availableEntries)
        } else {
            // Going backward - start with last available hour for new target date
            findLastAvailableHour(nextTargetDate, currentSelection.pressure, availableEntries)
        }
        
        if (targetHour == null) {
            Logger.log("WAVE_LOGIC", LogLevel.WARN, "No available hours for target date: $nextTargetDate")
            return null
        }
        
        // Find matching entry to get the correct forecast date
        val matchingEntry = availableEntries.find { 
            it.targetDate == nextTargetDate && it.hour == targetHour && it.pressure == currentSelection.pressure 
        }
        
        return if (matchingEntry != null) {
            WaveSelection(
                forecastDate = matchingEntry.forecastDate,
                targetDate = matchingEntry.targetDate,
                hour = matchingEntry.hour,
                pressure = matchingEntry.pressure,
                filePath = matchingEntry.filePath
            )
        } else {
            Logger.log("WAVE_LOGIC", LogLevel.WARN, "No matching entry found for cross-date navigation")
            null
        }
    }
    
    /**
     * Step pressure by delta (-1 for lower pressure/higher altitude, +1 for higher pressure/lower altitude)
     */
    fun stepPressure(
        currentSelection: WaveSelection,
        delta: Int,
        availableEntries: List<WaveEntry>
    ): WaveSelection? {
        val currentIndex = VALID_PRESSURES.indexOf(currentSelection.pressure)
        if (currentIndex == -1) {
            Logger.log("WAVE_LOGIC", LogLevel.WARN, "Invalid current pressure: ${currentSelection.pressure}")
            return null
        }
        
        val newIndex = currentIndex + delta
        if (newIndex < 0 || newIndex >= VALID_PRESSURES.size) {
            Logger.log("WAVE_LOGIC", LogLevel.DEBUG, "Pressure step out of bounds: $newIndex")
            return null
        }
        
        val newPressure = VALID_PRESSURES[newIndex]
        val newSelection = currentSelection.copy(pressure = newPressure)
        
        if (!hasEntryForSelection(newSelection, availableEntries)) {
            Logger.log("WAVE_LOGIC", LogLevel.DEBUG, "No entry available for pressure: $newPressure")
            return null
        }
        
        return newSelection
    }
    
    /**
     * Find the best initial selection for a given list of entries
     */
    fun findInitialSelection(availableEntries: List<WaveEntry>): WaveSelection? {
        // Only consider VV MBTiles files for wave layer (exclude U/V TIFF files)
        val waveEntries = availableEntries.filter { entry ->
            entry.filePath.endsWith(".mbtiles")
        }

        if (waveEntries.isEmpty()) return null

        // Try to find a reasonable default (around noon, middle pressure)
        val preferredHour = 12
        val preferredPressure = 700

        // First try preferred values
        val preferredSelection = WaveSelection("", "", preferredHour, preferredPressure)
        if (hasEntryForSelection(preferredSelection, waveEntries)) {
            val entry = findEntryForSelection(preferredSelection, waveEntries)
            return entry?.let {
                WaveSelection(it.forecastDate, it.targetDate, it.hour, it.pressure, it.filePath)
            }
        }

        // Fall back to first available VV MBTiles entry
        val firstEntry = waveEntries.first()
        return WaveSelection(firstEntry.forecastDate, firstEntry.targetDate, firstEntry.hour, firstEntry.pressure, firstEntry.filePath)
    }
    
    /**
     * Check if a VV MBTiles entry exists for the given selection
     */
    private fun hasEntryForSelection(selection: WaveSelection, availableEntries: List<WaveEntry>): Boolean {
        // Only consider VV MBTiles files for wave layer (exclude U/V TIFF files)
        val waveEntries = availableEntries.filter { entry ->
            entry.filePath.endsWith(".mbtiles")
        }
        return waveEntries.any { entry ->
            entry.hour == selection.hour && entry.pressure == selection.pressure &&
            entry.targetDate == selection.targetDate
        }
    }
    
    /**
     * Find the first available hour for a given target date and pressure
     */
    private fun findFirstAvailableHour(targetDate: String, pressure: Int, availableEntries: List<WaveEntry>): Int? {
        return VALID_HOURS.firstOrNull { hour ->
            availableEntries.any { entry ->
                entry.targetDate == targetDate && entry.hour == hour && entry.pressure == pressure
            }
        }
    }
    
    /**
     * Find the last available hour for a given target date and pressure
     */
    private fun findLastAvailableHour(targetDate: String, pressure: Int, availableEntries: List<WaveEntry>): Int? {
        return VALID_HOURS.lastOrNull { hour ->
            availableEntries.any { entry ->
                entry.targetDate == targetDate && entry.hour == hour && entry.pressure == pressure
            }
        }
    }
    
    /**
     * Find the exact entry matching the selection
     */
    private fun findEntryForSelection(selection: WaveSelection, availableEntries: List<WaveEntry>): WaveEntry? {
        // Only consider VV MBTiles files for wave layer (exclude U/V TIFF files)
        val waveEntries = availableEntries.filter { entry ->
            entry.filePath.endsWith(".mbtiles")
        }

        // First try exact match (forecast + target + hour + pressure)
        val exactMatch = waveEntries.find { entry ->
            entry.forecastDate == selection.forecastDate &&
            entry.targetDate == selection.targetDate &&
            entry.hour == selection.hour &&
            entry.pressure == selection.pressure
        }
        if (exactMatch != null) return exactMatch

        // Then try same forecast, same target, any hour/pressure
        val sameForecastTarget = waveEntries.find { entry ->
            entry.forecastDate == selection.forecastDate &&
            entry.targetDate == selection.targetDate
        }
        if (sameForecastTarget != null) return sameForecastTarget

        // Then try same forecast, any target, same hour/pressure
        val sameForecastHourPressure = waveEntries.find { entry ->
            entry.forecastDate == selection.forecastDate &&
            entry.hour == selection.hour &&
            entry.pressure == selection.pressure
        }
        if (sameForecastHourPressure != null) return sameForecastHourPressure

        // Finally try same forecast, any target, any hour/pressure
        return waveEntries.find { entry ->
            entry.forecastDate == selection.forecastDate
        }
    }
    
    /**
     * Update selection with the path to the matching entry
     */
    fun updateSelectionWithPath(selection: WaveSelection, availableEntries: List<WaveEntry>): WaveSelection {
        val matchingEntry = findEntryForSelection(selection, availableEntries)
        return if (matchingEntry != null) {
            selection.copy(
                forecastDate = matchingEntry.forecastDate,
                targetDate = matchingEntry.targetDate,
                filePath = matchingEntry.filePath
            )
        } else {
            selection
        }
    }
}

/**
 * Navigation capabilities for current selection
 */
data class NavigationCapabilities(
    val canPrevHour: Boolean,
    val canNextHour: Boolean,
    val canPressureDown: Boolean,
    val canPressureUp: Boolean
)
