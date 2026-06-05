package org.mountaincircles.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.ui.modules.ComposableProvider
import org.mountaincircles.app.ui.modules.ComposableProviderRegistry
import org.mountaincircles.app.settings.ClassMetadata
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Provider interface for module settings metadata
 */
interface SettingsMetadataProvider {
    val moduleId: String

    fun canProvide(module: ModuleBase): Boolean

    @Composable
    fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>>

    suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any)

    suspend fun resetSettingsToDefaults(module: ModuleBase)
}











/**
 * Registry for module-specific settings UI components and metadata providers
 *
 * This keeps settings UI logic organized and allows the core SettingsComposable
 * to remain generic. Each module registers its settings providers here.
 *
 * NEW: Now uses generic registry pattern - modules register themselves automatically
 * NEW: Supports both UI providers and metadata providers for complete genericity
 */
object ModuleSettingsRegistry {

    private val uiRegistry = ComposableProviderRegistry.create()
    private val metadataProviders = mutableMapOf<String, SettingsMetadataProvider>()

    init {
        // Providers will be registered by modules during their initialization
        // This keeps the registry generic and moves specific code to modules
    }

    // ===============================
    // UI PROVIDER METHODS
    // ===============================

    /**
     * Render settings for a given module using registry pattern
     *
     * NEW: Uses registry instead of when statements - no manual additions needed!
     */
    @Composable
    fun RenderModuleSettings(module: ModuleBase) {
        val provider = uiRegistry.getProvider(module)
        if (provider != null) {
            provider.provideComposable(module)
        } else {
            // Unknown module type - this is fine, just skip it
            // Logger could be added here if needed
        }
    }

    /**
     * Check if a module has settings UI available using registry
     *
     * NEW: Uses registry instead of when statements - automatically detects available UI
     */
    fun hasSettingsUI(module: ModuleBase): Boolean {
        return uiRegistry.hasProvider(module)
    }

    /**
     * Register a new UI provider for a module (for external modules)
     *
     * NEW: External modules can register their UI providers dynamically
     */
    fun registerUIProvider(moduleId: String, provider: ComposableProvider) {
        uiRegistry.registerProvider(moduleId, provider)
    }

    /**
     * Unregister a UI provider for a module
     */
    fun unregisterUIProvider(moduleId: String) {
        uiRegistry.unregisterProvider(moduleId)
    }

    // ===============================
    // METADATA PROVIDER METHODS
    // ===============================

    /**
     * Get settings metadata for a given module using registry pattern
     *
     * NEW: Uses metadata registry instead of direct calls - completely generic!
     */
    @Composable
    fun getModuleSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        val provider = metadataProviders[module.moduleId]
        Logger.log("SETTINGS_REGISTRY", LogLevel.DEBUG, "${module.moduleId}: provider exists=${provider != null}")
        if (provider != null) {
            Logger.log("SETTINGS_REGISTRY", LogLevel.DEBUG, "${module.moduleId}: canProvide=${provider.canProvide(module)}")
        }

        return if (provider != null && provider.canProvide(module)) {
            val result = provider.getSettingsMetadata(module)
            Logger.log("SETTINGS_REGISTRY", LogLevel.DEBUG, "${module.moduleId}: metadata fields=${result.first?.fields?.size ?: 0}")
            result
        } else {
            // Unknown module type or no metadata provider
            Logger.log("SETTINGS_REGISTRY", LogLevel.DEBUG, "${module.moduleId}: no provider or cannot provide - returning null")
            null to emptyMap()
        }
    }

    /**
     * Handle setting change for a given module using registry pattern
     *
     * NEW: Uses metadata registry instead of direct calls - completely generic!
     */
    suspend fun handleModuleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        val provider = metadataProviders[module.moduleId]
        if (provider != null && provider.canProvide(module)) {
            provider.handleSettingChange(module, fieldName, value)
        }
    }

    /**
     * Reset settings to defaults for a given module using registry pattern
     */
    suspend fun resetModuleSettingsToDefaults(module: ModuleBase) {
        val provider = metadataProviders[module.moduleId]
        if (provider != null && provider.canProvide(module)) {
            provider.resetSettingsToDefaults(module)
        }
    }

    /**
     * Check if a module has settings metadata available using registry
     *
     * NEW: Uses registry instead of when statements - automatically detects available metadata
     */
    fun hasSettingsMetadata(module: ModuleBase): Boolean {
        val provider = metadataProviders[module.moduleId]
        return provider != null && provider.canProvide(module)
    }

    /**
     * Register a new metadata provider for a module
     *
     * NEW: Modules register their metadata providers dynamically
     */
    fun registerMetadataProvider(moduleId: String, provider: SettingsMetadataProvider) {
        metadataProviders[moduleId] = provider
    }

    /**
     * Unregister a metadata provider for a module
     */
    fun unregisterMetadataProvider(moduleId: String) {
        metadataProviders.remove(moduleId)
    }


}
