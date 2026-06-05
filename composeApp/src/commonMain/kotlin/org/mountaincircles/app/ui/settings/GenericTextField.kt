package org.mountaincircles.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.settings.FieldMetadata
import org.mountaincircles.app.settings.FieldType

/**
 * Generic text field component with validation and formatting.
 */
@Composable
fun GenericTextField(
    metadata: FieldMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    // Only render if this is a text field
    if (metadata.fieldType != FieldType.TEXT) {
        return
    }

    val isEnabled = enabled && metadata.enabled
    val currentValue = value?.toString() ?: ""

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

        // Text field
        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                onValueChange(newValue)
            },
            enabled = isEnabled,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = getKeyboardOptions(metadata),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Cyan,
                unfocusedIndicatorColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.Gray,
                cursorColor = Color.Cyan,
                focusedLabelColor = Color.Cyan,
                unfocusedLabelColor = Color.Gray,
                disabledLabelColor = Color.DarkGray,
                errorIndicatorColor = Color.Red,
                errorTextColor = Color.Red,
                errorCursorColor = Color.Red
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Character count for text fields
        if (metadata.type == String::class && maxLines > 1) {
            Text(
                text = "${currentValue.length} characters",
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
 * Determine appropriate keyboard options based on field metadata
 */
private fun getKeyboardOptions(metadata: FieldMetadata): KeyboardOptions {
    return when (metadata.type) {
        Int::class, Long::class -> KeyboardOptions(keyboardType = KeyboardType.Number)
        Float::class, Double::class -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else -> KeyboardOptions(keyboardType = KeyboardType.Text)
    }
}
