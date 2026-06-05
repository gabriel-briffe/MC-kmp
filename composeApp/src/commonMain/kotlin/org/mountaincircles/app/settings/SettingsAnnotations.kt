package org.mountaincircles.app.settings

import kotlin.reflect.KClass

/**
 * Annotation for settings fields to enable automatic UI generation and validation.
 *
 * This annotation provides metadata about settings fields that can be used to:
 * - Generate appropriate UI components (slider, toggle, dropdown, etc.)
 * - Apply validation constraints (min/max values, units, etc.)
 * - Provide user-friendly labels and descriptions
 * - Support different field types with proper handling
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsField(
    /** User-friendly label for the setting */
    val label: String,

    /** Optional description explaining what this setting does */
    val description: String = "",

    /** The type of UI component to use for this field */
    val type: FieldType = FieldType.AUTO,

    /** The unit of measurement (e.g., "dp", "sp", "%", "m/s") */
    val unit: String = "",

    /** Minimum value for numeric fields */
    val min: Double = Double.NEGATIVE_INFINITY,

    /** Maximum value for numeric fields */
    val max: Double = Double.POSITIVE_INFINITY,

    /** Step size for sliders and numeric inputs */
    val step: Double = 1.0,

    /** Number of decimal places to show */
    val decimals: Int = 0,

    /** Whether this field is required */
    val required: Boolean = false,

    /** Whether this field is enabled/editable */
    val enabled: Boolean = true,

    /** Group name for organizing related settings */
    val group: String = "",

    /** Display order within the group (lower numbers appear first) */
    val order: Int = 0,

    /** Icon resource name for this setting */
    val icon: String = "",

    /** Advanced options as key-value pairs */
    val options: Array<String> = []
)

/**
 * Specifies the type of UI component to use for a settings field.
 */
enum class FieldType {
    /** Automatically determine the best component based on field type */
    AUTO,

    /** Slider component for numeric values */
    SLIDER,

    /** Range slider component for numeric ranges with two values */
    RANGE_SLIDER,

    /** Toggle/switch component for boolean values */
    TOGGLE,

    /** Dropdown/selection component */
    DROPDOWN,

    /** Text input field */
    TEXT,

    /** Color picker component */
    COLOR,

    /** File/directory picker */
    FILE,

    /** Custom component (requires special handling) */
    CUSTOM
}

/**
 * Annotation for settings classes to provide class-level metadata.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsClass(
    /** Display name for the settings class */
    val name: String,

    /** Optional description of what these settings control */
    val description: String = "",

    /** Version of the settings format (for migrations) */
    val version: Int = 1,

    /** Icon resource name for the settings class */
    val icon: String = "",

    /** Category this settings class belongs to */
    val category: String = "General"
)

/**
 * Annotation to mark fields that should be persisted but not shown in UI.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiddenField(
    /** Optional key for persistence (defaults to field name) */
    val key: String = ""
)

/**
 * Annotation to mark fields that require special validation.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidatedField(
    /** Validation rule class to use */
    val validator: KClass<*>,

    /** Error message to show when validation fails */
    val errorMessage: String = "Invalid value"
)

/**
 * Annotation to specify dependencies between settings fields.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(
    /** Name of the field this depends on */
    val field: String,

    /** Required value(s) for the dependency field */
    val values: Array<String> = [],

    /** Whether this field should be hidden when dependency is not met */
    val hideWhenDisabled: Boolean = true
)

/**
 * Annotation to mark settings that require restart to take effect.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresRestart(
    /** Message to show to user about restart requirement */
    val message: String = "App restart required for changes to take effect"
)

/**
 * Annotation to mark experimental or advanced settings.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Advanced(
    /** Warning message about the experimental nature */
    val warning: String = "This is an experimental feature"
)
