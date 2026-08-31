package dev.bartuzen.qbitcontroller.network.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QBTorrentInfo(
    val hash: String = "",
    val name: String = "",
    val state: String = "",
    val progress: Float = 0f,
    @SerialName("dlspeed") val dlSpeed: Long = 0,
    @SerialName("upspeed") val upSpeed: Long = 0,
    val eta: Long = 8640000,
    val size: Long = 0,
    val downloaded: Long = 0,
    val uploaded: Long = 0,
    @SerialName("num_seeds") val numSeeds: Int = 0,
    @SerialName("num_leechs") val numLeechs: Int = 0,
    @SerialName("total_size") val totalSize: Long = 0
)

@Serializable
data class QBTorrentFile(
    val index: Int = 0,
    val name: String = "",
    val size: Long = 0,
    val progress: Float = 0f,
    val priority: Int = 1,
    @SerialName("is_seed") val isSeed: Boolean = false,
    val availability: Float = 0f
)

class QBittorrentTunnelClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 6000
            connectTimeoutMillis = 6000
            socketTimeoutMillis = 6000
        }
    }

    private val loggedInTunnels = mutableSetOf<String>()

    private suspend fun ensureAuthenticated(tunnelUrl: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = tunnelUrl.trimEnd('/')
        if (loggedInTunnels.contains(cleanUrl)) return@withContext Result.success(Unit)

        try {
            val response: HttpResponse = client.submitForm(
                url = "$cleanUrl/api/v2/auth/login",
                formParameters = parameters {
                    append("username", "admin")
                    append("password", password)
                }
            )

            val body = response.bodyAsText()
            if (response.status == HttpStatusCode.OK && (body.startsWith("Ok") || body.isBlank())) {
                loggedInTunnels.add(cleanUrl)
                Result.success(Unit)
            } else {
                Result.failure(Exception("qBittorrent auth failed: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTorrentInfo(tunnelUrl: String, password: String): Result<QBTorrentInfo?> = withContext(Dispatchers.IO) {
        val cleanUrl = tunnelUrl.trimEnd('/')
        try {
            val authRes = ensureAuthenticated(cleanUrl, password)
            if (authRes.isFailure) return@withContext Result.failure(authRes.exceptionOrNull()!!)

            var response: HttpResponse = client.get("$cleanUrl/api/v2/torrents/info")
            if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.Unauthorized) {
                loggedInTunnels.remove(cleanUrl)
                val reauth = ensureAuthenticated(cleanUrl, password)
                if (reauth.isFailure) return@withContext Result.failure(reauth.exceptionOrNull()!!)
                response = client.get("$cleanUrl/api/v2/torrents/info")
            }

            if (response.status == HttpStatusCode.OK) {
                val list: List<QBTorrentInfo> = response.body()
                Result.success(list.firstOrNull())
            } else {
                Result.failure(Exception("torrents/info status: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTorrentFiles(tunnelUrl: String, password: String, hash: String): Result<List<QBTorrentFile>> = withContext(Dispatchers.IO) {
        val cleanUrl = tunnelUrl.trimEnd('/')
        try {
            ensureAuthenticated(cleanUrl, password)
            val response: HttpResponse = client.get("$cleanUrl/api/v2/torrents/files?hash=$hash")
            if (response.status == HttpStatusCode.OK) {
                val files: List<QBTorrentFile> = response.body()
                Result.success(files)
            } else {
                Result.failure(Exception("torrents/files status: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameTorrent(tunnelUrl: String, password: String, hash: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = tunnelUrl.trimEnd('/')
        try {
            ensureAuthenticated(cleanUrl, password)
            val response: HttpResponse = client.submitForm(
                url = "$cleanUrl/api/v2/torrents/rename",
                formParameters = parameters {
                    append("hash", hash)
                    append("name", newName)
                }
            )
            if (response.status == HttpStatusCode.OK) Result.success(Unit)
            else Result.failure(Exception("rename status: ${response.status.value}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(
        tunnelUrl: String,
        password: String,
        hash: String,
        oldPath: String,
        newPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = tunnelUrl.trimEnd('/')
        try {
            ensureAuthenticated(cleanUrl, password)
            val response: HttpResponse = client.submitForm(
                url = "$cleanUrl/api/v2/torrents/renameFile",
                formParameters = parameters {
                    append("hash", hash)
                    append("oldPath", oldPath)
                    append("newPath", newPath)
                }
            )
            if (response.status == HttpStatusCode.OK) Result.success(Unit)
            else Result.failure(Exception("renameFile status: ${response.status.value}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
