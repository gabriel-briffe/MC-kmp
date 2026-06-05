package org.mountaincircles.app.modules.airspace.logic.data

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Minimal data structures for airspace features
 * Based on GeoJSON Feature specification
 */
@Serializable
data class AirspaceFeature(
    val type: String,
    val name: String? = null,           // ✅ Made nullable with default
    val upperLimit: String? = null,     // ✅ Made nullable with default
    val lowerLimit: String? = null,     // ✅ Made nullable with default
    val ai: String? = null,             // Airspace ID
    val ag: String? = null,             // Ground frequency
    val af: String? = null,             // Air frequency
    val geometry: GeoJsonGeometry
)

@Serializable
data class AirspaceData(
    val type: String = "FeatureCollection", // GeoJSON FeatureCollection type
    val features: List<AirspaceFeature>,
    val availableTypes: Set<String> = emptySet(), // Unique airspace types present in this data
    val lastUpdated: Long = currentTimeMillis()
)

/**
 * Simple GeoJSON geometry types for airspace
 * Using a flexible approach to handle different geometry types
 */
@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement
) {
    /**
     * Check if this geometry is a polygon type
     */
    val isPolygon: Boolean get() = type == "Polygon"
    val isMultiPolygon: Boolean get() = type == "MultiPolygon"
    val isPoint: Boolean get() = type == "Point"

    /**
     * Get coordinates as list of doubles (for Point)
     */
    fun asPointCoordinates(): List<Double>? {
        return if (isPoint && coordinates is JsonArray) {
            coordinates.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
        } else null
    }

    /**
     * Get coordinates as polygon rings (for Polygon)
     */
    fun asPolygonCoordinates(): List<List<List<Double>>>? {
        return if (isPolygon && coordinates is JsonArray) {
            coordinates.jsonArray.mapNotNull { ring ->
                if (ring is JsonArray) {
                    ring.jsonArray.mapNotNull { point ->
                        if (point is JsonArray) {
                            point.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
                        } else null
                    }
                } else null
            }
        } else null
    }

    /**
     * Get coordinates as multi-polygon (for MultiPolygon)
     */
    fun asMultiPolygonCoordinates(): List<List<List<List<Double>>>>? {
        return if (isMultiPolygon && coordinates is JsonArray) {
            coordinates.jsonArray.mapNotNull { polygon ->
                if (polygon is JsonArray) {
                    polygon.jsonArray.mapNotNull { ring ->
                        if (ring is JsonArray) {
                            ring.jsonArray.mapNotNull { point ->
                                if (point is JsonArray) {
                                    point.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
                                } else null
                            }
                        } else null
                    }
                } else null
            }
        } else null
    }
}

/**
 * Country source for airspace data
 */
data class CountrySource(
    val code: String,
    val name: String,
    val url: String,
    val selected: Boolean = false
)

/**
 * Available airspace countries (European countries)
 */
object AirspaceSources {
    val countries: List<CountrySource> = listOf(
        CountrySource("fr", "France", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/fr_asp_v2.txt"),
        CountrySource("it", "Italy", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/it_asp_v2.txt"),
        CountrySource("ch", "Switzerland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ch_asp_v2.txt"),
        CountrySource("de", "Germany", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/de_asp_v2.txt"),
        CountrySource("es", "Spain", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/es_asp_v2.txt"),
        CountrySource("at", "Austria", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/at_asp_v2.txt"),
        CountrySource("be", "Belgium", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/be_asp_v2.txt"),
        CountrySource("nl", "Netherlands", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/nl_asp_v2.txt"),
        CountrySource("pt", "Portugal", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/pt_asp_v2.txt"),
        CountrySource("gb", "United Kingdom", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/gb_asp_v2.txt"),
        CountrySource("ie", "Ireland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ie_asp_v2.txt"),
        CountrySource("dk", "Denmark", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/dk_asp_v2.txt"),
        CountrySource("se", "Sweden", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/se_asp_v2.txt"),
        CountrySource("no", "Norway", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/no_asp_v2.txt"),
        CountrySource("fi", "Finland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/fi_asp_v2.txt"),
        CountrySource("pl", "Poland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/pl_asp_v2.txt"),
        CountrySource("cz", "Czech Republic", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/cz_asp_v2.txt"),
        CountrySource("sk", "Slovakia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/sk_asp_v2.txt"),
        CountrySource("hu", "Hungary", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/hu_asp_v2.txt"),
        CountrySource("ro", "Romania", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ro_asp_v2.txt"),
        CountrySource("bg", "Bulgaria", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/bg_asp_v2.txt"),
        CountrySource("gr", "Greece", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/gr_asp_v2.txt"),
        CountrySource("hr", "Croatia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/hr_asp_v2.txt"),
        CountrySource("si", "Slovenia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/si_asp_v2.txt"),
        CountrySource("lt", "Lithuania", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lt_asp_v2.txt"),
        CountrySource("lv", "Latvia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lv_asp_v2.txt"),
        CountrySource("ee", "Estonia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ee_asp_v2.txt"),
        CountrySource("lu", "Luxembourg", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lu_asp_v2.txt"),
        CountrySource("mt", "Malta", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/mt_asp_v2.txt"),
        CountrySource("cy", "Cyprus", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/cy_asp_v2.txt")
    ).sortedBy { it.name }

    val defaultSelectedCountries = listOf("fr") // Default to France only

    // Default visible airspace types - all types visible by default
    val defaultVisibleTypes = setOf(
        "A", "C", "D", "E", "G", "PROHIBITED", "RESTRICTED", "DANGER", "MTA",
        "OVERFLIGHT_RESTRICTION", "GLIDING_SECTOR", "ACTIVITY", "TRA", "RMZ", "TMZ",
        "FIS", "ATZ", "VFRSEC", "FIR", "UNCLASSIFIED"
    )
}
