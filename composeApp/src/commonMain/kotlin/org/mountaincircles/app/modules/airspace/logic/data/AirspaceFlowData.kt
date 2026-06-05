package org.mountaincircles.app.modules.airspace.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData
import org.mountaincircles.app.modules.airspace.logic.data.toUnifiedProgress

/**
 * Data classes for selective reactive flows in AirspaceModule
 */
data class AirspaceImportDisplayData(
    val isDownloading: Boolean,
    val currentProgress: UnifiedProgress?,
    val selectedCountries: List<String>,
    val hasData: Boolean,
    val importedAt: Long?,
    val hasError: Boolean,
    val errorMessage: String?
)

data class AirspaceLayerDisplayData(
    val isVisible: Boolean,
    val visibleTypes: Set<String>,
    val showPopup: Boolean,
    val popupFeatures: List<AirspaceFeatureData>,
)


data class AirspaceFilterDisplayData(
    val visibleTypes: Set<String>,
    val availableTypes: Set<String>,
    val hasData: Boolean
)
