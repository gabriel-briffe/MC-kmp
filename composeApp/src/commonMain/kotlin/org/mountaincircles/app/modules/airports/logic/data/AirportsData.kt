package org.mountaincircles.app.modules.airports.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Airport data sources and configurations
 * Based on airspace sources but with airport-specific URLs
 */

data class AirportSource(
    val code: String,
    val name: String,
    val url: String,
    val selected: Boolean = false
)

/**
 * Available airport sources (European countries)
 * Same countries as airspace but with airport data URLs
 */
object AirportSources {
    val countries: List<AirportSource> = listOf(
        AirportSource("fr", "France", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/fr_apt.geojson"),
        AirportSource("it", "Italy", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/it_apt.geojson"),
        AirportSource("ch", "Switzerland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ch_apt.geojson"),
        AirportSource("de", "Germany", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/de_apt.geojson"),
        AirportSource("es", "Spain", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/es_apt.geojson"),
        AirportSource("at", "Austria", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/at_apt.geojson"),
        AirportSource("be", "Belgium", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/be_apt.geojson"),
        AirportSource("nl", "Netherlands", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/nl_apt.geojson"),
        AirportSource("pt", "Portugal", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/pt_apt.geojson"),
        AirportSource("gb", "United Kingdom", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/gb_apt.geojson"),
        AirportSource("ie", "Ireland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ie_apt.geojson"),
        AirportSource("dk", "Denmark", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/dk_apt.geojson"),
        AirportSource("se", "Sweden", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/se_apt.geojson"),
        AirportSource("no", "Norway", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/no_apt.geojson"),
        AirportSource("fi", "Finland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/fi_apt.geojson"),
        AirportSource("pl", "Poland", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/pl_apt.geojson"),
        AirportSource("cz", "Czech Republic", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/cz_apt.geojson"),
        AirportSource("sk", "Slovakia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/sk_apt.geojson"),
        AirportSource("hu", "Hungary", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/hu_apt.geojson"),
        AirportSource("ro", "Romania", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ro_apt.geojson"),
        AirportSource("bg", "Bulgaria", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/bg_apt.geojson"),
        AirportSource("gr", "Greece", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/gr_apt.geojson"),
        AirportSource("hr", "Croatia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/hr_apt.geojson"),
        AirportSource("si", "Slovenia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/si_apt.geojson"),
        AirportSource("lt", "Lithuania", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lt_apt.geojson"),
        AirportSource("lv", "Latvia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lv_apt.geojson"),
        AirportSource("ee", "Estonia", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/ee_apt.geojson"),
        AirportSource("lu", "Luxembourg", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/lu_apt.geojson"),
        AirportSource("mt", "Malta", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/mt_apt.geojson"),
        AirportSource("cy", "Cyprus", "https://storage.googleapis.com/29f98e10-a489-4c82-ae5e-489dbcd4912f/cy_apt.geojson")
    ).sortedBy { it.name }

    val defaultSelectedCountries = listOf("fr") // Default to France only
}

/**
 * Display data for airports import UI - reactive flow data
 * Mirrors AirspaceImportDisplayData structure
 */
data class AirportsImportDisplayData(
    val isDownloading: Boolean,
    val currentProgress: UnifiedProgress?,
    val selectedCountries: List<String>,
    val hasDataToRender: Boolean,
    val importedAt: Long?,
    val hasError: Boolean,
    val errorMessage: String?
)