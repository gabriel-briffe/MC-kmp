package org.mountaincircles.app.modules.airports.import.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.import.ui.AirportsImportSection
import org.mountaincircles.app.ui.modules.ScrollableComposableProvider

/**
 * Provider for Airports module sheet UI
 */
class AirportsSheetProvider : ScrollableComposableProvider {
    override val moduleId: String = "airports"

    override fun canProvide(module: ModuleBase): Boolean = module is AirportsModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is AirportsModule) {
            AirportsImportSection(module = module, onScrollToTop = onScrollToTop)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    override fun provideScrollTrigger(onTrigger: () -> Unit) {
        // The scroll trigger is handled by the AirportsImportSection
        // This method is just for interface compliance
    }
}
