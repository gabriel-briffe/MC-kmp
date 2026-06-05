# Circle Pack Definitions

This folder contains the configuration for all available downloadable circle packs.

## How to Add New Packs

To add a new downloadable circle pack, simply add a new `CirclePackDefinition` to the `availablePacks` list in `CirclePackDefinitions.kt`.

### Example:

```kotlin
CirclePackDefinition(
    packId = "pyrenees",           // Region identifier
    configId = "25-100-250",       // Configuration (ground-circuit-radius)
    displayName = "Pyrenees 25-100-250"  // User-friendly name
)
```

### Parameters:

- **packId**: The region or mountain range identifier (e.g., "alpes", "pyrenees", "rockies")
- **configId**: The circle configuration in format "X-Y-Z" where:
  - X = Ground level circle radius (meters)
  - Y = Circuit level circle radius (meters)
  - Z = Maximum circle radius (meters)
- **displayName**: User-friendly name shown in the UI
- **url**: Optional custom URL. If not provided, defaults to `https://data.mountain-circles.org/{packId}_{configId}.zip`

### Automatic Features:

1. **URL Construction**: Automatically builds download URL from packId and configId
2. **File Naming**: Automatically generates filename as `{packId}_{configId}.zip`
3. **UI Generation**: Buttons are automatically created in the import UI
4. **State Management**: Download/upload/delete states are automatically managed
5. **Visual Feedback**: Green backgrounds and appropriate icons based on pack status

### Adding Multiple Configurations:

You can add multiple configurations for the same region:

```kotlin
CirclePackDefinition(
    packId = "alpes",
    configId = "25-100-250",
    displayName = "Alps Standard"
),

CirclePackDefinition(
    packId = "alpes",
    configId = "15-100-250",
    displayName = "Alps High Detail"
)
```

The system will automatically create separate download buttons for each configuration and manage their individual states.

### File Structure:

- `CirclePackDefinition.kt`: Contains the data classes and configuration
- `README.md`: This documentation file

The UI will automatically update to show all defined packs as soon as you add them to the `availablePacks` list!
