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

private const val TAG = "GlanceWidget_WaveToday"

/**
 * Glance-based Wave Today Widget.
 * 
 * Displays today's wave forecast image with title and refresh button.
 * Uses the same StateFlow pattern as MeteogramGlanceWidget.
 */
class WaveTodayGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }
}

/**
 * Main widget content composable.
 */
@Composable
private fun Content() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Load data if state is Loading (e.g., after app restart)
    if (WaveTodayRepo.state.value is WaveTodayState.Loading) {
        scope.launch { WaveTodayRepo.loadData(context) }
    }
    
    // Observe the state flow
    val waveState by WaveTodayRepo.state.collectAsState()
    
    Logger.log(TAG, LogLevel.DEBUG, "Content composing with state: ${waveState::class.simpleName}")
    
    // Create refresh action
    val onRefresh: () -> Unit = {
        scope.launch { refreshWaveToday(context) }
    }
    
    GlanceTheme {
        WaveTodayGlanceLayout(
            state = waveState,
            onRefreshClick = onRefresh
        )
    }
}

/**
 * Refresh the wave forecast data.
 */
suspend fun refreshWaveToday(context: Context) {
    Logger.log(TAG, LogLevel.INFO, "Refresh triggered")
    
    // Update widget to show loading state
    WaveTodayGlanceWidget().updateAll(context)
    
    // Refresh data
    WaveTodayRepo.refreshData(context)
    
    // Update widget with new data
    WaveTodayGlanceWidget().updateAll(context)
    
    Logger.log(TAG, LogLevel.DEBUG, "Refresh complete")
}

/**
 * Receiver that registers the WaveTodayGlanceWidget with the system.
 */
class WaveTodayGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaveTodayGlanceWidget()
    
    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        Logger.log(TAG, LogLevel.INFO, "Widget enabled - loading data")
        context?.let {
            CoroutineScope(Dispatchers.IO).launch {
                WaveTodayRepo.loadData(it)
            }
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Logger.log(TAG, LogLevel.INFO, "onUpdate triggered - refreshing wave data")
        CoroutineScope(Dispatchers.IO).launch {
            WaveTodayRepo.refreshData(context)
            WaveTodayGlanceWidget().updateAll(context)
        }
    }
}
