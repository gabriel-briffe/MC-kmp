package org.mountaincircles.app.modules.airspace.submenu.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFilterDisplayData
import org.mountaincircles.app.ui.components.CheckboxListWidget
import org.mountaincircles.app.ui.components.GenericSidebarWidget

/**
 * Airspace Filter Widget for sidebar
 * Allows users to show/hide different airspace types
 */
@Composable
fun AirspaceFilterWidget(
    airspaceModule: AirspaceModule,
    modifier: Modifier = Modifier
) {
    val airspaceState by airspaceModule.airspaceState.collectAsState()
    val filterData = remember(airspaceState) {
        val visibleTypes = airspaceState.currentVisibleTypes
        AirspaceFilterDisplayData(visibleTypes, airspaceState.availableTypes, airspaceState.hasDataToRender ?: false)
    }

    GenericSidebarWidget(
        title = "Airspace",
        modifier = modifier
    ) {
        CheckboxListWidget(
            hasData = filterData.hasData,
            availableItems = filterData.availableTypes.toSet(),
            visibleItems = filterData.visibleTypes,
            getDisplayName = { type -> getTypeDisplayName(type) },
            getColor = { type -> AirspaceColors.getAirspaceTypeColor(type) },
            onCheckedChange = { type, visible ->
                Logger.log("AIRSPACE_FILTER", LogLevel.INFO, "Toggling airspace type $type: $visible")
                airspaceModule.toggleAirspaceType(type, visible)
            },
            emptyDataMessage = "No airspace data\nImport data to filter types",
            noItemsMessage = "No airspace types found in data",
            sortItems = { types ->
                types.sortedBy { type ->
                    airspaceTypeOrder.indexOf(type).takeIf { it >= 0 } ?: 999
                }
            }
        )
    }
}

// Airspace type order matching Android native app
private val airspaceTypeOrder = listOf(
    "A", "C", "D", "E", "G", "PROHIBITED", "RESTRICTED", "DANGER", "MTA",
    "OVERFLIGHT_RESTRICTION", "GLIDING_SECTOR", "ACTIVITY", "TRA", "RMZ", "TMZ",
    "ATZ", "VFRSEC", "FIS", "FIR", "UNCLASSIFIED"
)

private fun getTypeDisplayName(type: String): String {
    return when (type) {
        "A" -> "Class A"
        "C" -> "Class C"
        "D" -> "Class D"
        "E" -> "Class E"
        "G" -> "Class G"
        "PROHIBITED" -> "Prohibited"
        "DANGER" -> "Danger"
        "RESTRICTED" -> "Restricted"
        "MTA" -> "MTA"
        "OVERFLIGHT_RESTRICTION" -> "Overflight Restriction"
        "GLIDING_SECTOR" -> "Gliding Sector"
        "ACTIVITY" -> "Activity Zone"
        "TRA" -> "TRA"
        "RMZ" -> "RMZ"
        "TMZ" -> "TMZ"
        "FIS" -> "Flight Information Service"
        "ATZ" -> "ATZ"
        "VFRSEC" -> "VFR Sector"
        "FIR" -> "Flight Information Region"
        "UNCLASSIFIED" -> "Unclassified"
        else -> type
    }
}
