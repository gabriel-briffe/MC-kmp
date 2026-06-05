package org.mountaincircles.app.modules.airspace.overlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData
import org.mountaincircles.app.modules.airspace.overlay.data.AirspaceLayoutConfig
import org.mountaincircles.app.modules.airspace.overlay.ui.AirspaceCrossSectionView
import org.mountaincircles.app.ui.overlay.OverlayProvider
import org.mountaincircles.app.ui.overlay.OverlayPosition
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceLayerDisplayData
import org.mountaincircles.app.ui.overlay.SwipeablePopup
import org.mountaincircles.app.ui.overlay.SwipeablePopupConfig
import org.mountaincircles.app.ui.overlay.SwipeablePopupContent
import org.mountaincircles.app.state.PopupId
import org.mountaincircles.app.state.GlobalState

/**
 * Airspace module popup overlay provider
 * Shows airspace information when airspace popup is triggered by clicking
 * Popup can be dismissed by swiping left on the popup content
 */
class AirspacePopupOverlay : OverlayProvider {
    override val moduleId = "airspace"
    override val priority = 20  // Higher priority than circles (10) to appear above
    override val position = OverlayPosition.BOTTOM_CENTER

    @Composable
    override fun OverlayContent(module: ModuleBase, globalState: GlobalState) {
        Logger.log("RECOMPOSITION_POPUP", LogLevel.DEBUG, "AirspacePopupOverlay.OverlayContent recomposing")
        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "🎯 AirspacePopupOverlay.OverlayContent called")

        val airspaceModule = module as AirspaceModule

        // ✅ Observe centralized popup state (exclusive like submenus)
        val activePopup by globalState.navigationState.popupVisible.collectAsState()

        // Check if this airspace popup should be shown
        val showPopup = activePopup?.moduleId == moduleId && activePopup?.dataId != null

        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "   activePopup: ${activePopup?.moduleId}:${activePopup?.dataId}")
        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "   showPopup: $showPopup")

        if (showPopup) {
            val featureId = activePopup?.dataId
            Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🎯 Showing airspace popup overlay for feature: $featureId")

            // Get airspace data from module state (this will be reactive)
            val airspaceState by airspaceModule.airspaceState.collectAsState()
            val popupFeatures = airspaceState.popupFeatures

            if (popupFeatures.isNotEmpty()) {
                // Log that we're about to render the cross-section
                Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "🎯 About to render AirspacePopupContent with ${popupFeatures.size} features")

                AirspacePopupContent(
                    features = popupFeatures,
                    onDismiss = {
                        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🔴 OVERLAY onDismiss called - optimized cleanup sequence")
                        // Clear highlight when popup is dismissed
                        airspaceModule.getLayerManager()?.clearHighlight()
                        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "Highlight cleared, now calling hideAirspacePopup (marker will hide first)")
                        globalState.navigationState.closePopup()
                        airspaceModule.hideAirspacePopup() // Also update module state
                        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "✅ OVERLAY onDismiss completed - marker hidden, popup closed, layers readjusted")
                    },
                    onHighlightUpdate = { aiField ->
                        airspaceModule.getLayerManager()?.updateHighlight(aiField)
                    }
                )
            } else {
                Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "🎯 Airspace popup not shown - no features available")
            }
        } else {
            Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "🎯 Airspace popup not shown - not active popup")
        }
    }
}

/**
 * Airspace popup content component
 * Displays airspace information in a scrollable list similar to android native
 */
@Composable
private fun AirspacePopupContent(
    features: List<AirspaceFeatureData>,
    onDismiss: () -> Unit,
    onHighlightUpdate: (String?) -> Unit
) {
    Logger.log("RECOMPOSITION_POPUP", LogLevel.DEBUG, "AirspacePopupContent recomposing")
    Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "   Building airspace popup with ${features.size} features")

    // Use generic swipeable popup component
    val config = SwipeablePopupConfig(
        heightRatio = AirspaceLayoutConfig.popupHeightRatio,
        swipeThresholdDp = AirspaceLayoutConfig.popupSwipeThresholdDp.toInt(),
        containerColor = androidx.compose.ui.graphics.Color.Black,
        containerAlpha = 0.9f,
        logTag = "AIRSPACE_POPUP"
    )

    SwipeablePopup(config = config, onDismiss = onDismiss) {
        PopupContent(features, onHighlightUpdate)
    }
}

@Composable
private fun PopupContent(
    features: List<AirspaceFeatureData>,
    onHighlightUpdate: (String?) -> Unit
) {
    Logger.log("RECOMPOSITION_POPUP", LogLevel.DEBUG, "PopupContent recomposing")
    // Track currently selected feature ID (AI) for card styling and cross-section dimming
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Track whether the last selection came from a bar click (for autoscroll)
    var lastSelectionFromBar by remember { mutableStateOf(false) }

    // Clear highlight whenever features change (popup opens/reopens) but only if current highlight is not in new features
    LaunchedEffect(features) {
        if (selectedId != null) {
            // Check if the currently selected feature is in the new features list
            val selectedFeatureInNewList = features.any { feature ->
                val featureId = feature.id.ifBlank {
                    feature.allProperties["AI"] ?: feature.allProperties["id"] ?: "N/A"
                }
                featureId == selectedId
            }

            if (!selectedFeatureInNewList) {
                Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "🔄 Clearing highlight as selected feature is not in new features list")
                selectedId = null
                lastSelectionFromBar = false
                onHighlightUpdate(null)
            } else {
                Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "✅ Keeping highlight as selected feature is still in new features list")
            }
        }
    }

    // Lazy list state for autoscroll functionality
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Simple placeholder for debugging
    SwipeablePopupContent {
            // Horizontal layout: Cross-section on left, list on right (matching Android native)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Cross-section view on the left (full height, dynamic width matching Android native wrap_content)
                if (features.isNotEmpty()) {
                    // Compute selected index for cross-section highlighting
                    val selectedIndex = selectedId?.let { id ->
                        val idx = features.indexOfFirst { it.id == id }
                        if (idx >= 0) idx else null
                    }
                    AirspaceCrossSectionView(
                        features = features,
                        selectedIndex = selectedIndex,
                        onBarClick = { idx ->
                            val feature = features.getOrNull(idx)
                            if (feature != null) {
                                val aiProperty = feature.id.ifBlank { feature.allProperties["AI"] ?: feature.allProperties["id"] ?: "N/A" }
                                if (selectedId == aiProperty) {
                                    selectedId = null
                                    lastSelectionFromBar = false
                                    onHighlightUpdate(null)
                                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.INFO, "🔘 Highlight cleared (re-click on bar)")
                                } else {
                                    selectedId = aiProperty
                                    lastSelectionFromBar = true // Mark as bar click for autoscroll
                                    onHighlightUpdate(aiProperty)
                                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.INFO, "🔵 Highlight updated from bar click for AI: '$aiProperty'")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                        .padding(end = AirspaceLayoutConfig.crossSectionRightMarginDp.dp)
                    )
                }

                // Autoscroll to selected item when selectedId changes (only from bar clicks)
                LaunchedEffect(selectedId) {
                    if (lastSelectionFromBar && selectedId != null) {
                        val targetId = selectedId!!
                        val targetIndex = features.indexOfFirst { feature ->
                            val aiProperty = feature.id.ifBlank {
                                feature.allProperties["AI"] ?: feature.allProperties["id"] ?: "N/A"
                            }
                            aiProperty == targetId
                        }

                        if (targetIndex >= 0) {
                            Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "📍 Autoscrolling to feature at index $targetIndex (ID: $targetId) - triggered by bar click")
                            coroutineScope.launch {
                                // Calculate offset to center the item in the viewport
                                val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                                val centerOffset = -(viewportHeight / 2)

                                lazyListState.animateScrollToItem(
                                    index = targetIndex,
                                    scrollOffset = centerOffset
                                )
                            }
                        }
                    }
                }

                // Scrollable list of airspace features on the right
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    itemsIndexed(features) { index, feature ->
                        AirspaceFeatureItem(
                            feature = feature,
                            index = index + 1,
                            isSelected = feature.id == selectedId,
                            onClick = {
                                val aiProperty = feature.id.ifBlank { feature.allProperties["AI"] ?: feature.allProperties["id"] ?: "N/A" }
                                Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🆔 Card clicked - AI: '$aiProperty', Name: '${feature.name}', Type: '${feature.type}'")
                                Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "📊 All Properties: ${feature.allProperties.entries.joinToString(", ") { "${it.key}=${it.value}" }}")

                                // Toggle highlight: select if different, clear if same
                                if (selectedId == aiProperty) {
                                    selectedId = null
                                    lastSelectionFromBar = false
                                    onHighlightUpdate(null)
                                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.INFO, "🔘 Highlight cleared (re-click on selected card)")
                                } else {
                                    selectedId = aiProperty
                                    lastSelectionFromBar = false // Mark as card click (no autoscroll)
                                    onHighlightUpdate(aiProperty)
                                    Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.INFO, "🔵 Highlight updated for AI: '$aiProperty'")
                                }
                            },
                            onHighlightUpdate = onHighlightUpdate
                        )

                        if (index < features.size - 1) {
                        Spacer(modifier = Modifier.height(AirspaceLayoutConfig.popupFeaturesCardSpacingDp.dp))
                    }
                }
            }
        }
    }
}

/**
 * Individual airspace feature item in the popup
 */
@Composable
private fun AirspaceFeatureItem(
    feature: AirspaceFeatureData,
    index: Int,
    onClick: () -> Unit,
    isSelected: Boolean,
    onHighlightUpdate: (String?) -> Unit
) {
    Logger.log("RECOMPOSITION_POPUP", LogLevel.DEBUG, "AirspaceFeatureItem recomposing - feature: ${feature.name}, isSelected: $isSelected")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.border(
                AirspaceLayoutConfig.popupFeaturesBorderThicknessDp.dp, AirspaceLayoutConfig.popupFeaturesBorderColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    AirspaceLayoutConfig.popupBorderRadiusDp.dp)) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = AirspaceLayoutConfig.popupFeaturesCardBackgroundColor.copy(alpha = AirspaceLayoutConfig.popupFeaturesCardBackgroundAlpha)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AirspaceLayoutConfig.popupFeaturesCardPaddingHorizontalDp.dp,
                    vertical = AirspaceLayoutConfig.popupFeaturesCardPaddingVerticalDp.dp
                )
        ) {
            // Name only (no number prefix)
            Text(
                text = feature.name,
                color = AirspaceLayoutConfig.textColor,
                fontSize = AirspaceLayoutConfig.popupFeaturesNameSizeSp.sp,
                fontWeight = AirspaceLayoutConfig.popupFeaturesNameWeight,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(0.dp))

            // Table-like layout: Limits stacked vertically on left, type badge vertically centered on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Upper and Lower limits stacked vertically
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = feature.upperLimit,
                        color = AirspaceLayoutConfig.popupFeaturesLimitColor,
                        fontSize = AirspaceLayoutConfig.popupFeaturesLimitSizeSp.sp
                    )

                    Text(
                        text = feature.lowerLimit,
                        color = AirspaceLayoutConfig.popupFeaturesLimitColor,
                        fontSize = AirspaceLayoutConfig.popupFeaturesLimitSizeSp.sp
                    )
                }

                // Right column: Type badge vertically centered across both limit lines
                Surface(
                    color = AirspaceColors.getAirspaceTypeColor(feature.type),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        AirspaceLayoutConfig.popupFeaturesBadgeCornerRadiusDp.dp)
                ) {
                    Text(
                        text = feature.type,
                        color = AirspaceLayoutConfig.popupFeaturesBadgeTextColor,
                        fontSize = AirspaceLayoutConfig.popupFeaturesTypeSizeSp.sp,
                        fontWeight = AirspaceLayoutConfig.popupFeaturesTypeWeight,
                        modifier = Modifier.padding(
                            horizontal = AirspaceLayoutConfig.popupFeaturesBadgeHorizontalPaddingDp.dp,
                            vertical = AirspaceLayoutConfig.popupFeaturesBadgeVerticalPaddingDp.dp
                        )
                    )
                }
            }

            // Frequency (if present)
            if (feature.frequency.isNotBlank()) {
                Spacer(modifier = Modifier.height(AirspaceLayoutConfig.popupFeaturesLowerToFrequencyDp.dp))
                Text(
                    text = feature.frequency,
                    color = AirspaceLayoutConfig.popupFeaturesFrequencyColor,
                    fontSize = AirspaceLayoutConfig.popupFeaturesFrequencySizeSp.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


