package com.torrentactions.app.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

private const val TAG = "GitHubApiClient"

class GitHubApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun dispatchWorkflow(
        token: String,
        owner: String,
        repo: String,
        workflowFile: String,
        branch: String,
        magnet: String,
        imdbId: String?,
        torrentName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches"
            val inputs = mutableMapOf("magnet" to magnet)
            if (!imdbId.isNullOrBlank()) {
                inputs["imdb_id"] = imdbId.trim()
            }
            if (!torrentName.isNullOrBlank()) {
                inputs["torrent_name"] = torrentName.trim()
            }
            val payload = DispatchPayload(ref = branch, inputs = inputs)
            val requestBody = json.encodeToString(payload).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 204) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string().orEmpty()
                Result.failure(IOException("GitHub Error [${response.code}]: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        workflowFile: String
    ): Result<List<WorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=25"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<WorkflowRunsResponse>(body)
                Result.success(parsed.workflowRuns)
            } else {
                val errorBody = response.body?.string().orEmpty()
                Result.failure(IOException("Error fetching runs [${response.code}]: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRunJobs(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<WorkflowJob>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "getRunJobs: runId=$runId http=${response.code} body_length=${body.length} preview=${body.take(300)}")
                val parsed = json.decodeFromString<WorkflowJobsResponse>(body)
                Result.success(parsed.jobs)
            } else {
                val errorBody = response.body?.string().orEmpty()
                val resetAt = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    ?.let { java.time.Instant.ofEpochSecond(it) }
                Log.w(TAG, "getRunJobs: runId=$runId http=${response.code} rateLimit_remaining=${response.header("X-RateLimit-Remaining")} resets_at=$resetAt")
                Result.failure(IOException("Error fetching jobs [${response.code}]: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRunProgress(
        token: String,
        owner: String,
        repo: String,
        runId: Long,
        branch: String = "main"
    ): Result<RunProgress?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/.torrent-progress/$runId.json?ref=$branch"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.code == 404) {
                return@withContext Result.success(null)
            }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return@withContext Result.failure(IOException("Error fetching progress file [${response.code}]: $errorBody"))
            }

            val body = response.body?.string().orEmpty()
            val fileResponse = json.decodeFromString<RepoContentResponse>(body)
            val content = fileResponse.content ?: return@withContext Result.success(null)
            val decoded = String(Base64.getDecoder().decode(content.replace("\n", "")), Charsets.UTF_8)
            val progress = parseProgressJson(decoded)
            Result.success(progress)
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    fun parseProgressJson(raw: String): RunProgress? {
        if (raw.isBlank()) return null
        return try {
            val payload = json.decodeFromString<RunProgressJson>(raw)
            RunProgress(
                stage = payload.stage ?: "queued",
                percent = payload.percent ?: 0,
                downloaded = payload.downloaded ?: "--",
                total = payload.total ?: "--",
                speed = payload.speed ?: "--",
                eta = payload.eta ?: "--",
                peers = payload.peers ?: 0,
                lastUpdated = payload.updatedAt ?: payload.timestamp ?: "",
                tunnelUrl = payload.tunnelUrl?.takeIf { it.isNotBlank() },
                qbtPassword = payload.qbtPassword?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getJobLogs(
        token: String,
        owner: String,
        repo: String,
        jobId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs"
            Log.d(TAG, "getJobLogs: jobId=$jobId url=$url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()

            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "getJobLogs: jobId=$jobId http=${response.code} finalUrl=${response.request.url}")
            return@withContext when {
                response.isSuccessful -> {
                    val payload = response.body?.bytes() ?: ByteArray(0)
                    Log.d(TAG, "getJobLogs: jobId=$jobId payload_bytes=${payload.size} header=${payload.take(4).map { it.toInt() and 0xFF }}")
                    val logs = decodeLogPayload(payload)
                    Log.d(TAG, "getJobLogs: jobId=$jobId decoded_chars=${logs.length} blank=${logs.isBlank()} preview=${logs.take(200).replace('\n', '|')}")
                    Result.success(logs)
                }
                response.code == 302 -> {
                    // Should not happen since OkHttp follows redirects, but log if it does
                    val location = response.header("Location") ?: ""
                    Log.w(TAG, "getJobLogs: jobId=$jobId got 302 redirect not followed, location=$location")
                    Result.success("")
                }
                response.code == 410 -> {
                    Log.w(TAG, "getJobLogs: jobId=$jobId 410 Gone - logs expired or job still running")
                    Result.success("")
                }
                response.code == 404 -> {
                    Log.w(TAG, "getJobLogs: jobId=$jobId 404 Not Found")
                    Result.success("")
                }
                else -> {
                    val body = response.body?.string().orEmpty()
                    Log.w(TAG, "getJobLogs: jobId=$jobId unexpected http=${response.code} body=${body.take(200)}")
                    Result.success("")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getJobLogs: jobId=$jobId exception: ${e.message}", e)
            Result.success("")
        }
    }

    @Serializable
    data class RepoContentResponse(
        @SerialName("content") val content: String? = null
    )

    @Serializable
    data class RunProgressJson(
        @SerialName("stage") val stage: String? = null,
        @SerialName("percent") val percent: Int? = null,
        @SerialName("downloaded") val downloaded: String? = null,
        @SerialName("total") val total: String? = null,
        @SerialName("speed") val speed: String? = null,
        @SerialName("eta") val eta: String? = null,
        @SerialName("peers") val peers: Int? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("timestamp") val timestamp: String? = null,
        @SerialName("tunnel_url") val tunnelUrl: String? = null,
        @SerialName("qbt_password") val qbtPassword: String? = null
    )

    data class RunProgress(
        val stage: String = "queued",
        val percent: Int = 0,
        val downloaded: String = "--",
        val total: String = "--",
        val speed: String = "--",
        val eta: String = "--",
        val peers: Int = 0,
        val lastUpdated: String = "",
        val tunnelUrl: String? = null,
        val qbtPassword: String? = null
    )

    companion object {
        // GitHub log lines are prefixed with a timestamp: "2024-01-01T00:00:00.0000000Z "
        private val logTimestampRegex = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z ")
        private val utfBom = "\uFEFF"

        private fun stripTimestamps(text: String): String {
            val stripped = if (text.startsWith(utfBom)) text.substring(1) else text
            return stripped.lines().joinToString("\n") { logTimestampRegex.replace(it, "") }
        }

        fun decodeLogPayload(payload: ByteArray): String {
            if (payload.isEmpty()) return ""
            return try {
                val zipHeader = byteArrayOf(0x50.toByte(), 0x4b.toByte())
                val gzipHeader = byteArrayOf(0x1f.toByte(), 0x8b.toByte())
                when {
                    payload.size >= 2 && payload.copyOfRange(0, 2).contentEquals(zipHeader) -> {
                        val chunks = mutableListOf<String>()
                        ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val text = zip.readBytes().toString(Charsets.UTF_8)
                                    if (text.isNotBlank()) chunks += stripTimestamps(text)
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                        chunks.joinToString("\n")
                    }
                    payload.size >= 2 && payload.copyOfRange(0, 2).contentEquals(gzipHeader) -> {
                        val text = java.util.zip.GZIPInputStream(ByteArrayInputStream(payload))
                            .use { it.readBytes().toString(Charsets.UTF_8) }
                        stripTimestamps(text)
                    }
                    else -> stripTimestamps(payload.toString(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                try { stripTimestamps(payload.toString(Charsets.ISO_8859_1)) } catch (_: Exception) { "" }
            }
        }
    }

    suspend fun getJobStepsSummary(
        token: String,
        owner: String,
        repo: String,
        jobId: Long
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext ""
            val body = response.body?.string().orEmpty()
            val job = json.decodeFromString<WorkflowJob>(body)
            Log.d(TAG, "getJobStepsSummary: jobId=$jobId steps=${job.steps.size} statuses=${job.steps.map { it.status }}")
            if (job.steps.isEmpty()) return@withContext ""
            job.steps.joinToString("\n") { step ->
                val icon = when (step.status) {
                    "completed" -> if (step.conclusion == "success") "✅" else "❌"
                    "in_progress" -> "⏳"
                    else -> "⬜"
                }
                "$icon  ${step.name}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "getJobStepsSummary: jobId=$jobId exception: ${e.message}")
            ""
        }
    }

    suspend fun cancelWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/cancel"
            val emptyBody = "".toRequestBody(null)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .post(emptyBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 202) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string().orEmpty()
                Result.failure(IOException("Error cancelling run [${response.code}]: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateConnection(
        token: String,
        owner: String,
        repo: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First check user token
            val userRequest = Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val userResp = okHttpClient.newCall(userRequest).execute()
            if (!userResp.isSuccessful) {
                return@withContext Result.failure(IOException("Invalid Token (HTTP ${userResp.code})"))
            }
            val userBody = userResp.body?.string().orEmpty()
            val user = json.decodeFromString<GitHubUserResponse>(userBody)

            // Then check repo access
            val repoRequest = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val repoResp = okHttpClient.newCall(repoRequest).execute()
            if (!repoResp.isSuccessful) {
                return@withContext Result.failure(IOException("Repository '$owner/$repo' not found or inaccessible (HTTP ${repoResp.code})"))
            }

            Result.success("Connected as @${user.login} to $owner/$repo")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestDeviceCode(
        clientId: String,
        scope: String = "repo workflow"
    ): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/login/device/code"
            val formBody = okhttp3.FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scope)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString<DeviceCodeResponse>(body)
                Result.success(parsed)
            } else {
                Result.failure(IOException("Failed to get device code [${response.code}]: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceToken(
        clientId: String,
        deviceCode: String
    ): Result<DeviceAccessTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/login/oauth/access_token"
            val formBody = okhttp3.FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString<DeviceAccessTokenResponse>(body)
                Result.success(parsed)
            } else {
                Result.failure(IOException("Polling failed [${response.code}]: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

