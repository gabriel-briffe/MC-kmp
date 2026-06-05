package org.mountaincircles.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.ui.theme.AppTheme

/**
 * Standard button component based on main menu styling
 * Used across all module sheets for consistent theming
 */
@Composable
fun StandardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.Buttons.standardContainerColor,
            contentColor = AppTheme.Buttons.standardContentColor,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(AppTheme.Buttons.standardCornerRadius),
        contentPadding = AppTheme.Buttons.standardPadding,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Buttons.standardIconSpacing)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (enabled) AppTheme.Buttons.standardContentColor else Color.Gray
                )
            }
            Text(
                text = text,
                style = AppTheme.Buttons.buttonTextStyle.copy(
                    color = if (enabled) AppTheme.Buttons.standardContentColor else Color.Gray
                )
            )
        }
    }
}

/**
 * Clear/delete button with dark red theme
 */
@Composable
fun ClearButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.Buttons.clearContainerColor,
            contentColor = AppTheme.Buttons.clearContentColor,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(AppTheme.Buttons.standardCornerRadius),
        contentPadding = AppTheme.Buttons.standardPadding,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Buttons.standardIconSpacing)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (enabled) AppTheme.Buttons.clearContentColor else Color.Gray
                )
            }
            Text(
                text = text,
                style = AppTheme.Buttons.clearButtonTextStyle.copy(
                    color = if (enabled) AppTheme.Buttons.clearContentColor else Color.Gray
                )
            )
        }
    }
}

