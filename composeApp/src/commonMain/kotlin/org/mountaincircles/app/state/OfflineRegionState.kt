package org.mountaincircles.app.state

import org.maplibre.spatialk.geojson.Polygon

/** Geographic bounds [west, south, east, north] in WGS84 degrees. */
data class GeoBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west < east) { "west must be less than east" }
        require(south < north) { "south must be less than north" }
    }

    fun toPolygon(): Polygon {
        val json = """
            {
                "type": "Polygon",
                "coordinates": [[
                    [$west, $south],
                    [$east, $south],
                    [$east, $north],
                    [$west, $north],
                    [$west, $south]
                ]]
            }
        """.trimIndent()
        return Polygon.fromJson(json)
    }
}

enum class OfflineSelectionPhase {
    Idle,
    Drawing,
    Preview,
    Downloading,
}

data class OfflineDownloadUiState(
    val completedResources: Long = 0,
    val requiredResources: Long = 0,
    val statusMessage: String = "",
    val error: String? = null,
    val isComplete: Boolean = false,
)

object OfflineRegionConfig {
    const val MIN_ZOOM = 0
    const val MAX_ZOOM = 7
}
