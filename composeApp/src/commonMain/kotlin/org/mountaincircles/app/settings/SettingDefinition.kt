package org.mountaincircles.app.settings

/**
 * Definition for a single setting with all its metadata
 */
data class SettingDefinition(
    val name: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val defaultValue: Float,
    val unit: String,
    val group: String,
    val step: Float = 1.0f,
    val decimals: Int = 1,
    val order: Int = 0,
    val description: String = ""
)

/**
 * Extension to convert SettingDefinition to FieldMetadata for UI
 */
fun SettingDefinition.toFieldMetadata(): FieldMetadata {
    // Detect boolean-like settings (0..1 range with step 1 and 0 decimals)
    val isBooleanLike = range.start == 0f && range.endInclusive == 1f && step == 1f && decimals == 0

    // Detect range slider settings by name containing "range"
    val isRangeSlider = name.contains("range", ignoreCase = true)

    return when {
        isBooleanLike -> SettingsMetadataExtractor.booleanField(
            name = name,
            label = label,
            description = description,
            group = group,
            order = order
        )
        isRangeSlider -> SettingsMetadataExtractor.rangeSliderField(
            name = name,
            label = label,
            description = description,
            min = range.start.toDouble(),
            max = range.endInclusive.toDouble(),
            step = step.toDouble(),
            unit = unit,
            decimals = decimals,
            group = group,
            order = order
        )
        else -> SettingsMetadataExtractor.sliderField(
            name = name,
            label = label,
            description = description,
            min = range.start.toDouble(),
            max = range.endInclusive.toDouble(),
            step = step.toDouble(),
            unit = unit,
            decimals = decimals,
            group = group,
            order = order
        )
    }
}
