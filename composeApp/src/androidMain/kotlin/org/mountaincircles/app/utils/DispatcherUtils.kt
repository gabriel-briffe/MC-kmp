package org.mountaincircles.app.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android implementation of dispatcher utilities
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
