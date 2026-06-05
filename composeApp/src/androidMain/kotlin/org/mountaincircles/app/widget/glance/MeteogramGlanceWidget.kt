package org.mountaincircles.app.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

private const val TAG = "GlanceWidget_Main"

/**
 * Glance-based Meteogram Widget.
 * 
 * This is the modern replacement for MeteogramWidgetWithFooterProvider,
 * using Jetpack Glance for declarative widget UI.
 * 
 * Uses StateFlow pattern for reactive updates following the Android sample pattern:
 * - provideGlance is minimal, just provides content
 * - State observation happens inside Content composable
 * - Refresh uses rememberCoroutineScope() for proper lifecycle integration
 */
class MeteogramGlanceWidget : GlanceAppWidget() {

    // Use Exact size mode - widget fills available space, content scrolls when needed
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Keep provideGlance minimal - just provide content
        provideContent { Content() }
    }
}

/**
 * Main widget content composable.
 * 
 * Following Android sample pattern:
 * - Observes StateFlow with collectAsState()
 * - Uses rememberCoroutineScope() for refresh action
 * - Refresh lambda is passed to layout
 */
@Composable
private fun Content() {
    // Get context and coroutine scope from composition
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize repository on first composition
    MeteogramRepo.init(context)
    
    // Load cached data if state is Loading (e.g., after app restart/update)
    if (MeteogramRepo.state.value is MeteogramState.Loading) {
        scope.launch { MeteogramRepo.loadData(context) }
    }
    
    // Observe the state flow - widget recomposes when state changes
    val meteogramState by MeteogramRepo.state.collectAsState()
    
    // Debug: Log the current state being rendered
    val stateType = when (meteogramState) {
        is MeteogramState.Loading -> "Loading"
        is MeteogramState.Available -> "Available(hasData=${(meteogramState as MeteogramState.Available).data.hasData})"
        is MeteogramState.Unavailable -> "Unavailable"
    }
    Logger.log(TAG, LogLevel.DEBUG, "Content composing with state: $stateType")
    
    // Create actions using the composable's scope
    val onRefresh: () -> Unit = {
        scope.launch { refreshMeteogram(context) }
    }
    
    val onPrevious: () -> Unit = {
        MeteogramRepo.navigatePrevious()
        scope.launch { MeteogramGlanceWidget().updateAll(context) }
    }
    
    val onNext: () -> Unit = {
        MeteogramRepo.navigateNext()
        scope.launch { MeteogramGlanceWidget().updateAll(context) }
    }
    
    val repository = MeteogramRepo.getRepository()
    
    // Get data from state, or create empty data
    val data = when (meteogramState) {
        is MeteogramState.Available -> (meteogramState as MeteogramState.Available).data
        else -> MeteogramData(weatherData = null, metadata = null, hasData = false)
    }
    
    val locationText = when (meteogramState) {
        is MeteogramState.Available -> (meteogramState as MeteogramState.Available).locationText
        else -> "Tap refresh"
    }
    
    val currentTableIndex = when (meteogramState) {
        is MeteogramState.Available -> (meteogramState as MeteogramState.Available).currentTableIndex
        else -> 0
    }
    
    // Get the no-data message (for Unavailable state)
    val noDataMessage = when (meteogramState) {
        is MeteogramState.Unavailable -> (meteogramState as MeteogramState.Unavailable).message
        else -> "No data - tap refresh"
    }
    
    // Get last refresh time from repository
    val lastRefreshTime = repository?.getLastRefreshTime()
    
    // Debug: Log what we're about to render
    Logger.log(TAG, LogLevel.DEBUG, "About to render: repository=${repository != null}, data.hasData=${data.hasData}, tableIndex=$currentTableIndex")
    
    GlanceTheme {
        // Always show the grid layout
        if (repository != null) {
            MeteogramGlanceLayout(
                data = data,
                repository = repository,
                locationText = locationText,
                currentTableIndex = currentTableIndex,
                noDataMessage = noDataMessage,
                lastRefreshTime = lastRefreshTime,
                onRefreshClick = onRefresh,
                onPreviousClick = onPrevious,
                onNextClick = onNext
            )
        } else {
            Logger.log(TAG, LogLevel.ERROR, "Repository is NULL - cannot render layout!")
        }
    }
}

/**
 * Refresh the meteogram data.
 * 
 * Following Android sample pattern EXACTLY:
 * 1. Call updateAll() FIRST to trigger recomposition
 * 2. Call fetchFreshData() SYNCHRONOUSLY (not in background)
 * 3. Call updateAll() AGAIN after data is fetched to show new data
 */
suspend fun refreshMeteogram(context: Context) {
    Logger.log(TAG, LogLevel.INFO, "Refresh triggered")
    
    // Step 1: Call updateAll first (like sample)
    Logger.log(TAG, LogLevel.DEBUG, "Step 1: Calling updateAll before fetch")
    MeteogramGlanceWidget().updateAll(context)
    
    // Step 2: Fetch data SYNCHRONOUSLY (like sample - not in background!)
    try {
        Logger.log(TAG, LogLevel.INFO, "Step 2: Starting synchronous data fetch")
        MeteogramRepo.fetchFreshData(context)
        Logger.log(TAG, LogLevel.INFO, "Step 2: Data fetch completed")
    } catch (e: Exception) {
        Logger.log(TAG, LogLevel.ERROR, "Fetch error: ${e.message}", e)
    }
    
    // Step 3: Call updateAll AGAIN after data is ready
    Logger.log(TAG, LogLevel.DEBUG, "Step 3: Calling updateAll after fetch")
    MeteogramGlanceWidget().updateAll(context)
    Logger.log(TAG, LogLevel.DEBUG, "Refresh complete")
}

/**
 * Receiver that registers the MeteogramGlanceWidget with the system.
 * This is the entry point declared in AndroidManifest.xml.
 */
class MeteogramGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeteogramGlanceWidget()
    
    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        // Load data when widget is first added
        context?.let {
            CoroutineScope(Dispatchers.IO).launch {
                MeteogramRepo.init(it)
                MeteogramRepo.loadData(it)
            }
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Periodic update (every 12 hours) - fetch fresh data
        Logger.log(TAG, LogLevel.INFO, "onUpdate triggered - fetching fresh data")
        CoroutineScope(Dispatchers.IO).launch {
            MeteogramRepo.init(context)
            MeteogramRepo.fetchFreshData(context)
            MeteogramGlanceWidget().updateAll(context)
        }
    }
}
