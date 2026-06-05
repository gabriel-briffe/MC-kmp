package org.mountaincircles.app.modules.airspace.import.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.ui.AirspaceImportComposable
import org.mountaincircles.app.modules.airspace.ui.AirspaceImportViewModelImpl
import org.mountaincircles.app.ui.modules.ScrollableComposableProvider

/**
 * Provider for Airspace module sheet UI
 */
class AirspaceSheetProvider : ScrollableComposableProvider {
    override val moduleId: String = "airspace"

    override fun canProvide(module: ModuleBase): Boolean = module is AirspaceModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is AirspaceModule) {
            val viewModel = remember(module) { AirspaceImportViewModelImpl(module) }

            AirspaceImportComposable(
                viewModel = viewModel,
                onScrollToTop = onScrollToTop
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    override fun provideScrollTrigger(onTrigger: () -> Unit) {
        // The scroll trigger is handled by the AirspaceImportComposable
        // This method is just for interface compliance
    }
}
