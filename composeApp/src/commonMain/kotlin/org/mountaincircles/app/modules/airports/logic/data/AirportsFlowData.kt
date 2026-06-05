package org.mountaincircles.app.modules.airports.logic.data

/**
 * Data classes for selective reactive flows in AirportsModule
 */
data class AirportsFilterDisplayData(
    val visibleTypes: Set<String>,
    val availableTypes: Set<String>,
    val hasDataToRender: Boolean
)



data class AirportsLayerDisplayData(
    val airportsVisibility: Boolean,
    val visibleTypes: Set<String>,
    val hasDataToRender: Boolean,
    val airportIconSize: Float,
    val airportIconsMinZoom: Float,
    val airportLabelSize: Float,
    val airportLabelsMinZoom: Float
)

data class AirportFeatureData(
    val name: String,
    val icaoCode: String? = null,
    val id: String, // The _id field used for identification
    val type: String,
    val elevation: String? = null,
    val trafficType: List<String> = emptyList(),
    val frequencies: List<FrequencyData> = emptyList(),
    val runways: List<RunwayData> = emptyList(),
    val description: String? = null,
    val disabled: Boolean? = null,
    val pics: List<String> = emptyList() // Picture file paths relative to airports directory
)

data class FrequencyData(
    val name: String,
    val value: String,
    val primary: Boolean = false
)

data class RunwayData(
    val designator: String,
    val length: String,
    val width: String,
    val mainComposite: String
)

data class AirportsPopupDisplayData(
    val showPopup: Boolean,
    val popupFeatures: List<AirportFeatureData>
)