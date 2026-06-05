package org.mountaincircles.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Base interface for all effects (side effects)
 */
interface Effect {
    val effectId: String
        get() = this::class.simpleName ?: "UnknownEffect"
    val moduleId: String
    val priority: EffectPriority
        get() = EffectPriority.NORMAL
    val canBeCancelled: Boolean
        get() = true
}

/**
 * Effect priority levels
 */
enum class EffectPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * Standard effects that can be used by any module
 */
sealed class ModuleEffect(override val moduleId: String) : Effect {

    /**
     * Load data from a data source
     */
    data class LoadData<T : Any>(
        override val moduleId: String,
        val dataType: kotlin.reflect.KClass<T>,
        val source: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "LoadData"
        override val priority = EffectPriority.HIGH
    }

    /**
     * Save data to persistence
     */
    data class SaveData<T>(
        override val moduleId: String,
        val data: T,
        val key: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "SaveData"
        override val priority = EffectPriority.NORMAL
    }

    /**
     * Import data from external source
     */
    data class ImportData(
        override val moduleId: String,
        val config: Map<String, Any?>,
        val importType: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "ImportData"
        override val priority = EffectPriority.HIGH
        override val canBeCancelled = false // Import operations shouldn't be cancelled
    }

    /**
     * Export data to external destination
     */
    data class ExportData(
        override val moduleId: String,
        val data: Any,
        val destination: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "ExportData"
        override val priority = EffectPriority.NORMAL
    }

    /**
     * Delete data or files
     */
    data class DeleteData(
        override val moduleId: String,
        val dataId: String,
        val deleteType: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "DeleteData"
        override val priority = EffectPriority.LOW
    }

    /**
     * Clear all data for a module
     */
    data class ClearAllData(
        override val moduleId: String
    ) : ModuleEffect(moduleId) {
        override val effectId = "ClearAllData"
        override val priority = EffectPriority.LOW
    }

    /**
     * Show user notification or message
     */
    data class ShowNotification(
        override val moduleId: String,
        val message: String,
        val type: NotificationType = NotificationType.INFO
    ) : ModuleEffect(moduleId) {
        override val effectId = "ShowNotification"
        override val priority = EffectPriority.NORMAL
    }

    /**
     * Navigate to different UI state
     */
    data class Navigate(
        override val moduleId: String,
        val destination: String,
        val params: Map<String, Any?> = emptyMap()
    ) : ModuleEffect(moduleId) {
        override val effectId = "Navigate"
        override val priority = EffectPriority.HIGH
    }

    /**
     * Custom module-specific effect
     */
    data class Custom(
        override val moduleId: String,
        val action: String,
        val data: Map<String, Any?> = emptyMap()
    ) : ModuleEffect(moduleId) {
        override val effectId = "Custom"
        override val priority = EffectPriority.NORMAL
    }
}

/**
 * Notification types for user feedback
 */
enum class NotificationType {
    INFO, SUCCESS, WARNING, ERROR
}

/**
 * Result of effect execution
 */
sealed class EffectResult {
    data class Success(val data: Any? = null) : EffectResult()
    data class Error(val exception: Exception) : EffectResult()
    data object Cancelled : EffectResult()
}

/**
 * Effect execution context
 */
data class EffectContext(
    val effect: Effect,
    val scope: CoroutineScope,
    val onComplete: (EffectResult) -> Unit,
    val onProgress: (Float) -> Unit = {}
)

/**
 * Effect queue for managing concurrent effects
 */
class EffectQueue {
    private val _effects = MutableSharedFlow<EffectContext>(replay = 0)
    val effects: Flow<EffectContext> = _effects.asSharedFlow()

    private val activeEffects = mutableMapOf<String, Job>()
    private val queuedEffects = mutableListOf<EffectContext>()

    /**
     * Queue an effect for execution
     */
    suspend fun queueEffect(context: EffectContext) {
        Logger.log("EFFECT_QUEUE", LogLevel.DEBUG,
            "Queueing effect: ${context.effect.effectId} for module ${context.effect.moduleId}")

        // Add to queue if there are active effects that can't be interrupted
        val hasUninterruptableEffects = activeEffects.values.any { !it.isCompleted && !it.isCancelled }

        if (hasUninterruptableEffects && context.effect.priority != EffectPriority.CRITICAL) {
            queuedEffects.add(context)
            Logger.log("EFFECT_QUEUE", LogLevel.DEBUG,
                "Effect queued (position ${queuedEffects.size}): ${context.effect.effectId}")
        } else {
            _effects.emit(context)
        }
    }

    /**
     * Register an active effect
     */
    fun registerActiveEffect(effectId: String, job: Job) {
        activeEffects[effectId] = job
        Logger.log("EFFECT_QUEUE", LogLevel.DEBUG, "Registered active effect: $effectId")
    }

    /**
     * Unregister a completed effect
     */
    fun unregisterActiveEffect(effectId: String) {
        activeEffects.remove(effectId)
        Logger.log("EFFECT_QUEUE", LogLevel.DEBUG, "Unregistered active effect: $effectId")

        // Process next queued effect if any
        processQueuedEffects()
    }

    /**
     * Cancel an effect by ID
     */
    fun cancelEffect(effectId: String) {
        val job = activeEffects[effectId]
        if (job != null && !job.isCompleted) {
            job.cancel(CancellationException("Effect cancelled: $effectId"))
            Logger.log("EFFECT_QUEUE", LogLevel.INFO, "Cancelled effect: $effectId")
        }
    }

    /**
     * Cancel all effects for a module
     */
    fun cancelModuleEffects(moduleId: String) {
        val effectsToCancel = activeEffects.filterKeys { it.startsWith("$moduleId:") }
        effectsToCancel.forEach { (effectId, job) ->
            if (!job.isCompleted) {
                job.cancel(CancellationException("Module effects cancelled: $moduleId"))
                Logger.log("EFFECT_QUEUE", LogLevel.INFO, "Cancelled module effect: $effectId")
            }
        }
    }

    /**
     * Process queued effects
     */
    private fun processQueuedEffects() {
        if (queuedEffects.isNotEmpty() && activeEffects.values.none { !it.isCompleted && !it.isCancelled }) {
            val nextEffect = queuedEffects.removeAt(0)
            // Note: In a real implementation, you'd emit this to the flow
            Logger.log("EFFECT_QUEUE", LogLevel.DEBUG,
                "Processing queued effect: ${nextEffect.effect.effectId}")
        }
    }

    /**
     * Get queue status
     */
    fun getStatus(): EffectQueueStatus {
        return EffectQueueStatus(
            activeCount = activeEffects.size,
            queuedCount = queuedEffects.size,
            activeEffectIds = activeEffects.keys.toList()
        )
    }
}

/**
 * Effect queue status for monitoring
 */
data class EffectQueueStatus(
    val activeCount: Int,
    val queuedCount: Int,
    val activeEffectIds: List<String>
)
