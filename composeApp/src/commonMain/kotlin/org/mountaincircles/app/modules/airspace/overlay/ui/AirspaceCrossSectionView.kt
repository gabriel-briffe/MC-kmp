package org.mountaincircles.app.modules.airspace.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData
import org.mountaincircles.app.modules.airspace.overlay.data.AirspaceLayoutConfig

/**
 * Cross-section view data class representing a single airspace bar
 */
data class CrossSectionBar(
    val lowerLimit: Double,
    val upperLimit: Double,
    val color: Color,
    val name: String,
    val type: String,
    val lowerDisplay: String,
    val upperDisplay: String,
    val index: Int
)



/**
 * Cross-section view composable with variable-height altitude bands
 * Labels centered across all columns, bars can cross intermediate label spaces
 */
@Composable
fun AirspaceCrossSectionView(
    features: List<AirspaceFeatureData>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onBarClick: ((Int) -> Unit)? = null
) {
    Logger.log("RECOMPOSITION_POPUP", LogLevel.DEBUG, "AirspaceCrossSectionView recomposing - features: ${features.size}, selectedIndex: $selectedIndex")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 AirspaceCrossSectionView COMPOSABLE CALLED with ${features.size} features")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 Modifier: $modifier")

    if (features.isEmpty()) {
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 No features to display, returning empty box")
        Box(modifier = modifier) // Empty box to maintain layout
        return
    }

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.INFO, "🎨 Rendering cross-section for ${features.size} features")

    // Log detailed feature properties
    features.forEachIndexed { index, feature ->
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 Feature ${index + 1}/${features.size}:")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   ID: ${feature.id}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Name: ${feature.name}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Type: ${feature.type}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Upper Limit: ${feature.upperLimit}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Lower Limit: ${feature.lowerLimit}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Frequency: ${feature.frequency}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   All Properties: ${feature.allProperties}")
    }

    // Convert features to cross-section bars
    val bars = features.mapIndexed { index, feature ->
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "Processing bar ${index}: ${feature.name}, upper=${feature.upperLimit}, lower=${feature.lowerLimit}")

        // Use pre-converted altitude values from properties
        val lowerMeters = feature.allProperties["lowerLimitMeters"]?.toDoubleOrNull() ?: 0.0
        val upperMeters = feature.allProperties["upperLimitMeters"]?.toDoubleOrNull() ?: 10000.0
        val color = AirspaceColors.getAirspaceTypeColor(feature.type)

        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "🎯 Bar ${index} altitude from properties:")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "   Raw: upper='${feature.upperLimit}', lower='${feature.lowerLimit}'")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "   Properties: upperMeters=${feature.allProperties["upperLimitMeters"]}, lowerMeters=${feature.allProperties["lowerLimitMeters"]}")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "   Using: upper=${upperMeters}m, lower=${lowerMeters}m")
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "   Color: $color")

        CrossSectionBar(
            lowerLimit = lowerMeters,
            upperLimit = upperMeters,
            color = color,
            name = feature.name,
            type = feature.type,
            lowerDisplay = feature.lowerLimit,
            upperDisplay = feature.upperLimit,
            index = index
        )
    }

    // Collect all unique altitudes from bars and create display text mapping
    val allAltitudes = mutableSetOf<Double>()
    val altitudeToDisplayText = mutableMapOf<Double, String>()

    bars.forEach { bar ->
        allAltitudes.add(bar.lowerLimit)
        allAltitudes.add(bar.upperLimit)

        // Map meter values to their display text
        altitudeToDisplayText[bar.lowerLimit] = bar.lowerDisplay
        altitudeToDisplayText[bar.upperLimit] = bar.upperDisplay
    }

    // Sort altitudes in descending order (highest to lowest)
    val sortedAltitudes = allAltitudes.sortedDescending()

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
        "Found ${sortedAltitudes.size} unique altitudes: ${sortedAltitudes.map { "${it.toInt()}m -> '${altitudeToDisplayText[it]}'" }}")

    // Implement column packing algorithm (matching Android native)
    val barsByColumn = packBarsIntoColumns(bars)

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "Packed ${bars.size} bars into ${barsByColumn.size} columns")

    // Calculate required width based on number of columns
    val barWidthPx = 44f
    val barSpacingPx = 8f
    val leftMarginPx = 0f
    val rightMarginPx = 0f

    // Calculate dynamic width based on content
    val contentWidthPx = if (barsByColumn.isNotEmpty()) {
        leftMarginPx + barSpacingPx + (barsByColumn.size * (barWidthPx + barSpacingPx)) - barSpacingPx + rightMarginPx
    } else {
        44f  // Width of single bar when no bars
    }

    // Minimum width to accommodate labels like "6500ft MSL"
    // Estimate: ~220px needed for typical altitude labels with padding
    val minWidthPx = 220f

    // Use maximum of content width and minimum width
    val requiredWidthPx = maxOf(contentWidthPx, minWidthPx)

    val requiredWidthDp = (requiredWidthPx / androidx.compose.ui.platform.LocalDensity.current.density).dp

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
        "Width calculation: content=${contentWidthPx}px, min=${minWidthPx}px, final=${requiredWidthPx}px (${requiredWidthDp.value}dp)")

    // State for canvas size and interactions
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var barHitRects by remember { mutableStateOf(listOf<Pair<Int, Rect>>()) }

    // State for altitude maps - calculated in LaunchedEffect
    var labelsMap by remember { mutableStateOf<Map<Double, Float>>(emptyMap()) }
    var topAltitudesMap by remember { mutableStateOf<Map<Double, Float>>(emptyMap()) }
    var bottomAltitudesMap by remember { mutableStateOf<Map<Double, Float>>(emptyMap()) }

    // Text measurer for drawing text
    val textMeasurer = rememberTextMeasurer()

    // Calculate altitude maps and hit rectangles whenever size or data changes
    LaunchedEffect(canvasSize, barsByColumn, sortedAltitudes) {
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        if (width <= 0f || height <= 0f || barsByColumn.isEmpty() || sortedAltitudes.size <= 1) {
            barHitRects = emptyList()
            labelsMap = emptyMap()
            topAltitudesMap = emptyMap()
            bottomAltitudesMap = emptyMap()
        } else {
            // Calculate altitude maps now that we have valid canvas dimensions
            val minAltitude = sortedAltitudes.last()
            val maxAltitude = sortedAltitudes.first()
            val altitudeRange = maxAltitude - minAltitude

            val (newLabelsMap, newTopAltitudesMap, newBottomAltitudesMap) = createAltitudeMaps(
                sortedAltitudes, height, altitudeRange
            )

            // Update state variables
            labelsMap = newLabelsMap
            topAltitudesMap = newTopAltitudesMap
            bottomAltitudesMap = newBottomAltitudesMap

            // Calculate hit rectangles using the new altitude maps
            val barWidthPx = AirspaceLayoutConfig.crossSectionBarWidthPx
            val barSpacingPx = AirspaceLayoutConfig.crossSectionBarSpacingPx

            val totalColumnsWidth = barsByColumn.size * (barWidthPx + barSpacingPx) - (if (barsByColumn.isNotEmpty()) barSpacingPx else 0f)
            val startX = barSpacingPx + (width - (barSpacingPx + totalColumnsWidth + barSpacingPx)) / 2f

            val rects = mutableListOf<Pair<Int, Rect>>()
            var x = startX
            barsByColumn.forEach { column ->
                column.forEach { bar ->
                    // Use the newly calculated altitude maps
                    val yTop = newTopAltitudesMap[bar.upperLimit] ?: 0f
                    val yBottom = newBottomAltitudesMap[bar.lowerLimit] ?: height.toFloat()
                    val barLength = yBottom - yTop
                    if (barLength > 0 && yTop >= 0 && yBottom <= height) {
                        rects.add(bar.index to Rect(x, yTop, x + barWidthPx, yBottom))
                    }
                }
                x += barWidthPx + barSpacingPx
            }

            barHitRects = rects
        }
    }

    Canvas(
        modifier = modifier
            .width(requiredWidthDp)
            .fillMaxHeight()
            .onSizeChanged { canvasSize = it }
            .pointerInput(barHitRects) {
                detectTapGestures { offset ->
                    // Hit test bars; prefer the first match (leftmost column order)
                    val hit = barHitRects.firstOrNull { (_, rect) ->
                        offset.x >= rect.left && offset.x <= rect.right && offset.y >= rect.top && offset.y <= rect.bottom
                    }
                    hit?.let { (barIndex, _) ->
                        onBarClick?.invoke(barIndex)
                    }
                }
            }
    ) {

        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 Canvas ready: ${canvasSize.width}x${canvasSize.height}")

        // Only draw if we have valid altitude maps
        if (barsByColumn.isNotEmpty() && sortedAltitudes.size > 1 && labelsMap.isNotEmpty()) {
            Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 Drawing continuous scale cross-section...")
            drawContinuousScaleCrossSection(
                barsByColumn = barsByColumn,
                sortedAltitudes = sortedAltitudes,
                altitudeToDisplayText = altitudeToDisplayText,
                labelsMap = labelsMap,
                topAltitudesMap = topAltitudesMap,
                bottomAltitudesMap = bottomAltitudesMap,
                canvasSize = canvasSize,
                selectedIndex = selectedIndex,
                textMeasurer = textMeasurer
            )
        } else {
            Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🎨 Not drawing cross-section: barsByColumn=${barsByColumn.size}, sortedAltitudes=${sortedAltitudes.size}, labelsMap=${labelsMap.size}")
        }
    }
}

/**
 * Pack bars into columns to avoid overlap (matching Android native algorithm)
 */
private fun packBarsIntoColumns(bars: List<CrossSectionBar>): List<List<CrossSectionBar>> {
    val columns = mutableListOf<MutableList<CrossSectionBar>>()

    bars.forEach { bar ->
        var placed = false
        // Try to place in existing columns
        for (column in columns) {
            val overlaps = column.any { existing ->
                // Check if bars overlap in altitude
                !(bar.upperLimit <= existing.lowerLimit || bar.lowerLimit >= existing.upperLimit)
            }
            if (!overlaps) {
                column.add(bar)
                placed = true
                break
            }
        }
        // Create new column if couldn't place in existing ones
        if (!placed) {
            columns.add(mutableListOf(bar))
        }
    }

    return columns
}

/**
 * Draw the continuous scale cross-section using proportional altitude positioning
 */
private fun DrawScope.drawContinuousScaleCrossSection(
    barsByColumn: List<List<CrossSectionBar>>,
    sortedAltitudes: List<Double>,
    altitudeToDisplayText: Map<Double, String>,
    labelsMap: Map<Double, Float>,
    topAltitudesMap: Map<Double, Float>,
    bottomAltitudesMap: Map<Double, Float>,
    canvasSize: IntSize,
    selectedIndex: Int?,
    textMeasurer: TextMeasurer
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
        "🔧 Starting drawContinuousScaleCrossSection: canvas=${canvasWidth}x${canvasHeight}")

    if (barsByColumn.isEmpty() || sortedAltitudes.size < 2) {
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "🔧 Skipping draw: barsByColumn=${barsByColumn.size}, sortedAltitudes=${sortedAltitudes.size}")
        return
    }

    // Constants
    val barWidthPx = AirspaceLayoutConfig.crossSectionBarWidthPx
    val barSpacingPx = AirspaceLayoutConfig.crossSectionBarSpacingPx
    val fontSizePx = AirspaceLayoutConfig.crossSectionFontSizePx
    val labelPaddingPx = AirspaceLayoutConfig.crossSectionLabelPaddingPx
    val labelHeightPx = AirspaceLayoutConfig.crossSectionLabelHeightPx

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
        "🔧 Drawing proportional cross-section: ${canvasWidth}x${canvasHeight}, ${barsByColumn.size} columns, ${sortedAltitudes.size} altitudes")

    // Position bars with proper spacing
    val totalColumnsWidth = barsByColumn.size * (barWidthPx + barSpacingPx) - (if (barsByColumn.isNotEmpty()) barSpacingPx else 0f)
    val startX = barSpacingPx + (canvasWidth - (barSpacingPx + totalColumnsWidth + barSpacingPx)) / 2f

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
        "Bar positioning: canvas=${canvasWidth}px, columns=${barsByColumn.size}, totalWidth=${totalColumnsWidth}px, startX=${startX}px")

    // Draw bars using pre-calculated altitude positions
    var x = startX
    barsByColumn.forEachIndexed { colIndex, column ->
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "Drawing column ${colIndex + 1}/${barsByColumn.size} with ${column.size} bars")

        column.forEach { bar ->
            Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
                "🔧 Processing bar '${bar.name}': upper=${bar.upperLimit}m, lower=${bar.lowerLimit}m")

            // Use pre-calculated altitude positions
            val yTop = topAltitudesMap[bar.upperLimit] ?: 0f
            val yBottom = bottomAltitudesMap[bar.lowerLimit] ?: canvasHeight.toFloat()

            val barLength = yBottom - yTop

            Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.INFO,
                "📏 Bar for '${bar.name}' starts at ${yTop.toInt()}px and stops at ${yBottom.toInt()}px, so length = ${barLength.toInt()}px")

            // Only draw bars with valid dimensions
            if (barLength > 0 && yTop >= 0 && yBottom <= canvasHeight) {
                Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
                    "🎨 Drawing filled rounded bar '${bar.name}' at x=$x, from y=$yTop to y=$yBottom (${barLength.toInt()}px)")

                // Draw filled rounded rectangle with specific colors
                drawRoundRect(
                    color = if (selectedIndex == null || bar.index == selectedIndex) bar.color else bar.color.copy(alpha = 0.5f),
                    topLeft = Offset(x, yTop),
                    size = Size(barWidthPx, barLength),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
            } else {
                Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.WARN,
                    "⚠️ Skipping bar '${bar.name}': invalid dimensions (length=${barLength.toInt()}, yTop=${yTop.toInt()}, yBottom=${yBottom.toInt()}, canvasHeight=$canvasHeight)")
            }
        }
        x += barWidthPx + barSpacingPx
    }

    // Create label positions from the pre-calculated labels map
    val labelPositions = sortedAltitudes.mapNotNull { altitude ->
        labelsMap[altitude]?.let { y -> altitude to y }
    }
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🔧 Drawing ${labelPositions.size} labels")

    // Draw altitude labels on top of bars (foreground layer)
    drawSemiTransparentLabels(labelPositions, altitudeToDisplayText, canvasWidth, fontSizePx, labelPaddingPx, textMeasurer)
}

/**
 * Draw altitude labels with white background and rounded corners
 */
private fun DrawScope.drawSemiTransparentLabels(
    labelPositions: List<Pair<Double, Float>>,
    altitudeToDisplayText: Map<Double, String>,
    canvasWidth: Float,
    fontSizePx: Float,
    labelPaddingPx: Float,
    textMeasurer: TextMeasurer
) {
    // Note: Using cross-platform drawing functions instead of Android-specific Paint

    labelPositions.forEach { (altitude, labelY) ->
        val displayText = altitudeToDisplayText[altitude] ?: "${altitude.toInt()}M"

        // Create text style for measurement
        val textStyle = TextStyle(
            color = Color.Black,
            fontSize = fontSizePx.sp
        )

        // Measure text to get actual dimensions
        val textLayoutResult = textMeasurer.measure(
            text = displayText,
            style = textStyle
        )

        val measuredTextWidth = textLayoutResult.size.width.toFloat()
        val measuredTextHeight = textLayoutResult.size.height.toFloat()

        // Calculate background dimensions based on measured text plus padding
        val backgroundWidth = measuredTextWidth + (labelPaddingPx * 2)
        val backgroundHeight = measuredTextHeight + (labelPaddingPx * 2)
        val cornerRadius = backgroundHeight / 4f

        // Center the background horizontally
        val backgroundLeft = (canvasWidth - backgroundWidth) / 2f
        val backgroundTop = labelY - backgroundHeight / 2f

        // Draw rounded white background
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(backgroundLeft, backgroundTop),
            size = androidx.compose.ui.geometry.Size(backgroundWidth, backgroundHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )

        // Center text within the background
        val x = backgroundLeft + (backgroundWidth - measuredTextWidth) / 2f
        val y = backgroundTop + (backgroundHeight - measuredTextHeight) / 2f

        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(x, y)
        )

        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "Label '${displayText}' at x=${canvasWidth / 2f}, y=${labelY.toInt()} (measured: ${measuredTextWidth.toInt()}x${measuredTextHeight.toInt()}px, bg: ${backgroundLeft.toInt()}-${(backgroundLeft + backgroundWidth).toInt()} x ${backgroundTop.toInt()}-${(backgroundTop + backgroundHeight).toInt()})")
    }
}







/**
 * Create altitude to Y coordinate mappings using proportional altitude scale
 */
private fun createAltitudeMaps(
    sortedAltitudes: List<Double>,
    canvasHeight: Float,
    altitudeRange: Double
): Triple<Map<Double, Float>, Map<Double, Float>, Map<Double, Float>> {
    val labelsMap = mutableMapOf<Double, Float>()
    val topAltitudesMap = mutableMapOf<Double, Float>()
    val bottomAltitudesMap = mutableMapOf<Double, Float>()

    val minAltitude = sortedAltitudes.last()
    val maxAltitude = sortedAltitudes.first()

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🔧 Creating proportional altitude maps:")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Altitudes: $sortedAltitudes")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Canvas height: ${canvasHeight.toInt()}px")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Altitude range: ${altitudeRange.toInt()}m")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Min altitude: ${minAltitude.toInt()}m, Max altitude: ${maxAltitude.toInt()}m")

    // Reserve space for labels at bottom
    val labelHeightPx = AirspaceLayoutConfig.crossSectionStandardLabelHeightPx
    val numLabels = sortedAltitudes.size
    val altitudeHeight = canvasHeight - (numLabels * labelHeightPx)

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Label height: ${labelHeightPx.toInt()}px")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Number of labels: $numLabels")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Altitude height should be: ${altitudeHeight.toInt()}px (${canvasHeight.toInt()} - ($numLabels × ${labelHeightPx.toInt()}))")

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Altitude scale height: ${altitudeHeight.toInt()}px")

    // Initialize altitude mappings
    for (altitude in sortedAltitudes) {
        topAltitudesMap[altitude] = 0f
        bottomAltitudesMap[altitude] = 0f
    }

    // Position altitudes from highest to lowest (descending)
    // Use proportional spacing based on altitude differences
    var currentY = 0f  // Start from top of canvas

    for (i in sortedAltitudes.indices) {  // sortedAltitudes is already highest to lowest
        val altitude = sortedAltitudes[i]

        // Set bottom position (start of this altitude band)
        bottomAltitudesMap[altitude] = currentY
        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "currentY: ${currentY.toInt()}px")

        // Calculate the height for this altitude band
        var bandHeight = 0f  // Default to label height

        if (i < sortedAltitudes.size - 1) {
            // Calculate proportional height based on altitude difference
            val nextAltitude = sortedAltitudes[i + 1]
            val altitudeDelta = altitude - nextAltitude
            val proportionalHeight = (altitudeDelta / altitudeRange) * altitudeHeight
            
            bandHeight = proportionalHeight.toFloat()

            Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
                "     Altitude delta ${altitude.toInt()}m-${nextAltitude.toInt()} = ${altitudeDelta.toInt()}, bandHeight: ${bandHeight.toInt()}px")
        }

        // Set top position and label position
        topAltitudesMap[altitude] = currentY + labelHeightPx
        labelsMap[altitude] = currentY + (labelHeightPx / 2f)

        Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG,
            "   Altitude ${altitude.toInt()}m: bottom=${bottomAltitudesMap[altitude]?.toInt()}px, " +
            "label=${labelsMap[altitude]?.toInt()}px, top=${topAltitudesMap[altitude]?.toInt()}px, " +
            "bandHeight=${bandHeight.toInt()}px")

        // Move to next altitude position
        currentY = currentY + labelHeightPx + bandHeight
    }

    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "🔧 Proportional altitude maps created:")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Labels: ${labelsMap.map { "${it.key.toInt()}m -> ${it.value.toInt()}px" }}")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Top altitudes: ${topAltitudesMap.map { "${it.key.toInt()}m -> ${it.value.toInt()}px" }}")
    Logger.log("AIRSPACE_CROSS_SECTION", LogLevel.DEBUG, "   Bottom altitudes: ${bottomAltitudesMap.map { "${it.key.toInt()}m -> ${it.value.toInt()}px" }}")

    return Triple(labelsMap, topAltitudesMap, bottomAltitudesMap)
}














