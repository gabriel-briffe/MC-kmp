package org.mountaincircles.app.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Middleware for handling effects triggered by actions
 */
interface EffectMiddleware {
    /**
     * Process an action and potentially trigger effects
     */
    suspend fun process(action: StateAction): List<Effect>?

    /**
     * Get middleware name for debugging
     */
    val middlewareName: String
        get() = this::class.simpleName ?: "UnknownMiddleware"
}

/**
 * Base effect middleware with common functionality
 */
abstract class BaseEffectMiddleware : EffectMiddleware {
    protected val scope: CoroutineScope? = null
    protected val actionDispatcher: ActionDispatcher? = null

    /**
     * Set the coroutine scope for effect processing
     */
    fun setScope(scope: CoroutineScope) {
        // Note: In a real implementation, you'd use a setter or constructor
        // This is simplified for the example
    }

    /**
     * Set the action dispatcher for dispatching follow-up actions
     */
    fun setActionDispatcher(dispatcher: ActionDispatcher) {
        // Note: In a real implementation, you'd use a setter or constructor
        // This is simplified for the example
    }

    /**
     * Dispatch a follow-up action
     */
    protected suspend fun dispatchAction(action: StateAction) {
        actionDispatcher?.dispatch(action) ?: Logger.log("EFFECT_MIDDLEWARE", LogLevel.WARN,
            "No action dispatcher set for middleware: $middlewareName")
    }
}

/**
 * Module-specific effect middleware
 */
class ModuleEffectMiddleware(
    private val moduleId: String,
    private val effectProcessor: EffectProcessor? = null
) : BaseEffectMiddleware() {

    override val middlewareName: String = "ModuleEffectMiddleware"

    override suspend fun process(action: StateAction): List<Effect>? {
        if (action.moduleId != moduleId) {
            return null
        }

        Logger.log("MODULE_EFFECT_MIDDLEWARE", LogLevel.DEBUG,
            "Processing action ${action.actionType} for module $moduleId")

        return when (action) {
            is ModuleAction.Initialize -> processInitialize(action)
            is ModuleAction.SetLoading -> processSetLoading(action)
            is ModuleAction.SetError -> processSetError(action)
            is ModuleAction.ClearError -> processClearError(action)
            is ModuleAction.Reset -> processReset(action)
            is ModuleAction.Custom -> processCustom(action)
            else -> null
        }
    }

    private fun processInitialize(action: ModuleAction.Initialize): List<Effect> {
        return listOf(
            ModuleEffect.LoadData<Any>(
                moduleId = moduleId,
                dataType = Any::class,
                source = "initialization"
            )
        )
    }

    private fun processSetLoading(action: ModuleAction.SetLoading): List<Effect> {
        if (!action.isLoading) {
            return listOf(
                ModuleEffect.ShowNotification(
                    moduleId = moduleId,
                    message = "Loading completed",
                    type = NotificationType.SUCCESS
                )
            )
        }
        return emptyList()
    }

    private fun processSetError(action: ModuleAction.SetError): List<Effect> {
        return listOf(
            ModuleEffect.ShowNotification(
                moduleId = moduleId,
                message = "Error: ${action.message}",
                type = NotificationType.ERROR
            )
        )
    }

    private fun processClearError(action: ModuleAction.ClearError): List<Effect> {
        return listOf(
            ModuleEffect.ShowNotification(
                moduleId = moduleId,
                message = "Error cleared",
                type = NotificationType.INFO
            )
        )
    }

    private fun processReset(action: ModuleAction.Reset): List<Effect> {
        return listOf(
            ModuleEffect.ClearAllData(moduleId = moduleId)
        )
    }

    private fun processCustom(action: ModuleAction.Custom): List<Effect> {
        return when (action.action) {
            "IMPORT_DATA" -> listOf(
                ModuleEffect.ImportData(
                    moduleId = moduleId,
                    config = action.data,
                    importType = action.data["importType"] as? String ?: "unknown"
                )
            )
            "EXPORT_DATA" -> listOf(
                ModuleEffect.ExportData(
                    moduleId = moduleId,
                    data = action.data["data"] ?: Unit,
                    destination = action.data["destination"] as? String ?: "default"
                )
            )
            "DELETE_ITEM" -> listOf(
                ModuleEffect.DeleteData(
                    moduleId = moduleId,
                    dataId = action.data["itemId"] as? String ?: "",
                    deleteType = action.data["deleteType"] as? String ?: "item"
                )
            )
            "SAVE_SETTINGS" -> listOf(
                ModuleEffect.SaveData(
                    moduleId = moduleId,
                    data = action.data["settings"] ?: Unit,
                    key = action.data["settingsKey"] as? String ?: "settings"
                )
            )
            else -> emptyList()
        }
    }
}

/**
 * Effect middleware manager
 */
class EffectMiddlewareManager {
    private val middlewares = mutableListOf<EffectMiddleware>()
    private val effectProcessorRegistry = globalEffectProcessorRegistry

    /**
     * Add middleware to the chain
     */
    fun addMiddleware(middleware: EffectMiddleware) {
        middlewares.add(middleware)
        Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.INFO,
            "Added middleware: ${middleware.middlewareName}")
    }

    /**
     * Remove middleware from the chain
     */
    fun removeMiddleware(middleware: EffectMiddleware) {
        middlewares.remove(middleware)
        Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.INFO,
            "Removed middleware: ${middleware.middlewareName}")
    }

    /**
     * Process an action through all middlewares
     */
    suspend fun processAction(action: StateAction) {
        Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.DEBUG,
            "Processing action ${action.actionType} through ${middlewares.size} middlewares")

        val allEffects = mutableListOf<Effect>()

        // Collect effects from all middlewares
        for (middleware in middlewares) {
            try {
                val effects = middleware.process(action)
                if (effects != null) {
                    allEffects.addAll(effects)
                    Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.DEBUG,
                        "Middleware ${middleware.middlewareName} produced ${effects.size} effects")
                }
            } catch (e: Exception) {
                Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.ERROR,
                    "Middleware ${middleware.middlewareName} failed", e)
            }
        }

        // Process effects if any were generated
        if (allEffects.isNotEmpty()) {
            processEffects(allEffects, action)
        }
    }

    /**
     * Process generated effects
     */
    private suspend fun processEffects(effects: List<Effect>, originalAction: StateAction) {
        Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.INFO,
            "Processing ${effects.size} effects from action ${originalAction.actionType}")

        for (effect in effects) {
            val processor = effectProcessorRegistry.getProcessor(effect.moduleId)

            if (processor != null) {
                val context = EffectContext(
                    effect = effect,
                    scope = org.mountaincircles.app.utils.ScopeManager.computationScope,
                    onComplete = { result ->
                        handleEffectResult(effect, result, originalAction)
                    },
                    onProgress = { progress ->
                        handleEffectProgress(effect, progress)
                    }
                )

                try {
                    processor.enqueueEffect(context)
                    Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.DEBUG,
                        "Queued effect ${effect.effectId} for processing")
                } catch (e: Exception) {
                    Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.ERROR,
                        "Failed to queue effect ${effect.effectId}", e)
                }
            } else {
                Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.WARN,
                    "No processor found for module ${effect.moduleId}")
            }
        }
    }

    /**
     * Handle effect completion
     */
    private fun handleEffectResult(effect: Effect, result: EffectResult, originalAction: StateAction) {
        when (result) {
            is EffectResult.Success -> {
                Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.INFO,
                    "Effect ${effect.effectId} completed successfully: ${result.data}")
            }
            is EffectResult.Error -> {
                Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.ERROR,
                    "Effect ${effect.effectId} failed", result.exception)
            }
            is EffectResult.Cancelled -> {
                Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.INFO,
                    "Effect ${effect.effectId} was cancelled")
            }
        }
    }

    /**
     * Handle effect progress updates
     */
    private fun handleEffectProgress(effect: Effect, progress: Float) {
        Logger.log("EFFECT_MIDDLEWARE_MANAGER", LogLevel.DEBUG,
            "Effect ${effect.effectId} progress: ${(progress * 100).toInt()}%")
    }

    /**
     * Get middleware status
     */
    fun getStatus(): MiddlewareStatus {
        return MiddlewareStatus(
            middlewareCount = middlewares.size,
            middlewareNames = middlewares.map { it.middlewareName },
            processorModules = effectProcessorRegistry.getRegisteredModules().toList()
        )
    }
}

/**
 * Middleware status for monitoring
 */
data class MiddlewareStatus(
    val middlewareCount: Int,
    val middlewareNames: List<String>,
    val processorModules: List<String>
)

/**
 * Global effect middleware manager instance
 */
val globalEffectMiddlewareManager = EffectMiddlewareManager()

/**
 * Initialize standard effect middleware for all modules
 */
fun initializeStandardEffectMiddleware() {
    // Register standard processors for all modules
    val standardModules = listOf("circles", "wave", "geolocation", "maps")

    for (moduleId in standardModules) {
        val processor = ModuleEffectProcessor(moduleId)
        globalEffectProcessorRegistry.registerProcessor(moduleId, processor)

        val middleware = ModuleEffectMiddleware(moduleId, processor)
        globalEffectMiddlewareManager.addMiddleware(middleware)
    }

    Logger.log("EFFECT_SYSTEM", LogLevel.INFO, "Initialized standard effect middleware for ${standardModules.size} modules")
}
