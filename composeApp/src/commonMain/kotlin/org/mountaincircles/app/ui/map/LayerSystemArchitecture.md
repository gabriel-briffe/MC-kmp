# Advanced Layer Management System

## Overview

The new layer management system provides **index-based layer ordering** and **fine-grained click handling control**, replacing the previous module-based priority system. This enables mixing layers from different modules with precise control over rendering and interaction priorities.

## Key Components

### 1. LayerDescriptor
```kotlin
data class LayerDescriptor(
    val id: String,                    // Unique layer identifier
    val moduleId: String,              // Owning module
    val displayName: String,           // Human-readable name
    val renderPriority: Int,           // Z-order for rendering (lower = background)
    val isVisible: Boolean = true,     // Visibility control
    val isInteractive: Boolean = false,// Can handle clicks
    val layerType: LayerType,          // BASE, FEATURE, OVERLAY, INTERACTION
    val description: String? = null,   // Documentation
    val tags: Set<String> = emptySet() // Categorization tags
)
```

### 2. LayerManager
Centralized singleton that manages all map layers:
- **Layer Registration**: Register layers with priorities
- **Dynamic Ordering**: Change layer priorities at runtime
- **Click Handling**: Propagate clicks through layers by priority
- **Visibility Control**: Show/hide layers dynamically

### 3. LayerRegistrationHelper
Simplified API for modules to register layers:
```kotlin
// Simple registration
val layerId = LayerRegistrationHelper.registerLayer(
    moduleId = "geolocation",
    layerName = "marker",
    renderPriority = 180,
    isInteractive = true,
    composable = { LocationMarker() }
)

// Batch registration
LayerRegistrationHelper.registerModuleLayers("maps", listOf(
    LayerPresets.baseTerrain("maps", { TerrainComposable() }),
    LayerPresets.feature("roads", 120, composable = { RoadsComposable() })
))
```

## Priority System

### Standard Priority Ranges
```
0-99:    Base Layers (terrain, tiles, grid)
100-199:  Feature Layers (circles, wave data, airports)
200-299:  Overlay Layers (selection, highlights, UI)
300+:     Interaction Layers (click areas, controls)
```

### Click Priority vs Render Priority
- **Render Priority**: Lower numbers = background layers
- **Click Priority**: Higher numbers = handle clicks first

Example:
```kotlin
// Location marker: appears above features but handles clicks before overlays
LayerDescriptor(
    renderPriority = 180,  // Above feature layers (100-199)
   // Above interaction layers (300+)
    isInteractive = true
)
```

## Usage Examples

### 1. Basic Layer Registration
```kotlin
@Composable
fun MyModuleLayers(module: MyModule) {
    // Register layers when module initializes
    DisposableEffect(Unit) {
        val layerManager = LayerRegistrationHelper

        val terrainId = layerManager.registerLayer(
            moduleId = "maps",
            layerName = "terrain",
            renderPriority = LayerPresets.BASE_TERRAIN,
            composable = { TerrainLayer() }
        )

        onDispose {
            LayerRegistrationHelper.layerManager.unregisterLayer(terrainId)
        }
    }
}
```

### 2. Interactive Feature Layer
```kotlin
val circlesId = LayerRegistrationHelper.registerLayer(
    moduleId = "circles",
    layerName = "aviation_circles",
    renderPriority = LayerPresets.FEATURE_CIRCLES,
    isInteractive = true,
    composable = { AviationCirclesLayer() }
)

// Register click handler
LayerRegistrationHelper.registerClickHandler(circlesId) { event ->
    // Handle circle clicks
    val clickedCircle = findCircleAt(event.latitude, event.longitude)
    if (clickedCircle != null) {
        showCircleInfo(clickedCircle)
        return@registerClickHandler true
    }
    false
}
```

### 3. Dynamic Priority Updates
```kotlin
// Promote selection overlay to top
LayerRegistrationHelper.layerManager.updateLayerPriorities(
    layerId = "selection_overlay",
    renderPriority = 250,  // Move to top of overlays
)

// Hide layer temporarily
LayerRegistrationHelper.layerManager.setLayerVisibility("debug_layer", false)
```

## Migration Guide

### From Module-Based System
**Before:**
```kotlin
// Module-level priority only
override val layerPriority: Int = 2  // All module layers together
```

**After:**
```kotlin
// Individual layer control
val circlesId = LayerRegistrationHelper.registerLayer(
    moduleId = "circles",
    layerName = "main_circles",
    renderPriority = 110,
    isInteractive = true,
    composable = { CirclesComposable() }
)

val labelsId = LayerRegistrationHelper.registerLayer(
    moduleId = "circles",
    layerName = "circle_labels",
    renderPriority = 115,  // Above circles
    composable = { LabelsComposable() }
)
```

## Click Handling Architecture

### Priority-Based Propagation
1. Click occurs on map
2. LayerManager gets all interactive layers
3. Sorts by click priority (highest first)
4. Calls handlers in order until one returns `true`
5. If no handler consumes the click, event propagates

### Handler Signature
```kotlin
val handler: (MapClickEvent) -> Boolean

data class MapClickEvent(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val screenX: Float,
    val screenY: Float,
    val modifiers: Set<ClickModifier> // SHIFT, CTRL, ALT, LONG_PRESS
)
```

## Benefits

### 🎯 **Precise Control**
- Individual layers instead of module groups
- Fine-grained render and click priorities
- Dynamic priority adjustments

### 🔄 **Flexibility**
- Mix layers from different modules
- Runtime layer management
- Conditional layer visibility

### 📱 **Future-Proof**
- Ready for complex click interactions
- Supports multi-touch gestures
- Extensible for new layer types

### 🏗️ **Maintainable**
- Clear separation of concerns
- Centralized layer management
- Easy debugging and monitoring

## Integration with Main Map

Replace the current system:
```kotlin
// Old system
ModuleLayerComposables(globalState)

// New system
LayerManagerComposables(globalState)

// Hybrid (during migration)
HybridLayerComposables(globalState)
```

## Best Practices

1. **Use Preset Priorities**: Use `LayerPresets` constants for consistency
2. **Plan Layer Interactions**: Consider both render and click priorities together
3. **Tag Layers**: Use tags for filtering and debugging
4. **Handle Cleanup**: Unregister layers when modules are destroyed
5. **Monitor Performance**: Log layer statistics for optimization

This architecture provides the foundation for sophisticated map interactions while maintaining clean separation between rendering and user interaction logic.
