package org.mountaincircles.app.modules.maps.import.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.maps.ui.MapsImportComposable
import org.mountaincircles.app.modules.maps.ui.MapsImportViewModelImpl
import org.mountaincircles.app.ui.modules.ComposableProvider

/**
 * Provider for Maps module sheet UI
 */
class MapsSheetProvider : ComposableProvider {
    override val moduleId: String = "maps"

    override fun canProvide(module: ModuleBase): Boolean = module is MapsModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is MapsModule) {
            // Create ViewModel with proper lifecycle management
            val viewModel = remember(module) {
                MapsImportViewModelImpl(module)
            }

            MapsImportComposable(viewModel = viewModel)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
