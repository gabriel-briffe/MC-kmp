package org.mountaincircles.app.ui.settings

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
import org.mountaincircles.app.settings.FieldMetadata
import org.mountaincircles.app.settings.FieldType
import org.mountaincircles.app.utils.formatNumber
import kotlin.math.roundToInt

/**
 * Generic slider component that can handle different numeric types
 * with validation, units, and proper formatting.
 */
@Composable
fun GenericSlider(
    metadata: FieldMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Only render if this is a slider field
    if (metadata.fieldType != FieldType.SLIDER) {
        return
    }

    val isEnabled = enabled && metadata.enabled

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

        // Convert value to float for slider
        val currentValue = when (value) {
            is Number -> value.toFloat()
            else -> metadata.min.toFloat()
        }

        // Slider row with value display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Slider
            Slider(
                value = currentValue,
                onValueChange = { newValue ->
                    val constrainedValue = newValue.coerceIn(metadata.min.toFloat(), metadata.max.toFloat())
                    val steppedValue = if (metadata.step > 0) {
                        ((constrainedValue / metadata.step).roundToInt() * metadata.step).toFloat()
                    } else {
                        constrainedValue
                    }
                    onValueChange(convertFloatToFieldType(steppedValue, metadata))
                },
                valueRange = metadata.min.toFloat()..metadata.max.toFloat(),
                steps = if (metadata.step > 0) {
                    ((metadata.max - metadata.min) / metadata.step).toInt().coerceAtLeast(0)
                } else {
                    0
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f),
                    activeTickColor = Color.White.copy(alpha = 0.7f),
                    inactiveTickColor = Color.White.copy(alpha = 0.5f)
                ),
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )

            // Value display with unit
            val displayValue = formatValueForDisplay(currentValue, metadata)
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) Color.Cyan else Color.Gray,
                textAlign = TextAlign.End,
                modifier = Modifier.width(80.dp)
            )
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
                        text = "⚠️",
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
    }
}

/**
 * Convert a float value back to the appropriate field type
 */
private fun convertFloatToFieldType(value: Float, metadata: FieldMetadata): Any {
    return when (metadata.type) {
        Int::class -> value.roundToInt()
        Long::class -> value.roundToInt().toLong()
        Float::class -> value
        Double::class -> value.toDouble()
        else -> value
    }
}

/**
 * Format a value for display with proper decimal places and units
 */
private fun formatValueForDisplay(value: Float, metadata: FieldMetadata): String {
    val displayValue = if (metadata.unit == "%") {
        // For percentage units, convert to 0-100 range
        value * 100
    } else {
        value
    }

    val formattedValue = if (metadata.decimals > 0) {
        val formatString = "%." + metadata.decimals + "f"
        formatNumber(displayValue.toDouble(), metadata.decimals)
    } else {
        displayValue.roundToInt().toString()
    }

    return if (metadata.unit.isNotEmpty()) {
        "$formattedValue${metadata.unit}"
    } else {
        formattedValue
    }
}
