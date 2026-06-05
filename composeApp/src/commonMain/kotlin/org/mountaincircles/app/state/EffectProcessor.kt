package org.mountaincircles.app.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Interface for processing effects
 */
interface EffectProcessor {
    /**
     * The module ID this processor handles
     */
    val moduleId: String

    /**
     * Check if this processor can handle the given effect
     */
    fun canProcess(effect: Effect): Boolean

    /**
     * Process the effect
     */
    suspend fun process(context: EffectContext): EffectResult

    /**
     * Queue an effect for processing
     */
    suspend fun enqueueEffect(context: EffectContext)
}

/**
 * Base effect processor with common functionality
 */
abstract class BaseEffectProcessor : EffectProcessor {
    protected val effectQueue = EffectQueue()
    protected val activeJobs = mutableMapOf<String, Job>()

    override val moduleId: String = "base" // Override in subclasses



    /**
     * Start processing effects
     */
    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            effectQueue.effects.collect { context ->
                processEffectInScope(context)
            }
        }
    }

    /**
     * Queue an effect for processing
     */
    suspend fun queueEffect(context: EffectContext) {
        effectQueue.queueEffect(context)
    }

    /**
     * Override enqueueEffect to use the effect queue
     */
    override suspend fun enqueueEffect(context: EffectContext) {
        Logger.log("BASE_EFFECT_PROCESSOR", LogLevel.INFO, "Enqueueing effect: ${context.effect}")
        effectQueue.queueEffect(context)
        Logger.log("BASE_EFFECT_PROCESSOR", LogLevel.INFO, "Effect queued successfully: ${context.effect}")
    }

    /**
     * Cancel an effect by ID
     */
    fun cancelEffect(effectId: String) {
        effectQueue.cancelEffect(effectId)
    }

    /**
     * Cancel all effects for a module
     */
    fun cancelModuleEffects(moduleId: String) {
        effectQueue.cancelModuleEffects(moduleId)
    }

    /**
     * Get queue status
     */
    fun getQueueStatus(): EffectQueueStatus {
        return effectQueue.getStatus()
    }

    private fun processEffectInScope(context: EffectContext) {
        if (!canProcess(context.effect)) {
            Logger.log("EFFECT_PROCESSOR", LogLevel.WARN,
                "Cannot process effect: ${context.effect.effectId}")
            org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                context.onComplete(EffectResult.Error(IllegalArgumentException("Cannot process effect")))
            }
            return
        }

        val effectId = "${context.effect.moduleId}:${context.effect.effectId}"
        val job = context.scope.launch {
            try {
                Logger.log("EFFECT_PROCESSOR", LogLevel.INFO,
                    "Processing effect: $effectId")

                effectQueue.registerActiveEffect(effectId, coroutineContext[Job]!!)

                val result = process(context)

                Logger.log("EFFECT_PROCESSOR", LogLevel.INFO,
                    "Effect completed: $effectId with result: ${result::class.simpleName}")

                Logger.log("EFFECT_PROCESSOR", LogLevel.INFO, "Calling onComplete callback for $effectId")
                // Dispatch callback to main thread to ensure proper context
                org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                    Logger.log("EFFECT_PROCESSOR", LogLevel.INFO, "Executing callback on main thread for $effectId")
                    context.onComplete(result)
                    Logger.log("EFFECT_PROCESSOR", LogLevel.INFO, "Callback execution completed for $effectId")
                }
                Logger.log("EFFECT_PROCESSOR", LogLevel.INFO, "onComplete callback dispatched for $effectId")

            } catch (e: Exception) {
                Logger.log("EFFECT_PROCESSOR", LogLevel.ERROR,
                    "Effect failed: $effectId", e)
                org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                    context.onComplete(EffectResult.Error(e))
                }
            } finally {
                effectQueue.unregisterActiveEffect(effectId)
            }
        }

        activeJobs[effectId] = job
    }
}

/**
 * Generic module effect processor
 */
open class ModuleEffectProcessor(
    override val moduleId: String,
    private val supportedEffects: Set<String> = emptySet()
) : BaseEffectProcessor() {

    override fun canProcess(effect: Effect): Boolean {
        return when (effect) {
            is ModuleEffect -> {
                effect.moduleId == moduleId &&
                (supportedEffects.isEmpty() || supportedEffects.contains(effect.effectId))
            }
            else -> false
        }
    }

    override suspend fun process(context: EffectContext): EffectResult {
        val effect = context.effect as ModuleEffect

        return try {
            when (effect) {
                is ModuleEffect.LoadData<*> -> processLoadData(effect, context)
                is ModuleEffect.SaveData<*> -> processSaveData(effect, context)
                is ModuleEffect.ImportData -> processImportData(effect, context)
                is ModuleEffect.ExportData -> processExportData(effect, context)
                is ModuleEffect.DeleteData -> processDeleteData(effect, context)
                is ModuleEffect.ClearAllData -> processClearAllData(effect, context)
                is ModuleEffect.ShowNotification -> processShowNotification(effect, context)
                is ModuleEffect.Navigate -> processNavigate(effect, context)
                is ModuleEffect.Custom -> processCustom(effect, context)
            }
        } catch (e: Exception) {
            Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.ERROR,
                "Error processing effect ${effect.effectId}", e)
            EffectResult.Error(e)
        }
    }

    protected open suspend fun processLoadData(effect: ModuleEffect.LoadData<*>, context: EffectContext): EffectResult {
        // Default implementation - override in specific processors
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Loading data for module ${effect.moduleId} from ${effect.source}")
        return EffectResult.Success("Data loaded")
    }

    protected open suspend fun processSaveData(effect: ModuleEffect.SaveData<*>, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Saving data for module ${effect.moduleId} with key ${effect.key}")
        return EffectResult.Success("Data saved")
    }

    protected open suspend fun processImportData(effect: ModuleEffect.ImportData, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Importing ${effect.importType} data for module ${effect.moduleId}")

        // Simulate progress updates
        context.onProgress(0.2f)
        // ... import logic ...
        context.onProgress(0.8f)

        return EffectResult.Success("Data imported")
    }

    protected open suspend fun processExportData(effect: ModuleEffect.ExportData, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Exporting data for module ${effect.moduleId} to ${effect.destination}")
        return EffectResult.Success("Data exported")
    }

    protected open suspend fun processDeleteData(effect: ModuleEffect.DeleteData, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Deleting ${effect.deleteType} data for module ${effect.moduleId}: ${effect.dataId}")
        return EffectResult.Success("Data deleted")
    }

    protected open suspend fun processClearAllData(effect: ModuleEffect.ClearAllData, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Clearing all data for module ${effect.moduleId}")
        return EffectResult.Success("All data cleared")
    }

    protected open suspend fun processShowNotification(effect: ModuleEffect.ShowNotification, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Showing ${effect.type} notification for module ${effect.moduleId}: ${effect.message}")
        return EffectResult.Success("Notification shown")
    }

    protected open suspend fun processNavigate(effect: ModuleEffect.Navigate, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Navigating to ${effect.destination} for module ${effect.moduleId}")
        return EffectResult.Success("Navigation completed")
    }

    protected open suspend fun processCustom(effect: ModuleEffect.Custom, context: EffectContext): EffectResult {
        Logger.log("MODULE_EFFECT_PROCESSOR", LogLevel.INFO,
            "Processing custom action ${effect.action} for module ${effect.moduleId}")
        return EffectResult.Success("Custom action completed")
    }
}

/**
 * Effect processor registry
 */
class EffectProcessorRegistry {
    private val processors = mutableMapOf<String, EffectProcessor>()

    /**
     * Register a processor for a module
     */
    fun registerProcessor(moduleId: String, processor: EffectProcessor) {
        processors[moduleId] = processor
        Logger.log("EFFECT_PROCESSOR_REGISTRY", LogLevel.INFO,
            "Registered effect processor for module: $moduleId")
    }

    /**
     * Get processor for a module
     */
    fun getProcessor(moduleId: String): EffectProcessor? {
        return processors[moduleId]
    }

    /**
     * Check if module has a processor
     */
    fun hasProcessor(moduleId: String): Boolean {
        return processors.containsKey(moduleId)
    }

    /**
     * Remove processor for a module
     */
    fun removeProcessor(moduleId: String) {
        processors.remove(moduleId)
        Logger.log("EFFECT_PROCESSOR_REGISTRY", LogLevel.INFO,
            "Removed effect processor for module: $moduleId")
    }

    /**
     * Get all registered modules
     */
    fun getRegisteredModules(): Set<String> {
        return processors.keys
    }

    /**
     * Clear all processors
     */
    fun clear() {
        processors.clear()
        Logger.log("EFFECT_PROCESSOR_REGISTRY", LogLevel.INFO, "Cleared all effect processors")
    }
}

/**
 * Global effect processor registry instance
 */
val globalEffectProcessorRegistry = EffectProcessorRegistry()
