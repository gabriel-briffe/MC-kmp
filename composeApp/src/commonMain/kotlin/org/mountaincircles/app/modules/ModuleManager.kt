package org.mountaincircles.app.modules

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.state.GlobalState

@OptIn(FlowPreview::class)
class ModuleManager private constructor() {

    companion object {
        fun create(): ModuleManager = ModuleManager()
    }

    private val _registeredModules = MutableStateFlow<List<ModuleBase>>(emptyList())
    private val _modulesAvailableForUI = MutableStateFlow<List<ModuleBase>>(emptyList())
    val modulesAvailableForUI: StateFlow<List<ModuleBase>> = _modulesAvailableForUI

    // Reactive modules available for UI that updates when any module state changes
    private var reactiveModulesAvailableForUIJob: Job? = null

    private lateinit var globalState: GlobalState
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val moduleMutex = Mutex()
    private val updateMutex = Mutex()
    
    /**
     * Initialize the module manager
     */
    fun initialize(globalState: GlobalState) {
        this.globalState = globalState
        Logger.log("MODULES", LogLevel.INFO, "ModuleManager initialized")
        setupReactiveModulesAvailableForUI()
    }

    /**
     * Register all available modules
     *
     * To add a new module:
     * 1. Import the module class above
     * 2. Add it to the modules list below
     */
    suspend fun registerAllModules() {
        Logger.log("MODULES", LogLevel.INFO, "Starting module registration...")

        val modules = listOf<ModuleBase>(
            MapsModule(),         // Maps (base terrain layer)
            CirclesModule(),      // Circles (aviation data)
            WaveModule(),         // Wave (meteorological data)
            AirspaceModule(),     // Airspace (airspace data)
            AirportsModule(),     // Airports (airport data)
            LiveTrackingModule(), // Live Tracking (real-time aircraft)
            SkysightModule(),     // Skysight (minimal module)
        )

        var successCount = 0
        var failureCount = 0

        try {
            registerModulesBatch(modules)
            successCount = modules.size
            Logger.log("MODULES", LogLevel.INFO, "Registered ${modules.size} modules successfully")
        } catch (e: Exception) {
            Logger.log("MODULES", LogLevel.ERROR, "Failed to register modules: ${e.message}", e)
            failureCount = modules.size
        }

        Logger.log("MODULES", LogLevel.INFO,
            "Module registration completed: $successCount successful, $failureCount failed")
    }

    

    /**
     * Register multiple modules in batch mode (single UI update)
     */
    suspend fun registerModulesBatch(modules: List<ModuleBase>) {
        moduleMutex.withLock {
            try {
                Logger.log("MODULES", LogLevel.INFO, "Batch registering ${modules.size} modules")

                val successfullyRegistered = mutableListOf<ModuleBase>()

                for (module in modules) {
                    try {
                        Logger.log("MODULES", LogLevel.DEBUG, "Registering module: ${module.moduleId}")

                        if (_registeredModules.value.any { it.moduleId == module.moduleId }) {
                            Logger.log("MODULES", LogLevel.WARN, "Module ${module.moduleId} already registered")
                            continue
                        }

                        Logger.log("MODULES", LogLevel.DEBUG, "Initializing module: ${module.moduleId}")
                        module.initialize(globalState)
                        Logger.log("MODULES", LogLevel.DEBUG, "Module ${module.moduleId} initialized")

                        successfullyRegistered.add(module)

                    } catch (e: Exception) {
                        Logger.log("MODULES", LogLevel.ERROR, "Failed to register module ${module.moduleId}: ${e.message}", e)
                    }
                }

                if (successfullyRegistered.isNotEmpty()) {
                    val updatedModules = (_registeredModules.value + successfullyRegistered)
                        .sortedBy { it.moduleInitializationOrder }
                    _registeredModules.value = updatedModules
                    // No need to call updateAvailableModules() - reactive flow handles it
                }

                Logger.log("MODULES", LogLevel.INFO, "Batch registration completed: ${successfullyRegistered.size} successful")

            } catch (e: Exception) {
                Logger.log("MODULES", LogLevel.ERROR, "Failed to batch register modules: ${e.message}", e)
            }
        }
    }

    
    
    /**
     * Get a specific module by ID
     */
    fun getModule(moduleId: String): ModuleBase? {
        return _registeredModules.value.find { it.moduleId == moduleId }
    }
    

    /**
     * Cleanup resources
     */
    /**
     * Set up reactive modules available for UI that update when any module state changes
     */
    private fun setupReactiveModulesAvailableForUI() {
        reactiveModulesAvailableForUIJob?.cancel()

        reactiveModulesAvailableForUIJob = scope.launch {
            // Combine all registered module states into a single flow
            _registeredModules.collect { modules ->
                if (modules.isEmpty()) {
                    _modulesAvailableForUI.value = emptyList()
                    return@collect
                }

                // Create a flow that emits whenever any module's isInitialized changes
                // This is more efficient than listening to full state changes
                val combinedInitializedFlow = combine(
                    modules.map { module ->
                        module.moduleState
                            .map { it.isInitialized }
                            .distinctUntilChanged()  // Only emit when isInitialized actually changes
                    }
                ) { initializedStates ->
                    modules.zip(initializedStates).filter { (_, isInitialized) ->
                        isInitialized  // Filter for initialized modules
                    }.map { (module, _) -> module }
                }

                combinedInitializedFlow.collect { available ->
                    _modulesAvailableForUI.value = available
                    Logger.log("MODULES", LogLevel.DEBUG, "Modules available for UI updated reactively: ${available.size} modules")
                }
            }
        }
    }

    fun cleanup() {
        Logger.log("MODULES", LogLevel.INFO, "Cleaning up ModuleManager")
        reactiveModulesAvailableForUIJob?.cancel()
        scope.cancel()
    }
}
