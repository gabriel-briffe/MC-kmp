package org.mountaincircles.app.modules.circles.import.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.ui.modules.ComposableProvider
import org.mountaincircles.app.modules.circles.ui.CirclesImportComposable
import org.mountaincircles.app.modules.circles.ui.CirclesImportViewModelImpl
import org.mountaincircles.app.ui.modules.createTutorialImageContent

/**
 * Provider for Circles module sheet UI
 */
class CirclesSheetProvider : ComposableProvider {
    override val moduleId: String = "circles"

    override fun canProvide(module: ModuleBase): Boolean = module is CirclesModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is CirclesModule) {
            val viewModel = CirclesImportViewModelImpl(module)
            CirclesImportComposable(viewModel = viewModel)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    override fun provideFullWidthContent(module: ModuleBase): List<@Composable () -> Unit> {
        if (module is CirclesModule) {
            return listOf(
                createTutorialImageContent("files/circles1.webp", "Circles tutorial 1"),
                createTutorialImageContent("files/circles2.webp", "Circles tutorial 2")
            )
        }
        return emptyList()
    }
}
