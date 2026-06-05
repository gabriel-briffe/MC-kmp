package org.mountaincircles.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.settings.ClassMetadata
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.settings.GenericSettingsSection

/**
 * Reset button styling options
 */
enum class ResetButtonStyle {
    App,    // Red background, bold text, "Reset All Settings to Defaults"
    Module  // Gray background, medium text, module-specific text
}

/**
 * Generic collapsible settings section for both app and module settings
 */
@Composable
fun CollapsibleSettingsSection(
    icon: Painter,
    title: String,
    metadata: ClassMetadata?,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    onResetClick: (() -> Unit)? = null,
    resetButtonText: String = "",
    resetButtonStyle: ResetButtonStyle = ResetButtonStyle.Module,
    onExpanded: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section header - clickable to expand/collapse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val wasExpanded = isExpanded
                        isExpanded = !isExpanded
                        if (!wasExpanded && isExpanded && onExpanded != null) {
                            onExpanded()
                        }
                    }
                    .padding(bottom = 12.dp)
            ) {
                // Settings icon
                Icon(
                    painter = icon,
                    contentDescription = "$title Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                // Section title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Expand/collapse indicator
                Icon(
                    painter = if (isExpanded) AppIcons.ExpandLess() else AppIcons.ExpandMore(),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Collapsible content
            if (isExpanded) {
                if (metadata != null && metadata.fields.isNotEmpty()) {
                    // Use GenericSettingsSection for metadata-driven settings
                    GenericSettingsSection(
                        metadata = metadata,
                        values = values,
                        onValueChange = onValueChange,
                        showGrouped = true,
                        showAdvancedFields = false,
                        enabled = true,
                        showHeader = false
                    )
                }

                // Reset button (if provided)
                onResetClick?.let { resetCallback ->
                    val (buttonColors, buttonText, fontWeight) = when (resetButtonStyle) {
                        ResetButtonStyle.App -> Triple(
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF666666), 
                                contentColor = Color.White
                            ),
                            resetButtonText.ifEmpty { "Reset App Settings" },
                            FontWeight.Medium 
                        )
                        ResetButtonStyle.Module -> Triple(
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF666666),
                                contentColor = Color.White
                            ),
                            resetButtonText.ifEmpty { "Reset Settings" },
                            FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = resetCallback,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = buttonColors
                    ) {
                        Text(
                            text = buttonText,
                            fontWeight = fontWeight
                        )
                    }
                }
            }
        }
    }
}
