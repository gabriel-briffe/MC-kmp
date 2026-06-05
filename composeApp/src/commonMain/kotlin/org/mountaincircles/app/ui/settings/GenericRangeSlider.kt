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
 * Generic range slider component with two thumbs for min/max values
 * Shows white on extremities (filtering out the range between thumbs)
 */
@Composable
fun GenericRangeSlider(
    metadata: FieldMetadata,
    value: Any?,
    onValueChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Only render if this is a range slider field
    if (metadata.fieldType != FieldType.RANGE_SLIDER) {
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

        // Convert value to Pair<Float, Float> for range slider
        val currentRange = when (value) {
            is Pair<*, *> -> {
                val first = (value.first as? Number)?.toFloat() ?: metadata.min.toFloat()
                val second = (value.second as? Number)?.toFloat() ?: metadata.max.toFloat()
                Pair(first, second)
            }
            else -> Pair(metadata.min.toFloat(), metadata.max.toFloat())
        }

        // Range slider row with value displays
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left value display
            val leftDisplayValue = formatValueForDisplay(currentRange.first, metadata)
            Text(
                text = leftDisplayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) Color.Cyan else Color.Gray,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(60.dp)
            )

            // Range slider with white on extremities
            androidx.compose.material3.RangeSlider(
                value = currentRange.first..currentRange.second,
                onValueChange = { range ->
                    val constrainedMin = range.start.coerceIn(metadata.min.toFloat(), metadata.max.toFloat())
                    val constrainedMax = range.endInclusive.coerceIn(metadata.min.toFloat(), metadata.max.toFloat())

                    val steppedMin = if (metadata.step > 0) {
                        ((constrainedMin / metadata.step).roundToInt() * metadata.step).toFloat()
                    } else {
                        constrainedMin
                    }

                    val steppedMax = if (metadata.step > 0) {
                        ((constrainedMax / metadata.step).roundToInt() * metadata.step).toFloat()
                    } else {
                        constrainedMax
                    }

                    // Ensure min <= max
                    val finalMin = minOf(steppedMin, steppedMax)
                    val finalMax = maxOf(steppedMin, steppedMax)

                    onValueChange(Pair(finalMin, finalMax))
                },
                valueRange = metadata.min.toFloat()..metadata.max.toFloat(),
                steps = if (metadata.step > 0) {
                    ((metadata.max - metadata.min) / metadata.step).toInt().coerceAtLeast(0)
                } else {
                    0
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Gray.copy(alpha = 0.3f), // Dimmed range between thumbs
                    inactiveTrackColor = Color.White.copy(alpha = 1.0f), // White on extremities
                    activeTickColor = Color.White.copy(alpha = 0.7f),
                    inactiveTickColor = Color.White.copy(alpha = 0.5f)
                ),
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )

            // Right value display
            val rightDisplayValue = formatValueForDisplay(currentRange.second, metadata)
            Text(
                text = rightDisplayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) Color.Cyan else Color.Gray,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp)
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