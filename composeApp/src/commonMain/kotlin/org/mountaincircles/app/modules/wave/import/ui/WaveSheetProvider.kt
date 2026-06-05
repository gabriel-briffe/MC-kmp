package org.mountaincircles.app.modules.wave.import.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.ui.WaveImportComposable
import org.mountaincircles.app.modules.wave.ui.WaveImportViewModelImpl
import org.mountaincircles.app.ui.modules.ComposableProvider
import org.mountaincircles.app.ui.modules.provideTutorialImageContent
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel
import kotlinx.coroutines.launch

/**
 * Provider for Wave module sheet UI
 */
class WaveSheetProvider : ComposableProvider {
    override val moduleId: String = "wave"

    override fun canProvide(module: ModuleBase): Boolean = module is WaveModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is WaveModule) {
            // Create ViewModel with proper lifecycle management
            val viewModel = remember(module) {
                WaveImportViewModelImpl(module)
            }

            WaveImportComposable(viewModel = viewModel)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    override fun provideFullWidthContent(module: ModuleBase): List<@Composable () -> Unit> {
        if (module is WaveModule) {
            return module.provideTutorialImageContent("files/wave1.webp", "Wave forecast tutorial")
        }
        return emptyList()
    }
}
