package org.mountaincircles.app.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * State projection interface
 */
interface StateProjection<TSource, TTarget> {
    /**
     * Project source state to target state
     */
    fun project(sourceState: TSource): TTarget

    /**
     * Check if projection is applicable for the given state
     */
    fun canProject(state: Any): Boolean

    /**
     * Get projection name for debugging
     */
    val projectionName: String
        get() = this::class.simpleName ?: "UnknownProjection"
}

/**
 * Base state projection with common functionality
 */
abstract class BaseStateProjection<TSource, TTarget> : StateProjection<TSource, TTarget> {
    protected val projectionId: String = "projection_${currentTimeMillis()}_${hashCode().toString(16)}"

    override fun toString(): String = "$projectionName ($projectionId)"
}

/**
 * Module state projection
 */
class ModuleStateProjection(
    private val sourceModule: String,
    private val targetModule: String,
    private val projectionFunction: (Any) -> Any
) : BaseStateProjection<Any, Any>() {

    override val projectionName: String = "ModuleStateProjection"

    override fun project(sourceState: Any): Any {
        return try {
            projectionFunction(sourceState)
        } catch (e: Exception) {
            Logger.log("STATE_PROJECTION", LogLevel.ERROR,
                "Projection failed: $sourceModule -> $targetModule", e)
            sourceState // Return original state on error
        }
    }

    override fun canProject(state: Any): Boolean {
        return true // Generic projection accepts any state
    }
}

/**
 * Typed state projection with type safety
 */
class TypedStateProjection<TSource : Any, TTarget : Any>(
    private val sourceType: kotlin.reflect.KClass<TSource>,
    private val targetType: kotlin.reflect.KClass<TTarget>,
    private val projectionFunction: (TSource) -> TTarget
) : BaseStateProjection<TSource, TTarget>() {

    override val projectionName: String = "TypedStateProjection"

    override fun project(sourceState: TSource): TTarget {
        return projectionFunction(sourceState)
    }

    override fun canProject(state: Any): Boolean {
        return sourceType.isInstance(state)
    }
}

/**
 * Composite state projection that combines multiple projections
 */
class CompositeStateProjection<TSource, TTarget>(
    private val projections: List<StateProjection<*, *>>
) : BaseStateProjection<TSource, TTarget>() {

    override val projectionName: String = "CompositeStateProjection"

    override fun project(sourceState: TSource): TTarget {
        var currentState: Any = sourceState as Any

        for (projection in projections) {
            if (projection.canProject(currentState)) {
                @Suppress("UNCHECKED_CAST")
                currentState = (projection as StateProjection<Any, Any>).project(currentState)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return currentState as TTarget
    }

    override fun canProject(state: Any): Boolean {
        return projections.any { it.canProject(state) }
    }
}

/**
 * Conditional state projection that only applies when condition is met
 */
class ConditionalStateProjection<TSource, TTarget>(
    private val condition: (TSource) -> Boolean,
    private val projection: StateProjection<TSource, TTarget>
) : BaseStateProjection<TSource, TTarget>() {

    override val projectionName: String = "ConditionalStateProjection"

    override fun project(sourceState: TSource): TTarget {
        return projection.project(sourceState)
    }

    override fun canProject(state: Any): Boolean {
        return projection.canProject(state) && condition(state as TSource)
    }
}

/**
 * State projection registry
 */
class StateProjectionRegistry {
    private val projections = mutableMapOf<String, StateProjection<*, *>>()

    /**
     * Register a projection
     */
    fun <TSource, TTarget> register(
        key: String,
        projection: StateProjection<TSource, TTarget>
    ) {
        projections[key] = projection
        Logger.log("STATE_PROJECTION", LogLevel.INFO,
            "Registered projection: $key -> ${projection.projectionName}")
    }

    /**
     * Get a projection by key
     */
    @Suppress("UNCHECKED_CAST")
    fun <TSource, TTarget> get(key: String): StateProjection<TSource, TTarget>? {
        return projections[key] as? StateProjection<TSource, TTarget>
    }

    /**
     * Remove a projection
     */
    fun remove(key: String) {
        projections.remove(key)
        Logger.log("STATE_PROJECTION", LogLevel.INFO, "Removed projection: $key")
    }

    /**
     * Get all registered projections
     */
    fun getAllProjections(): Map<String, StateProjection<*, *>> = projections.toMap()

    /**
     * Clear all projections
     */
    fun clear() {
        projections.clear()
        Logger.log("STATE_PROJECTION", LogLevel.INFO, "Cleared all projections")
    }
}

/**
 * Global state projection registry instance
 */
val globalStateProjectionRegistry = StateProjectionRegistry()

/**
 * State projection factory for creating common projections
 */
object StateProjectionFactory {

    /**
     * Create a property mapping projection
     */
    fun createPropertyMappingProjection(
        sourceModule: String,
        targetModule: String,
        propertyMappings: Map<String, String>
    ): ModuleStateProjection {
        return ModuleStateProjection(sourceModule, targetModule) { sourceState ->
            // This is a simplified example - in practice you'd need reflection
            // or a more sophisticated mapping system
            Logger.log("STATE_PROJECTION", LogLevel.DEBUG,
                "Mapping properties for $sourceModule -> $targetModule: $propertyMappings")
            sourceState // Return as-is for now
        }
    }

    /**
     * Create a filter projection that only passes certain state properties
     */
    fun createFilterProjection(
        sourceModule: String,
        targetModule: String,
        allowedProperties: Set<String>
    ): ModuleStateProjection {
        return ModuleStateProjection(sourceModule, targetModule) { sourceState ->
            // Filter state to only include allowed properties
            Logger.log("STATE_PROJECTION", LogLevel.DEBUG,
                "Filtering state for $sourceModule -> $targetModule, allowed: $allowedProperties")
            sourceState
        }
    }

    /**
     * Create a transform projection that transforms state values
     */
    fun createTransformProjection(
        sourceModule: String,
        targetModule: String,
        transform: (Any) -> Any
    ): ModuleStateProjection {
        return ModuleStateProjection(sourceModule, targetModule, transform)
    }

    /**
     * Create a conditional projection
     */
    fun <TSource, TTarget> createConditionalProjection(
        condition: (TSource) -> Boolean,
        projection: StateProjection<TSource, TTarget>
    ): ConditionalStateProjection<TSource, TTarget> {
        return ConditionalStateProjection(condition, projection)
    }

    /**
     * Create a composite projection
     */
    fun <TSource, TTarget> createCompositeProjection(
        projections: List<StateProjection<*, *>>
    ): CompositeStateProjection<TSource, TTarget> {
        return CompositeStateProjection(projections)
    }
}

/**
 * State flow projection extension functions
 */
object StateFlowProjections {

    /**
     * Project a state flow using a projection
     */
    fun Flow<Any>.project(
        projection: StateProjection<*, *>
    ): Flow<Any> {
        return this.map { state ->
            if (projection.canProject(state)) {
                @Suppress("UNCHECKED_CAST")
                (projection as StateProjection<Any, Any>).project(state)
            } else {
                // Return state as-is if projection not applicable
                state
            }
        }
    }

    /**
     * Project state flow with error handling
     */
    fun Flow<Any>.projectSafely(
        projection: StateProjection<*, *>,
        fallback: (Exception) -> Any
    ): Flow<Any> {
        return this.map { state ->
            try {
                if (projection.canProject(state)) {
                    @Suppress("UNCHECKED_CAST")
                    (projection as StateProjection<Any, Any>).project(state)
                } else {
                    fallback(IllegalStateException("Projection not applicable"))
                }
            } catch (e: Exception) {
                Logger.log("STATE_PROJECTION", LogLevel.ERROR,
                    "Projection error in ${projection.projectionName}", e)
                fallback(e)
            }
        }
    }
}
