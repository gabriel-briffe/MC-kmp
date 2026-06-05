package org.mountaincircles.app.ui.map

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

/**
 * Descriptor for individual map layers with fine-grained control over rendering and interaction
 */
@Serializable
data class LayerDescriptor(
    val id: String,
    val moduleId: String,
    val displayName: String,

    // Rendering priority (lower = background, higher = foreground)
    val renderPriority: Int,

    // Layer properties
    val isVisible: Boolean = true,
    val isInteractive: Boolean = false,
    val layerType: LayerType = LayerType.FEATURE,

    // Metadata
    val description: String? = null,
    val tags: Set<String> = emptySet()
) {

    /**
     * Layer types for categorization and filtering
     */
    enum class LayerType {
        BASE,      // Base map layers (terrain, tiles)
        FEATURE,   // Feature layers (circles, wave data, markers)
        OVERLAY,   // UI overlays (selection, highlights)
        INTERACTION // Interactive layers (click areas, controls)
    }

    /**
     * Create a copy with updated visibility
     */
    fun withVisibility(visible: Boolean) = copy(isVisible = visible)


    /**
     * Check if this layer should render based on current conditions
     */
    fun shouldRender(): Boolean = isVisible

    /**
     * Check if this layer can handle click events
     */
    fun canHandleClicks(): Boolean = isInteractive && isVisible

    companion object {
        // Standard priority ranges for consistency
        const val PRIORITY_BASE_LOW = 0       // Background tiles
        const val PRIORITY_BASE_HIGH = 99     // Base map features
        const val PRIORITY_FEATURE_LOW = 100  // Background features
        const val PRIORITY_FEATURE_HIGH = 199 // Foreground features
        const val PRIORITY_OVERLAY_LOW = 200  // UI overlays
        const val PRIORITY_OVERLAY_HIGH = 299 // Top-level overlays
        const val PRIORITY_INTERACTION = 300  // Click handlers (highest)
    }
}
