package org.mountaincircles.app.modules.circles.submenu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.logic.data.PackMetadata
import org.mountaincircles.app.ui.components.GenericSidebarWidget

@Composable
fun CirclePacksWidget(
    circlesModule: CirclesModule,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // ✅ UNIFIED STATE: Direct access to state data
    val circlesState by circlesModule.circlesState.collectAsState()

    val availableConfigs = circlesState.availableConfigs
    val activeConfig = circlesState.activeConfig

    // Group packs by policy (from metadata)
    val packsByPolicy = remember(availableConfigs) {
        availableConfigs.groupBy { pack ->
            pack.metadata?.policy ?: "Unknown"
        }
    }

    // Track selected pack (policy + prefix combination)
    val selectedPackKey = remember(activeConfig) {
        activeConfig?.let { config ->
            val policy = config.metadata?.policy ?: "Unknown"
            val prefix = config.metadata?.prefix ?: config.configId
            "$policy:$prefix"
        }
    }

    GenericSidebarWidget(
        title = "Circle Packs",
        modifier = modifier
    ) {
        // Content based on state
        if (availableConfigs.isEmpty()) {
            Text(
                text = "No circle packs available",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        } else {
            // Display packs grouped by policy
            packsByPolicy.forEach { entry ->
                val policy = entry.key
                val packs = entry.value
                PolicySection(
                    policy = policy,
                    packs = packs,
                    selectedPackKey = selectedPackKey,
                    circlesModule = circlesModule,
                    scope = scope,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PolicySection(
    policy: String,
    packs: List<PackConfig>,
    selectedPackKey: String?,
    circlesModule: CirclesModule,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Policy header - centered
        Text(
            text = policy,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Prefix buttons
        packs.forEach { pack ->
            val prefix = pack.metadata?.prefix ?: pack.configId
            val packKey = "$policy:$prefix"
            val isSelected = packKey == selectedPackKey

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                PrefixButton(
                    prefix = prefix,
                    isSelected = isSelected,
                    onClick = {
                        Logger.log("CIRCLE_PACKS", LogLevel.INFO, "Selecting pack: $policy/$prefix")

                        // Launch coroutine to select the pack
                        scope.launch {
                            try {
                                circlesModule.selectPackConfig(pack.packId, pack.configId)
                                Logger.log("CIRCLE_PACKS", LogLevel.INFO, "Successfully selected pack: $policy/$prefix")
                            } catch (e: Exception) {
                                Logger.log("CIRCLE_PACKS", LogLevel.ERROR, "Failed to select pack: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)  // 80% width for responsive centering
                )
            }
        }
    }
}

@Composable
private fun PrefixButton(
    prefix: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColors = if (isSelected) {
        ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1976D2), // Material Blue
            contentColor = Color.White
        )
    } else {
        ButtonDefaults.textButtonColors(
            contentColor = Color.LightGray
        )
    }

    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = buttonColors,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = prefix,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = prefix,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
