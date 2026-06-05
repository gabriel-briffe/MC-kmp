package org.mountaincircles.app.ui.modules

import androidx.compose.runtime.Composable
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.maps.import.ui.MapsSheetProvider
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.import.ui.WaveSheetProvider
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.import.ui.CirclesSheetProvider
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.import.ui.AirspaceSheetProvider
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.import.ui.AirportsSheetProvider
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.import.ui.LiveTrackingSheetProvider
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.import.ui.SkysightSheetProvider

/**
 * Interface for providing UI components for modules
 */
interface UIProvider<T> {
    val moduleId: String
    val priority: Int get() = 0

    fun canProvide(module: ModuleBase): Boolean
    fun provide(module: ModuleBase): T
}

/**
 * Composable UI provider interface
 */
interface ComposableProvider {
    val moduleId: String
    val priority: Int get() = 0

    fun canProvide(module: ModuleBase): Boolean

    @Composable
    fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)? = null)

    /**
     * Optional full-width content to display below the main content (no padding)
     */
    @Composable
    fun provideFullWidthContent(module: ModuleBase): List<@Composable () -> Unit> {
        return emptyList() // Default: no full-width content
    }
}

/**
 * ComposableProvider that supports scroll-to-top functionality
 */
interface ScrollableComposableProvider : ComposableProvider {
    val supportsScrollToTop: Boolean get() = true
    val supportsLazyScrolling: Boolean get() = false

    @Composable
    fun provideScrollTrigger(onTrigger: () -> Unit)
}

/**
 * Generic registry for UI providers
 */
class UIProviderRegistry<T> {
    private val providers = mutableMapOf<String, UIProvider<T>>()
    private var prioritizedProviders = mutableListOf<UIProvider<T>>()

    /**
     * Register a provider for a specific module
     */
    fun registerProvider(moduleId: String, provider: UIProvider<T>) {
        providers[moduleId] = provider
        prioritizedProviders.add(provider)
        prioritizedProviders.sortByDescending { it.priority }
    }

    /**
     * Unregister a provider for a module
     */
    fun unregisterProvider(moduleId: String) {
        providers.remove(moduleId)
        prioritizedProviders = prioritizedProviders.filter { it.moduleId != moduleId }.toMutableList()
    }

    /**
     * Get provider for a module
     */
    fun getProvider(module: ModuleBase): UIProvider<T>? {
        return providers.values.firstOrNull { it.canProvide(module) }
    }

    /**
     * Check if a module has a provider
     */
    fun hasProvider(module: ModuleBase): Boolean {
        return providers.values.any { it.canProvide(module) }
    }

    /**
     * Get all registered providers
     */
    fun getAllProviders(): List<UIProvider<T>> = prioritizedProviders.toList()

    /**
     * Clear all providers
     */
    fun clear() {
        providers.clear()
        prioritizedProviders.clear()
    }
}

/**
 * Registry for composable providers
 */
class ComposableProviderRegistry private constructor() {
    private val providers = mutableMapOf<String, ComposableProvider>()
    private var prioritizedProviders = mutableListOf<ComposableProvider>()

    /**
     * Register a composable provider for a specific module
     */
    fun registerProvider(moduleId: String, provider: ComposableProvider) {
        providers[moduleId] = provider
        prioritizedProviders.add(provider)
        prioritizedProviders.sortByDescending { it.priority }
    }

    /**
     * Unregister a composable provider for a module
     */
    fun unregisterProvider(moduleId: String) {
        providers.remove(moduleId)
        prioritizedProviders = prioritizedProviders.filter { it.moduleId != moduleId }.toMutableList()
    }

    /**
     * Get provider for a module
     */
    fun getProvider(module: ModuleBase): ComposableProvider? {
        return prioritizedProviders.firstOrNull { it.canProvide(module) }
    }

    /**
     * Check if a module has a provider
     */
    fun hasProvider(module: ModuleBase): Boolean {
        return prioritizedProviders.any { it.canProvide(module) }
    }

    /**
     * Get all registered providers
     */
    fun getAllProviders(): List<ComposableProvider> = prioritizedProviders.toList()

    /**
     * Clear all providers
     */
    fun clear() {
        providers.clear()
        prioritizedProviders.clear()
    }

    companion object {
        /**
         * Create a new instance of ComposableProviderRegistry
         */
        fun create(): ComposableProviderRegistry = ComposableProviderRegistry()
    }
}









/**
 * Registry for module-specific sheet UI components
 *
 * This handles the rendering of dedicated sheets for modules,
 * similar to how ModuleSettingsRegistry handles settings UI.
 *
 * Each module can have its own dedicated sheet for complex interactions
 * like imports, data management, or specialized controls.
 *
 * NEW: Now uses generic registry pattern - modules register themselves automatically
 */
object ModuleSheetRegistry {

    private val registry = ComposableProviderRegistry.create()

    init {
        // Register built-in providers
        registry.registerProvider("maps", MapsSheetProvider())
        registry.registerProvider("wave", WaveSheetProvider())
        registry.registerProvider("circles", CirclesSheetProvider())
        registry.registerProvider("airspace", AirspaceSheetProvider())
        registry.registerProvider("airports", AirportsSheetProvider())
        registry.registerProvider("livetracking", LiveTrackingSheetProvider())
        registry.registerProvider("skysight", SkysightSheetProvider())
    }

    /**
     * Render module-specific sheet content using registry pattern
     *
     * NEW: Uses registry instead of when statements - no manual additions needed!
     */
    @Composable
    fun RenderModuleSheet(module: ModuleBase, onScrollToTop: (() -> Unit)? = null) {
        val provider = registry.getProvider(module)
        if (provider != null) {
            provider.provideComposable(module, onScrollToTop)
        } else {
            // Default: No sheet content for this module
            // This is fine - not all modules need dedicated sheets
        }
    }
    
    /**
     * Check if a module has a dedicated sheet UI available using registry
     *
     * NEW: Uses registry instead of when statements - automatically detects available UI
     */
    fun hasSheetUI(module: ModuleBase): Boolean {
        return registry.hasProvider(module)
    }

    /**
     * Get the provider for a module (for advanced use cases)
     */
    fun getProvider(module: ModuleBase): ComposableProvider? {
        return registry.getProvider(module)
    }

    /**
     * Get the title for a module's sheet using registry
     *
     * NEW: Uses registry pattern - titles can be defined in providers
     */
    fun getSheetTitle(module: ModuleBase): String {
        return when (module) {
            is MapsModule -> "Import Hillshaded Maps"
            is WaveModule -> "Import Wave Forecasts"
            is CirclesModule -> "Import Circles Packs"
            is AirspaceModule -> "Import Airspace Data"
            is AirportsModule -> "Import Airport Data"
            is LiveTrackingModule -> "Friend List"
            is SkysightModule -> "SkySight"
            else -> "${module.displayName} Actions"
        }
    }

    /**
     * Register a new UI provider for a module (for external modules)
     *
     * NEW: External modules can register their UI providers dynamically
     */
    fun registerProvider(moduleId: String, provider: ComposableProvider) {
        registry.registerProvider(moduleId, provider)
    }

    /**
     * Unregister a UI provider for a module
     */
    fun unregisterProvider(moduleId: String) {
        registry.unregisterProvider(moduleId)
    }
}
