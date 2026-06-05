package org.mountaincircles.app.ui.import

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.BaseStatefulModule
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.ui.components.UnifiedProgressIndicator
import org.mountaincircles.app.ui.theme.AppTheme

/**
 * Generic download item data class for unified download UI
 */
data class DownloadItem<T>(
    val id: T,
    val title: String,
    val getSubtitle: (DownloadState<T>) -> String,
    val isInstalled: () -> Boolean,
    val isDownloading: () -> Boolean,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val icon: (@Composable () -> Unit)? = null
)

/**
 * Generic download state for unified UI behavior
 */
data class DownloadState<T>(
    val activeItem: T? = null,
    val isDownloadActive: Boolean = false
)

/**
 * Base interface for all import sections - Phase 1: Progress + Phase 2: Error Handling + Phase 3: Action Patterns + Phase 4: Status Text + Phase 5: Common Item Components
 */
@OptIn(ExperimentalFoundationApi::class)
abstract class BaseImportSection<ModuleType : BaseStatefulModule<*>> {

    // 🎯 ABSTRACT: Module instance (provided by implementations)
    protected abstract val module: ModuleType

    // 🎯 COMMON: Layout structure
    @Composable
    protected fun BaseLayout(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }

    // 🎯 COMMON: Progress display
    @Composable
    protected fun ProgressSection(progress: UnifiedProgress) {
        if (progress.isDownloading) {
            UnifiedProgressIndicator(
                progress = progress,
                showFileProgress = true
            )
        }
    }

    // 🎯 COMMON: Error display (Phase 2)
    @Composable
    protected fun ErrorSection(hasError: Boolean, errorMessage: String?) {
        if (hasError && errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Error: $errorMessage",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    // 🎯 COMMON: Action launcher (Phase 3)
    protected fun launchAction(
        actionName: String,
        tag: String,
        coroutineScope: CoroutineScope,
        action: suspend () -> Unit
    ) {
        coroutineScope.launch {
            try {
                Logger.log(tag, LogLevel.INFO, "Starting $actionName")
                action()
            } catch (e: Exception) {
                Logger.log(tag, LogLevel.ERROR, "$actionName failed: ${e.message}", e)
            }
        }
    }

    // 🎯 COMMON: Status text (Phase 4)
    @Composable
    protected fun StatusText(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    // 🎯 GENERIC: Unified download items rendering
    @Composable
    protected fun <T> renderDownloadItems(
        items: List<DownloadItem<T>>,
        downloadState: DownloadState<T>,
        progress: UnifiedProgress,
        cancelDownload: (T) -> Unit,
        coroutineScope: CoroutineScope
    ) {
        items.forEach { item ->
            GenericImportItem(
                title = item.title,
                subtitle = item.getSubtitle(downloadState),
                isInstalled = item.isInstalled(),
                isDownloading = item.isDownloading(),
                progress = progress,
                isDownloadActive = downloadState.isDownloadActive,
                isThisItemActive = downloadState.activeItem == item.id,
                onCancel = {
                    launchAction("cancel download", "GENERIC_UI", coroutineScope) {
                        cancelDownload(item.id)
                    }
                },
                icon = item.icon,
                onClick = item.onClick,
                onLongClick = item.onLongClick ?: {}
            )
        }
    }

    // 🎯 COMMON: Generic import item component (Phase 5 - Commonalization)
    @Composable
    protected fun GenericImportItem(
        title: String,
        subtitle: String? = null,
        isInstalled: Boolean = false,
        isActive: Boolean = false,
        isDownloading: Boolean = false,
        progress: UnifiedProgress? = null, // THE generic progress UI
        isDownloadActive: Boolean = false,     // NEW: Global download flag
        isThisItemActive: Boolean = false,     // NEW: Is this the active download
        onCancel: (() -> Unit)? = null,        // NEW: Cancel callback
        icon: (@Composable () -> Unit)? = null,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        // Determine button behavior based on download state
        val buttonEnabled = !isDownloadActive || isThisItemActive  // Enable only if no download OR this is active
        val buttonOnClick = if (isThisItemActive && onCancel != null) {
            { onCancel() }  // Cancel if this is the active download
        } else if (!isDownloadActive) {
            onClick  // Normal click if no download active
        } else {
            {}  // Disabled if other download is active
        }

        val containerColor = when {
            !buttonEnabled -> Color.Gray.copy(alpha = 0.2f)  // Greyed out when disabled
            isActive && isInstalled -> Color.Green.copy(alpha = 0.3f)
            isInstalled -> Color.Green.copy(alpha = 0.15f)
            else -> AppTheme.Buttons.standardContainerColor
        }

        val contentColor = when {
            !buttonEnabled -> Color.Gray.copy(alpha = 0.5f)  // Greyed out text/icons when disabled
            else -> AppTheme.Buttons.standardContentColor
        }

        val borderColor = when {
            isActive && isInstalled -> Color.Cyan
            else -> Color.Transparent
        }

        val borderWidth = if (isActive && isInstalled) 2.dp else 0.dp

        Card(
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
            shape = RoundedCornerShape(AppTheme.Buttons.standardCornerRadius),
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = buttonEnabled,
                    onClick = buttonOnClick,
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.Buttons.standardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!buttonEnabled) contentColor else Color.White
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            fontSize = 10.sp,
                            color = if (!buttonEnabled) contentColor.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = if (!buttonEnabled) contentColor else Color.Blue,
                            strokeWidth = 2.dp
                        )
                    }
                    isInstalled && icon != null -> {
                        icon()
                    }
                    isInstalled -> {
                        Icon(
                            painter = AppIcons.Check(),
                            contentDescription = "Installed",
                            tint = if (!buttonEnabled) contentColor else Color.Green,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            painter = AppIcons.Download(),
                            contentDescription = "Download",
                            tint = if (!buttonEnabled) contentColor else Color.Blue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // 🎯 ABSTRACT: Module-specific content (implemented by each module)
    @Composable
    abstract fun ImportSectionContent(progress: UnifiedProgress, coroutineScope: kotlinx.coroutines.CoroutineScope)

    // 🎯 COMPOSABLE: Main entry point
    @Composable
    fun ImportSection(
        modifier: Modifier = Modifier
    ) {
        // Get progress flow from module - implementations must provide access
        val progress = getProgressFlow().collectAsState().value
        val coroutineScope = rememberCoroutineScope()

        BaseLayout(modifier = modifier) {
            ProgressSection(progress)
            ImportSectionContent(progress, coroutineScope)
        }
    }

    // 🎯 ABSTRACT: Module implementations must provide progress flow access
    @Composable
    protected abstract fun getProgressFlow(): kotlinx.coroutines.flow.StateFlow<UnifiedProgress>
}
