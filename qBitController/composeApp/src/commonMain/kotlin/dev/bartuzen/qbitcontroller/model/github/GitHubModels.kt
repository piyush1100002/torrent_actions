package dev.bartuzen.qbitcontroller.model.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubConfig(
    val token: String = "",
    val owner: String = "",
    val repo: String = "",
    val workflowFile: String = "torrent_download.yml",
    val branch: String = "main"
) {
    val isValid: Boolean
        get() = token.isNotBlank() && (owner.isNotBlank() || repo.isNotBlank())

    val displayRepo: String
        get() {
            val o = owner.ifBlank { repo.substringBefore("/") }
            val r = repo.substringAfter("/")
            return if (o.isNotBlank() && r.isNotBlank()) "$o/$r" else repo.ifBlank { owner }
        }
}

enum class WorkerStage(val label: String) {
    QUEUED("Queued"),
    SETTING_UP("Setting Up"),
    DOWNLOADING("Downloading"),
    UPLOADING_HF("Uploading to HuggingFace"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList()
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("display_title") val displayTitle: String? = null
)

@Serializable
data class DweetResponse(
    @SerialName("this") val status: String? = null,
    val with: List<DweetItem>? = null
)

@Serializable
data class DweetItem(
    val thing: String? = null,
    val created: String? = null,
    val content: kotlinx.serialization.json.JsonObject? = null
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

data class TorrentWorker(
    val runId: Long,
    val jobId: Long? = null,
    val torrentName: String,
    val imdbId: String? = null,
    val infoHash: String? = null,
    val stage: WorkerStage = WorkerStage.QUEUED,
    val progressPercent: Int = 0,
    val downloadedSize: String = "--",
    val totalSize: String = "--",
    val downloadSpeed: String = "--",
    val eta: String = "--",
    val peerCount: Int = 0,
    val tunnelUrl: String? = null,
    val qbtPassword: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val htmlUrl: String? = null
) {
    val isActive: Boolean
        get() = stage == WorkerStage.QUEUED ||
                stage == WorkerStage.SETTING_UP ||
                stage == WorkerStage.DOWNLOADING ||
                stage == WorkerStage.UPLOADING_HF
}
