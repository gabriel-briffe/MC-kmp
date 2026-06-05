package org.mountaincircles.app.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Global reference to the current GlobalState instance
 * Set by ProvideGlobalState, accessed by getGlobalState()
 */
private var globalStateInstance: GlobalState? = null

/**
 * CompositionLocal for providing GlobalState to the composition tree
 */
val LocalGlobalState = staticCompositionLocalOf<GlobalState> {
    error("GlobalState not provided. Make sure to wrap your app with ProvideGlobalState.")
}

/**
 * Extension function for easy access to GlobalState in composables
 */
@Composable
fun globalState(): GlobalState = LocalGlobalState.current

/**
 * Non-composable function to get the current GlobalState instance
 * Use this in non-composable contexts like module methods
 */
fun getGlobalState(): GlobalState = globalStateInstance ?: error("GlobalState not initialized. Make sure ProvideGlobalState is used.")

/**
 * Provider composable for GlobalState
 */
@Composable
fun ProvideGlobalState(
    globalState: GlobalState,
    content: @Composable () -> Unit
) {
    // Store the instance for non-composable access
    SideEffect {
        globalStateInstance = globalState
    }

    CompositionLocalProvider(
        LocalGlobalState provides globalState,
        content = content
    )
}
