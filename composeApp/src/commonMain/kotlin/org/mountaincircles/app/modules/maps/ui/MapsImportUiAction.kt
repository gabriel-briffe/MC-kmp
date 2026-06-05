package org.mountaincircles.app.modules.maps.ui

/**
 * UI actions for the Maps import section
 * Defines all actions that can be triggered from the UI
 */
sealed class MapsImportUiAction {
    data class DownloadMap(val mapId: String) : MapsImportUiAction()
    data class DeleteMap(val mapId: String) : MapsImportUiAction()
    data class CancelDownload(val mapId: String) : MapsImportUiAction()
    data object RefreshAvailability : MapsImportUiAction()
    data object ClearAllMaps : MapsImportUiAction()
}
