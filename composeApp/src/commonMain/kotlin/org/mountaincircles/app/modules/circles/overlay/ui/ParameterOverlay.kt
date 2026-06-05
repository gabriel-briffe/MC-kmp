package org.mountaincircles.app.modules.circles.overlay.ui

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

/**
 * Generic parameter overlay component for displaying text-based parameters
 */
@Composable
fun ParameterOverlay(
    text: String,
    backgroundColor: Color = Color.Black.copy(alpha = 0.7f),
    textColor: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(0.8f)
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            fontWeight = fontWeight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}
