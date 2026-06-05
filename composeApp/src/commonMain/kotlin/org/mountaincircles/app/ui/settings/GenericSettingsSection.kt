package org.mountaincircles.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.settings.*

/**
 * Generic settings section that automatically generates UI from metadata.
 *
 * This composable takes settings metadata and automatically renders the appropriate
 * UI components for each field, with proper grouping, validation, and styling.
 */
@Composable
fun GenericSettingsSection(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    showGrouped: Boolean = true,
    showAdvancedFields: Boolean = false,
    enabled: Boolean = true,
    showHeader: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Section header
        if (showHeader && metadata.name.isNotEmpty()) {
            SettingsSectionHeader(
                title = metadata.name,
                description = metadata.description,
                icon = metadata.icon
            )
        }

        // Render fields
        if (showGrouped) {
            RenderGroupedFields(
                metadata = metadata,
                values = values,
                onValueChange = onValueChange,
                showAdvancedFields = showAdvancedFields,
                enabled = enabled
            )
        } else {
            RenderFlatFields(
                metadata = metadata,
                values = values,
                onValueChange = onValueChange,
                showAdvancedFields = showAdvancedFields,
                enabled = enabled
            )
        }
    }
}

/**
 * Render fields in grouped layout (organized by group name)
 */
@Composable
private fun RenderGroupedFields(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    showAdvancedFields: Boolean,
    enabled: Boolean
) {
    val groupedFields = SettingsMetadataUtils.getGroupedFields(metadata)

    groupedFields.forEach { (groupName, fields) ->
        val visibleFields = fields.filter { field ->
            (showAdvancedFields || !field.isAdvanced) && !field.isHidden
        }

        if (visibleFields.isNotEmpty()) {
            SettingsGroup(
                title = groupName,
                fields = visibleFields,
                values = values,
                onValueChange = onValueChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Render fields in flat layout (no grouping)
 */
@Composable
private fun RenderFlatFields(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    showAdvancedFields: Boolean,
    enabled: Boolean
) {
    val visibleFields = metadata.fields.filter { field ->
        (showAdvancedFields || !field.isAdvanced) && !field.isHidden
    }

    visibleFields.forEach { field ->
        SettingsUIUtils.RenderField(
            metadata = field,
            value = values[field.name],
            onValueChange = { onValueChange(field.name, it) },
            modifier = Modifier.padding(vertical = 8.dp),
            enabled = enabled
        )
    }
}

/**
 * Settings section header with title, description, and icon
 */
@Composable
private fun SettingsSectionHeader(
    title: String,
    description: String,
    icon: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon placeholder (could be extended to render actual icons)
                if (icon.isNotEmpty()) {
                    Text(
                        text = "⚙️",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * A group of related settings fields
 */
@Composable
private fun SettingsGroup(
    title: String,
    fields: List<FieldMetadata>,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    enabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Group header
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Cyan,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Group fields
            fields.forEach { field ->
                SettingsUIUtils.RenderField(
                    metadata = field,
                    value = values[field.name],
                    onValueChange = { onValueChange(field.name, it) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    enabled = enabled
                )
            }
        }
    }
}

/**
 * Compact settings section for small spaces (like sidebars)
 */
@Composable
fun CompactSettingsSection(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier) {
        // Only show non-hidden, non-advanced fields
        val compactFields = metadata.fields.filter { field ->
            !field.isHidden && !field.isAdvanced
        }.take(5) // Limit to first 5 fields for compact view

        compactFields.forEach { field ->
            SettingsUIUtils.RenderField(
                metadata = field,
                value = values[field.name],
                onValueChange = { onValueChange(field.name, it) },
                modifier = Modifier.padding(vertical = 4.dp),
                enabled = enabled
            )
        }

        // Show "More Settings..." indicator if there are additional fields
        if (metadata.fields.size > compactFields.size) {
            TextButton(
                onClick = { /* Navigate to full settings */ },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "More Settings...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Cyan
                )
            }
        }
    }
}

/**
 * Settings section with search/filter functionality
 */
@Composable
fun SearchableSettingsSection(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Settings") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Cyan,
                unfocusedIndicatorColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Filter fields based on search
        val filteredFields = if (searchQuery.isEmpty()) {
            metadata.fields
        } else {
            metadata.fields.filter { field ->
                field.name.contains(searchQuery, ignoreCase = true) ||
                field.label.contains(searchQuery, ignoreCase = true) ||
                field.description.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filteredFields.isNotEmpty()) {
            // Render filtered fields in flat layout
            filteredFields.forEach { field ->
                if (!field.isHidden) {
                    SettingsUIUtils.RenderField(
                        metadata = field,
                        value = values[field.name],
                        onValueChange = { onValueChange(field.name, it) },
                        modifier = Modifier.padding(vertical = 4.dp),
                        enabled = enabled
                    )
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            Text(
                text = "No settings found matching \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Settings section with validation summary
 */
@Composable
fun ValidatedSettingsSection(
    metadata: ClassMetadata,
    values: Map<String, Any?>,
    onValueChange: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showValidationSummary: Boolean = true
) {
    val validationErrors = remember(values) {
        metadata.fields.mapNotNull { field ->
            val validationResult = SettingsMetadataUtils.validateField(field, values[field.name])
            if (validationResult.isNotEmpty()) {
                field to validationResult.first()
            } else {
                null
            }
        }
    }

    Column(modifier = modifier) {
        // Validation summary
        if (showValidationSummary && validationErrors.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A1A1A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ Validation Issues",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold
                    )

                    validationErrors.forEach { pair ->
                        val field = pair.first
                        val error = pair.second
                        Text(
                            text = "• ${field.name}: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Yellow,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Render fields with validation
        metadata.fields.forEach { field ->
            if (!field.isHidden) {
                SettingsUIUtils.RenderField(
                    metadata = field,
                    value = values[field.name],
                    onValueChange = { onValueChange(field.name, it) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    enabled = enabled
                )
            }
        }
    }
}
