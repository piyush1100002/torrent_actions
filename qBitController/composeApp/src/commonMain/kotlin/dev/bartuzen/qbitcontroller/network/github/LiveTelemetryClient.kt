package dev.bartuzen.qbitcontroller.network.github

import dev.bartuzen.qbitcontroller.model.github.DweetResponse
import dev.bartuzen.qbitcontroller.model.github.LiveTelemetry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class LiveTelemetryClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 4000
            connectTimeoutMillis = 4000
            socketTimeoutMillis = 4000
        }
    }

    suspend fun fetchLiveTelemetry(runId: Long): LiveTelemetry? = withContext(Dispatchers.IO) {
        try {
            val url = "https://dweet.cc/get/latest/dweet/for/torrent-actions-$runId"
            val response = client.get(url)
            if (response.status != HttpStatusCode.OK) return@withContext null

            val dweet: DweetResponse = response.body()
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
        } catch (_: Exception) {
            null
        }
    }
}
