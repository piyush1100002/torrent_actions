package com.torrentactions.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "QBittorrentApiClient"

@Serializable
data class QBTorrentInfo(
    val hash: String = "",
    val name: String = "",
    val state: String = "",
    val progress: Float = 0f,
    @SerialName("dlspeed") val dlSpeed: Long = 0,
    val eta: Long = 8640000,
    val size: Long = 0,
    val downloaded: Long = 0,
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

class QBittorrentApiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val sidCache = ConcurrentHashMap<String, String>()

    private suspend fun getAuthenticatedSid(tunnelUrl: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val cached = sidCache[tunnelUrl]
        if (cached != null) return@withContext Result.success(cached)

        try {
            val loginResp = client.newCall(
                Request.Builder()
                    .url("$tunnelUrl/api/v2/auth/login")
                    .post(FormBody.Builder().add("username", "admin").add("password", password).build())
                    .build()
            ).execute()

            val loginText = loginResp.body?.string().orEmpty()
            if (!loginText.startsWith("Ok")) {
                return@withContext Result.failure(Exception("qBittorrent auth failed: $loginText"))
            }

            val sid = loginResp.headers("Set-Cookie")
                .firstOrNull { it.startsWith("SID=") }
                ?.substringAfter("SID=")?.substringBefore(";")
                ?: return@withContext Result.failure(Exception("No SID cookie in login response"))

            sidCache[tunnelUrl] = sid
            Result.success(sid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTorrentInfo(tunnelUrl: String, password: String): Result<QBTorrentInfo?> = withContext(Dispatchers.IO) {
        try {
            var sidResult = getAuthenticatedSid(tunnelUrl, password)
            if (sidResult.isFailure) return@withContext Result.failure(sidResult.exceptionOrNull()!!)
            var sid = sidResult.getOrThrow()

            var infoResp = client.newCall(
                Request.Builder()
                    .url("$tunnelUrl/api/v2/torrents/info")
                    .addHeader("Cookie", "SID=$sid")
                    .get()
                    .build()
            ).execute()

            if (infoResp.code == 403) {
                // SID expired, re-authenticate
                sidCache.remove(tunnelUrl)
                sidResult = getAuthenticatedSid(tunnelUrl, password)
                if (sidResult.isFailure) return@withContext Result.failure(sidResult.exceptionOrNull()!!)
                sid = sidResult.getOrThrow()
                infoResp = client.newCall(
                    Request.Builder()
                        .url("$tunnelUrl/api/v2/torrents/info")
                        .addHeader("Cookie", "SID=$sid")
                        .get()
                        .build()
                ).execute()
            }

            if (!infoResp.isSuccessful) {
                return@withContext Result.failure(Exception("torrents/info http=${infoResp.code}"))
            }

            val list = json.decodeFromString<List<QBTorrentInfo>>(infoResp.body?.string().orEmpty())
            Result.success(list.firstOrNull())
        } catch (e: Exception) {
            Log.d(TAG, "getTorrentInfo failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getTorrentFiles(tunnelUrl: String, password: String, hash: String): Result<List<QBTorrentFile>> = withContext(Dispatchers.IO) {
        try {
            val sidResult = getAuthenticatedSid(tunnelUrl, password)
            if (sidResult.isFailure) return@withContext Result.failure(sidResult.exceptionOrNull()!!)
            val sid = sidResult.getOrThrow()

            val resp = client.newCall(
                Request.Builder()
                    .url("$tunnelUrl/api/v2/torrents/files?hash=$hash")
                    .addHeader("Cookie", "SID=$sid")
                    .get()
                    .build()
            ).execute()

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("torrents/files http=${resp.code}"))
            }

            val files = json.decodeFromString<List<QBTorrentFile>>(resp.body?.string().orEmpty())
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "getTorrentFiles failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun renameTorrent(tunnelUrl: String, password: String, hash: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sidResult = getAuthenticatedSid(tunnelUrl, password)
            if (sidResult.isFailure) return@withContext Result.failure(sidResult.exceptionOrNull()!!)
            val sid = sidResult.getOrThrow()

            val form = FormBody.Builder()
                .add("hash", hash)
                .add("name", newName)
                .build()

            val resp = client.newCall(
                Request.Builder()
                    .url("$tunnelUrl/api/v2/torrents/rename")
                    .addHeader("Cookie", "SID=$sid")
                    .post(form)
                    .build()
            ).execute()

            if (resp.isSuccessful || resp.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("rename failed http=${resp.code}"))
            }
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
        try {
            val sidResult = getAuthenticatedSid(tunnelUrl, password)
            if (sidResult.isFailure) return@withContext Result.failure(sidResult.exceptionOrNull()!!)
            val sid = sidResult.getOrThrow()

            val form = FormBody.Builder()
                .add("hash", hash)
                .add("oldPath", oldPath)
                .add("newPath", newPath)
                .build()

            val resp = client.newCall(
                Request.Builder()
                    .url("$tunnelUrl/api/v2/torrents/renameFile")
                    .addHeader("Cookie", "SID=$sid")
                    .post(form)
                    .build()
            ).execute()

            if (resp.isSuccessful || resp.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("renameFile failed http=${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
