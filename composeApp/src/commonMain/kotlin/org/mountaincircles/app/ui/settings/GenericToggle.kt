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
 * Generic toggle component for boolean settings with enhanced features.
 */
@Composable
fun GenericToggle(
    metadata: FieldMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Only render if this is a toggle field
    if (metadata.fieldType != FieldType.TOGGLE) {
        return
    }

    val isEnabled = enabled && metadata.enabled
    val currentValue = value as? Boolean ?: false

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

        // Toggle row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Toggle switch
            Switch(
                checked = currentValue,
                onCheckedChange = { newValue ->
                    onValueChange(newValue)
                },
                enabled = isEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Cyan,
                    checkedTrackColor = Color.Cyan.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f),
                    disabledCheckedThumbColor = Color.Gray,
                    disabledCheckedTrackColor = Color.Gray.copy(alpha = 0.2f),
                    disabledUncheckedThumbColor = Color.DarkGray,
                    disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.2f)
                )
            )

            // Status text
            Text(
                text = if (currentValue) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentValue) Color.Cyan else Color.Gray,
                fontWeight = FontWeight.Medium
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
