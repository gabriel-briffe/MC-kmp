package org.mountaincircles.app.modules.airspace.import.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.network.ProgressData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeature
import org.mountaincircles.app.modules.airspace.logic.data.GeoJsonGeometry

/**
 * OpenAir format parser for airspace data
 * Ported from Android-native OpenAipProcessor
 */
class OpenAirParser {

    /**
     * Round a coordinate to 5 decimal places in a locale-independent way
     * This avoids issues with European locales using commas as decimal separators
     */
    private fun roundTo5Decimals(value: Double): Double {
        // Round to 5 decimal places using multiplication/division
        // This is more reliable than string formatting for locale independence
        val factor = 100000.0  // 10^5 for 5 decimal places
        return kotlin.math.round(value * factor) / factor
    }



    /**
     * Parse OpenAir format text into AirspaceData
     */
    suspend fun parseFrenchAirspace(
        openAirText: String,
        onProgress: (ProgressData) -> Unit
    ): Result<AirspaceData> {
        return try {
            Logger.log("AIRSPACE_PARSER", LogLevel.INFO, "Starting OpenAir parsing")

            val features = parseOpenAir(openAirText) { progress ->
                onProgress(ProgressData.fromBytes(progress.toLong(), 100L, "Parsing airspace data..."))
            }

            Logger.log("AIRSPACE_PARSER", LogLevel.INFO, "Parsed ${features.size} airspace features")

            // 🎯 PRESERVE ORIGINAL AI VALUES: Don't override AI values here
            // Global AI assignment will be handled at the download manager level
            val featuresWithOriginalAI = features

            // Count features by type for debugging and extract available types
            val typeCounts = featuresWithOriginalAI.groupBy { it.type }.mapValues { it.value.size }
            val availableTypes = featuresWithOriginalAI.map { it.type }.toSet()
            Logger.log("AIRSPACE_PARSER", LogLevel.INFO, "Feature type counts: $typeCounts")
            Logger.log("AIRSPACE_PARSER", LogLevel.INFO, "Available airspace types: $availableTypes")

            val airspaceData = AirspaceData(features = featuresWithOriginalAI, availableTypes = availableTypes)
            Result.success(airspaceData)

        } catch (e: Exception) {
            Logger.log("AIRSPACE_PARSER", LogLevel.ERROR, "Failed to parse OpenAir data: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse OpenAir text into feature list
     */
    private suspend fun parseOpenAir(text: String, onProgress: (Float) -> Unit): List<AirspaceFeature> {
        val features = mutableListOf<AirspaceFeature>()
        var currentFeature: AirspaceFeatureData? = null

        val lines = text.lineSequence()
        val totalLines = lines.count()
        var processedLines = 0

        lines.forEach { rawLine ->
            processedLines++
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            // Update progress only at major milestones (25%, 50%, 75%, 100%)
            val progressPercent = (processedLines.toFloat() / totalLines.toFloat()) * 0.4f + 0.5f // 50-90% range
            val currentPercent = (progressPercent * 100).toInt()

            if (currentPercent == 50 || currentPercent == 75) {
                onProgress(progressPercent)
            }

            // Remove comments
            val cleanLine = if (line.contains('*')) line.substringBefore('*').trim() else line
            if (cleanLine.isEmpty()) return@forEach

            val parts = cleanLine.split(' ', limit = 2)
            if (parts.isEmpty()) return@forEach

            val command = parts[0]
            val content = if (parts.size > 1) parts[1] else ""

            when (command) {
                "AC" -> {
                    // Start new airspace
                    currentFeature?.let { features.add(convertToAirspaceFeature(it)) }
                    currentFeature = AirspaceFeatureData()
                    val airspaceClass = content.uppercase()
                    currentFeature?.properties?.set("AC", airspaceClass)
                }
                "AI" -> {
                    // Airspace ID - will be overridden with sequential ID later
                    currentFeature?.properties?.set("AI", content)
                }
                "AN" -> {
                    // Airspace name
                    currentFeature?.properties?.set("name", content)
                }
                "AG" -> {
                    // Airspace frequency (ground)
                    currentFeature?.properties?.set("AG", content)
                }
                "AF" -> {
                    // Airspace frequency (air)
                    currentFeature?.properties?.set("AF", content)
                }
                "AY" -> {
                    // Airspace type (fallback when AC is UNC or missing)
                    currentFeature?.properties?.set("AY", content.uppercase())
                }
                "AH" -> {
                    // Upper altitude - apply formatting rules like PWA app
                    val formattedAltitude = formatAltitude(content)
                    currentFeature?.properties?.set("upperLimit", formattedAltitude)
                }
                "AL" -> {
                    // Lower altitude - apply formatting rules like PWA app
                    val formattedAltitude = formatAltitude(content)
                    currentFeature?.properties?.set("lowerLimit", formattedAltitude)
                }
                "DP", "DA", "DB" -> {
                    // Geometry points
                    parseGeometryPoint(currentFeature, command, content)
                }
            }
        }

        // Add final feature
        currentFeature?.let { features.add(convertToAirspaceFeature(it)) }

        return features
    }

    /**
     * Format altitude string with replacement rules from PWA app
     * Matches the JavaScript implementation in processOpenAip.js
     */
    private fun formatAltitude(altitudeContent: String): String {
        var altContent = altitudeContent.trim().uppercase()

        // Apply altitude formatting rules - matching PWA app exactly
        altContent = altContent.replace("AMSL", "MSL")
        altContent = altContent.replace("AGL", "GND")
        altContent = altContent.replace("SFC", "GND")
        altContent = altContent.replace(" FT", "FT")
        altContent = altContent.replace(" M", "M")
        altContent = altContent.replace("FTMSL", "FT MSL")
        altContent = altContent.replace("MMSL", "M MSL")
        altContent = altContent.replace("FTGND", "FT GND")
        altContent = altContent.replace("MGND", "M GND")

        // Remove leading spaces from FL values
        if (altContent.startsWith("FL ")) {
            altContent = "FL" + altContent.substring(3)
        }

        // Remove leading zeros from flight level values
        if (altContent.startsWith("FL")) {
            val flValue = altContent.substring(2)
            val flNum = flValue.toIntOrNull()
            if (flNum != null) {
                altContent = "FL$flNum"
            }
        }

        return altContent
    }

    private fun parseGeometryPoint(feature: AirspaceFeatureData?, command: String, content: String) {
        if (feature == null) return

        when (command) {
            "DP" -> {
                // Direct point
                parseCoordinate(content)?.let { (lon, lat) ->
                    feature.geometryPoints.add(GeometryPoint.Point(lon, lat))
                }
            }
            "DA" -> {
                // Arc
                parseArc(content)?.let { (radius, startAngle, endAngle) ->
                    feature.geometryPoints.add(GeometryPoint.Arc(radius, startAngle, endAngle))
                }
            }
            "DB" -> {
                // Arc between points
                parseArcBetweenPoints(content)?.let { (startLon, startLat, endLon, endLat) ->
                    feature.geometryPoints.add(GeometryPoint.ArcBetweenPoints(startLon, startLat, endLon, endLat))
                }
            }
        }
    }

    private fun parseCoordinate(coord: String): Pair<Double, Double>? {
        // Parse DD:MM:SS N/S DD:MM:SS E/W format
        val regex = Regex("(\\d{1,3}):(\\d{1,2}):(\\d{1,2})\\s*([NS])\\s+(\\d{1,3}):(\\d{1,2}):(\\d{1,2})\\s*([EW])")
        val match = regex.find(coord) ?: return null

        val (latD, latM, latS, latDir, lonD, lonM, lonS, lonDir) = match.destructured

        var lat = latD.toInt() + latM.toInt() / 60.0 + latS.toInt() / 3600.0
        var lon = lonD.toInt() + lonM.toInt() / 60.0 + lonS.toInt() / 3600.0

        if (latDir.uppercase() == "S") lat = -lat
        if (lonDir.uppercase() == "W") lon = -lon

        return Pair(lon, lat)
    }

    private fun parseArc(content: String): Triple<Double, Double, Double>? {
        val parts = content.split(',').map { it.trim() }
        if (parts.size != 3) return null

        val radius = parts[0].toDoubleOrNull() ?: return null
        val startAngle = parts[1].toDoubleOrNull() ?: return null
        val endAngle = parts[2].toDoubleOrNull() ?: return null

        return Triple(radius, startAngle, endAngle)
    }

    private fun parseArcBetweenPoints(content: String): Quadruple<Double, Double, Double, Double>? {
        val parts = content.split(',').map { it.trim() }
        if (parts.size != 2) return null

        val start = parseCoordinate(parts[0]) ?: return null
        val end = parseCoordinate(parts[1]) ?: return null

        return Quadruple(start.first, start.second, end.first, end.second)
    }

    private fun finalizeFeature(featureData: AirspaceFeatureData) {
        val properties = featureData.properties
        val ac = properties["AC"]
        val ay = properties["AY"]

        // Match Android native logic: use AC if present and not "UNC", otherwise use AY
        if (ac != null && ac != "UNC") {
            properties["type"] = ac
        } else if (ay != null) {
            // Map AY OVERFLIGHT_RESTRICTION to PROHIBITED to match mapping
            properties["type"] = if (ay == "OVERFLIGHT_RESTRICTION") "PROHIBITED" else ay
        }

        // Apply type remapping (same as Android native)
        remapType(properties)
    }

    private fun remapType(properties: MutableMap<String, String>) {
        val typeMap = mapOf(
            "P" to "PROHIBITED",
            "R" to "RESTRICTED",
            "Q" to "DANGER",
            "ASRA" to "ACTIVITY",
            "OFR" to "PROHIBITED",
            "GSEC" to "GLIDING_SECTOR"
        )
        properties["type"]?.let { currentType ->
            typeMap[currentType]?.let { mappedType ->
                properties["type"] = mappedType
            }
        }
    }

    private fun convertToAirspaceFeature(featureData: AirspaceFeatureData): AirspaceFeature {
        // Apply finalization logic before converting
        finalizeFeature(featureData)

        val properties = featureData.properties
        val type = properties["type"] ?: "UNKNOWN"
        val name = properties["name"]
        val upperLimit = properties["upperLimit"]
        val lowerLimit = properties["lowerLimit"]
        val ag = properties["AG"]  // Ground frequency
        val af = properties["AF"]  // Air frequency
        // AI will be set with sequential ID later in the pipeline


        // Convert geometry points to GeoJSON coordinates
        val pointCoordinates: List<List<Double>> = featureData.geometryPoints
            .filterIsInstance<GeometryPoint.Point>()
            .map { listOf(it.lon, it.lat) }

        // Create polygon if we have enough points
        val geometry = if (pointCoordinates.size >= 3) {
            // Close the polygon by adding the first point at the end
            val firstPoint = pointCoordinates.firstOrNull()
            if (firstPoint != null) {
                val closedCoords = pointCoordinates.toMutableList().apply { add(firstPoint) }
                // 🚀 COMPACT COORDINATES: Round to 5 decimal places for GeoJSON optimization
                // Use locale-independent coordinate formatting to avoid European comma issues
                val coordinatesJson = JsonArray(listOf(
                    JsonArray(closedCoords.map { JsonArray(it.map { coord ->
                        JsonPrimitive(roundTo5Decimals(coord))
                    }) })
                ))
                GeoJsonGeometry(type = "Polygon", coordinates = coordinatesJson)
            } else {
                GeoJsonGeometry(type = "MultiPolygon", coordinates = JsonArray(emptyList()))
            }
        } else {
            GeoJsonGeometry(type = "MultiPolygon", coordinates = JsonArray(emptyList()))
        }

        return AirspaceFeature(
            type = type,
            name = name,
            upperLimit = upperLimit,
            lowerLimit = lowerLimit,
            ai = null,  // Will be set with sequential ID later
            ag = ag,
            af = af,
            geometry = geometry
        )
    }

    // Helper data classes
    private data class AirspaceFeatureData(
        val properties: MutableMap<String, String> = mutableMapOf(),
        val geometryPoints: MutableList<GeometryPoint> = mutableListOf()
    )

    private sealed class GeometryPoint {
        data class Point(val lon: Double, val lat: Double) : GeometryPoint()
        data class Arc(val radius: Double, val startAngle: Double, val endAngle: Double) : GeometryPoint()
        data class ArcBetweenPoints(val startLon: Double, val startLat: Double, val endLon: Double, val endLat: Double) : GeometryPoint()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
