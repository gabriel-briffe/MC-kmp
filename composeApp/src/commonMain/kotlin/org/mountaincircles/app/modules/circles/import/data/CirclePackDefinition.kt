package org.mountaincircles.app.modules.circles.import.data

/**
 * Definition of a downloadable circle pack configuration
 */
data class CirclePackDefinition(
    val packId: String,
    val configId: String,
    val displayName: String,
    val url: String? = null // If null, will be constructed from packId and configId
) {
    /**
     * Get the download URL for this pack
     */
    fun getDownloadUrl(): String {
        return url ?: "https://data.mountain-circles.org/${packId}_${configId}.zip"
    }

    /**
     * Get the filename for this pack
     */
    fun getFileName(): String {
        return "${packId}_${configId}.zip"
    }

    /**
     * Get the full pack identifier for state management
     */
    fun getFullPackId(): String {
        return "${packId}_${configId}_4210"
    }
}

/**
 * Configuration object containing all available circle pack definitions
 */
object CirclePackDefinitions {

    /**
     * List of all available circle pack configurations
     * Add new packs here to automatically show them in the UI
     */
    val availablePacks = listOf(

        CirclePackDefinition(
            packId = "alpes",
            configId = "10-100-250",
            displayName = "Alps 10-100-250"
        ),
        
        CirclePackDefinition(
            packId = "alpes",
            configId = "20-100-250",
            displayName = "Alps 20-100-250"
        ),

        CirclePackDefinition(
            packId = "alpes",
            configId = "25-100-250",
            displayName = "Alps 25-100-250"
        ),

        CirclePackDefinition(
            packId = "alpes",
            configId = "30-100-250",
            displayName = "Alps 30-100-250"
        ),


        // Example pack definitions - uncomment and modify as new packs become available
        // CirclePackDefinition(
        //     packId = "pyrenees",
        //     configId = "25-100-250",
        //     displayName = "Pyrenees 25-100-250",
        //     description = "Mountain circles for Pyrenees region"
        // ),

        // CirclePackDefinition(
        //     packId = "rockies",
        //     configId = "25-100-250",
        //     displayName = "Rocky Mountains 25-100-250",
        //     description = "Mountain circles for Rocky Mountains region"
        // ),

        // CirclePackDefinition(
        //     packId = "alpes",
        //     configId = "15-100-250",
        //     displayName = "Alps 15-100-250",
        //     description = "Ultra high detail circles for Alpine region (15m ground, 100m circuit, 250m radius)"
        // ),

        // CirclePackDefinition(
        //     packId = "scandinavia",
        //     configId = "25-100-250",
        //     displayName = "Scandinavia 25-100-250",
        //     description = "Mountain circles for Scandinavia region"
        // )
    )

    /**
     * Get a pack definition by its identifiers
     */
    fun getPackDefinition(packId: String, configId: String): CirclePackDefinition? {
        return availablePacks.find { it.packId == packId && it.configId == configId }
    }

    /**
     * Get all pack definitions for a specific region/pack
     */
    fun getPackDefinitions(packId: String): List<CirclePackDefinition> {
        return availablePacks.filter { it.packId == packId }
    }
}
