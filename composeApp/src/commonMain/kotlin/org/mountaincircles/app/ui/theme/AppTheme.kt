package org.mountaincircles.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppTheme {
    object Colors {
        val background = Color.Black
        val surface = Color.Black.copy(alpha = 0.95f)
        val primary = Color(0xFF1976D2)
        val onBackground = Color.White
        val onSurface = Color.White
        val secondary = Color.Gray
    }
    
    object Typography {
        val bottomSheetTitle = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.onSurface
        )
        
        val settingLabel = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.onSurface
        )
        
        val settingValue = TextStyle(
            fontSize = 14.sp,
            color = Colors.secondary
        )
        
        // Progress bar typography
        val progressStatus = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.onSurface
        )
        
        val progressPercentage = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.onSurface
        )
        
        val progressDetail = TextStyle(
            fontSize = 10.sp,
            color = Colors.secondary
        )
        
        val progressCounter = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
    
    object Spacing {
        val bottomSheetPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        val bottomSheetTitleBottom = 16.dp // Reduced from 24.dp
        val settingItemSpacing = 16.dp
        val settingInternalSpacing = 8.dp
    }
    
    object ProgressBar {
        val containerColor = Color.Blue.copy(alpha = 0.2f)
        val barColor = Color.Blue
        val trackColor = Color.Gray.copy(alpha = 0.3f)
        val barHeight = 8.dp
        val cardPadding = 16.dp
        val itemSpacing = 8.dp
        val percentageMinWidth = 48.dp // Ensure percentage doesn't jump lines
        
        val successColor = Color.Green
        val errorColor = Color.Red
    }
    
    object Buttons {
        // Standard sheet button (main menu style)
        val standardContainerColor = Color.Gray.copy(alpha = 0.3f)
        val standardContentColor = Color.White
        val standardPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        val standardCornerRadius = 8.dp
        val standardIconSpacing = 8.dp
        
        // Clear/delete button (dark red theme)
        val clearContainerColor = Color.Red.copy(alpha = 0.7f)
        val clearContentColor = Color.White
        
        // Button typography
        val buttonTextStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = standardContentColor
        )
        
        val clearButtonTextStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = clearContentColor
        )
    }
}
