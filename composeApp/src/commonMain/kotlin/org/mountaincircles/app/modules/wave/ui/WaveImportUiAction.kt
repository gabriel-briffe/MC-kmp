package org.mountaincircles.app.modules.wave.ui

sealed class WaveImportUiAction {
    data class DownloadForecast(val forecastType: String) : WaveImportUiAction()
    data class ToggleIncludeWindFiles(val include: Boolean) : WaveImportUiAction()
    data class ToggleWindRegion(val region: WindRegion, val selected: Boolean) : WaveImportUiAction()
    data object CancelDownload : WaveImportUiAction()
    data object ClearAllFiles : WaveImportUiAction()
    data object RefreshAvailability : WaveImportUiAction()
}
