package org.mountaincircles.app.modules.circles.ui

/**
 * UI actions for Circles import section
 */
sealed class CirclesImportUiAction {
    data class DownloadPack(val packId: String, val configId: String) : CirclesImportUiAction()
    data class SelectPack(val packId: String, val configId: String) : CirclesImportUiAction()
    data class DeletePack(val packId: String, val configId: String) : CirclesImportUiAction()
    data class CancelDownload(val fullPackId: String) : CirclesImportUiAction()
    data object RefreshAvailability : CirclesImportUiAction()
    data object ImportFromZip : CirclesImportUiAction()
    data object ClearAllPacks : CirclesImportUiAction()
}
