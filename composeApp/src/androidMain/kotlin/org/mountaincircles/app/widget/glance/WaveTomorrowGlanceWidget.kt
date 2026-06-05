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

private const val TAG = "GlanceWidget_WaveTomorrow"

/**
 * Glance-based Wave Tomorrow Widget.
 */
class WaveTomorrowGlanceWidget : GlanceAppWidget() {

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
    
    // Load data if state is Loading
    if (WaveTomorrowRepo.state.value is WaveTomorrowState.Loading) {
        scope.launch { WaveTomorrowRepo.loadData(context) }
    }
    
    val waveState by WaveTomorrowRepo.state.collectAsState()
    
    Logger.log(TAG, LogLevel.DEBUG, "Content composing with state: ${waveState::class.simpleName}")
    
    val onRefresh: () -> Unit = {
        scope.launch { refreshWaveTomorrow(context) }
    }
    
    GlanceTheme {
        WaveTomorrowGlanceLayout(
            state = waveState,
            onRefreshClick = onRefresh
        )
    }
}

/**
 * Refresh the wave forecast data.
 */
suspend fun refreshWaveTomorrow(context: Context) {
    Logger.log(TAG, LogLevel.INFO, "Refresh triggered")
    
    WaveTomorrowGlanceWidget().updateAll(context)
    WaveTomorrowRepo.refreshData(context)
    WaveTomorrowGlanceWidget().updateAll(context)
    
    Logger.log(TAG, LogLevel.DEBUG, "Refresh complete")
}

/**
 * Receiver that registers the WaveTomorrowGlanceWidget with the system.
 */
class WaveTomorrowGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaveTomorrowGlanceWidget()
    
    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        Logger.log(TAG, LogLevel.INFO, "Widget enabled - loading data")
        context?.let {
            CoroutineScope(Dispatchers.IO).launch {
                WaveTomorrowRepo.loadData(it)
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
            WaveTomorrowRepo.refreshData(context)
            WaveTomorrowGlanceWidget().updateAll(context)
        }
    }
}
