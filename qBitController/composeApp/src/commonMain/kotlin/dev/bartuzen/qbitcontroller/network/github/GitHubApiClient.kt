package dev.bartuzen.qbitcontroller.network.github

import dev.bartuzen.qbitcontroller.model.github.GitHubWorkflowRun
import dev.bartuzen.qbitcontroller.model.github.GitHubWorkflowRunsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class GitHubApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun sanitizeOwnerRepo(owner: String, repo: String): Pair<String, String> {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").trim('/')
        return if (cleanRepo.contains("/")) {
            val o = cleanRepo.substringBefore("/").trim()
            val r = cleanRepo.substringAfter("/").trim()
            (if (owner.isNotBlank()) owner.trim() else o) to r
        } else {
            owner.trim() to cleanRepo
        }
    }

    private fun sanitizeWorkflow(wf: String): String {
        return wf.trim().removePrefix(".github/workflows/").ifBlank { "torrent_download.yml" }
    }

    suspend fun getWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        workflowFile: String = "torrent_download.yml"
    ): Result<List<GitHubWorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val (o, r) = sanitizeOwnerRepo(owner, repo)
            val wf = sanitizeWorkflow(workflowFile)
            val url = "https://api.github.com/repos/$o/$r/actions/workflows/$wf/runs?per_page=30"
            val response = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "qBitController-TorrentActions")
            }

            if (response.status == HttpStatusCode.OK) {
                val parsed: GitHubWorkflowRunsResponse = response.body()
                Result.success(parsed.workflowRuns)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("GitHub API error (${response.status.value}): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchWorkflow(
        token: String,
        owner: String,
        repo: String,
        workflowFile: String = "torrent_download.yml",
        branch: String = "main",
        magnet: String,
        imdbId: String? = null,
        torrentName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (o, r) = sanitizeOwnerRepo(owner, repo)
            val wf = sanitizeWorkflow(workflowFile)
            val url = "https://api.github.com/repos/$o/$r/actions/workflows/$wf/dispatches"
            val payload = buildJsonObject {
                put("ref", branch.ifBlank { "main" })
                putJsonObject("inputs") {
                    put("magnet", magnet)
                    if (!imdbId.isNullOrBlank()) {
                        put("imdb_id", imdbId)
                    }
                    if (!torrentName.isNullOrBlank()) {
                        put("torrent_name", torrentName)
                    }
                }
            }

            val response = client.post(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "qBitController-TorrentActions")
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }

            if (response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                Result.success(Unit)
            } else {
                val body = response.bodyAsText()
                Result.failure(Exception("Dispatch failed (${response.status.value}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (o, r) = sanitizeOwnerRepo(owner, repo)
            val url = "https://api.github.com/repos/$o/$r/actions/runs/$runId/cancel"
            val response = client.post(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "qBitController-TorrentActions")
            }

            if (response.status == HttpStatusCode.Accepted || response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                val body = response.bodyAsText()
                Result.failure(Exception("Cancel failed (${response.status.value}): $body"))
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
            val (o, r) = sanitizeOwnerRepo(owner, repo)
            val url = "https://api.github.com/repos/$o/$r"
            val response = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header("User-Agent", "qBitController-TorrentActions")
            }

            if (response.status == HttpStatusCode.OK) {
                Result.success("Connected to $o/$r successfully!")
            } else {
                Result.failure(Exception("Validation failed (${response.status.value}): ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
