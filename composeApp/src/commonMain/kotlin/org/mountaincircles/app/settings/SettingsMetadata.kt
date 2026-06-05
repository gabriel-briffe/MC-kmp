package org.mountaincircles.app.settings

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Data class representing metadata for a settings field.
 * This simplified version works without reflection in common code.
 */
data class FieldMetadata(
    /** The name of the property */
    val name: String,

    /** The declared type of the property */
    val type: KClass<*>,

    /** The field type to use */
    val fieldType: FieldType,

    /** User-friendly label */
    val label: String,

    /** Description of what this field does */
    val description: String,

    /** Unit of measurement */
    val unit: String,

    /** Minimum value for numeric fields */
    val min: Double,

    /** Maximum value for numeric fields */
    val max: Double,

    /** Step size for sliders */
    val step: Double,

    /** Number of decimal places */
    val decimals: Int,

    /** Whether the field is required */
    val required: Boolean,

    /** Whether the field is enabled */
    val enabled: Boolean,

    /** Group name for organization */
    val group: String,

    /** Display order within group */
    val order: Int,

    /** Icon resource name */
    val icon: String,

    /** Whether this field should be hidden from UI */
    val isHidden: Boolean,

    /** Whether this field requires restart */
    val requiresRestart: Boolean,

    /** Whether this is an advanced/experimental field */
    val isAdvanced: Boolean
)

/**
 * Data class representing metadata for a settings class.
 * This simplified version works without reflection in common code.
 */
data class ClassMetadata(
    /** Display name for the settings class */
    val name: String,

    /** Description of what these settings control */
    val description: String,

    /** Version of the settings format */
    val version: Int,

    /** Icon resource name */
    val icon: String,

    /** Category this settings class belongs to */
    val category: String,

    /** All field metadata for this class */
    val fields: List<FieldMetadata>
)

/**
 * Utility class for creating metadata from settings classes.
 * This simplified version works without reflection in common code.
 */
object SettingsMetadataExtractor {

    /**
     * Create metadata for a settings class with manual field definitions.
     * This approach works in common source sets without reflection.
     *
     * @param name Display name for the settings class
     * @param description Description of what these settings control
     * @param fields List of field metadata for this class
     * @param version Version of the settings format
     * @param category Category this settings class belongs to
     * @param icon Icon resource name
     * @return ClassMetadata describing the settings class
     */
    fun createClassMetadata(
        name: String,
        description: String = "",
        fields: List<FieldMetadata>,
        version: Int = 1,
        category: String = "General",
        icon: String = ""
    ): ClassMetadata {
        return ClassMetadata(
            name = name,
            description = description,
            version = version,
            icon = icon,
            category = category,
            fields = fields.sortedBy { it.order }
        )
    }

    /**
     * Create field metadata for a boolean setting with toggle UI.
     */
    fun booleanField(
        name: String,
        label: String,
        description: String = "",
        defaultValue: Boolean = false,
        required: Boolean = false,
        enabled: Boolean = true,
        group: String = "",
        order: Int = 0,
        isHidden: Boolean = false,
        requiresRestart: Boolean = false,
        isAdvanced: Boolean = false
    ): FieldMetadata {
        return FieldMetadata(
            name = name,
            type = Boolean::class,
            fieldType = FieldType.TOGGLE,
            label = label,
            description = description,
            unit = "",
            min = 0.0,
            max = 1.0,
            step = 1.0,
            decimals = 0,
            required = required,
            enabled = enabled,
            group = group,
            order = order,
            icon = "",
            isHidden = isHidden,
            requiresRestart = requiresRestart,
            isAdvanced = isAdvanced
        )
    }

    /**
     * Create field metadata for a numeric setting with slider UI.
     */
    fun sliderField(
        name: String,
        label: String,
        description: String = "",
        min: Double = 0.0,
        max: Double = 100.0,
        step: Double = 1.0,
        unit: String = "",
        decimals: Int = 0,
        required: Boolean = false,
        enabled: Boolean = true,
        group: String = "",
        order: Int = 0,
        isHidden: Boolean = false,
        requiresRestart: Boolean = false,
        isAdvanced: Boolean = false
    ): FieldMetadata {
        return FieldMetadata(
            name = name,
            type = when {
                decimals > 0 -> Float::class
                else -> Int::class
            },
            fieldType = FieldType.SLIDER,
            label = label,
            description = description,
            unit = unit,
            min = min,
            max = max,
            step = step,
            decimals = decimals,
            required = required,
            enabled = enabled,
            group = group,
            order = order,
            icon = "",
            isHidden = isHidden,
            requiresRestart = requiresRestart,
            isAdvanced = isAdvanced
        )
    }

    /**
     * Create field metadata for a range slider setting with two values.
     */
    fun rangeSliderField(
        name: String,
        label: String,
        description: String = "",
        min: Double = 0.0,
        max: Double = 100.0,
        step: Double = 1.0,
        unit: String = "",
        decimals: Int = 1,
        required: Boolean = false,
        enabled: Boolean = true,
        group: String = "",
        order: Int = 0,
        isHidden: Boolean = false,
        requiresRestart: Boolean = false,
        isAdvanced: Boolean = false
    ): FieldMetadata {
        return FieldMetadata(
            name = name,
            type = Pair::class,
            fieldType = FieldType.RANGE_SLIDER,
            label = label,
            description = description,
            unit = unit,
            min = min,
            max = max,
            step = step,
            decimals = decimals,
            required = required,
            enabled = enabled,
            group = group,
            order = order,
            icon = "",
            isHidden = isHidden,
            requiresRestart = requiresRestart,
            isAdvanced = isAdvanced
        )
    }

    /**
     * Create field metadata for a text setting.
     */
    fun textField(
        name: String,
        label: String,
        description: String = "",
        required: Boolean = false,
        enabled: Boolean = true,
        group: String = "",
        order: Int = 0,
        isHidden: Boolean = false,
        requiresRestart: Boolean = false,
        isAdvanced: Boolean = false
    ): FieldMetadata {
        return FieldMetadata(
            name = name,
            type = String::class,
            fieldType = FieldType.TEXT,
            label = label,
            description = description,
            unit = "",
            min = Double.NEGATIVE_INFINITY,
            max = Double.POSITIVE_INFINITY,
            step = 1.0,
            decimals = 0,
            required = required,
            enabled = enabled,
            group = group,
            order = order,
            icon = "",
            isHidden = isHidden,
            requiresRestart = requiresRestart,
            isAdvanced = isAdvanced
        )
    }
}

/**
 * Utility functions for working with settings metadata.
 */
object SettingsMetadataUtils {

    /**
     * Get all visible (non-hidden) fields from class metadata.
     */
    fun getVisibleFields(metadata: ClassMetadata): List<FieldMetadata> {
        return metadata.fields.filter { !it.isHidden }
    }

    /**
     * Get fields grouped by their group name.
     */
    fun getGroupedFields(metadata: ClassMetadata): Map<String, List<FieldMetadata>> {
        return metadata.fields
            .filter { !it.isHidden }
            .groupBy { it.group.ifEmpty { "General" } }
    }

    /**
     * Get all fields that require restart.
     */
    fun getFieldsRequiringRestart(metadata: ClassMetadata): List<FieldMetadata> {
        return metadata.fields.filter { it.requiresRestart }
    }

    /**
     * Get all advanced/experimental fields.
     */
    fun getAdvancedFields(metadata: ClassMetadata): List<FieldMetadata> {
        return metadata.fields.filter { it.isAdvanced }
    }

    /**
     * Validate a value for a field.
     */
    fun validateField(field: FieldMetadata, value: Any?): List<String> {
        val errors = mutableListOf<String>()

        if (field.required && value == null) {
            errors.add("${field.label} is required")
        }

        when (field.fieldType) {
            FieldType.SLIDER -> {
                if (value is Number) {
                    val doubleValue = value.toDouble()
                    if (doubleValue < field.min) {
                        errors.add("${field.label} must be at least ${field.min}")
                    }
                    if (doubleValue > field.max) {
                        errors.add("${field.label} must be at most ${field.max}")
                    }
                }
            }
            else -> {
                // Add more validation logic for other field types as needed
            }
        }

        return errors
    }

    /**
     * Check if a field value is valid.
     */
    fun isValidField(field: FieldMetadata, value: Any?): Boolean {
        return validateField(field, value).isEmpty()
    }
}
