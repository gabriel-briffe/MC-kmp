package org.mountaincircles.app.widget.glance

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

private const val TAG = "GlanceWidget_Layout"

/**
 * Composable layout for the Meteogram Glance Widget.
 * Uses paginated bitmap tables to avoid RemoteViews size limits.
 */
@Composable
fun MeteogramGlanceLayout(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    locationText: String,
    currentTableIndex: Int,
    noDataMessage: String,
    lastRefreshTime: String?,
    onRefreshClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Logger.log(TAG, LogLevel.DEBUG, "MeteogramGlanceLayout: data.hasData=${data.hasData}, tableIndex=$currentTableIndex")
    
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(MeteogramColors.background))
    ) {
        if (!data.hasData) {
            Logger.log(TAG, LogLevel.DEBUG, "Rendering NoDataContent")
            NoDataContent(message = noDataMessage)
        } else {
            Logger.log(TAG, LogLevel.DEBUG, "Rendering BitmapTable")
            BitmapTableContent(
                data = data,
                repository = repository,
                locationText = locationText,
                currentTableIndex = currentTableIndex,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick
            )
        }
        
        // Refresh row - always shown (for both data and nodata states)
        RefreshRow(lastRefreshTime = lastRefreshTime, onRefreshClick = onRefreshClick)
    }
}

/**
 * Refresh button row - centered, always visible.
 * Shows last refresh time (HH:mm) before the refresh icon.
 */
@Composable
private fun RefreshRow(lastRefreshTime: String?, onRefreshClick: () -> Unit) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(20.dp)
            .background(ColorProvider(MeteogramColors.headerBackground))
            .clickable { onRefreshClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lastRefreshTime != null) {
            Text(
                text = lastRefreshTime,
                style = TextStyle(
                    color = ColorProvider(MeteogramColors.textSecondary),
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Box(
            modifier = GlanceModifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⟳",
                style = TextStyle(
                    color = ColorProvider(MeteogramColors.textPrimary),
                    fontSize = 14.sp
                )
            )
        }
    }
}

/**
 * Displays the current table as a bitmap with navigation buttons.
 */
@Composable
private fun ColumnScope.BitmapTableContent(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    locationText: String,
    currentTableIndex: Int,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit
) {
    // Get widget size and convert to pixels
    val size = LocalSize.current
    val density = repository.context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt()
    
    Logger.log(TAG, LogLevel.DEBUG, "Widget size: ${size.width} x ${size.height}, widthPx=$widthPx, density=$density")
    
    // Render the table as a bitmap at the exact widget width (includes location legend)
    val renderer = TableBitmapRenderer(repository, data, widthPx, locationText)
    val tableBitmap = renderer.renderTable(currentTableIndex)
    
    Logger.log(TAG, LogLevel.DEBUG, "Table $currentTableIndex bitmap: ${tableBitmap.width}x${tableBitmap.height}")
    
    // Table bitmap image - full width, top aligned; leftover space below image uses main background
    // With transparent left/right touch zones for navigation
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(ColorProvider(MeteogramColors.background)),
        contentAlignment = Alignment.TopStart
    ) {
        // The table image
        Image(
            provider = ImageProvider(tableBitmap),
            contentDescription = "Table ${currentTableIndex + 1}",
            modifier = GlanceModifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
        
        // Transparent touch zones overlay - left and right halves
        Row(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Left half - previous page
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable { if (currentTableIndex > 0) onPreviousClick() }
            ) {}
            
            // Right half - next page
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable { if (currentTableIndex < 2) onNextClick() }
            ) {}
        }
    }
    
    // Navigation row: ◀ ●○○ ▶ (centered)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(40.dp)
            .background(ColorProvider(MeteogramColors.headerBackground)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous arrow
        Box(
            modifier = GlanceModifier
                .width(40.dp)
                .height(40.dp)
                .clickable { onPreviousClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentTableIndex > 0) "◀" else "",
                style = TextStyle(
                    color = ColorProvider(MeteogramColors.textPrimary),
                    fontSize = 18.sp
                )
            )
        }
        
        // Page indicators
        for (i in 0..2) {
            val color = if (i == currentTableIndex) MeteogramColors.cyan else MeteogramColors.textSecondary
            Text(
                text = if (i == currentTableIndex) "●" else "○",
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 16.sp
                )
            )
            if (i < 2) Spacer(modifier = GlanceModifier.width(8.dp))
        }
        
        // Next arrow
        Box(
            modifier = GlanceModifier
                .width(40.dp)
                .height(40.dp)
                .clickable { onNextClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentTableIndex < 2) "▶" else "",
                style = TextStyle(
                    color = ColorProvider(MeteogramColors.textPrimary),
                    fontSize = 18.sp
                )
            )
        }
    }
}

@Composable
private fun ColumnScope.NoDataContent(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(ColorProvider(MeteogramColors.gridBackground)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(MeteogramColors.textSecondary),
                fontSize = Dimens.noDataText
            )
        )
    }
}

/**
 * Scrollable grid with 3 tables (days 1-3, 4-6, 7-9).
 */
@Composable
private fun ColumnScope.ScrollableMeteogramGrid(
    data: MeteogramData,
    repository: MeteogramDataRepository
) {
    Logger.log(TAG, LogLevel.DEBUG, "ScrollableMeteogramGrid: Starting render")
    
    // Build row list (without SkySight for now)
    val rowConfigs = listOf(
        RowType.WIND_500,
        RowType.WIND_600,
        RowType.WIND_700,
        RowType.FREEZING,
        RowType.PRECIP,
        RowType.WEATHER,
        RowType.TEMP,
        RowType.QNH
    )
    
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(ColorProvider(MeteogramColors.gridBackground))
    ) {
        LazyColumn(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Table 1: Days 0-2
            item { HourHeaderRow(data, repository, tableIndex = 0) }
            items(rowConfigs.size) { index ->
                DataRow(data, repository, rowType = rowConfigs[index], tableIndex = 0)
            }
            
            // Table 2: Days 3-5
            item { HourHeaderRow(data, repository, tableIndex = 1) }
            items(rowConfigs.size) { index ->
                DataRow(data, repository, rowType = rowConfigs[index], tableIndex = 1)
            }
            
            // TEMPORARILY DISABLED - Testing with 2 tables + text only
            // Table 3: Days 6-8
            // item { HourHeaderRow(data, repository, tableIndex = 2) }
            // items(rowConfigs.size) { index ->
            //     DataRow(data, repository, rowType = rowConfigs[index], tableIndex = 2)
            // }
        }
    }
    
    Logger.log(TAG, LogLevel.DEBUG, "ScrollableMeteogramGrid: Render complete")
}

/**
 * Hour header row showing hour/date labels.
 */
@Composable
private fun HourHeaderRow(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    tableIndex: Int
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label column
        LabelCell(text = "hPa")
        
        // Three day columns with hour labels
        for (dayOffset in 0 until 3) {
            val dayIndex = tableIndex * 3 + dayOffset
            HourLabelsCell(data, repository, dayIndex)
        }
    }
}

/**
 * Data row for weather information.
 */
@Composable
private fun DataRow(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    rowType: RowType,
    tableIndex: Int
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label column
        LabelCell(text = rowType.label, rowType = rowType)
        
        // Three day columns
        for (dayOffset in 0 until 3) {
            val dayIndex = tableIndex * 3 + dayOffset
            DayDataCell(data, repository, rowType, dayIndex)
        }
    }
}

/**
 * Label cell (first column).
 */
@Composable
private fun RowScope.LabelCell(text: String, rowType: RowType? = null) {
    val height = rowType?.let { Dimens.getRowHeight(it) } ?: Dimens.headerRowHeight
    Box(
        modifier = GlanceModifier
            .width(Dimens.labelCellWidth)
            .height(height)
            .background(ColorProvider(MeteogramColors.headerBackground)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(MeteogramColors.textPrimary),
                fontSize = Dimens.gridText,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

/**
 * Hour labels cell containing 4 hour labels.
 */
@Composable
private fun RowScope.HourLabelsCell(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    dayIndex: Int
) {
    Row(modifier = GlanceModifier.defaultWeight()) {
        for (hourIndex in 0 until 4) {
            val timeIndex = repository.calculateTimeIndex(hourIndex, dayIndex)
            val hourInfo = repository.getHourText(data, timeIndex)
            
            val bgColor = if (hourIndex == 0) {
                MeteogramColors.midnightBackground
            } else {
                MeteogramColors.headerBackground
            }
            
            val textColor = if (hourInfo.isMidnight) {
                MeteogramColors.cyan
            } else {
                MeteogramColors.textPrimary
            }
            
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(Dimens.headerRowHeight)
                    .background(ColorProvider(bgColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hourInfo.text,
                    style = TextStyle(
                        color = ColorProvider(textColor),
                        fontSize = Dimens.gridText
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Day data cell containing 4 hour values.
 */
@Composable
private fun RowScope.DayDataCell(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    rowType: RowType,
    dayIndex: Int
) {
    Row(modifier = GlanceModifier.defaultWeight()) {
        for (hourIndex in 0 until 4) {
            val timeIndex = repository.calculateTimeIndex(hourIndex, dayIndex)
            val isMidnight = hourIndex == 0
            
            HourDataCell(
                data = data,
                repository = repository,
                rowType = rowType,
                timeIndex = timeIndex,
                isMidnight = isMidnight
            )
        }
    }
}

/**
 * Individual hour data cell.
 */
@Composable
private fun RowScope.HourDataCell(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    rowType: RowType,
    timeIndex: Int,
    isMidnight: Boolean
) {
    val backgroundColor = if (isMidnight) {
        MeteogramColors.midnightBackground
    } else {
        MeteogramColors.cellBackground
    }
    
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(Dimens.getRowHeight(rowType))
            .background(ColorProvider(backgroundColor)),
        contentAlignment = Alignment.Center
    ) {
        when (rowType) {
            RowType.WIND_500, RowType.WIND_600, RowType.WIND_700 -> {
                // Wind rows: TEMPORARILY show text instead of images to test size
                val pressureLevel = when (rowType) {
                    RowType.WIND_500 -> "500hPa"
                    RowType.WIND_600 -> "600hPa"
                    else -> "700hPa"
                }
                val speed = repository.getWindSpeed(data, pressureLevel, timeIndex)
                val direction = repository.getWindDirection(data, pressureLevel, timeIndex)
                
                if (speed != null && direction != null) {
                    // Use arrow character based on direction
                    val arrow = when ((direction.toInt() + 22) / 45 % 8) {
                        0 -> "↓"  // N wind blows south
                        1 -> "↙"
                        2 -> "←"
                        3 -> "↖"
                        4 -> "↑"
                        5 -> "↗"
                        6 -> "→"
                        7 -> "↘"
                        else -> "•"
                    }
                    Text(
                        text = "${speed.toInt()}$arrow",
                        style = TextStyle(
                            color = ColorProvider(MeteogramColors.textPrimary),
                            fontSize = 10.sp
                        )
                    )
                }
            }
            RowType.WEATHER -> {
                // Weather row: TEMPORARILY show text instead of icon to test size
                val code = repository.getWeatherCode(data, timeIndex)
                if (code != null) {
                    // Simple weather emoji based on code
                    val emoji = when {
                        code == 0 -> "☀"
                        code in 1..3 -> "⛅"
                        code in 45..48 -> "🌫"
                        code in 51..67 -> "🌧"
                        code in 71..77 -> "❄"
                        code in 80..82 -> "🌦"
                        code in 95..99 -> "⛈"
                        else -> "?"
                    }
                    Text(
                        text = emoji,
                        style = TextStyle(
                            color = ColorProvider(MeteogramColors.textPrimary),
                            fontSize = 12.sp
                        )
                    )
                }
            }
            RowType.WAVE -> {
                // Wave row: show w4000 value with color indicator
                val waveData = repository.getWaveData(data, timeIndex)
                if (waveData != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = waveData.formattedValue,
                            style = TextStyle(
                                color = ColorProvider(MeteogramColors.textPrimary),
                                fontSize = Dimens.multiLineText,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "███",
                            style = TextStyle(
                                color = ColorProvider(waveData.color),
                                fontSize = Dimens.multiLineText,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            RowType.SKYSIGHT -> {
                // SkySight row: show pfdtot/hwcrit/wstar with color indicator
                val skysightData = repository.getSkySightData(data, timeIndex)
                if (skysightData != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${skysightData.formattedValue}\n${skysightData.unit}",
                            style = TextStyle(
                                color = ColorProvider(MeteogramColors.textPrimary),
                                fontSize = Dimens.multiLineText,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 2
                        )
                        Text(
                            text = "███",
                            style = TextStyle(
                                color = ColorProvider(skysightData.color),
                                fontSize = Dimens.multiLineText,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            else -> {
                // Text-based rows (freezing, precip, temp, qnh)
                val cellContent = getCellContent(data, repository, rowType, timeIndex)
                Text(
                    text = cellContent,
                    style = TextStyle(
                        color = ColorProvider(MeteogramColors.textPrimary),
                        fontSize = Dimens.gridText,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Get cell content for text-based rows.
 * Wind and weather rows use images instead.
 */
private fun getCellContent(
    data: MeteogramData,
    repository: MeteogramDataRepository,
    rowType: RowType,
    timeIndex: Int
): String {
    return when (rowType) {
        RowType.FREEZING -> {
            val freezing = repository.getFreezingLevel(data, timeIndex)
            freezing?.toString() ?: ""
        }
        RowType.PRECIP -> {
            val precip = repository.getPrecipitation(data, timeIndex)
            if (precip != null && precip > 0) {
                String.format("%.1f", precip)
            } else ""
        }
        RowType.TEMP -> {
            val temp = repository.getTemperature(data, timeIndex)
            temp?.let { String.format("%.0f°", it) } ?: ""
        }
        RowType.QNH -> {
            val pressure = repository.getPressure(data, timeIndex)
            pressure?.toString() ?: ""
        }
        // Wind and weather rows are handled with images in HourDataCell
        else -> ""
    }
}

/**
 * Location row with refresh button.
 */
@Composable
private fun LocationRow(
    locationText: String,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(Dimens.footerHeight)
            .background(ColorProvider(MeteogramColors.headerBackground)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Location text
        Text(
            text = locationText,
            style = TextStyle(
                color = ColorProvider(MeteogramColors.textSecondary),
                fontSize = Dimens.footerText
            ),
            maxLines = 1
        )
        
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        // Refresh button
        Box(
            modifier = GlanceModifier
                .background(ColorProvider(MeteogramColors.buttonBackground))
                .clickable { onRefreshClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = " ↻ ",
                style = TextStyle(
                    color = ColorProvider(MeteogramColors.textPrimary),
                    fontSize = Dimens.refreshButtonText
                )
            )
        }
    }
}

/**
 * Row types for the meteogram grid.
 */
internal enum class RowType(val label: String) {
    WIND_500("500"),
    WIND_600("600"),
    WIND_700("700"),
    WAVE("wave"),        // w4000 wave data (conditional, multi-line)
    SKYSIGHT("SS"),      // SkySight data (conditional, multi-line)
    FREEZING("0°C"),     // Freezing level
    PRECIP("Prcp"),      // Precipitation
    WEATHER(""),         // Weather icon
    TEMP("T°C"),         // Temperature
    QNH("QNH")           // Pressure
}

/**
 * Single source of truth for all layout dimensions.
 */
private object Dimens {
    // Cell dimensions
    val labelCellWidth = 32.dp
    val footerHeight = 20.dp
    
    // Row heights by type
    val imageRowHeight = 24.dp      // Wind and weather rows (with images)
    val textRowHeight = 16.dp       // Single-line text rows
    val multiLineRowHeight = 32.dp  // Wave and skysight rows (multi-line)
    val headerRowHeight = 16.dp     // Hour header row
    
    // Image sizes
    val windBarbSize = 20.dp
    val weatherIconSize = 20.dp
    const val windBarbBitmapSize = 40 // pixels for bitmap generation
    
    // Text sizes
    val gridText = 9.sp
    val multiLineText = 7.sp        // Smaller text for multi-line cells
    val footerText = 10.sp
    val refreshButtonText = 12.sp
    val noDataText = 12.sp
    
    // Get row height based on row type
    fun getRowHeight(rowType: RowType) = when (rowType) {
        RowType.WIND_500, RowType.WIND_600, RowType.WIND_700, RowType.WEATHER -> imageRowHeight
        RowType.WAVE, RowType.SKYSIGHT -> multiLineRowHeight
        RowType.FREEZING, RowType.PRECIP, RowType.TEMP, RowType.QNH -> textRowHeight
    }
}

/**
 * Color palette for the Meteogram widget.
 */
private object MeteogramColors {
    val background = Color(0xFF010203)
    val headerBackground = Color(0xFF222222)
    val gridBackground = Color(0xFF2A2A2A)
    val cellBackground = Color(0xFF333333)
    val midnightBackground = Color(0xFF2A2A2A)
    val buttonBackground = Color(0xFF444444)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFAAAAAA)
    val cyan = Color.Cyan
}
