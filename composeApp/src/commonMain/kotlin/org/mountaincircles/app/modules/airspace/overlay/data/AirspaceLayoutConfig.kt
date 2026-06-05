package org.mountaincircles.app.modules.airspace.overlay.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors

/**
 * Flattened layout configuration for airspace overlay components.
 * Eliminated nested object hierarchies for simpler, direct access.
 */
object AirspaceLayoutConfig {

    // ============================================================================
    // CROSS-SECTION LAYOUT (Flattened)
    // ============================================================================
    const val crossSectionBarWidthPx = 44f
    const val crossSectionBarSpacingPx = 12f
    const val crossSectionFontSizePx = 12f
    const val crossSectionLabelPaddingPx = 8f
    val crossSectionLabelHeightPx = crossSectionFontSizePx + (crossSectionLabelPaddingPx * 2)
    const val crossSectionStandardLabelHeightPx = 56f
    const val crossSectionRightMarginDp = 12f

    // ============================================================================
    // POPUP OVERLAY LAYOUT (Flattened)
    // ============================================================================
    const val popupHeightRatio = 0.5f
    const val popupBorderRadiusDp = 8f
    const val popupContainerAlpha = 0.9f
    val popupContainerColor = Color.Black

    // Content padding (flattened from nested object)
    const val popupPaddingStartDp = 8f
    const val popupPaddingEndDp = 8f
    const val popupPaddingTopDp = 4f
    const val popupPaddingBottomDp = 16f

    // Swipe interaction (flattened from nested object)
    const val popupSwipeThresholdDp = 120f
    const val popupSwipeHintBottomMarginDp = 4f
    const val popupSwipeHintEndMarginDp = 8f
    const val popupSwipeHintAlpha = 0.7f
    val popupSwipeHintColor = AirspaceColors.UI.SWIPE_HINT

    // Feature list layout (flattened from nested objects)
    const val popupFeaturesCardPaddingVerticalDp = 4f
    const val popupFeaturesCardPaddingHorizontalDp = 8f
    const val popupFeaturesCardSpacingDp = 4f
    const val popupFeaturesBorderThicknessDp = 2f
    val popupFeaturesBorderColor = AirspaceColors.UI.SELECTION_BORDER
    const val popupFeaturesCardBackgroundAlpha = 0.1f
    val popupFeaturesCardBackgroundColor = AirspaceColors.UI.CARD_BACKGROUND

    // Typography (flattened from nested object)
    const val popupFeaturesNameSizeSp = 16f
    val popupFeaturesNameWeight = FontWeight.Bold
    const val popupFeaturesLimitSizeSp = 14f
    val popupFeaturesLimitColor = AirspaceColors.UI.LIMIT
    const val popupFeaturesFrequencySizeSp = 14f
    val popupFeaturesFrequencyColor = AirspaceColors.UI.FREQUENCY
    const val popupFeaturesTypeSizeSp = 16f
    val popupFeaturesTypeWeight = FontWeight.Bold

    // Vertical spacing within feature cards (flattened from nested object)
    const val popupFeaturesNameToUpperDp = 0f
    const val popupFeaturesLowerToFrequencyDp = 0f

    // Type badge appearance (flattened from nested object)
    const val popupFeaturesBadgeCornerRadiusDp = 4f
    const val popupFeaturesBadgeHorizontalPaddingDp = 8f
    const val popupFeaturesBadgeVerticalPaddingDp = 2f
    val popupFeaturesBadgeTextColor = AirspaceColors.UI.TEXT_COLOR

    // ============================================================================
    // SHARED COLORS AND STYLES (Flattened)
    // ============================================================================
    val textColor = AirspaceColors.UI.TEXT_COLOR
    const val selectedBarAlpha = 0.5f
}
