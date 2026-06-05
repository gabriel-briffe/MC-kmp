package org.mountaincircles.app.ui

import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel

/**
 * iOS implementation of glyph base URI
 */
actual val glyphsBaseUri: String get() {
    // For iOS, use the direct bundle path with Resources directory
    val bundlePath = NSBundle.mainBundle.bundlePath
    val uri = "$bundlePath/Resources/"  // Include Resources directory for iOS
    Logger.log("GLYPH_CONFIG", LogLevel.DEBUG, "Glyphs base URI: $uri (direct bundle path)")

    // Test if font file is accessible
    val testFontPath = "$bundlePath/Resources/Open Sans Regular/0-255.pbf"
    val fileManager = NSFileManager.defaultManager
    val fileExists = fileManager.fileExistsAtPath(testFontPath)
    Logger.log("GLYPH_CONFIG", LogLevel.DEBUG, "Test font file exists at $testFontPath: $fileExists")

    return uri
}
