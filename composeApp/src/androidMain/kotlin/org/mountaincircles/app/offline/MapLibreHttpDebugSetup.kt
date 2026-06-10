package org.mountaincircles.app.offline

import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Adds in-app HTTP tracing on top of MapLibre's existing OkHttp client.
 * Installed lazily when an offline download starts — not at app launch.
 */
internal fun installMapLibreHttpDebugClientIfNeeded() {
    if (installed) return
    synchronized(InstallLock) {
        if (installed) return
        val tracedClient = wrapWithTraceInterceptor(currentMapLibreCallFactory())
        HttpRequestUtil.setOkHttpClient(tracedClient)
        installed = true
    }
}

private fun wrapWithTraceInterceptor(factory: Call.Factory): OkHttpClient {
    val builder = when (factory) {
        is OkHttpClient -> factory.newBuilder()
        else -> OkHttpClient.Builder().dispatcher(mapLibreDispatcher())
    }
    return builder
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(OfflineDownloadHttpInterceptor())
        .build()
}

/** Preserve MapLibre's per-host concurrency (20 on API 21+). */
private fun mapLibreDispatcher(): Dispatcher =
    Dispatcher().apply { maxRequestsPerHost = 20 }

private fun currentMapLibreCallFactory(): Call.Factory {
    val implClass = Class.forName("org.maplibre.android.module.http.HttpRequestImpl")
    val field = implClass.getDeclaredField("client")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(null) as Call.Factory
}

private object InstallLock

@Volatile
private var installed = false

private class OfflineDownloadHttpInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var request = chain.request()
        val url = request.url.toString()

        if (request.url.host.endsWith("openstreetmap.org")) {
            request = request.newBuilder()
                .header("User-Agent", "MountainCirclesOffline/1.0 (+https://github.com/gabriel-briffe/MC-kmp)")
                .build()
        }

        if (OfflineDownloadHttpTracker.isActive) {
            OfflineDownloadHttpTracker.logRequest(url)
        }

        return try {
            val response = chain.proceed(request)
            if (OfflineDownloadHttpTracker.isActive) {
                val length = response.body?.contentLength()?.takeIf { it >= 0 }
                OfflineDownloadHttpTracker.logResponse(
                    url = url,
                    statusCode = response.code,
                    statusMessage = response.message.ifBlank { "OK" },
                    contentLength = length,
                )
            }
            response
        } catch (e: IOException) {
            if (OfflineDownloadHttpTracker.isActive) {
                OfflineDownloadHttpTracker.logFailure(url, statusCode = -1, message = e.message ?: "IO error")
            }
            throw e
        }
    }
}
