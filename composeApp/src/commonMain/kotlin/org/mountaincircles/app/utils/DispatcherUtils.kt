package org.mountaincircles.app.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Cross-platform dispatcher utilities
 */
expect val ioDispatcher: CoroutineDispatcher
