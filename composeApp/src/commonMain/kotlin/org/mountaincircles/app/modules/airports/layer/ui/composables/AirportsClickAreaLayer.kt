package org.mountaincircles.app.modules.airports.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.layer.ui.AirportsLayerManager
import org.mountaincircles.app.modules.airports.logic.data.AirportsLayerDisplayData
import org.mountaincircles.app.modules.airports.logic.data.AirportFeatureData
import kotlinx.serialization.json.JsonPrimitive

/**
 * Click area layer for airports (invisible circles for click detection)
 * This layer handles clicks using MapLibre's built-in feature querying
 */
@Composable
fun AirportsClickAreaLayer(module: AirportsModule, layerManager: AirportsLayerManager) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airports click area layer recomposing")

    // ✅ AIRSPACE-STYLE: Simple visibility + raw data + MapLibre filtering
    val isVisible by module.layerVisibility.collectAsState()
    val iconsMinZoom by module.iconsMinZoom.collectAsState()

    // 🎯 Selective reactivity - collect only needed fields
    val currentVisibleTypes by module.currentVisibleTypes.collectAsState()
    val disabledIds by module.disabledAirportIds.collectAsState()

    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Airports click layer with MapLibre filtering (visible: $isVisible, visibleTypes: ${currentVisibleTypes.size})")

    // Early exit if not visible
    if (!isVisible) {
        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Airports click layer not rendered - visibility: $isVisible")
        return
    }

    // Click area settings - use derived state for performance
    val airportClickSize by remember { derivedStateOf { 20.0f } } // Fixed size for now, no settings yet

    // Get raw airports data URI
    val rawAirportsUri = run {
        val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
        val dataDir = fileManager.getAppDataDirectory()
        val airportsDir = "$dataDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_DIR}"
        val filePath = "$airportsDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_GEOJSON_FILE}"
        "file://$filePath"
    }

    // Create GeoJSON source from raw data
    val geoJsonSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(rawAirportsUri)
    )

    // Create MapLibre filter for airport visibility (same as circles/labels)
    val airportTypeFilter = remember(currentVisibleTypes, disabledIds) {
        // Check if airport is disabled first, then fall back to type logic
        switch(
            condition(
                test = const(disabledIds.toList()).contains(feature["_id"].asString()),
                output = const(currentVisibleTypes.toList()).contains(const("disabled"))
            ),
            fallback = const(currentVisibleTypes.toList()).contains(feature["type"].asString())
        )
    }

    // Create invisible circle layer for click areas
    CircleLayer(
        id = "airports_click_areas",
        source = geoJsonSource,
        filter = airportTypeFilter,  // 🎯 MapLibre filtering instead of pre-filtered data
        visible = isVisible,
        minZoom = iconsMinZoom,
        radius = const(airportClickSize.dp), // Click area size (fixed for now)
        color = const(androidx.compose.ui.graphics.Color.Transparent), // Completely invisible
        opacity = const(0.0f), // Always invisible
        onClick = { features ->
            // Handle click using MapLibre's feature querying
            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "CircleLayer onClick: Received ${features.size} features")

            if (features.isNotEmpty()) {
                val feature = features.first()
                val airportName = feature.getStringProperty("name")
                val airportType = feature.getStringProperty("originalType") ?: feature.getStringProperty("type")
                val id = feature.getStringProperty("_id") ?: "N/A"
                val icaoCode = feature.getStringProperty("icaoCode")

                if (!airportName.isNullOrBlank() && airportType != null) {
                    Logger.log("AIRPORTS_CLICK", LogLevel.INFO, "🏁 Clicked on airport: '$airportName' (ID: $id, Type: $airportType)")
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Feature properties keys: ${feature.properties?.keys}")

                    // Extract additional airport data from properties
                    // Parse elevation object to readable string
                    val elevationRaw = feature.properties?.get("elevation")
                    val elevationString = when (elevationRaw) {
                        is kotlinx.serialization.json.JsonObject -> {
                            val value = (elevationRaw["value"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                            val unit = (elevationRaw["unit"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                            val referenceDatum = (elevationRaw["referenceDatum"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

                            if (value != null) {
                                val unitStr = when (unit) {
                                    0 -> "m"
                                    2 -> "ft"
                                    else -> "?"
                                }
                                val datumStr = when (referenceDatum) {
                                    1 -> "msl"
                                    else -> "?"
                                }
                                "$value$unitStr $datumStr"
                            } else null
                        }
                        is String -> elevationRaw // Fallback for already parsed strings (backward compatibility)
                        else -> null
                    }
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Elevation raw: $elevationRaw, parsed: $elevationString")

                    val trafficTypeRaw = feature.properties?.get("trafficType")
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Traffic type raw type: ${trafficTypeRaw?.javaClass?.simpleName}")
                    val trafficType = when (trafficTypeRaw) {
                        is kotlinx.serialization.json.JsonArray -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Traffic type is JsonArray with ${trafficTypeRaw.size} items")
                            trafficTypeRaw.mapNotNull { jsonElement ->
                                (jsonElement as? kotlinx.serialization.json.JsonPrimitive)?.content
                            }
                        }
                        else -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Traffic type is not JsonArray, it's: ${trafficTypeRaw?.javaClass?.simpleName}")
                            emptyList()
                        }
                    }
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Traffic type raw: $trafficTypeRaw, parsed: $trafficType")

                    val frequenciesRaw = feature.properties?.get("frequencies")
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequencies raw type: ${frequenciesRaw?.javaClass?.simpleName}")
                    val frequencies = when (frequenciesRaw) {
                        is kotlinx.serialization.json.JsonArray -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequencies is JsonArray with ${frequenciesRaw.size} items")
                            frequenciesRaw.mapNotNull { freqElement ->
                                Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Processing frequency item: $freqElement (type: ${freqElement?.javaClass?.simpleName})")
                                when (freqElement) {
                                    is kotlinx.serialization.json.JsonObject -> {
                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequency item is JsonObject")
                                        val nameElement = freqElement["name"]
                                        val valueElement = freqElement["value"]
                                        val primaryElement = freqElement["primary"]

                                        val name = (nameElement as? kotlinx.serialization.json.JsonPrimitive)?.content
                                        val value = (valueElement as? kotlinx.serialization.json.JsonPrimitive)?.content
                                        val primary = (primaryElement as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() ?: false

                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequency parsed - name: $name, value: $value, primary: $primary")
                                        if (name != null && value != null) {
                                            org.mountaincircles.app.modules.airports.logic.data.FrequencyData(name, value, primary)
                                        } else null
                                    }
                                    else -> {
                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequency item is not JsonObject")
                                        null
                                    }
                                }
                            }.sortedByDescending { it.primary }
                        }
                        else -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequencies is not JsonArray, it's: ${frequenciesRaw?.javaClass?.simpleName}")
                            emptyList()
                        }
                    }
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Frequencies parsed: $frequencies")

                    // Parse runways
                    val runwaysRaw = feature.properties?.get("runways")
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runways raw: $runwaysRaw")
                    val runways = when (runwaysRaw) {
                        is kotlinx.serialization.json.JsonArray -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runways is JsonArray with ${runwaysRaw.size} items")
                            runwaysRaw.mapNotNull { runwayElement ->
                                Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Processing runway item: $runwayElement (type: ${runwayElement?.javaClass?.simpleName})")
                                when (runwayElement) {
                                    is kotlinx.serialization.json.JsonObject -> {
                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runway item is JsonObject")
                                        val designatorElement = runwayElement["designator"]
                                        val dimensionElement = runwayElement["dimension"]
                                        val surfaceElement = runwayElement["surface"]

                                        val designator = (designatorElement as? kotlinx.serialization.json.JsonPrimitive)?.content

                                        // Parse dimensions
                                        val length = when (dimensionElement) {
                                            is kotlinx.serialization.json.JsonObject -> {
                                                val lengthObj = dimensionElement["length"] as? kotlinx.serialization.json.JsonObject
                                                val lengthValue = (lengthObj?.get("value") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                                                val lengthUnit = (lengthObj?.get("unit") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                                                if (lengthValue != null) {
                                                    when (lengthUnit) {
                                                        0 -> "${lengthValue}m"
                                                        2 -> "${lengthValue}ft"
                                                        else -> "$lengthValue"
                                                    }
                                                } else null
                                            }
                                            else -> null
                                        }

                                        val width = when (dimensionElement) {
                                            is kotlinx.serialization.json.JsonObject -> {
                                                val widthObj = dimensionElement["width"] as? kotlinx.serialization.json.JsonObject
                                                val widthValue = (widthObj?.get("value") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                                                val widthUnit = (widthObj?.get("unit") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                                                if (widthValue != null) {
                                                    when (widthUnit) {
                                                        0 -> "${widthValue}m"
                                                        2 -> "${widthValue}ft"
                                                        else -> "$widthValue"
                                                    }
                                                } else null
                                            }
                                            else -> null
                                        }

                                        // Parse surface mainComposite
                                        val mainComposite = when (surfaceElement) {
                                            is kotlinx.serialization.json.JsonObject -> {
                                                val mainCompositeValue = (surfaceElement["mainComposite"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                                                when (mainCompositeValue) {
                                                    0 -> "Asphalt"
                                                    1 -> "Concrete"
                                                    2 -> "Grass"
                                                    3 -> "Sand"
                                                    4 -> "Water"
                                                    5 -> "Bitume"
                                                    else -> "Unknown"
                                                }
                                            }
                                            else -> null
                                        }

                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runway parsed - designator: $designator, length: $length, width: $width, mainComposite: $mainComposite")
                                        if (designator != null && length != null && width != null) {
                                            // mainComposite is now optional - use blank space if not present
                                            val surfaceType = mainComposite ?: ""
                                            org.mountaincircles.app.modules.airports.logic.data.RunwayData(designator, length, width, surfaceType)
                                        } else null
                                    }
                                    else -> {
                                        Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runway item is not JsonObject")
                                        null
                                    }
                                }
                            }
                        }
                        else -> {
                            Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runways is not JsonArray, it's: ${runwaysRaw?.javaClass?.simpleName}")
                            emptyList()
                        }
                    }
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Runways parsed: $runways")

                    // Parse description
                    val descriptionRaw = feature.properties?.get("description")
                    val description = (descriptionRaw as? kotlinx.serialization.json.JsonPrimitive)?.content
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Description parsed: $description")

                    // Parse disabled state - if property exists, airport is disabled
                    val disabled = if (feature.properties?.containsKey("disabled") == true) true else null
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Disabled parsed: $disabled")

                    // Parse pics array
                    val pics = when (val picsRaw = feature.properties?.get("pics")) {
                        is kotlinx.serialization.json.JsonArray -> {
                            picsRaw.mapNotNull { picElement ->
                                (picElement as? kotlinx.serialization.json.JsonPrimitive)?.content
                            }
                        }
                        else -> emptyList()
                    }
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Pics parsed: $pics")

                    // Create airport feature data and show popup
                    val airportFeature = AirportFeatureData(
                        name = airportName,
                        icaoCode = icaoCode,
                        id = id, // The _id used for identification
                        type = airportType,
                        elevation = elevationString,
                        trafficType = trafficType,
                        frequencies = frequencies,
                        runways = runways,
                        description = description,
                        disabled = disabled,
                        pics = pics
                    )

                    Logger.log("AIRPORTS_CLICK", LogLevel.INFO, "🎯 Showing airport popup for: $airportName")
                    module.showAirportPopup(listOf(airportFeature))

                    ClickResult.Consume
                } else {
                    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "CircleLayer onClick: Feature has no valid name property")
                    ClickResult.Pass
                }
            } else {
                Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "CircleLayer onClick: No features in click area")
                ClickResult.Pass
            }
        }
    )

    Logger.log("AIRPORTS_CLICK", LogLevel.DEBUG, "Created airports click area layer (${airportClickSize}px invisible click areas)")
}
