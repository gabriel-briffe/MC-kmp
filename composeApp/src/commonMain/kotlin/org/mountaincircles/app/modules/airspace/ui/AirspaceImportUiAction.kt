package org.mountaincircles.app.modules.airspace.ui

/**
 * Sealed class representing UI actions that can be performed on the Airspace import screen.
 */
sealed class AirspaceImportUiAction {
    data class UpdateCountrySelection(val countryCodes: List<String>) : AirspaceImportUiAction()
    data class ToggleCountry(val countryCode: String, val selected: Boolean) : AirspaceImportUiAction()
    object SelectAllCountries : AirspaceImportUiAction()
    object SelectNoCountries : AirspaceImportUiAction()
    object ImportAirspace : AirspaceImportUiAction()
    object ClearAirspaceData : AirspaceImportUiAction()
    object RefreshStatus : AirspaceImportUiAction()
}
