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
import org.mountaincircles.app.ui.components.GenericBottomSheet
import org.mountaincircles.app.ui.components.BottomSheetConfigs

/**
 * Simple About sheet - displays app information
 */
@Composable
fun AboutComposable(onDismiss: () -> Unit = {}) {
    GenericBottomSheet(
        visible = true, // Always visible when called
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "About sheet dismissed")
            onDismiss() // Notify parent to reset state
        },
        config = BottomSheetConfigs.Settings, // Use same config as settings
        content = {
            AboutContent()
        }
    )
}

/**
 * About content - scrollable container for app information
 */
@Composable
private fun AboutContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "About MountainCircles",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Content column - all left-aligned for coherency
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Disclaimer
            Text(
                text = "This is an experimental project, none of the information here is guaranteed to be correct.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            // Contact
            Text(
                text = "Feedback and bugs to gabriel.briffe@gmail.com",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            // Acknowledgments header
            Text(
                text = "Thanks to:",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Acknowledgments items
            Text(
                text = "• Open Street Map for the map",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• Shuttle Radar Topography Mission for the topographic model",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• The Open Glider Network for the livetracking data",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• SkySight for the great gliding forecasts",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• Meteo France for the Vertical Velocity and Wind forecast of the Arome model",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• OpenAip for the aeronautical data",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• Maplibre, Maplibre Compose and their contributors",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "• All those who had the original ideas that have been copied and gathered in this app",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth()
            )
        }

    }
}