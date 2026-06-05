package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase

/**
 * Generic import sheet composable for modules that support import functionality
 * but don't have a custom import sheet implementation yet.
 */
@Composable
fun GenericImportSheet(module: ModuleBase) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Module icon
        module.getIcon()?.let { icon ->
            Icon(
                painter = icon,
                contentDescription = "${module.displayName} Icon",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Module name
        Text(
            text = module.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = "Import functionality for ${module.displayName} will be available here.",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Placeholder button
        Button(
            onClick = {
                Logger.log("UI", LogLevel.INFO, "Generic import sheet action for ${module.moduleId}")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Gray.copy(alpha = 0.3f),
                contentColor = Color.White
            )
        ) {
            Text("Import ${module.displayName} Data")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer text
        Text(
            text = "Custom import sheet coming soon!",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
