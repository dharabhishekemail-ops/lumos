package com.lumos.core.remoteconfig

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteConfigFetcher(
    private val http: OkHttpClient = OkHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetch(url: String, timeoutMs: Long): ByteArray? = withContext(dispatcher) {
        val req = Request.Builder().url(url).get().build()
        val client = http.newBuilder()
            .callTimeout(java.time.Duration.ofMillis(timeoutMs))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }
}
