package org.mountaincircles.app.modules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.ui.settings.ModuleSettingsRegistry
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider
import org.mountaincircles.app.utils.ScopeManager

/**
 * Represents an action that a module can contribute to the main menu
 *
 * This allows modules to add their own buttons to the main menu
 * that open dedicated sheets or perform specific actions
 */
data class ModuleMenuAction(
    val id: String,
    val title: String,
    val description: String,
    val getIcon: @Composable () -> Painter,
    val action: () -> Unit,
    val isEnabled: Boolean = true
)

/**
 * Base interface for all app modules
 */
interface ModuleBase {
    /** Unique module identifier */
    val moduleId: String

    /** Display name for the module */
    val displayName: String

    /** Module initialization order (lower numbers initialize first) */
    val moduleInitializationOrder: Int

    /** Sidebar widget display order (higher numbers appear first) */
    val sidebarWidgetOrder: Int

    /** Main menu ordering (lower numbers appear first) */
    val mainMenuOrder: Int

    /** Whether this module has a top menu button (declarative capability) */
    val hasTopMenuButton: Boolean
        get() = false  // Default: no top menu button

    /** Whether this module has main menu actions (declarative capability) */
    val hasMainMenuButton: Boolean
        get() = false  // Default: no main menu actions

    /** Whether this module has a sidebar widget (declarative capability) */
    val hasSidebarWidget: Boolean
        get() = false  // Default: no sidebar widget

    /** Whether this module has a settings panel (declarative capability) */
    val hasSettingsPanel: Boolean
        get() = false  // Default: no settings panel

    /** Whether this module has an import sheet (declarative capability) */
    val hasImportSheet: Boolean
        get() = false  // Default: no import sheet

    /** Whether this module controls its own re-rendering instead of reactive layer updates */
    val controlsReRender: Boolean
        get() = false  // Default: reactive layer updates

    // UI Methods (moved from ModuleUI interface - always available with safe defaults)

    /** Module icon (optional) */
    @Composable
    open fun getIcon(): androidx.compose.ui.graphics.painter.Painter? = null


    /** Check if module's top menu button is currently visible (based on state/data) */
    open val isTopMenuButtonVisible: Boolean
        get() = moduleState.value.hasDataToRender ?: false  // Default to false if not set

    /** Get the type of top menu button (default: SUBMENU for backward compatibility) */
    open fun getTopMenuButtonType(): TopMenuButtonType = TopMenuButtonType.SUBMENU

    /** Whether the top menu icon should be cropped (default: false) */
    open fun shouldCropTopMenuIcon(): Boolean = false

    /** Get the current button icon based on module state */
    @Composable
    open fun getButtonIcon(): androidx.compose.ui.graphics.painter.Painter? {
        return getIcon() // Default implementation
    }

    /** Get the button click action */
    open fun getButtonAction(): (() -> Unit)? = null

    /** Get the button long click action */
    open fun getButtonLongClickAction(): (() -> Unit)? {
        // Generic implementation: if module has import sheet, long-click opens it
        return if (hasImportSheet) {
            {
                Logger.log("UI", LogLevel.INFO, "$displayName top menu button long clicked - opening import sheet")
                val globalState = getGlobalState()
                globalState.navigationState.openImportSheet(moduleId)
            }
        } else {
            null
        }
    }




    /** Sidebar widget content (only called if hasSidebarWidget is true) */
    @Composable
    open fun SidebarWidget(onExpanded: (() -> Unit)? = null) {}

    /** Sidebar widget content with generic expansion management */
    @Composable
    open fun SidebarWidget(isExpanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        // Default implementation calls the legacy version for backward compatibility
        SidebarWidget(onExpanded = null)
    }


    /** Get the list of actions this module contributes to the main menu */
    open fun getModuleActions(): List<ModuleMenuAction> = emptyList()

    /** Module state flow */
    val moduleState: StateFlow<ModuleState>

    /** UI state flow for reactive UI components */
    val uiState: StateFlow<ModuleUIState>
        get() = moduleState.map { state ->
            ModuleUIState(
                isVisible = state.isInitialized,
                hasContent = true, // Default: assume content exists if initialized
                isLoading = false,
                errorMessage = state.errorMessage?.takeIf { state.hasError }
            )
        }.stateIn(
            scope = org.mountaincircles.app.utils.ScopeManager.computationScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ModuleUIState.Empty
        )

    /** Initialize the module */
    suspend fun initialize(globalState: GlobalState)

    /** Cleanup module resources */
    suspend fun cleanup()



    companion object {
        /**
         * ✅ PHASE 1: UTILITY FUNCTIONS - Extracted to ModuleBase
         * Standardizes selective flow creation across all modules
         */

        /**
         * Create a standardized selective flow for optimal UI performance
         * Reduces boilerplate from 8-12 lines to 1 line per selective flow
         */
        fun <TState, TResult> createSelectiveFlow(
            stateFlow: StateFlow<TState>,
            mapper: (TState) -> TResult,
            initialValue: TResult
        ): StateFlow<TResult> = stateFlow
            .map(mapper)
            .distinctUntilChanged()
            .stateIn(
                scope = createModuleScope(),
                started = SharingStarted.WhileSubscribed(),
                initialValue = initialValue
            )

        /**
         * Create a standardized coroutine scope for module operations
         * Ensures consistent SupervisorJob + Dispatchers pattern across all modules
         */
        fun createModuleScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Create a standardized UI state flow with initialization barriers.
         * Reduces boilerplate UI state derivation to a single function call.
         *
         * @param stateFlow The module state flow (ModuleState subtype)
         * @param hasContentPredicate Whether the module has content to show
         * @param isLoadingPredicate Whether the module is in a loading state
         * @param isVisiblePredicate Optional; when to show as visible (default: true when ready)
         * @param showReadyWhen Optional; when to emit Ready instead of Empty (default: isInitialized)
         */
        fun createUIStateFlow(
            stateFlow: StateFlow<ModuleState>,
            hasContentPredicate: (ModuleState) -> Boolean,
            isLoadingPredicate: (ModuleState) -> Boolean,
            isVisiblePredicate: (ModuleState) -> Boolean = { true },
            showReadyWhen: (ModuleState) -> Boolean = { it.isInitialized }
        ): StateFlow<ModuleUIState> = stateFlow.map { state ->
            if (showReadyWhen(state)) {
                ModuleUIState.Ready(
                    isVisible = isVisiblePredicate(state),
                    hasContent = hasContentPredicate(state),
                    isLoading = isLoadingPredicate(state),
                    errorMessage = state.errorMessage?.takeIf { state.hasError }
                )
            } else {
                ModuleUIState.Empty
            }
        }.stateIn(
            scope = createModuleScope(),
            started = SharingStarted.WhileSubscribed(),
            initialValue = ModuleUIState.Empty
        )
    }
}

/**
 * Base state for all modules
 */
abstract class ModuleState {
    abstract val isInitialized: Boolean
    abstract val hasError: Boolean
    abstract val errorMessage: String?
    open val hasDataToRender: Boolean? = null

    /**
     * Validate the current state and return any validation errors
     * @return List of validation error messages, empty if state is valid
     */
}



/**
 * Standardized state management for all modules
 *
 * Provides consistent state management with validation, safety, and performance.
 */
class StateManager<TState : Any>(
    private val validationEnabled: Boolean = true
) {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<TState?>(null)
    private var _isInitialized = false

    val state: kotlinx.coroutines.flow.StateFlow<TState> by lazy {
        if (!_isInitialized) {
            throw IllegalStateException("State not initialized - call initializeState() first")
        }
        _state.filterNotNull().stateIn(
            scope = ScopeManager.uiScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = _state.value!!
        )
    }

    fun initialize(initialState: TState, validator: ((TState) -> List<String>)? = null) {
        require(!_isInitialized) { "State already initialized" }

        if (validationEnabled && validator != null) {
            val errors = validator(initialState)
            require(errors.isEmpty()) { "Invalid initial state: ${errors.joinToString()}" }
        }

        _state.value = initialState
        _isInitialized = true
        Logger.log("STATE_MANAGER", LogLevel.DEBUG, "State initialized: $initialState")
    }

    fun update(transform: (TState) -> TState, validator: ((TState) -> List<String>)? = null) {
        val currentState = _state.value ?: throw IllegalStateException("State not initialized")
        val newState = transform(currentState)

        if (validationEnabled && validator != null) {
            val errors = validator(newState)
            require(errors.isEmpty()) { "Invalid state update: ${errors.joinToString()}" }
        }

        _state.value = newState
        // Commented out to prevent OOM when logging large state objects
        // Logger.log("STATE_MANAGER", LogLevel.DEBUG, "State updated: $newState")
    }

    fun reset(toState: TState, validator: ((TState) -> List<String>)? = null) {
        Logger.log("STATE_MANAGER", LogLevel.INFO, "Resetting state to: $toState")
        initialize(toState, validator)
    }

    fun isInitialized(): Boolean = _state.value != null
}

/**
 * Module UI capabilities
 */
/**
 * Defines the type of top menu button behavior for a module
 */
enum class TopMenuButtonType {
    /** Opens a submenu with module controls (default) */
    SUBMENU,
    /** Toggles module layer visibility */
    TOGGLE
}



/**
 * Module map layer management
 */
interface ModuleMapLayer {
    /** Add module layers to the map */
    suspend fun addLayers()

    /** Remove module layers from the map */
    suspend fun removeLayers()

    /** Update layer visibility */
    suspend fun updateVisibility(visible: Boolean)

    /** Check if layers are currently added */
    fun areLayersAdded(): Boolean
}

/**
 * Base Stateful Module - Phase 3: Base Classes
 *
 * Extracts common initialization logic from all modules to eliminate code duplication.
 * Provides a standardized initialization flow while allowing module-specific customization.
 *
 * @param TState The module's state type
 */
abstract class BaseStatefulModule<TState : ModuleState> : ModuleBase {

    /** Global state reference (set during initialization) */
    internal lateinit var globalState: GlobalState

    /** Layer manager instance (created during initialization) */
    protected var layerManager: ModuleMapLayer? = null

    /** Module-specific child scopes for proper lifecycle management */
    protected var uiScope: CoroutineScope? = null
    protected var ioScope: CoroutineScope? = null
    protected var computationScope: CoroutineScope? = null

    // ========================================
    // STANDARDIZED STATE MANAGEMENT (REQUIRED)
    // ========================================

    /** Standardized state management (always available) */
    protected val stateManager = StateManager<TState>()

    /** Public state access */
    val state: StateFlow<TState> by lazy { stateManager.state }

    /** ModuleBase required: expose state as moduleState (default implementation) */
    override val moduleState: StateFlow<TState> get() = state

    /**
     * Initialize state with validation (called in module init block)
     */
    protected fun initializeState(initialState: TState) {
        stateManager.initialize(initialState, ::validateState)
    }

    /**
     * Update state with validation (modules can override for their own logic)
     * Standardized modules should delegate to stateManager
     */
    /**
     * Update state with validation (standardized implementation)
     */
    internal open fun updateState(transform: (TState) -> TState) {
        stateManager.update(transform, ::validateState)
    }

    /**
     * Override to provide state validation
     */
    protected open fun validateState(state: TState): List<String> = emptyList()

    /**
     * Create the layer manager for this module
     * Override this to provide module-specific layer management
     */
    /**
     * Standardized settings registration for modules
     * Call this in your initialization functions to register settings in the standard way
     */
    fun registerModuleSettings(settingsRegistration: () -> Unit, metadataProvider: SettingsMetadataProvider) {
        // Register the settings definitions and update functions
        settingsRegistration()

        // Register the metadata provider for UI
        org.mountaincircles.app.ui.settings.ModuleSettingsRegistry.registerMetadataProvider(moduleId, metadataProvider)
    }

    protected abstract fun createLayerManager(): ModuleMapLayer?

    /**
     * Module-specific initialization logic
     * Override this to add custom initialization steps
     */
    protected abstract suspend fun onInitialize()

    /**
     * Standard initialization flow for all stateful modules
     * Handles common initialization steps and delegates module-specific logic
     */
    override suspend fun initialize(globalState: GlobalState) {
        Logger.log(moduleId.uppercase(), LogLevel.INFO, "Initializing $displayName module")

        try {
            // 1. Store global state reference (common to all modules)
            this.globalState = globalState

            // 2. Create module-specific child scopes for proper lifecycle management
            uiScope = org.mountaincircles.app.utils.ScopeManager.createUIChildScope()
            ioScope = org.mountaincircles.app.utils.ScopeManager.createIOChildScope()
            computationScope = org.mountaincircles.app.utils.ScopeManager.createComputationChildScope()
            Logger.log(moduleId.uppercase(), LogLevel.DEBUG, "Created child scopes for $displayName module")

            // 3. Initialize layer manager (common pattern)
            layerManager = createLayerManager()
            layerManager?.let { manager ->
                Logger.log(moduleId.uppercase(), LogLevel.DEBUG, "Layer manager created")
                // Note: Layer registration is deferred until after module initialization completes
                // This ensures layers are only registered when the module is fully ready
            }

            // 3. Initialize settings (common to modules with settings)
            // Note: Settings initialization is handled by each module's specific initialize() override
            // This allows modules to control the timing and order of settings initialization
            Logger.log(moduleId.uppercase(), LogLevel.DEBUG, "Settings initialization delegated to module-specific logic")

            // 4. Module-specific initialization (unique to each module)
            Logger.log(moduleId.uppercase(), LogLevel.DEBUG, "Running module-specific initialization")
            onInitialize()

            Logger.log(moduleId.uppercase(), LogLevel.INFO, "$displayName module initialized successfully")

        } catch (e: Exception) {
            Logger.log(moduleId.uppercase(), LogLevel.ERROR, "Failed to initialize $displayName module: ${e.message}", e)

            // Re-throw to let caller handle the error
            throw e
        }
    }


    /**
     * Standard cleanup flow for all stateful modules
     */
    override suspend fun cleanup() {
        Logger.log(moduleId.uppercase(), LogLevel.INFO, "Cleaning up $displayName module")

        try {
            // 1. Cancel module-specific child scopes
            uiScope?.cancel()
            ioScope?.cancel()
            computationScope?.cancel()
            Logger.log(moduleId.uppercase(), LogLevel.DEBUG, "Cancelled child scopes for $displayName module")

            // 2. Remove layers
            layerManager?.removeLayers()

            // 3. Module-specific cleanup
            onCleanup()

            Logger.log(moduleId.uppercase(), LogLevel.INFO, "$displayName module cleanup completed")

        } catch (e: Exception) {
            Logger.log(moduleId.uppercase(), LogLevel.ERROR, "Error during $displayName module cleanup: ${e.message}", e)
        }
    }

    /**
     * Module-specific cleanup logic
     * Override this to add custom cleanup steps
     */
    protected open suspend fun onCleanup() {
        // Default: no-op, modules can override for custom cleanup
    }
}
