package org.mountaincircles.app.io

import android.content.Context
import java.io.File

private var appContext: Context? = null

/**
 * Initialize app-local file storage. Call from MainActivity.onCreate().
 */
fun initializeAndroidAppStorage(context: Context) {
    appContext = context.applicationContext
}

internal fun requireAppContext(): Context {
    return appContext ?: throw IllegalStateException(
        "Android app storage not initialized. Call initializeAndroidAppStorage() from MainActivity."
    )
}

fun getFilesDirectory(): File = requireAppContext().filesDir
