package org.mountaincircles.app.modules.airports.submenu.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.logic.data.AirportColors
import org.mountaincircles.app.modules.airports.logic.data.AirportsFilterDisplayData
import org.mountaincircles.app.ui.components.CheckboxListWidget
import org.mountaincircles.app.ui.components.GenericSidebarWidget

/**
 * Airports Filter Widget for sidebar
 * Allows users to show/hide different airport types with color-coded checkboxes
 */
@Composable
fun AirportsFilterWidget(
    airportsModule: AirportsModule,
    modifier: Modifier = Modifier
) {
    val airportsState by airportsModule.airportsState.collectAsState()
    val filterData = remember(airportsState) {
        AirportsFilterDisplayData(airportsState.currentVisibleTypes, airportsState.availableTypes, airportsState.hasDataToRender ?: false)
    }

    GenericSidebarWidget(
        title = "Airports",
        modifier = modifier
    ) {
        CheckboxListWidget(
            hasData = filterData.hasDataToRender,
            availableItems = filterData.availableTypes.toSet(),
            visibleItems = filterData.visibleTypes,
            getDisplayName = { type -> type }, // Airport types are already display-friendly
            getColor = { type -> AirportColors.getAirportTypeColor(type) },
            onCheckedChange = { type, visible ->
                Logger.log("AIRPORTS_FILTER", LogLevel.INFO, "Toggling airport type $type: $visible")
                airportsModule.toggleAirportType(type, visible)
            },
            emptyDataMessage = "No airport data\nImport data to filter types",
            noItemsMessage = "No airport types found in data",
            sortItems = { types ->
                types.sortedBy { type ->
                    getTypeSortOrder(type)
                }
            }
        )
    }
}

private fun getTypeSortOrder(type: String): Int {
    return when (type) {
        "International Airport" -> 1
        "Airport (civil/military)" -> 2
        "Airport resp. Airfield IFR" -> 3
        "Military Aerodrome" -> 4
        "Airfield Civil" -> 5
        "Landing Strip" -> 6
        "Agricultural Landing Strip" -> 7
        "Ultra Light Flying Site" -> 8
        "Glider Site" -> 9
        "Altiport" -> 10
        else -> 999
    }
}

