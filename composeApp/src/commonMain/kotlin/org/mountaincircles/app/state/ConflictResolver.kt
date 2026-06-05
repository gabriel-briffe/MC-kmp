package org.mountaincircles.app.state

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Conflict resolution strategy
 */
enum class ConflictResolutionStrategy {
    LATEST_WINS,      // Use the most recent state change
    OLDEST_WINS,      // Use the oldest state (preserve original)
    PRIORITY_BASED,   // Use predefined priority order
    MERGE,           // Merge conflicting states
    CUSTOM           // Use custom resolution logic
}

/**
 * Conflict resolution result
 */
sealed class ConflictResolutionResult {
    data class Resolved(val resolvedState: Any) : ConflictResolutionResult()
    data class Failed(val reason: String) : ConflictResolutionResult()
    data object Unresolved : ConflictResolutionResult()
}

/**
 * Conflict resolver interface
 */
interface ConflictResolver {
    /**
     * Resolve a conflict between two module states
     */
    fun resolveConflict(
        module1: String,
        module2: String,
        state1: Any,
        state2: Any
    ): ConflictResolutionResult

    /**
     * Check if this resolver can handle the given conflict
     */
    fun canResolve(module1: String, module2: String, state1: Any, state2: Any): Boolean

    /**
     * Get resolver name for debugging
     */
    val resolverName: String
        get() = this::class.simpleName ?: "UnknownResolver"
}

/**
 * Base conflict resolver with common functionality
 */
abstract class BaseConflictResolver : ConflictResolver {
    protected val resolverId: String = "resolver_${currentTimeMillis()}_${hashCode().toString(16)}"

    override fun toString(): String = "$resolverName ($resolverId)"
}

/**
 * Latest wins conflict resolver
 */
class LatestWinsConflictResolver : BaseConflictResolver() {

    override val resolverName: String = "LatestWinsConflictResolver"

    override fun resolveConflict(
        module1: String,
        module2: String,
        state1: Any,
        state2: Any
    ): ConflictResolutionResult {
        // In a real implementation, you'd compare timestamps
        // For now, default to module1's state
        Logger.log("CONFLICT_RESOLVER", LogLevel.INFO,
            "Resolving conflict ($module1 vs $module2): Using $module1 state (latest wins)")

        return ConflictResolutionResult.Resolved(state1)
    }

    override fun canResolve(module1: String, module2: String, state1: Any, state2: Any): Boolean {
        return true // Can resolve any conflict
    }
}

/**
 * Priority-based conflict resolver
 */
class PriorityBasedConflictResolver(
    private val modulePriorities: Map<String, Int>
) : BaseConflictResolver() {

    override val resolverName: String = "PriorityBasedConflictResolver"

    override fun resolveConflict(
        module1: String,
        module2: String,
        state1: Any,
        state2: Any
    ): ConflictResolutionResult {
        val priority1 = modulePriorities[module1] ?: 0
        val priority2 = modulePriorities[module2] ?: 0

        val winner = if (priority1 >= priority2) {
            Logger.log("CONFLICT_RESOLVER", LogLevel.INFO,
                "Resolving conflict ($module1 vs $module2): $module1 wins (priority $priority1 vs $priority2)")
            state1
        } else {
            Logger.log("CONFLICT_RESOLVER", LogLevel.INFO,
                "Resolving conflict ($module1 vs $module2): $module2 wins (priority $priority1 vs $priority2)")
            state2
        }

        return ConflictResolutionResult.Resolved(winner)
    }

    override fun canResolve(module1: String, module2: String, state1: Any, state2: Any): Boolean {
        return modulePriorities.containsKey(module1) && modulePriorities.containsKey(module2)
    }
}

/**
 * Merge-based conflict resolver
 */
class MergeConflictResolver(
    private val mergeStrategy: (Any, Any) -> Any
) : BaseConflictResolver() {

    override val resolverName: String = "MergeConflictResolver"

    override fun resolveConflict(
        module1: String,
        module2: String,
        state1: Any,
        state2: Any
    ): ConflictResolutionResult {
        return try {
            val mergedState = mergeStrategy(state1, state2)
            Logger.log("CONFLICT_RESOLVER", LogLevel.INFO,
                "Resolving conflict ($module1 vs $module2): Merged states successfully")
            ConflictResolutionResult.Resolved(mergedState)
        } catch (e: Exception) {
            Logger.log("CONFLICT_RESOLVER", LogLevel.ERROR,
                "Failed to merge states ($module1 vs $module2)", e)
            ConflictResolutionResult.Failed("Merge failed: ${e.message}")
        }
    }

    override fun canResolve(module1: String, module2: String, state1: Any, state2: Any): Boolean {
        return true // Can attempt to resolve any conflict
    }
}

/**
 * Custom conflict resolver with user-defined logic
 */
class CustomConflictResolver(
    private val resolutionFunction: (String, String, Any, Any) -> ConflictResolutionResult
) : BaseConflictResolver() {

    override val resolverName: String = "CustomConflictResolver"

    override fun resolveConflict(
        module1: String,
        module2: String,
        state1: Any,
        state2: Any
    ): ConflictResolutionResult {
        return try {
            resolutionFunction(module1, module2, state1, state2)
        } catch (e: Exception) {
            Logger.log("CONFLICT_RESOLVER", LogLevel.ERROR,
                "Custom conflict resolution failed", e)
            ConflictResolutionResult.Failed("Custom resolution failed: ${e.message}")
        }
    }

    override fun canResolve(module1: String, module2: String, state1: Any, state2: Any): Boolean {
        return true // Delegate to custom function
    }
}

/**
 * Conflict resolver registry
 */
class ConflictResolverRegistry {
    private val resolvers = mutableMapOf<String, ConflictResolver>()

    /**
     * Register a conflict resolver
     */
    fun register(key: String, resolver: ConflictResolver) {
        resolvers[key] = resolver
        Logger.log("CONFLICT_RESOLVER", LogLevel.INFO,
            "Registered conflict resolver: $key -> ${resolver.resolverName}")
    }

    /**
     * Get resolver by key
     */
    fun get(key: String): ConflictResolver? {
        return resolvers[key]
    }

    /**
     * Get resolver for module pair
     */
    fun getForModules(module1: String, module2: String): ConflictResolver? {
        // Try specific key first
        val specificKey = "$module1:$module2"
        resolvers[specificKey]?.let { return it }

        // Try reverse key
        val reverseKey = "$module2:$module1"
        resolvers[reverseKey]?.let { return it }

        // Try module-specific resolvers
        resolvers[module1]?.let { return it }
        resolvers[module2]?.let { return it }

        // Return default resolver if available
        return resolvers["default"]
    }

    /**
     * Remove resolver
     */
    fun remove(key: String) {
        resolvers.remove(key)
        Logger.log("CONFLICT_RESOLVER", LogLevel.INFO, "Removed conflict resolver: $key")
    }

    /**
     * Get all resolvers
     */
    fun getAllResolvers(): Map<String, ConflictResolver> = resolvers.toMap()

    /**
     * Clear all resolvers
     */
    fun clear() {
        resolvers.clear()
        Logger.log("CONFLICT_RESOLVER", LogLevel.INFO, "Cleared all conflict resolvers")
    }
}

/**
 * Global conflict resolver registry instance
 */
val globalConflictResolverRegistry = ConflictResolverRegistry()

/**
 * Conflict resolver factory for creating common resolvers
 */
object ConflictResolverFactory {

    /**
     * Create latest wins resolver
     */
    fun createLatestWinsResolver(): LatestWinsConflictResolver {
        return LatestWinsConflictResolver()
    }

    /**
     * Create priority-based resolver
     */
    fun createPriorityBasedResolver(priorities: Map<String, Int>): PriorityBasedConflictResolver {
        return PriorityBasedConflictResolver(priorities)
    }

    /**
     * Create merge resolver
     */
    fun createMergeResolver(mergeFunction: (Any, Any) -> Any): MergeConflictResolver {
        return MergeConflictResolver(mergeFunction)
    }

    /**
     * Create custom resolver
     */
    fun createCustomResolver(
        resolutionFunction: (String, String, Any, Any) -> ConflictResolutionResult
    ): CustomConflictResolver {
        return CustomConflictResolver(resolutionFunction)
    }

    /**
     * Create circles-specific resolver
     */
    fun createCirclesResolver(): CustomConflictResolver {
        return createCustomResolver { module1, module2, state1, state2 ->
            // Circles-specific conflict resolution logic
            // For example, merge active configurations intelligently
            when {
                module1 == "circles" && module2 == "maps" -> {
                    // Maps module should not override circles active config
                    ConflictResolutionResult.Resolved(state1)
                }
                module1 == "maps" && module2 == "circles" -> {
                    // Same logic, different order
                    ConflictResolutionResult.Resolved(state2)
                }
                else -> {
                    // Default to latest wins
                    ConflictResolutionResult.Resolved(state1)
                }
            }
        }
    }

    /**
     * Create wave-specific resolver
     */
    fun createWaveResolver(): CustomConflictResolver {
        return createCustomResolver { module1, module2, state1, state2 ->
            // Wave-specific conflict resolution
            // For example, merge wave forecasts intelligently
            when {
                module1 == "wave" && module2 == "circles" -> {
                    // Wave data should not interfere with circles display
                    ConflictResolutionResult.Resolved(state1)
                }
                else -> {
                    ConflictResolutionResult.Resolved(state1)
                }
            }
        }
    }
}

/**
 * Initialize default conflict resolvers
 */
fun initializeDefaultConflictResolvers() {
    val registry = globalConflictResolverRegistry

    // Register default resolver
    registry.register("default", ConflictResolverFactory.createLatestWinsResolver())

    // Register module-specific resolvers
    registry.register("circles", ConflictResolverFactory.createCirclesResolver())
    registry.register("wave", ConflictResolverFactory.createWaveResolver())

    // Register priority-based resolver for critical modules
    val priorities = mapOf(
        "circles" to 10,
        "wave" to 8,
        "geolocation" to 5,
        "maps" to 3
    )
    registry.register("priority", ConflictResolverFactory.createPriorityBasedResolver(priorities))

    Logger.log("CONFLICT_RESOLVER", LogLevel.INFO, "Initialized default conflict resolvers")
}
