package org.mountaincircles.app.modules.skysight.import.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.ui.modules.ScrollableComposableProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Provider for Skysight module sheet UI - login form
 */
class SkysightSheetProvider : ScrollableComposableProvider {
    override val moduleId: String = "skysight"
    override val supportsLazyScrolling: Boolean = false

    override fun canProvide(module: ModuleBase): Boolean = module is SkysightModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is SkysightModule) {
            Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Rendering Skysight sheet")

            val scope = rememberCoroutineScope()

            // Stop batch downloads gracefully when sheet is closed
            DisposableEffect(Unit) {
                onDispose {
                    Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Sheet closed, stopping batch downloads gracefully")
                    // Launch in UI scope to survive sheet disposal
                    org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                        module.stopBatchDownloads()
                    }
                }
            }

            // Collect state values
            val isLoggedIn by module.isLoggedIn.collectAsState()
            val isLoggingIn by module.isLoggingIn.collectAsState()
            val email by module.email.collectAsState()
            val password by module.password.collectAsState()
            val errorMessage by remember { derivedStateOf { module.currentState.errorMessage } }

            // Storage stats state
            var storageStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
            var refreshTrigger by remember { mutableStateOf(0) }

            fun refreshStorageStats() {
                scope.launch {
                    try {
                        val stats = module.getStorageStats()
                        storageStats = stats
                    } catch (e: Exception) {
                        Logger.log("SKYSIGHT_SHEET", LogLevel.ERROR, "Failed to get storage stats: ${e.message}")
                    }
                }
            }

            LaunchedEffect(refreshTrigger) {
                refreshStorageStats()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoggedIn) {
                    // Logged in state
                    Text(
                        text = "Logged in as",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = email,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Spacer(modifier = Modifier.height(16.dp))

                    // Logout button
                    Button(
                        onClick = {
                            Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Logout button pressed")
                            scope.launch {
                                module.logout()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.8f),
                            contentColor = Color.Cyan
                        )
                    ) {
                        Text("Logout")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Region selection
                    Text(
                        text = "Selected region:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Spacer(modifier = Modifier.height(8.dp))

                    // Collect allowed regions and selected region
                    val allowedRegions by remember { derivedStateOf { module.currentState.allowedRegions } }
                    val selectedRegion by module.selectedRegion.collectAsState()
                    val availableLayers by module.availableLayers.collectAsState()
                    val isLoadingLayers by module.isLoadingLayers.collectAsState()
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedRegion.ifEmpty { "Select region..." },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Region") },
                            placeholder = { Text("Choose a region") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedPlaceholderColor = if (selectedRegion.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            allowedRegions.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region) },
                                    onClick = {
                                        Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Selected region: $region")
                                        scope.launch {
                                            module.updateSelectedRegion(region)
                                        }
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // Show loading indicator or available layers
                    if (selectedRegion.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoadingLayers) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading layers...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (availableLayers.isNotEmpty()) {
                            Text(
                                text = "Go directly to sidebar for direct display of forecast with on the fly downloads",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Yellow,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Batch import section
                        if (availableLayers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Batch Import (today only)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )



                                    Spacer(modifier = Modifier.height(16.dp))

                                    val layersToImport by module.layersToImport.collectAsState()
                                    val downloadingLayers by module.downloadingLayers.collectAsState()
                                    val layerImportCounts by module.layerImportCounts.collectAsState()

                                    // Import button (moved above slider)
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                // Set date to today for batch import
                                                val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                                                module.updateSelectedDate(today)

                                                val result = module.performBatchImport()
                                                if (result.isSuccess) {
                                                    Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Batch import completed successfully")
                                                    refreshTrigger++ // Trigger storage stats refresh
                                                } else {
                                                    Logger.log("SKYSIGHT_SHEET", LogLevel.ERROR, "Batch import failed: ${result.exceptionOrNull()?.message}")
                                                }
                                            }
                                        },
                                        enabled = layersToImport.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Import selected layers")
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Time range selector
                                    Text(
                                        text = "UTC time range:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    val importTimeStart by module.importTimeStart.collectAsState()
                                    val importTimeEnd by module.importTimeEnd.collectAsState()

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Convert slider value (0-48) to hours: value * 0.5
                                        val startTimeHours = importTimeStart * 0.5f
                                        val startHour = startTimeHours.toInt()
                                        val startMinute = ((startTimeHours % 1) * 60).toInt()
                                        Text(
                                            text = "${startHour.toString().padStart(2, '0')}:${startMinute.toString().padStart(2, '0')}z",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        androidx.compose.material3.RangeSlider(
                                            value = importTimeStart..importTimeEnd,
                                            onValueChange = { range ->
                                                scope.launch {
                                                    module.updateImportTimeRange(range.start, range.endInclusive)
                                                }
                                            },
                                            valueRange = 0f..48f,
                                            steps = 47, // 48 values (0-47) for 48 half-hour intervals in 24 hours
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 1.0f),
                                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Convert slider value (0-48) to hours: value * 0.5
                                        val endTimeHours = importTimeEnd * 0.5f
                                        val endHour = endTimeHours.toInt()
                                        val endMinute = ((endTimeHours % 1) * 60).toInt()
                                        Text(
                                            text = "${endHour.toString().padStart(2, '0')}:${endMinute.toString().padStart(2, '0')}z",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Layer selection checkboxes
                                    Text(
                                        text = "Select layers to import:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    availableLayers.forEach { layer ->
                                        val isSelected = layersToImport.contains(layer.id)
                                        val isDownloading = downloadingLayers.contains(layer.id)

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (!isDownloading) {
                                                        Modifier.clickable {
                                                            scope.launch {
                                                                val newSelection = if (isSelected) {
                                                                    layersToImport - layer.id
                                                                } else {
                                                                    layersToImport + layer.id
                                                                }
                                                                module.updateLayersToImport(newSelection)
                                                            }
                                                        }
                                                    } else {
                                                        Modifier // No click handler when downloading
                                                    }
                                                )
                                                // .padding(vertical = 4.dp)
                                        ) {
                                            if (isDownloading) {
                                                // Show spinner when downloading
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                // Show checkbox when not downloading
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        scope.launch {
                                                            val newSelection = if (checked) {
                                                                layersToImport + layer.id
                                                            } else {
                                                                layersToImport - layer.id
                                                            }
                                                            module.updateLayersToImport(newSelection)
                                                        }
                                                    }
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = layer.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDownloading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Show import count if available (files on filesystem / total requested)
                                            layerImportCounts[layer.id]?.let { (filesOnFilesystem, totalRequested) ->
                                                Text(
                                                    text = "$filesOnFilesystem/$totalRequested",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                }
                            }

                        }
                    }
                } else {
                    // Login form state
                    // Local state for form inputs
                    var emailInput by remember { mutableStateOf(email) }
                    var passwordInput by remember { mutableStateOf(password) }

                    // Email field
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )

                    // Password field
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )

                    // Error message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Login button
                    Button(
                        onClick = {
                            Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Login button pressed - email: $emailInput")
                            scope.launch {
                                val result = module.login(emailInput, passwordInput)
                                if (result.isSuccess) {
                                    Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "Login successful")
                                } else {
                                    Logger.log("SKYSIGHT_SHEET", LogLevel.ERROR, "Login failed: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoggingIn && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Cyan.copy(alpha = 0.8f),
                            contentColor = Color.Black
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Login")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Login credentials will be used for Skysight services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val fileCount = storageStats["fileCount"] as? Int ?: 0
            val totalSizeBytes = storageStats["totalSize"] as? Long ?: 0L
            val totalSizeMB = (totalSizeBytes / (1024.0 * 1024.0)).roundToInt()

            fun formatSize(bytes: Long): String {
                val kb = bytes / 1024.0
                val mb = bytes / (1024.0 * 1024.0)
                return if (mb >= 1.0) {
                    "${mb.roundToInt()}MB"
                } else {
                    "${kb.roundToInt()}KB"
                }
            }

            val satelliteCount = storageStats["satelliteCount"] as? Int ?: 0
            val satelliteSize = storageStats["satelliteSize"] as? Long ?: 0L
            val satelliteSizeFormatted = formatSize(satelliteSize)
            val rainCount = storageStats["rainCount"] as? Int ?: 0
            val rainSize = storageStats["rainSize"] as? Long ?: 0L
            val rainSizeFormatted = formatSize(rainSize)
            val forecastCount = storageStats["forecastCount"] as? Int ?: 0
            val forecastSize = storageStats["forecastSize"] as? Long ?: 0L
            val forecastSizeFormatted = formatSize(forecastSize)

            if (satelliteCount > 0 || rainCount > 0 || forecastCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (satelliteCount > 0) {
                        Text(
                            text = "Satellite: $satelliteCount files, $satelliteSizeFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (rainCount > 0) {
                        Text(
                            text = "Rain: $rainCount files, $rainSizeFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (forecastCount > 0) {
                        Text(
                            text = "Forecasts: $forecastCount files, $forecastSizeFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Clear all Skysight files button - visible both when logged in or not
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val result = module.clearAllSkysightFiles()
                        if (result.isSuccess) {
                            Logger.log("SKYSIGHT_SHEET", LogLevel.INFO, "All Skysight files cleared successfully")
                            refreshTrigger++ // Trigger storage stats refresh
                        } else {
                            Logger.log("SKYSIGHT_SHEET", LogLevel.ERROR, "Failed to clear Skysight files: ${result.exceptionOrNull()?.message}")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("Clear all SkySight files")
            }
        }
    }

    @Composable
    override fun provideScrollTrigger(onTrigger: () -> Unit) {
        // Skysight doesn't need scroll triggering
    }

    @Composable
    override fun provideFullWidthContent(module: ModuleBase): List<@Composable () -> Unit> {
        return emptyList()
    }
}