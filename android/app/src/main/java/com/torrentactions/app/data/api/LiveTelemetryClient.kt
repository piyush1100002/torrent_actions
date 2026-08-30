package com.torrentactions.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "LiveTelemetryClient"

@Serializable
data class DweetResponse(
    @SerialName("this") val status: String? = null,
    val with: List<DweetItem>? = null
)

@Serializable
data class DweetItem(
    val thing: String? = null,
    val created: String? = null,
    val content: JsonObject? = null
)

data class LiveTelemetry(
    val stage: String = "queued",
    val percent: Int = 0,
    val downloaded: String = "--",
    val total: String = "--",
    val speed: String = "--",
    val eta: String = "--",
    val peers: Int = 0,
    val seeds: Int = 0,
    val tunnelUrl: String? = null,
    val qbtPassword: String? = null,
    val imdbId: String? = null
)

class LiveTelemetryClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLiveTelemetry(runId: Long): LiveTelemetry? = withContext(Dispatchers.IO) {
        try {
            val url = "https://dweet.cc/get/latest/dweet/for/torrent-actions-$runId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            if (body.isBlank() || !body.contains("succeeded")) return@withContext null

            val dweet = json.decodeFromString<DweetResponse>(body)
            val content = dweet.with?.firstOrNull()?.content ?: return@withContext null

            fun getStr(key: String): String? = content[key]?.jsonPrimitive?.contentOrNull

            val stage = getStr("stage") ?: "queued"
            val percent = getStr("percent")?.toIntOrNull() ?: 0
            val downloaded = getStr("downloaded") ?: "--"
            val total = getStr("total") ?: "--"
            val speed = getStr("speed") ?: "--"
            val eta = getStr("eta") ?: "--"
            val peers = getStr("peers")?.toIntOrNull() ?: 0
            val seeds = getStr("seeds")?.toIntOrNull() ?: 0
            val tunnelUrl = getStr("url")?.takeIf { it.isNotBlank() && it.startsWith("http") }
            val qbtPassword = getStr("pass")?.takeIf { it.isNotBlank() }
            val imdbId = getStr("imdb_id")?.takeIf { it.isNotBlank() }

            LiveTelemetry(
                stage = stage,
                percent = percent,
                downloaded = downloaded,
                total = total,
                speed = speed,
                eta = eta,
                peers = peers + seeds,
                seeds = seeds,
                tunnelUrl = tunnelUrl,
                qbtPassword = qbtPassword,
                imdbId = imdbId
            )
        } catch (e: Exception) {
            Log.d(TAG, "fetchLiveTelemetry #$runId error: ${e.message}")
            null
        }
    }
}
