package org.mountaincircles.app.offline

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Routes MapLibre tile/style HTTP through OkHttp with optional in-app tracing.
 */
fun installMapLibreHttpDebugClient() {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(OfflineDownloadHttpInterceptor())
        .build()
    HttpRequestUtil.setOkHttpClient(client)
}

private class OfflineDownloadHttpInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url.toString()

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
