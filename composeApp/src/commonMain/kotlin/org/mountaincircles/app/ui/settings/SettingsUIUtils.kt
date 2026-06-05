package org.mountaincircles.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.settings.FieldMetadata
import org.mountaincircles.app.settings.FieldType

/**
 * Utility functions for working with settings UI components.
 */
object SettingsUIUtils {

    /**
     * Render the appropriate UI component for a field based on its metadata.
     */
    @Composable
    fun RenderField(
        metadata: FieldMetadata,
        value: Any?,
        onValueChange: (Any) -> Unit,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
        enabled: Boolean = true
    ) {
        when (metadata.fieldType) {
            FieldType.SLIDER -> {
                GenericSlider(
                    metadata = metadata,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
            FieldType.RANGE_SLIDER -> {
                GenericRangeSlider(
                    metadata = metadata,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
            FieldType.TOGGLE -> {
                GenericToggle(
                    metadata = metadata,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
            FieldType.DROPDOWN -> {
                // For dropdown, we need options - this would be provided by the calling code
                // For now, show a placeholder
                GenericTextField(
                    metadata = metadata,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
            FieldType.TEXT -> {
                GenericTextField(
                    metadata = metadata,
                    value = value,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
            else -> {
                // Fallback to text field for unsupported types
                GenericTextField(
                    metadata = metadata,
                    value = value?.toString() ?: "",
                    onValueChange = onValueChange,
                    modifier = modifier,
                    enabled = enabled
                )
            }
        }
    }

    /**
     * Get a human-readable description of a field type.
     */
    fun getFieldTypeDescription(fieldType: FieldType): String {
        return when (fieldType) {
            FieldType.AUTO -> "Automatically determined"
            FieldType.SLIDER -> "Numeric slider with min/max values"
            FieldType.RANGE_SLIDER -> "Numeric range slider with two values"
            FieldType.TOGGLE -> "On/off toggle switch"
            FieldType.DROPDOWN -> "Selection from predefined options"
            FieldType.TEXT -> "Text input field"
            FieldType.COLOR -> "Color picker"
            FieldType.FILE -> "File/directory picker"
            FieldType.CUSTOM -> "Custom implementation"
        }
    }

    /**
     * Check if a field should be visible in the UI.
     */
    fun isFieldVisible(metadata: FieldMetadata): Boolean {
        return !metadata.isHidden
    }

    /**
     * Get fields sorted by their order for display.
     */
    fun getOrderedFields(fields: List<FieldMetadata>): List<FieldMetadata> {
        return fields
            .filter { isFieldVisible(it) }
            .sortedBy { it.order }
    }

    /**
     * Group fields by their group name.
     */
    fun getGroupedFields(fields: List<FieldMetadata>): Map<String, List<FieldMetadata>> {
        return getOrderedFields(fields)
            .groupBy { it.group.ifEmpty { "General" } }
    }

    /**
     * Get validation status for a field.
     */
    fun getFieldValidationStatus(metadata: FieldMetadata, value: Any?): FieldValidationStatus {
        val errors = org.mountaincircles.app.settings.SettingsMetadataUtils.validateField(
            metadata, value
        )

        return if (errors.isEmpty()) {
            FieldValidationStatus.VALID
        } else {
            FieldValidationStatus.INVALID(errors.first())
        }
    }

    /**
     * Check if all fields in a group are valid.
     */
    fun areGroupFieldsValid(fields: List<FieldMetadata>, values: Map<String, Any?>): Boolean {
        return fields.all { field ->
            val value = values[field.name]
            getFieldValidationStatus(field, value) == FieldValidationStatus.VALID
        }
    }
}

/**
 * Validation status for a field.
 */
sealed class FieldValidationStatus {
    object VALID : FieldValidationStatus()
    data class INVALID(val errorMessage: String) : FieldValidationStatus()

    val isValid: Boolean get() = this is VALID
    val isInvalid: Boolean get() = this is INVALID
    val errorMessageOrNull: String? get() = when (this) {
        is INVALID -> errorMessage
        else -> null
    }
}

/**
 * Theme constants for settings UI components.
 */
object SettingsUITheme {
    val colors = SettingsUIColors
    val spacing = SettingsUISpacing
    val typography = SettingsUITypography
}

object SettingsUIColors {
    val primary = androidx.compose.ui.graphics.Color.Cyan
    val secondary = androidx.compose.ui.graphics.Color.Gray
    val background = androidx.compose.ui.graphics.Color.Black
    val surface = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val error = androidx.compose.ui.graphics.Color.Red
    val warning = androidx.compose.ui.graphics.Color.Yellow
    val success = androidx.compose.ui.graphics.Color.Green
    val textPrimary = androidx.compose.ui.graphics.Color.White
    val textSecondary = androidx.compose.ui.graphics.Color.Gray
    val textDisabled = androidx.compose.ui.graphics.Color.DarkGray
}

object SettingsUISpacing {
    val fieldSpacing = 16.dp
    val groupSpacing = 24.dp
    val sectionSpacing = 32.dp
    val cardPadding = 16.dp
    val fieldPadding = 8.dp
}

object SettingsUITypography {
    // These would be defined using MaterialTheme.typography in actual usage
    // This is just a placeholder for the concept
    const val titleScale = 1.2f
    const val subtitleScale = 1.0f
    const val bodyScale = 0.9f
    const val captionScale = 0.8f
}
