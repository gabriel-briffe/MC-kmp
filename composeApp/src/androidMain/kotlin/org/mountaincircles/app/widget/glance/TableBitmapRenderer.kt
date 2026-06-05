package org.mountaincircles.app.widget.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

private const val TAG = "GlanceWidget_Renderer"

/**
 * Renders a single meteogram table (3 days) as a bitmap.
 * This approach avoids the RemoteViews element count limit.
 * 
 * @param widthPx The target width in pixels (widget width)
 */
class TableBitmapRenderer(
    private val repository: MeteogramDataRepository,
    private val data: MeteogramData,
    private val widthPx: Int = 400,  // Default fallback
    private val locationText: String = ""  // Location legend text
) {
    // Simple formula: 13 equal columns (1 label + 12 data cells)
    private val tableWidth = maxOf(widthPx, 200)
    private val columnSize = tableWidth / 13
    
    // All columns are equal width
    private val labelWidth = columnSize
    private val cellWidth = columnSize
    
    // Row heights based on column size
    private val squareRowHeight = columnSize           // Image rows: square cells
    private val textRowHeight = columnSize /2         // Text rows: half height
    private val headerHeight = columnSize              // Header row height
    private val dayNameRowHeight = columnSize *2 / 3          // Day name row height (same as header)
    private val locationRowHeight = columnSize     // Location legend row height
    
    // Text sizes proportional to column size
    private val headerTextSize = (columnSize * 0.35f)
    private val cellTextSize = (columnSize * 0.35f)
    private val labelTextSize = (columnSize * 0.35f)
    
    // Icon sizes fit in square cells
    private val windBarbSize = (columnSize * 1.0f).toInt()
    private val weatherIconSize = (columnSize * 0.6f).toInt()
    
    // Colors (matching original widget colors)
    private val headerBackground = 0xFF010203.toInt()    // Dark gray for headers and labels
    private val midnightBackground = 0xFF0B0F13.toInt()  // Darker gray for midnight/date columns
    private val regularBackground = 0xFF141C23.toInt()   // Lighter gray for regular columns
    private val textPrimary = 0xFFFFFFFF.toInt()         // White text
    private val textSecondary = 0xFFB0B0B0.toInt()
    private val cyan = 0xFF00FFFF.toInt()                // Cyan
    
    // Check if SkySight data is available
    private val hasSkySight = repository.hasSkySightCredentials(data) && 
        (data.metadata?.w4000MaxByDayAndHour?.isNotEmpty() == true ||
         data.metadata?.pfdtotMaxByDay?.isNotEmpty() == true)
    
    // Row types to render (conditionally include SkySight rows)
    private val rowTypes = buildList {
        add(RowType.WIND_500)
        add(RowType.WIND_600)
        add(RowType.WIND_700)
        if (hasSkySight) add(RowType.WAVE)      // w4000 wave data
        if (hasSkySight) add(RowType.SKYSIGHT)  // pfdtot, hwcrit, wstar
        add(RowType.FREEZING)
        add(RowType.PRECIP)
        add(RowType.WEATHER)
        add(RowType.TEMP)
        add(RowType.QNH)
    }
    
    /**
     * Render a table for the given table index (0, 1, or 2).
     * Returns a bitmap containing the rendered table.
     */
    fun renderTable(tableIndex: Int): Bitmap {
        Logger.log(TAG, LogLevel.DEBUG, "Rendering table $tableIndex as bitmap")
        
        // Calculate total height (include day name row, header, data rows, and optionally location row)
        var totalHeight = dayNameRowHeight + headerHeight
        for (rowType in rowTypes) {
            totalHeight += getRowHeight(rowType)
        }
        if (locationText.isNotEmpty()) {
            totalHeight += locationRowHeight
        }
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(tableWidth, totalHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        
        // Fill background
        canvas.drawColor(regularBackground)
        
        var y = 0
        
        // Draw day name row (on top)
        drawDayNameRow(canvas, tableIndex, y)
        y += dayNameRowHeight
        
        // Draw header row
        drawHeaderRow(canvas, tableIndex, y)
        y += headerHeight
        
        // Draw data rows
        for (rowType in rowTypes) {
            drawDataRow(canvas, tableIndex, rowType, y)
            y += getRowHeight(rowType)
        }
        
        // Draw location legend row
        if (locationText.isNotEmpty()) {
            drawLocationRow(canvas, y)
        }
        
        Logger.log(TAG, LogLevel.DEBUG, "Table $tableIndex rendered: ${bitmap.width}x${bitmap.height}")
        return bitmap
    }
    
    private fun drawLocationRow(canvas: Canvas, y: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = labelTextSize
            color = textSecondary
        }
        
        // Draw background
        paint.color = headerBackground
        canvas.drawRect(0f, y.toFloat(), tableWidth.toFloat(), (y + locationRowHeight).toFloat(), paint)
        
        // Draw location text centered
        paint.color = textSecondary
        drawCenteredText(canvas, locationText, 0, y, tableWidth, locationRowHeight, paint)
    }
    
    private fun drawDayNameRow(canvas: Canvas, tableIndex: Int, y: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = headerTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Draw label cell background (empty)
        paint.color = headerBackground
        canvas.drawRect(0f, y.toFloat(), labelWidth.toFloat(), (y + dayNameRowHeight).toFloat(), paint)
        
        // Draw day name cells - each day spans 4 columns
        for (dayOffset in 0 until 3) {
            val dayIndex = tableIndex * 3 + dayOffset
            val dayName = repository.getDayName(data, dayIndex)
            
            // Calculate position: starts after label column + previous days' columns
            val x = labelWidth + dayOffset * 4 * cellWidth
            val dayWidth = 4 * cellWidth  // Each day spans 4 columns
            
            // Draw background
            paint.color = headerBackground
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + dayWidth).toFloat(), (y + dayNameRowHeight).toFloat(), paint)
            
            // Draw day name text centered
            paint.color = textPrimary
            paint.textSize = headerTextSize
            drawCenteredText(canvas, dayName, x, y, dayWidth, dayNameRowHeight, paint)
        }
    }
    
    private fun getRowHeight(rowType: RowType): Int {
        return when (rowType) {
            // Image/square rows: full column width height
            RowType.WIND_500, RowType.WIND_600, RowType.WIND_700, RowType.WEATHER,
            RowType.WAVE -> squareRowHeight
            // SkySight row: 1.5x column width for 3 lines
            RowType.SKYSIGHT -> (squareRowHeight * 1.5f).toInt()
            // Text rows: half height
            else -> textRowHeight
        }
    }
    
    private fun drawHeaderRow(canvas: Canvas, tableIndex: Int, y: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = headerTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Draw label cell background
        paint.color = headerBackground
        canvas.drawRect(0f, y.toFloat(), labelWidth.toFloat(), (y + headerHeight).toFloat(), paint)
        
        // Draw hour cells
        for (dayOffset in 0 until 3) {
            val dayIndex = tableIndex * 3 + dayOffset
            for (hourSlot in 0 until 4) {
                val cellIndex = dayOffset * 4 + hourSlot
                val x = labelWidth + cellIndex * cellWidth
                
                val timeIndex = repository.calculateTimeIndex(hourSlot, dayIndex)
                val hourInfo = repository.getHourText(data, timeIndex)
                
                // Background
                paint.color = if (hourSlot == 0) midnightBackground else headerBackground
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellWidth).toFloat(), (y + headerHeight).toFloat(), paint)
                
                // Hour text
                paint.color = if (hourInfo.isMidnight) cyan else textPrimary
                paint.textSize = cellTextSize
                drawCenteredText(canvas, hourInfo.text, x, y, cellWidth, headerHeight, paint)
            }
        }
    }
    
    private fun drawDataRow(canvas: Canvas, tableIndex: Int, rowType: RowType, y: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = cellTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val rowHeight = getRowHeight(rowType)
        
        // Draw label cell
        paint.color = headerBackground
        canvas.drawRect(0f, y.toFloat(), labelWidth.toFloat(), (y + rowHeight).toFloat(), paint)
        
        // Draw label text or logo
        val label = getRowLabel(rowType)
        if (label != null) {
            paint.color = textPrimary
            paint.textSize = labelTextSize
            drawCenteredText(canvas, label, 0, y, labelWidth, rowHeight, paint)
        } else if (rowType == RowType.SKYSIGHT) {
            // Draw SkySight logo
            drawSkySightLogo(canvas, 0, y, labelWidth, rowHeight, paint)
        }
        
        // Draw data cells
        for (dayOffset in 0 until 3) {
            val dayIndex = tableIndex * 3 + dayOffset
            for (hourSlot in 0 until 4) {
                val cellIndex = dayOffset * 4 + hourSlot
                val x = labelWidth + cellIndex * cellWidth
                val timeIndex = repository.calculateTimeIndex(hourSlot, dayIndex)
                
                // Cell background - use different colors for midnight vs regular columns
                val cellBackgroundColor = if (hourSlot == 0) midnightBackground else regularBackground
                paint.color = cellBackgroundColor
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellWidth).toFloat(), (y + rowHeight).toFloat(), paint)
                
                // Draw cell content based on row type
                drawCellContent(canvas, x, y, cellWidth, rowHeight, rowType, timeIndex, hourSlot, paint)
            }
        }
    }
    
    private fun drawCellContent(
        canvas: Canvas,
        x: Int, y: Int, width: Int, height: Int,
        rowType: RowType,
        timeIndex: Int,
        hourSlot: Int,
        paint: Paint
    ) {
        when (rowType) {
            RowType.WIND_500, RowType.WIND_600, RowType.WIND_700 -> {
                val pressureLevel = when (rowType) {
                    RowType.WIND_500 -> "500hPa"
                    RowType.WIND_600 -> "600hPa"
                    else -> "700hPa"
                }
                val speed = repository.getWindSpeed(data, pressureLevel, timeIndex)
                val direction = repository.getWindDirection(data, pressureLevel, timeIndex)
                
                if (speed != null && direction != null) {
                    // Draw wind barb bitmap (scaled)
                    val drawableId = repository.getWindBarbDrawableId(speed)
                    val rotatedBitmap = repository.createRotatedBitmap(drawableId, direction, windBarbSize)
                    if (rotatedBitmap != null) {
                        val bitmapX = x + (width - rotatedBitmap.width) / 2
                        val bitmapY = y + (height - rotatedBitmap.height) / 2
                        canvas.drawBitmap(rotatedBitmap, bitmapX.toFloat(), bitmapY.toFloat(), paint)
                    }
                }
            }
            
            RowType.WEATHER -> {
                val code = repository.getWeatherCode(data, timeIndex)
                if (code != null) {
                    val iconResId = repository.getWeatherIconDrawableId(code)
                    val drawable = ContextCompat.getDrawable(repository.context, iconResId)
                    if (drawable != null) {
                        // Scale icon to fill the entire cell exactly
                        val iconBitmap = drawable.toBitmap(width, height)
                        val srcRect = Rect(0, 0, iconBitmap.width, iconBitmap.height)
                        val dstRect = Rect(x, y, x + width, y + height)
                        canvas.drawBitmap(iconBitmap, srcRect, dstRect, null)
                    }
                }
            }
            
            RowType.TEMP -> {
                val temp = repository.getTemperature(data, timeIndex)
                if (temp != null) {
                    paint.color = textPrimary
                    paint.textSize = cellTextSize
                    drawCenteredText(canvas, "${temp.toInt()}°", x, y, width, height, paint)
                }
            }
            
            RowType.FREEZING -> {
                // Only display freezing level for 06h and 18h columns (hourSlot 1 and 3)
                if (hourSlot == 1 || hourSlot == 3) {
                    val freezing = repository.getFreezingLevel(data, timeIndex)
                    if (freezing != null) {
                        paint.color = textPrimary
                        paint.textSize = labelTextSize
                        val rounded = (kotlin.math.round(freezing / 10.0) * 10).toInt()
                        val text = "$rounded"
                        drawCenteredText(canvas, text, x, y, width, height, paint)
                    }
                }
            }
            
            RowType.QNH -> {
                // Only display QNH for 06h and 18h columns (hourSlot 1 and 3)
                if (hourSlot == 1 || hourSlot == 3) {
                    val pressure = repository.getPressure(data, timeIndex)
                    if (pressure != null) {
                        paint.color = textPrimary
                        paint.textSize = labelTextSize
                        drawCenteredText(canvas, "$pressure", x, y, width, height, paint)
                    }
                }
            }
            
            RowType.PRECIP -> {
                val precip = repository.getPrecipitation(data, timeIndex)
                if (precip != null && precip > 0) {
                    paint.color = cyan // Cyan (matching original widget)
                    paint.textSize = labelTextSize
                    // Convert from inches to millimeters (1 inch = 25.4 mm) like original widget
                    val precipMm = precip * 25.4
                    val text = String.format("%.0f", precipMm)
                    drawCenteredText(canvas, text, x, y, width, height, paint)
                }
            }
            
            RowType.WAVE -> {
                // w4000 wave data - only at 6h, 12h, 18h (hourSlot 1, 2, 3)
                // 2 lines: value, blocks
                if (hourSlot in 1..3) {
                    val waveData = repository.getWaveData(data, timeIndex)
                    if (waveData != null) {
                        val lineHeight = height / 2
                        
                        // Line 1: value (white)
                        paint.color = textPrimary
                        paint.textSize = cellTextSize
                        drawCenteredText(canvas, waveData.formattedValue, x, y, width, lineHeight, paint)
                        
                        // Line 2: colored blocks "███"
                        paint.color = colorToInt(waveData.color)
                        paint.textSize = cellTextSize /2
                        drawCenteredText(canvas, "█████", x, y + lineHeight, width, lineHeight, paint)
                    }
                }
            }
            
            RowType.SKYSIGHT -> {
                // SkySight data: pfdtot@6h, hwcrit@12h, wstar@18h (hourSlot 1, 2, 3)
                // 3 lines: value, unit, blocks
                if (hourSlot in 1..3) {
                    val ssData = repository.getSkySightData(data, timeIndex)
                    if (ssData != null) {
                        val padding = height / 12  // 1/12 padding on top and bottom
                        val availableHeight = height - padding * 2  // Height minus top and bottom padding
                        val textLineHeight = availableHeight / 3  // Each text line gets 1/3 of available height
                        val blocksHeight = availableHeight - textLineHeight * 2  // Remaining space for blocks

                        // Line 1: value (white) - starts after top padding
                        paint.color = textPrimary
                        paint.textSize = cellTextSize
                        drawCenteredText(canvas, ssData.formattedValue, x, y + padding, width, textLineHeight, paint)

                        // Line 2: unit (white) - after first text line
                        paint.color = textPrimary
                        paint.textSize = cellTextSize
                        drawCenteredText(canvas, ssData.unit, x, y + padding + textLineHeight, width, textLineHeight, paint)

                        // Line 3: colored blocks "███" - remaining space at bottom
                        paint.color = colorToInt(ssData.color)
                        paint.textSize = cellTextSize /2
                        drawCenteredText(canvas, "█████", x, y + padding + textLineHeight * 2, width, blocksHeight, paint)
                    }
                }
            }
            
            else -> {
                // Empty cell for unsupported row types
            }
        }
    }
    
    private fun getRowLabel(rowType: RowType): String? {
        return when (rowType) {
            RowType.WIND_500 -> "500"
            RowType.WIND_600 -> "600"
            RowType.WIND_700 -> "700"
            RowType.FREEZING -> "0°C"
            RowType.PRECIP -> "mm"
            RowType.WEATHER -> "Wx"
            RowType.TEMP -> "T°"
            RowType.QNH -> "QNH"
            RowType.WAVE -> "wave"
            RowType.SKYSIGHT -> null  // Uses logo instead
        }
    }
    
    /**
     * Draw SkySight logo in label cell.
     */
    private fun drawSkySightLogo(
        canvas: Canvas,
        x: Int, y: Int, width: Int, height: Int,
        paint: Paint
    ) {
        val drawable = ContextCompat.getDrawable(repository.context, org.mountaincircles.app.R.drawable.skysight)
        if (drawable != null) {
            val availableWidth = width - 4
            val availableHeight = height - 4

            // Calculate proportional scaling to fit within the cell
            val scaleX = availableWidth.toFloat() / drawable.intrinsicWidth
            val scaleY = availableHeight.toFloat() / drawable.intrinsicHeight
            val scale = minOf(scaleX, scaleY)

            val scaledWidth = (drawable.intrinsicWidth * scale).toInt()
            val scaledHeight = (drawable.intrinsicHeight * scale).toInt()

            val logoBitmap = drawable.toBitmap(scaledWidth, scaledHeight)
            val logoX = x + (width - scaledWidth) / 2
            val logoY = y + (height - scaledHeight) / 2
            canvas.drawBitmap(logoBitmap, logoX.toFloat(), logoY.toFloat(), paint)
        }
    }
    
    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        x: Int, y: Int, width: Int, height: Int,
        paint: Paint
    ) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val textX = x + (width - bounds.width()) / 2f
        val textY = y + (height + bounds.height()) / 2f
        canvas.drawText(text, textX, textY, paint)
    }
    
    /**
     * Convert Compose Color to Android int color.
     */
    private fun colorToInt(color: androidx.compose.ui.graphics.Color): Int {
        return android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
    
    // Expose context for drawable loading
    val context get() = repository.context
}
