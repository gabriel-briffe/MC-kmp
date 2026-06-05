package org.mountaincircles.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.settings.FieldMetadata
import org.mountaincircles.app.settings.FieldType

/**
 * Generic dropdown component for selection fields with enhanced features.
 */
@Composable
fun GenericDropdown(
    metadata: FieldMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    options: List<DropdownOption> = emptyList(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Only render if this is a dropdown field
    if (metadata.fieldType != FieldType.DROPDOWN) {
        return
    }

    val isEnabled = enabled && metadata.enabled
    var expanded by remember { mutableStateOf(false) }

    // Convert value to string for display
    val currentValue = value?.toString() ?: ""
    val selectedOption = options.find { it.value == currentValue } ?: options.firstOrNull()

    Column(modifier = modifier) {
        // Field label and description
        if (metadata.label.isNotEmpty()) {
            Text(
                text = metadata.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) Color.White else Color.Gray
            )
        }

        if (metadata.description.isNotEmpty()) {
            Text(
                text = metadata.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Dropdown box
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (isEnabled) expanded = true },
                enabled = isEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isEnabled) Color.White else Color.Gray,
                    disabledContentColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedOption?.label ?: currentValue.ifEmpty { "Select..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) Color.White else Color.Gray
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled) Color.Cyan else Color.Gray
                    )
                }
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (option.value == currentValue) Color.Cyan else Color.White
                            )
                        },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        }
                    )
                }

                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No options available",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        },
                        onClick = { expanded = false }
                    )
                }
            }
        }

        // Selected value description
        selectedOption?.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Advanced field indicator
        if (metadata.isAdvanced) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3A3A3A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "🧪",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Advanced/Experimental Feature",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow
                    )
                }
            }
        }

        // Restart warning if needed
        if (metadata.requiresRestart) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2A2A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "🔄",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "App restart required for changes to take effect",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow
                    )
                }
            }
        }

        // Validation errors
        val validationErrors = org.mountaincircles.app.settings.SettingsMetadataUtils.validateField(
            metadata, value
        )
        if (validationErrors.isNotEmpty()) {
            Text(
                text = validationErrors.first(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Data class representing a dropdown option
 */
data class DropdownOption(
    val value: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,
    val enabled: Boolean = true
)

/**
 * Predefined dropdown options for common use cases
 */
object CommonDropdownOptions {

    val BOOLEAN_OPTIONS = listOf(
        DropdownOption("true", "Yes", "Enable this feature"),
        DropdownOption("false", "No", "Disable this feature")
    )

    val LOG_LEVELS = listOf(
        DropdownOption("DEBUG", "Debug", "Detailed logging for development"),
        DropdownOption("INFO", "Info", "General information messages"),
        DropdownOption("WARN", "Warning", "Warning messages"),
        DropdownOption("ERROR", "Error", "Error messages only")
    )

    val THEME_OPTIONS = listOf(
        DropdownOption("light", "Light", "Light theme"),
        DropdownOption("dark", "Dark", "Dark theme"),
        DropdownOption("system", "System", "Follow system theme")
    )

    val LANGUAGE_OPTIONS = listOf(
        DropdownOption("en", "English", "English language"),
        DropdownOption("fr", "Français", "French language"),
        DropdownOption("de", "Deutsch", "German language"),
        DropdownOption("es", "Español", "Spanish language")
    )

    fun createNumberedOptions(start: Int, end: Int, step: Int = 1): List<DropdownOption> {
        return (start..end step step).map { value ->
            DropdownOption(value.toString(), value.toString())
        }
    }

    fun createCustomOptions(vararg options: Pair<String, String>): List<DropdownOption> {
        return options.map { (value, label) ->
            DropdownOption(value, label)
        }
    }
}
