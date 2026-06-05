package org.mountaincircles.app.modules.airspace.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.ui.components.StandardButton
import org.mountaincircles.app.ui.components.ClearButton
import org.mountaincircles.app.ui.components.UnifiedProgressIndicator
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.modules.airspace.logic.data.CountrySource

/**
 * New Airspace Import Composable using ViewModel pattern
 * Replaces AirspaceImportSectionImpl with clean separation of concerns
 */
@Composable
fun AirspaceImportComposable(
    viewModel: AirspaceImportViewModel,
    modifier: Modifier = Modifier,
    onScrollToTop: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.progressFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // CLEANUP: Reset download state when import sheet is dismissed
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                try {
                    viewModel.resetDownloadState()
                } catch (e: Exception) {
                    // Error handling is done in ViewModel
                }
            }
        }
    }

    // Base layout matching BaseImportSection.BaseLayout
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress section matching BaseImportSection.ProgressSection
        if (progress.isDownloading) {
            UnifiedProgressIndicator(
                progress = progress,
                showFileProgress = true
            )
        }

        // Error display matching BaseImportSection.ErrorSection
        if (uiState.hasError) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Error: ${uiState.errorMessage ?: "Unknown error"}",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Import section content (module-specific)
        AirspaceImportSectionContent(
            uiState = uiState,
            onAction = { viewModel.handleAction(it) },
            isDownloading = uiState.isDownloading,
            onScrollToTop = onScrollToTop
        )
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

@Composable
private fun AirspaceImportSectionContent(
    uiState: AirspaceImportUiState,
    onAction: (AirspaceImportUiAction) -> Unit,
    isDownloading: Boolean,
    onScrollToTop: (() -> Unit)? = null
) {
    // Explanatory text
    Text(
        text = "Select countries to get openAip data, then click on Import/Refresh",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
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
            enabled = !isDownloading,
            onClick = { onAction(AirspaceImportUiAction.SelectAllCountries) },
            modifier = Modifier.weight(1f)
        )

        StandardButton(
            text = "Select None",
            enabled = !isDownloading,
            onClick = { onAction(AirspaceImportUiAction.SelectNoCountries) },
            modifier = Modifier.weight(1f)
        )
    }

    // Country list with checkboxes - two columns
    val sortedCountries = AirspaceSources.countries.sortedBy { it.name }
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
                CountryCheckbox(
                    country = country,
                    isSelected = uiState.selectedCountries.contains(country.code),
                    enabled = !isDownloading,
                    onToggle = { selected ->
                        onAction(AirspaceImportUiAction.ToggleCountry(country.code, selected))
                    }
                )
            }
        }

        // Right column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            rightColumnCountries.forEach { country ->
                CountryCheckbox(
                    country = country,
                    isSelected = uiState.selectedCountries.contains(country.code),
                    enabled = !isDownloading,
                    onToggle = { selected ->
                        onAction(AirspaceImportUiAction.ToggleCountry(country.code, selected))
                    }
                )
            }
        }
    }

    // Import airspace button
    StandardButton(
        text = "Import / Refresh Airspace",
        enabled = !isDownloading,
        onClick = {
            // Scroll to top immediately when build is clicked
            onScrollToTop?.invoke()
            onAction(AirspaceImportUiAction.ImportAirspace)
        },
        modifier = Modifier.fillMaxWidth()
    )

    // Airspace data status and clear button
    if (uiState.hasData) {
        // Show import date above clear button
        StatusText(
            uiState.importedAt?.let { timestamp ->
                "imported on ${formatTimestamp(timestamp)}"
            } ?: "No import date available"
        )

        // Clear airspace data button
        ClearButton(
            text = "Clear Airspace Data",
            onClick = { onAction(AirspaceImportUiAction.ClearAirspaceData) },
            enabled = !isDownloading
        )
    } else {
        // Placeholder status text when no data loaded
        StatusText("No airspace data loaded")
    }
}

@Composable
private fun CountryCheckbox(
    country: CountrySource,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onToggle(!isSelected)
            }
            .padding(vertical = 2.dp, horizontal = 4.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                onToggle(checked)
            },
            enabled = enabled
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

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
