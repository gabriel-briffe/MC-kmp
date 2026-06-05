package org.mountaincircles.app.modules.livetracking.logic

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position as GeoPosition
import org.mountaincircles.app.io.getGlobalNetworkMonitor
import org.mountaincircles.app.io.isAppInBackground
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.PolledBoundaries
import org.mountaincircles.app.utils.ScopeManager
import java.net.UnknownHostException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Handles viewport-based polling, camera-move debouncing, and OGN API fetch/XML parsing for LiveTracking.
 * Extracted from LiveTrackingModule for Phase D.
 */
class LiveTrackingPollingController(private val module: LiveTrackingModule) {

    private var pollingJob: Job? = null
    private var cameraChangeJob: Job? = null
    private var networkMonitoringJob: Job? = null
    private var isNetworkMonitoringActive = false

    /**
     * Start the 20s polling cycle if not already running
     */
    fun startPollingCycle(cameraState: CameraState) {
        if (pollingJob?.isActive != true) {
            pollingJob = ScopeManager.uiScope.launch {
                Logger.log("LIVETRACKING_API", LogLevel.INFO, "Started aircraft data polling cycle")
                while (true) {
                    kotlinx.coroutines.delay(20000)
                    handleAircraftPolling(cameraState)
                }
            }
        }
    }

    /**
     * Stop the polling cycle
     */
    fun stopPollingCycle() {
        pollingJob?.cancel()
        pollingJob = null
        Logger.log("LIVETRACKING_API", LogLevel.INFO, "Stopped aircraft data polling cycle")
    }

    /**
     * Handle camera movement with debouncing and smart repolling
     */
    fun handleCameraMove(cameraState: CameraState) {
        cameraChangeJob?.cancel()
        cameraChangeJob = ScopeManager.uiScope.launch {
            delay(200)
            try {
                val projection = cameraState.projection
                if (projection == null) {
                    Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Camera projection not available during camera move")
                    return@launch
                }
                val visibleRegion = projection.queryVisibleRegion()
                val lngs = listOf(
                    visibleRegion.farLeft.longitude,
                    visibleRegion.farRight.longitude,
                    visibleRegion.nearLeft.longitude,
                    visibleRegion.nearRight.longitude
                )
                val lats = listOf(
                    visibleRegion.farLeft.latitude,
                    visibleRegion.farRight.latitude,
                    visibleRegion.nearLeft.latitude,
                    visibleRegion.nearRight.latitude
                )
                val viewportWest = lngs.minOrNull() ?: visibleRegion.farLeft.longitude
                val viewportEast = lngs.maxOrNull() ?: visibleRegion.farRight.longitude
                val viewportSouth = lats.minOrNull() ?: visibleRegion.nearLeft.latitude
                val viewportNorth = lats.maxOrNull() ?: visibleRegion.farLeft.latitude
                val extendedWest = viewportWest - 2.0
                val extendedEast = viewportEast + 2.0
                val extendedSouth = viewportSouth - 2.0
                val extendedNorth = viewportNorth + 2.0
                val lastBoundaries = module.currentState.lastPolledBoundaries
                val currentViewportBounds = PolledBoundaries(viewportNorth, viewportSouth, viewportEast, viewportWest)
                val extendedViewportBounds = PolledBoundaries(extendedNorth, extendedSouth, extendedEast, extendedWest)
                val needsRepoll = if (lastBoundaries != null) {
                    val extendsNorth = viewportNorth > lastBoundaries.north
                    val extendsSouth = viewportSouth < lastBoundaries.south
                    val extendsEast = viewportEast > lastBoundaries.east
                    val extendsWest = viewportWest < lastBoundaries.west
                    Logger.log("LIVETRACKING_API", LogLevel.DEBUG,
                        "Viewport comparison - Last Polled (extended): [${"%.2f".format(lastBoundaries.north)}, ${"%.2f".format(lastBoundaries.south)}, ${"%.2f".format(lastBoundaries.east)}, ${"%.2f".format(lastBoundaries.west)}] " +
                        "Current Viewport (not extended): [${"%.2f".format(currentViewportBounds.north)}, ${"%.2f".format(currentViewportBounds.south)}, ${"%.2f".format(currentViewportBounds.east)}, ${"%.2f".format(currentViewportBounds.west)}] " +
                        "Extends: N=$extendsNorth S=$extendsSouth E=$extendsEast W=$extendsWest")
                    extendsNorth || extendsSouth || extendsEast || extendsWest
                } else {
                    Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "No previous polling data, initial poll needed")
                    true
                }
                if (needsRepoll) {
                    Logger.log("LIVETRACKING_API", LogLevel.INFO,
                        "Camera moved beyond polled boundaries, triggering immediate repoll with extended bounds: [$extendedNorth, $extendedSouth, $extendedEast, $extendedWest]")
                    module.updateState { currentState ->
                        currentState.copy(lastPolledBoundaries = extendedViewportBounds)
                    }
                    attemptPoll(extendedNorth, extendedSouth, extendedEast, extendedWest)
                    pollingJob?.cancel()
                    pollingJob = ScopeManager.uiScope.launch {
                        Logger.log("LIVETRACKING_API", LogLevel.INFO, "Restarted aircraft data polling cycle after camera move")
                        kotlinx.coroutines.delay(20000)
                        while (true) {
                            handleAircraftPolling(cameraState)
                            kotlinx.coroutines.delay(20000)
                        }
                    }
                } else {
                    Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Camera move within polled boundaries, continuing normal polling cycle")
                }
            } catch (e: Exception) {
                Logger.log("LIVETRACKING_API", LogLevel.ERROR, "Error handling camera move: ${e.message}", e)
            }
        }
    }

    /**
     * Handle aircraft data polling with viewport extent calculation
     */
    suspend fun handleAircraftPolling(cameraState: CameraState) {
        try {
            val projection = cameraState.projection
            if (projection == null) {
                Logger.log("LIVETRACKING_API", LogLevel.WARN, "Camera projection not available, skipping aircraft polling")
                return
            }
            val visibleRegion = projection.queryVisibleRegion()
            val lngs = listOf(
                visibleRegion.farLeft.longitude,
                visibleRegion.farRight.longitude,
                visibleRegion.nearLeft.longitude,
                visibleRegion.nearRight.longitude
            )
            val lats = listOf(
                visibleRegion.farLeft.latitude,
                visibleRegion.farRight.latitude,
                visibleRegion.nearLeft.latitude,
                visibleRegion.nearRight.latitude
            )
            val west = (lngs.minOrNull() ?: visibleRegion.farLeft.longitude) - 2.0
            val east = (lngs.maxOrNull() ?: visibleRegion.farRight.longitude) + 2.0
            val south = (lats.minOrNull() ?: visibleRegion.nearLeft.latitude) - 2.0
            val north = (lats.maxOrNull() ?: visibleRegion.farLeft.latitude) + 2.0
            Logger.log("LIVETRACKING_API", LogLevel.INFO,
                "Polling aircraft data: viewport extent + 2° buffer=[$north, $south, $east, $west]")
            module.updateState { currentState ->
                currentState.copy(lastPolledBoundaries = PolledBoundaries(north, south, east, west))
            }
            attemptPoll(north, south, east, west)
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_API", LogLevel.ERROR, "Error in aircraft polling: ${e.message}", e)
        }
    }

    /**
     * Attempt to poll aircraft data - checks network validation before polling
     */
    suspend fun attemptPoll(north: Double, south: Double, east: Double, west: Double) {
        val networkMonitor = getGlobalNetworkMonitor()
        if (networkMonitor != null) {
            val isValidated = networkMonitor.isNetworkCurrentlyValidated()
            Logger.log("NETWORK", LogLevel.DEBUG, "Network validation check: isValidated=$isValidated")
            if (!isValidated) {
                Logger.log("NETWORK", LogLevel.DEBUG, "Network not validated - waiting for network restoration before polling")
                if (!isNetworkMonitoringActive) {
                    startNetworkMonitoringAfterFailure()
                }
                return
            }
            if (isNetworkMonitoringActive) {
                Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Stopping network monitoring - network is validated")
                stopNetworkMonitoring()
            }
        } else {
            Logger.log("NETWORK", LogLevel.WARN, "Network monitor not available, proceeding with poll")
        }
        pollAircraftData(north, south, east, west)
    }

    private suspend fun pollAircraftData(north: Double, south: Double, east: Double, west: Double) {
        if (isAppInBackground()) {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Skipping poll - app in background")
            return
        }
        if (!waitedEnough()) {
            Logger.log("NETWORK", LogLevel.DEBUG, "Skipped network request - less than 5 seconds since last update")
            return
        }
        try {
            val httpClient = HttpClient {
                install(HttpTimeout) {
                    socketTimeoutMillis = 8000
                    connectTimeoutMillis = 5000
                    requestTimeoutMillis = 15000
                }
            }
            val url = "https://live.glidernet.org/lxml.php?a=0&b=$north&c=$south&d=$east&e=$west"
            val lastBounds = module.currentState.lastPolledBoundaries
            val boundsStr = "[${"%.2f".format(north)}, ${"%.2f".format(south)}, ${"%.2f".format(east)}, ${"%.2f".format(west)}]"
            val lastBoundsStr = if (lastBounds != null) {
                " vs last=[${"%.2f".format(lastBounds.north)}, ${"%.2f".format(lastBounds.south)}, ${"%.2f".format(lastBounds.east)}, ${"%.2f".format(lastBounds.west)}]"
            } else ""
            Logger.log("NETWORK", LogLevel.INFO, "Requesting aircraft data: bounds=$boundsStr$lastBoundsStr")
            val response: HttpResponse = httpClient.get(url)
            val xmlContent = response.bodyAsText()
            Logger.log("NETWORK", LogLevel.INFO, "Received ${xmlContent.length} chars of XML data")
            xmlContent.lines().forEach { line ->
                if (line.isNotBlank()) {
                    Logger.log("LIVETRACKING_API", LogLevel.INFO, "  $line")
                }
            }
            val features = parseAircraftXmlToFeatures(xmlContent)
            Logger.log("LIVETRACKING_API", LogLevel.INFO, "Generated ${features.size} features from XML data")
            val now = Clock.System.now().toEpochMilliseconds()
            module.updateState { currentState ->
                val mergedFeatures = currentState.aircraftFeatures.toMutableMap()
                mergedFeatures.putAll(features)
                Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Merged features: ${currentState.aircraftFeatures.size} existing -> ${mergedFeatures.size} after merge (added ${features.size} from poll)")
                currentState.copy(
                    aircraftFeatures = mergedFeatures,
                    lastUpdateTimestamp = now
                )
            }
            updateAllTimeAgoInFeatures()
            cleanupOldFeatures()
            Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Updated GeoJSON and features with new aircraft data")
            if (isNetworkMonitoringActive) {
                Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Stopping network monitoring after successful poll")
                stopNetworkMonitoring()
                Logger.log("NETWORK", LogLevel.INFO, "Network confirmed working - resuming normal polling behavior")
            }
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_API", LogLevel.ERROR, "Failed to poll aircraft data: ${e.message}", e)
            if (isNetworkFailure(e)) {
                startNetworkMonitoringAfterFailure()
            }
        }
    }

    private fun isNetworkFailure(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("network") ||
            message.contains("connection") ||
            message.contains("timeout") ||
            message.contains("unreachable") ||
            message.contains("dns") ||
            message.contains("unable to resolve") ||
            message.contains("no address associated") ||
            e is ConnectTimeoutException ||
            e is SocketTimeoutException ||
            e is UnknownHostException
    }

    private fun startNetworkMonitoringAfterFailure() {
        if (isNetworkMonitoringActive) {
            Logger.log("LIVETRACKING_NETWORK", LogLevel.DEBUG, "Network monitoring already active, skipping")
            return
        }
        Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Starting network monitoring after poll failure")
        networkMonitoringJob?.cancel()
        networkMonitoringJob = ScopeManager.uiScope.launch {
            try {
                val networkMonitor = getGlobalNetworkMonitor()
                if (networkMonitor == null) {
                    Logger.log("LIVETRACKING_NETWORK", LogLevel.WARN, "Network monitoring not available on this platform")
                    return@launch
                }
                isNetworkMonitoringActive = true
                Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Network monitoring activated, waiting for connectivity restoration")
                networkMonitor.isNetworkAvailable.collect { isAvailable ->
                    if (isAvailable) {
                        Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Network connectivity restored! Triggering immediate poll")
                        stopNetworkMonitoring()
                        triggerImmediatePollAfterNetworkRestoration()
                    }
                }
            } catch (e: Exception) {
                Logger.log("LIVETRACKING_NETWORK", LogLevel.ERROR, "Error in network monitoring: ${e.message}", e)
            } finally {
                isNetworkMonitoringActive = false
            }
        }
    }

    private fun stopNetworkMonitoring() {
        Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Stopping network monitoring")
        networkMonitoringJob?.cancel()
        networkMonitoringJob = null
        isNetworkMonitoringActive = false
    }

    private fun triggerImmediatePollAfterNetworkRestoration() {
        Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Triggering immediate poll after network restoration")
        val lastBoundaries = module.currentState.lastPolledBoundaries
        if (lastBoundaries != null) {
            Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Using last polled boundaries for immediate poll")
            ScopeManager.uiScope.launch {
                try {
                    pollAircraftData(
                        lastBoundaries.north,
                        lastBoundaries.south,
                        lastBoundaries.east,
                        lastBoundaries.west
                    )
                    Logger.log("LIVETRACKING_NETWORK", LogLevel.INFO, "Immediate poll after network restoration completed successfully")
                    Logger.log("NETWORK", LogLevel.INFO, "Network restored - resuming normal polling behavior")
                } catch (e: Exception) {
                    Logger.log("LIVETRACKING_NETWORK", LogLevel.ERROR, "Failed immediate poll after network restoration: ${e.message}", e)
                }
            }
        } else {
            Logger.log("LIVETRACKING_NETWORK", LogLevel.WARN, "No last polled boundaries available, skipping immediate poll")
            Logger.log("NETWORK", LogLevel.INFO, "Network restored - resuming normal polling behavior (no immediate poll)")
        }
    }

    private fun calculateTimeAgo(lastTimeStr: String): String {
        try {
            val timeParts = lastTimeStr.split(":")
            if (timeParts.size != 3) return ""
            val lastHour = timeParts[0].toIntOrNull() ?: return ""
            val lastMinute = timeParts[1].toIntOrNull() ?: return ""
            val lastSecond = timeParts[2].toIntOrNull() ?: return ""
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val currentHour = now.hour
            val currentMinute = now.minute
            val currentSecond = now.second
            val lastTotalSeconds = lastHour * 3600 + lastMinute * 60 + lastSecond
            val currentTotalSeconds = currentHour * 3600 + currentMinute * 60 + currentSecond
            val diffSeconds = currentTotalSeconds - lastTotalSeconds
            val adjustedDiffSeconds = if (diffSeconds < 0) diffSeconds + 86400 else diffSeconds
            val diffMinutes = adjustedDiffSeconds / 60
            val diffHours = diffMinutes / 60
            return when {
                diffHours >= 1 -> "${diffHours}h"
                diffMinutes >= 1 -> "${diffMinutes}mn"
                else -> "<1mn"
            }
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_API", LogLevel.WARN, "Failed to parse time: $lastTimeStr", e)
            return ""
        }
    }

    private fun updateAllTimeAgoInFeatures() {
        module.updateState { currentState ->
            val updatedFeatures = currentState.aircraftFeatures.mapValues { (_, feature) ->
                val lastTime = when (val prop = feature.properties["lastTime"]) {
                    is JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                    else -> prop?.toString()
                }
                val newTimeAgo = lastTime?.let { calculateTimeAgo(it) } ?: ""
                val updatedProperties = buildJsonObject {
                    feature.properties.entries.forEach { (key, value) ->
                        if (key == "timeAgo") put(key, newTimeAgo)
                        else put(key, value)
                    }
                }
                feature.copy(properties = updatedProperties)
            }
            Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Updated timeAgo for ${updatedFeatures.size} aircraft features")
            currentState.copy(aircraftFeatures = updatedFeatures)
        }
    }

    private fun cleanupOldFeatures() {
        module.updateState { currentState ->
            val cleanedFeatures = currentState.aircraftFeatures.filter { (_, feature) ->
                val lastTime = when (val prop = feature.properties["lastTime"]) {
                    is JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                    else -> prop?.toString()
                }
                lastTime?.let { timeStr ->
                    try {
                        val parts = timeStr.split(":")
                        if (parts.size == 3) {
                            val hours = parts[0].toInt()
                            val minutes = parts[1].toInt()
                            val seconds = parts[2].toInt()
                            val lastTimeSeconds = hours * 3600 + minutes * 60 + seconds
                            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                            val currentTimeSeconds = now.hour * 3600 + now.minute * 60 + now.second
                            val diffSeconds = currentTimeSeconds - lastTimeSeconds
                            val adjustedDiffSeconds = if (diffSeconds < 0) diffSeconds + 86400 else diffSeconds
                            adjustedDiffSeconds <= (module.aircraftDataTimeout * 60.0f).toInt()
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
            Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Cleaned features: ${currentState.aircraftFeatures.size} -> ${cleanedFeatures.size} after removing old aircraft")
            currentState.copy(aircraftFeatures = cleanedFeatures)
        }
    }

    private fun waitedEnough(): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val timeSinceLastUpdate = now - module.currentState.lastUpdateTimestamp
        return timeSinceLastUpdate >= 5000
    }

    private fun getTimeDifferenceSeconds(lastTimeStr: String): Int {
        try {
            val timeParts = lastTimeStr.split(":")
            if (timeParts.size != 3) return Int.MAX_VALUE
            val lastHour = timeParts[0].toIntOrNull() ?: return Int.MAX_VALUE
            val lastMinute = timeParts[1].toIntOrNull() ?: return Int.MAX_VALUE
            val lastSecond = timeParts[2].toIntOrNull() ?: return Int.MAX_VALUE
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val lastTotalSeconds = lastHour * 3600 + lastMinute * 60 + lastSecond
            val currentTotalSeconds = now.hour * 3600 + now.minute * 60 + now.second
            val diffSeconds = currentTotalSeconds - lastTotalSeconds
            return if (diffSeconds < 0) diffSeconds + 86400 else diffSeconds
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_API", LogLevel.WARN, "Failed to parse time for age check: $lastTimeStr", e)
            return Int.MAX_VALUE
        }
    }

    private fun parseAircraftXmlToFeatures(xmlContent: String): Map<String, Feature<Point, JsonObject>> {
        val features = mutableMapOf<String, Feature<Point, JsonObject>>()
        try {
            val lines = xmlContent.lines()
            Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Total lines: ${lines.size}")
            val aircraftLines = lines.drop(1).dropLast(1)
            for (line in aircraftLines) {
                try {
                    val dataString = line.removePrefix("<m a=\"").removeSuffix("\"/>")
                    val parts = dataString.split(",")
                    if (parts.size >= 14) {
                        val latitude = parts[0].toDouble()
                        val longitude = parts[1].toDouble()
                        val registrationShort = parts[2]
                        val fullRegistration = parts[3]
                        val altitude = parts[4].toIntOrNull() ?: 0
                        val lastTime = parts[5]
                        val track = parts[7].toIntOrNull() ?: 0
                        val groundSpeed = parts[8].toIntOrNull() ?: 0
                        val verticalSpeed = parts[9].toDoubleOrNull() ?: 0.0
                        val aircraftTypeCode = parts[10].toIntOrNull() ?: 0
                        val receiverName = parts[11]
                        val receiverId = parts[12]
                        val deviceId = parts[13]
                        val aircraftTypes = arrayOf(
                            "unknown", "Glider/MotorGlider", "Tow Plane", "Helicopter",
                            "Parachute", "Drop Plane", "Hangglider", "Paraglider",
                            "Plane", "Jet", "UFO", "Balloon", "Airship", "Drone",
                            "unknown", "Static Object"
                        )
                        val aircraftType = if (aircraftTypeCode in aircraftTypes.indices) aircraftTypes[aircraftTypeCode] else "unknown"
                        val isGlider = aircraftTypeCode == 1
                        if (!isGlider) continue
                        Logger.log("LIVETRACKING_API", LogLevel.INFO,
                            "Aircraft: ${fullRegistration} (${registrationShort}) at ${latitude}, ${longitude} | " +
                            "Alt: ${altitude}m | Time: ${lastTime} | Track: ${track}° | GS: ${groundSpeed}km/h | VZ: ${verticalSpeed}m/s | Type: ${aircraftType}")
                        val timeAgo = calculateTimeAgo(lastTime)
                        val timeDiffSeconds = getTimeDifferenceSeconds(lastTime)
                        val timeoutSeconds = (module.aircraftDataTimeout * 60.0f).toInt()
                        if (timeDiffSeconds > timeoutSeconds) {
                            Logger.log("LIVETRACKING_API", LogLevel.DEBUG, "Skipping aircraft ${fullRegistration} - last update ${timeDiffSeconds} seconds ago (>${timeoutSeconds} sec timeout)")
                            continue
                        }
                        val friendlist = module.currentState.friendlist
                        val friendEntry = friendlist.find { it.deviceId == deviceId }
                        val displayName = friendEntry?.customName ?: registrationShort
                        val properties = buildJsonObject {
                            put("registration", fullRegistration)
                            put("registrationShort", registrationShort)
                            put("displayName", displayName)
                            put("altitude", altitude.toString())
                            put("lastTime", lastTime)
                            put("track", track)
                            put("groundSpeed", groundSpeed.toString())
                            put("verticalSpeed", verticalSpeed.toString())
                            put("aircraftType", aircraftType)
                            put("receiverName", receiverName)
                            put("receiverId", receiverId)
                            put("deviceId", deviceId)
                            put("isFriend", friendlist.any { it.deviceId == deviceId }.toString())
                            put("timeAgo", timeAgo)
                        }
                        val geometry = Point(GeoPosition(longitude = longitude, latitude = latitude))
                        val feature = Feature(geometry = geometry, properties = properties)
                        features[deviceId] = feature
                    }
                } catch (e: Exception) {
                    Logger.log("LIVETRACKING_API", LogLevel.WARN, "Skipping malformed line: $line", e)
                }
            }
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_API", LogLevel.ERROR, "Failed to parse XML: ${e.message}", e)
        }
        return features
    }
}
