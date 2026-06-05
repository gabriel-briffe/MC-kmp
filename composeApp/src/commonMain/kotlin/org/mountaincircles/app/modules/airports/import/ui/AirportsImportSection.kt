package org.mountaincircles.app.modules.airports.import.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mountaincircles.app.ui.components.StandardButton
import org.mountaincircles.app.ui.components.ClearButton
import org.mountaincircles.app.ui.AppIcons
import androidx.compose.material3.Icon
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.modules.airports.logic.data.toUnifiedProgress
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.modules.airports.logic.data.AirportSources
import org.mountaincircles.app.modules.airports.logic.data.AirportsImportDisplayData
import org.mountaincircles.app.modules.airports.import.ui.CupFilePicker
import org.mountaincircles.app.ui.import.BaseImportSection
import org.mountaincircles.app.ui.components.UnifiedProgress
import androidx.compose.runtime.collectAsState
// Cross-platform date formatting - using manual formatting instead of SimpleDateFormat

/**
 * Airports import section - migrated to BaseImportSection (Phase 1)
 */

// Helper function to check if a CUP file has a corresponding pics directory
private fun hasPicsDirectory(fileName: String): Boolean {
    return try {
        val baseName = fileName.substringBeforeLast(".")
        val picsDirName = "${baseName}_pics"
        val dataDir = getGlobalFileManager().getAppDataDirectory()
        val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
        val picsDirPath = "$airportsDir/$picsDirName"
        getGlobalFileManager().exists(picsDirPath)
    } catch (e: Exception) {
        Logger.log("AIRPORTS_UI", LogLevel.DEBUG, "Error checking pics directory for $fileName: ${e.message}")
        false
    }
}

// Helper function to get display name (show .cupx if pics directory exists)
private fun getDisplayFileName(fileName: String): String {
    return if (hasPicsDirectory(fileName)) {
        fileName.substringBeforeLast(".") + ".cupx"
    } else {
        fileName
    }
}

/**
 * Format timestamp to DD-MM-YYYY format (cross-platform implementation)
 */
private fun formatTimestamp(timestamp: Long): String {
    // Manual date calculation to avoid platform-specific date classes
    val seconds = timestamp / 1000
    val days = seconds / 86400
    val years = 1970 + (days / 365)

    // Simple approximation - not perfect but cross-platform
    val dayOfYear = days % 365
    val month = (dayOfYear / 30) + 1
    val day = (dayOfYear % 30) + 1

    return "${day.toString().padStart(2, '0')}-${month.toString().padStart(2, '0')}-${years.toString()}"
}

/**
 * Airports import section implementation - migrated to BaseImportSection
 */
class AirportsImportSectionImpl(
    override val module: AirportsModule,
    private val onScrollToTop: (() -> Unit)? = null
) : BaseImportSection<AirportsModule>() {

    @Composable
    override fun getProgressFlow(): StateFlow<UnifiedProgress> {
        return module.progressFlow
    }

    @Composable
    override fun ImportSectionContent(progress: UnifiedProgress, coroutineScope: kotlinx.coroutines.CoroutineScope) {
        // Module-specific state - reactive flow like airspace
        val airportsState by module.airportsState.collectAsState()
        val importData by remember(airportsState) {
            derivedStateOf {
                val progress = airportsState.currentProgress?.toUnifiedProgress()
                AirportsImportDisplayData(airportsState.isDownloading, progress, airportsState.selectedCountries, airportsState.hasDataToRender ?: false, airportsState.importedAt, airportsState.hasError, airportsState.errorMessage)
            }
        }

        var showCountryDialog by remember { mutableStateOf(false) }

        // Data status is already known from initialization and post-import rescans

        // Error display (using base class ErrorSection)
        ErrorSection(importData.hasError, importData.errorMessage)

        // Explanatory text
        Text(
            text = "Select countries to get openAip data, add optional .cup/.cupx outlanding files, then click on build/refresh",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Data has to be refreshed manually when desired by clicking on the build/refresh button",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Data is unofficial, crowd sourced, not to be used as primary source of information. Contribute at www.openaip.net",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Quick select buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StandardButton(
                text = "Select All",
                enabled = !importData.isDownloading,
                onClick = {
                    val allCodes = AirportSources.countries.map { it.code }
                    module.updateSelectedCountries(allCodes)
                },
                modifier = Modifier.weight(1f)
            )

            StandardButton(
                text = "Select None",
                enabled = !importData.isDownloading,
                onClick = {
                    module.updateSelectedCountries(emptyList())
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Country list with checkboxes - two columns
        val sortedCountries = AirportSources.countries.sortedBy { it.name }
        val leftColumnCountries = sortedCountries.filterIndexed { index, _ -> index % 2 == 0 }
        val rightColumnCountries = sortedCountries.filterIndexed { index, _ -> index % 2 == 1 }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                leftColumnCountries.forEach { country ->
                    val isSelected = importData.selectedCountries.contains(country.code)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelection = if (isSelected) {
                                    importData.selectedCountries - country.code
                                } else {
                                    importData.selectedCountries + country.code
                                }
                                module.updateSelectedCountries(newSelection)
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    importData.selectedCountries + country.code
                                } else {
                                    importData.selectedCountries - country.code
                                }
                                module.updateSelectedCountries(newSelection)
                            },
                            enabled = !importData.isDownloading
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${country.name} (${country.code.uppercase()})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Right column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rightColumnCountries.forEach { country ->
                    val isSelected = importData.selectedCountries.contains(country.code)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelection = if (isSelected) {
                                    importData.selectedCountries - country.code
                                } else {
                                    importData.selectedCountries + country.code
                                }
                                module.updateSelectedCountries(newSelection)
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    importData.selectedCountries + country.code
                                } else {
                                    importData.selectedCountries - country.code
                                }
                                module.updateSelectedCountries(newSelection)
                            },
                            enabled = !importData.isDownloading
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${country.name} (${country.code.uppercase()})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Progress indicator is now handled by BaseImportSection
        // No need to manually collect and display progress here

        // CUP files list state (moved here so it's available before use)
        val cupFiles = remember { mutableStateOf<List<String>>(emptyList()) }
        val processedCupFiles = remember { mutableStateOf<Set<String>>(emptySet()) }
        val isLoadingCupFiles = remember { mutableStateOf(false) }
        val refreshCupFilesTrigger = remember { mutableStateOf(0) }

        // List of CUP files in the system
        Spacer(modifier = Modifier.height(8.dp))

        // Load CUP files when component is first composed, refresh is triggered, or import completes
        LaunchedEffect(refreshCupFilesTrigger.value, importData.importedAt) {
            isLoadingCupFiles.value = true
            try {
                val files = module.airportsBusinessService.getCupFilesList()
                cupFiles.value = files.sorted()

                // Load processed CUP files from metadata
                val metadata = module.airportsStorage.loadAirportsMetadata().getOrNull()
                processedCupFiles.value = metadata?.processedCupFiles ?: emptySet()

                Logger.log("AIRPORTS_UI", LogLevel.DEBUG, "Loaded ${files.size} CUP files, ${processedCupFiles.value.size} processed")
            } catch (e: Exception) {
                Logger.log("AIRPORTS_UI", LogLevel.ERROR, "Failed to load CUP files: ${e.message}", e)
                cupFiles.value = emptyList()
                processedCupFiles.value = emptySet()
            } finally {
                isLoadingCupFiles.value = false
            }
        }


        if (isLoadingCupFiles.value) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            }
        } else if (cupFiles.value.isEmpty()) {
            Text(
                text = "No CUP files imported yet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                cupFiles.value.forEach { fileName ->
                    val isProcessed = processedCupFiles.value.contains(fileName)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .combinedClickable(
                                onClick = { /* No action on regular click */ },
                                onLongClick = {
                                    // Delete the CUP file
                                    deleteCupFile(module, fileName, refreshCupFilesTrigger)
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.DarkGray.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = getDisplayFileName(fileName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isProcessed) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Green,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            Text(
                                text = "long press to delete",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Add .cup file button
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                Logger.log("AIRPORTS_UI", LogLevel.INFO, "Add .cup file button clicked")
                launchAction("save CUP file", "AIRPORTS_UI", coroutineScope) {
                    val cupFilePicker = CupFilePicker()
                    cupFilePicker.launchCupSave(
                        module = module,
                        onResult = { saveSuccess ->
                            if (saveSuccess) {
                                Logger.log("AIRPORTS_UI", LogLevel.INFO, "CUP file saved to filesystem successfully")
                                // Refresh the CUP files list after successful save
                                refreshCupFilesTrigger.value++
                            } else {
                                Logger.log("AIRPORTS_UI", LogLevel.WARN, "CUP file save failed or was cancelled")
                            }
                        }
                    )
                }
            },
            enabled = !importData.isDownloading,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                containerColor = Color.Transparent
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = "add .cup/.cupx file",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White
                )
            )
        }

        // Import airports button
        Spacer(modifier = Modifier.height(16.dp))
        StandardButton(
            text = "Build / Refresh Airports",
            enabled = !importData.isDownloading,
            icon = AppIcons.Download(),
            onClick = {
                // Scroll to top immediately when build is clicked
                onScrollToTop?.invoke()

                Logger.log("AIRPORTS_UI", LogLevel.INFO, "Import airports button clicked in import sheet")
                Logger.log("AIRPORTS_IMPORT", LogLevel.INFO, "Starting airports import - not implemented yet")

                launchAction("airports import", "AIRPORTS_UI", coroutineScope) {
                    // Start async download - success/error handled through state updates
                    module.downloadAirportsPipeline()
                    Logger.log("AIRPORTS_UI", LogLevel.INFO, "Airports import started asynchronously")
                    // Pipeline handles success/error through state updates, UI will react via flows
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Airports data status and clear button
        if (importData.hasDataToRender) {
            // Show import date above clear button
            val statusText = importData.importedAt?.let { timestamp ->
                "imported on ${formatTimestamp(timestamp)}"
            } ?: "No import date available"
            StatusText(statusText)

            // Clear airports data button - using standardized clear button theme
            ClearButton(
                text = "Clear Airport Data",
                onClick = {
                    launchAction("clear airport data", "AIRPORTS_UI", coroutineScope) {
                        module.clearAirportsData()
                        Logger.log("AIRPORTS_UI", LogLevel.INFO, "Clear airport data initiated")
                    }
                },
                enabled = !importData.isDownloading,
                icon = AppIcons.Close()
            )
        } else {
            // Placeholder status text when no data loaded
            StatusText("No airport data loaded")
        }

    }
}

// Function to delete a CUP file
private fun deleteCupFile(module: AirportsModule, fileName: String, refreshTrigger: MutableState<Int>) {
    // Run in coroutine scope
    org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
        try {
            Logger.log("AIRPORTS_UI", LogLevel.INFO, "Deleting CUP file: $fileName")

            // Delete the file from storage using the module's storage service
            val deleted = module.airportsStorage.deleteCupFile(fileName)
            var allDeleted = deleted

            // If this CUP file has a pics directory (CUPX), also delete it
            if (hasPicsDirectory(fileName)) {
                try {
                    val baseName = fileName.substringBeforeLast(".")
                    val picsDirName = "${baseName}_pics"
                    Logger.log("AIRPORTS_UI", LogLevel.INFO, "Deleting associated pics directory: $picsDirName")

                    val picsDeleted = module.airportsStorage.deleteDirectory(picsDirName)
                    if (picsDeleted) {
                        Logger.log("AIRPORTS_UI", LogLevel.INFO, "Successfully deleted pics directory: $picsDirName")
                    } else {
                        Logger.log("AIRPORTS_UI", LogLevel.WARN, "Failed to delete pics directory: $picsDirName")
                        allDeleted = false
                    }
                } catch (e: Exception) {
                    Logger.log("AIRPORTS_UI", LogLevel.ERROR, "Exception while deleting pics directory for $fileName: ${e.message}", e)
                    allDeleted = false
                }
            }

            if (allDeleted) {
                Logger.log("AIRPORTS_UI", LogLevel.INFO, "Successfully deleted CUP file and associated data: $fileName")
                // Refresh the list
                refreshTrigger.value++
            } else {
                Logger.log("AIRPORTS_UI", LogLevel.ERROR, "Failed to delete CUP file or associated data: $fileName")
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_UI", LogLevel.ERROR, "Exception while deleting CUP file $fileName: ${e.message}", e)
        }
    }
}

// Composable wrapper for backward compatibility
@Composable
fun AirportsImportSection(
    module: AirportsModule,
    modifier: Modifier = Modifier,
    onScrollToTop: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    // CLEANUP: Reset download state when import sheet is dismissed
    DisposableEffect(Unit) {
        onDispose {
            Logger.log("AIRPORTS_UI", LogLevel.INFO, "🗑️ AirportsImportSection disposed - resetting download state")
            // Reset downloading state immediately when sheet is dismissed
            scope.launch {
                try {
                    // TODO: module.resetDownloadState()
                    Logger.log("AIRPORTS_UI", LogLevel.DEBUG, "Reset download state - not implemented yet")
                } catch (e: Exception) {
                    Logger.log("AIRPORTS_UI", LogLevel.ERROR, "Failed to reset download state on dispose: ${e.message}")
                }
            }
        }
    }

    AirportsImportSectionImpl(module, onScrollToTop).ImportSection(modifier)
}
