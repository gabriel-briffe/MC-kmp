package org.mountaincircles.app.modules.airspace.layer.logic

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Controller for airspace layer highlight operations
 * Handles highlight functionality for the layer manager
 */
class AirspaceLayerFilteringController(private val module: AirspaceModule) {


    /**
     * Create highlight data for a specific airspace identifier (AI field)
     */
    fun createHighlightData(aiField: String, rawAirspaceUri: String?): String {
        Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.DEBUG, "Updating highlight for AI: '$aiField'")

        // Get the raw airspace data (no filtering)
        val rawData = try {
            runBlocking {
                if (rawAirspaceUri?.startsWith("file://") == true) {
                    val filePath = rawAirspaceUri.substringAfter("file://")
                    val fileManager = getGlobalFileManager()
                    if (fileManager.exists(filePath)) {
                        fileManager.readText(filePath)
                    } else {
                        Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.WARN, "Raw airspace file not found: $filePath")
                        null
                    }
                } else {
                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.WARN, "Invalid raw URI format: $rawAirspaceUri")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.ERROR, "Failed to read raw airspace data: ${e.message}")
            null
        }

        if (rawData == null) {
            Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.WARN, "No raw data available for highlight")
            return """{"type":"FeatureCollection","features":[]}"""
        }

        try {
            // Parse the GeoJSON to find the feature with matching AI
            val lenientJson = Json { isLenient = true }
            val geoJson = lenientJson.parseToJsonElement(rawData)
            val features = geoJson.jsonObject["features"]?.jsonArray

            if (features != null) {
                // Find the feature with matching AI field
                val matchingFeature = features.find { feature ->
                    val properties = feature.jsonObject["properties"]?.jsonObject
                    val featureAI = properties?.get("AI")?.jsonPrimitive?.content
                    featureAI == aiField
                }

                if (matchingFeature != null) {
                    // Create single-feature GeoJSON string for highlight
                    val featureJson = Json.encodeToString(
                        JsonElement.serializer(),
                        buildJsonObject {
                            put("type", "FeatureCollection")
                            putJsonArray("features") {
                                add(matchingFeature)
                            }
                        }
                    )
                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.INFO, "Created highlight for AI: '$aiField'")
                    return featureJson
                } else {
                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.WARN, "No feature found with AI: '$aiField'")
                }
            } else {
                Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.WARN, "No features found in filtered GeoJSON")
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.ERROR, "Failed to create highlight data: ${e.message}")
        }

        // Return empty feature collection on failure
        return """{"type":"FeatureCollection","features":[]}"""
    }

    /**
     * Clear highlight data
     */
    fun clearHighlight(): String {
        Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.DEBUG, "Clearing highlight")
        return """{"type":"FeatureCollection","features":[]}"""
    }
}
