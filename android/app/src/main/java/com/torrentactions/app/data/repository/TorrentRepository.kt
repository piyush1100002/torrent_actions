package com.torrentactions.app.data.repository

import android.util.Log
import com.torrentactions.app.data.api.GitHubApiClient
import com.torrentactions.app.data.api.LiveTelemetryClient
import com.torrentactions.app.data.api.QBTorrentFile
import com.torrentactions.app.data.api.QBittorrentApiClient
import com.torrentactions.app.data.local.SecurePreferences
import com.torrentactions.app.data.parser.Aria2LogParser
import com.torrentactions.app.data.parser.WorkerStage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val TAG = "TorrentRepository"

private val qbtClient = QBittorrentApiClient()
private val telemetryClient = LiveTelemetryClient()
private val customTitleCache = mutableMapOf<Long, String>()

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

class TorrentRepository(
    private val apiClient: GitHubApiClient,
    private val prefs: SecurePreferences
) {
    private var isFetching = false

    suspend fun dispatchTorrent(magnet: String, imdbId: String?, torrentName: String? = null): Result<Unit> {
        val token = prefs.gitHubToken
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("GitHub Token not configured. Please open Settings."))
        }
        return apiClient.dispatchWorkflow(
            token = token,
            owner = prefs.repoOwner,
            repo = prefs.repoName,
            workflowFile = prefs.workflowFile,
            branch = prefs.targetBranch,
            magnet = magnet,
            imdbId = imdbId,
            torrentName = torrentName
        )
    }

    suspend fun cancelWorker(runId: Long): Result<Unit> {
        val token = prefs.gitHubToken
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("GitHub Token not configured."))
        }
        return apiClient.cancelWorkflowRun(
            token = token,
            owner = prefs.repoOwner,
            repo = prefs.repoName,
            runId = runId
        )
    }

    suspend fun getTorrentFiles(worker: TorrentWorker): Result<List<QBTorrentFile>> {
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash
        if (tunnelUrl.isNullOrBlank() || password.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Direct connection to worker not yet established."))
        }
        val targetHash = if (!hash.isNullOrBlank()) hash else {
            val info = qbtClient.getTorrentInfo(tunnelUrl, password).getOrNull()
            info?.hash ?: ""
        }
        return qbtClient.getTorrentFiles(tunnelUrl, password, targetHash)
    }

    suspend fun renameTorrent(worker: TorrentWorker, newName: String): Result<Unit> {
        customTitleCache[worker.runId] = newName
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash
        if (!tunnelUrl.isNullOrBlank() && !password.isNullOrBlank()) {
            val targetHash = if (!hash.isNullOrBlank()) hash else {
                val info = qbtClient.getTorrentInfo(tunnelUrl, password).getOrNull()
                info?.hash ?: ""
            }
            if (targetHash.isNotBlank()) {
                qbtClient.renameTorrent(tunnelUrl, password, targetHash, newName)
            }
        }
        return Result.success(Unit)
    }

    suspend fun renameFile(worker: TorrentWorker, oldPath: String, newPath: String): Result<Unit> {
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash
        if (tunnelUrl.isNullOrBlank() || password.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Direct connection not active."))
        }
        val targetHash = if (!hash.isNullOrBlank()) hash else {
            val info = qbtClient.getTorrentInfo(tunnelUrl, password).getOrNull()
            info?.hash ?: ""
        }
        return qbtClient.renameFile(tunnelUrl, password, targetHash, oldPath, newPath)
    }

    suspend fun fetchAllWorkers(): Result<List<TorrentWorker>> = coroutineScope {
        if (isFetching) {
            return@coroutineScope Result.success(emptyList())
        }
        isFetching = true
        try {
            val token = prefs.gitHubToken
            if (token.isBlank()) {
                return@coroutineScope Result.failure(IllegalStateException("Please configure your GitHub Token in Settings."))
            }

            val runsResult = apiClient.getWorkflowRuns(
                token = token,
                owner = prefs.repoOwner,
                repo = prefs.repoName,
                workflowFile = prefs.workflowFile
            )

            if (runsResult.isFailure) {
                return@coroutineScope Result.failure(runsResult.exceptionOrNull() ?: Exception("Unknown error"))
            }

            val runs = runsResult.getOrDefault(emptyList())
            val activeRuns = runs.filter { it.conclusion == null }

            // Fetch live telemetry for all active runs in parallel
            val activeTelemetryDeferreds = activeRuns.map { run ->
                async { run.id to telemetryClient.fetchLiveTelemetry(run.id) }
            }
            val telemetryResults = activeTelemetryDeferreds.awaitAll().toMap()

            val workers = runs.map { run ->
                val isActive = run.conclusion == null
                val telemetry = telemetryResults[run.id]

                val effectiveTunnelUrl = telemetry?.tunnelUrl
                val effectiveQbtPassword = telemetry?.qbtPassword

                // If direct tunnel is live, fetch high-frequency stats from qBittorrent
                val qbtInfo = if (isActive && !effectiveTunnelUrl.isNullOrBlank() && !effectiveQbtPassword.isNullOrBlank()) {
                    qbtClient.getTorrentInfo(effectiveTunnelUrl, effectiveQbtPassword).getOrNull()
                } else null

                val stage = when {
                    run.conclusion == "success" -> WorkerStage.COMPLETED
                    run.conclusion == "cancelled" -> WorkerStage.CANCELLED
                    run.conclusion == "failure" || run.conclusion == "timed_out" -> WorkerStage.FAILED
                    telemetry != null -> when (telemetry.stage.lowercase()) {
                        "queued" -> WorkerStage.QUEUED
                        "setting_up", "initializing" -> WorkerStage.SETTING_UP
                        "downloading" -> WorkerStage.DOWNLOADING
                        "uploading_hf", "uploading", "pushing" -> WorkerStage.UPLOADING_HF
                        "completed" -> WorkerStage.COMPLETED
                        "failed" -> WorkerStage.FAILED
                        "cancelled" -> WorkerStage.CANCELLED
                        else -> if (qbtInfo != null) WorkerStage.DOWNLOADING else WorkerStage.QUEUED
                    }
                    isActive -> WorkerStage.QUEUED
                    else -> WorkerStage.COMPLETED
                }

                val fmt = { v: Long -> when {
                    v >= 1_073_741_824L -> "%.2f GiB".format(v / 1_073_741_824.0)
                    v >= 1_048_576L -> "%.1f MiB".format(v / 1_048_576.0)
                    else -> "%.1f KiB".format(v / 1024.0)
                }}

                val etaText = qbtInfo?.eta?.let { e -> when {
                    e >= 8640000 -> "unknown"
                    e >= 3600 -> "${e / 3600}h ${(e % 3600) / 60}m"
                    e >= 60 -> "${e / 60}m ${e % 60}s"
                    else -> "${e}s"
                }}

                val name = customTitleCache[run.id]
                    ?: qbtInfo?.name?.takeIf { it.isNotBlank() }
                    ?: run.displayTitle
                    ?: "Torrent #${run.id}"

                val progressPercent = when {
                    stage == WorkerStage.COMPLETED -> 100
                    stage == WorkerStage.UPLOADING_HF -> 100
                    qbtInfo != null -> (qbtInfo.progress * 100).toInt()
                    telemetry != null -> telemetry.percent
                    else -> 0
                }

                val downloadedSize = when {
                    qbtInfo != null -> fmt(qbtInfo.downloaded)
                    telemetry != null && telemetry.downloaded != "--" -> telemetry.downloaded
                    else -> "--"
                }

                val totalSize = when {
                    qbtInfo != null -> fmt(qbtInfo.totalSize.takeIf { it > 0 } ?: qbtInfo.size)
                    telemetry != null && telemetry.total != "--" -> telemetry.total
                    else -> "--"
                }

                val speed = when {
                    stage != WorkerStage.DOWNLOADING -> "--"
                    qbtInfo != null -> fmt(qbtInfo.dlSpeed) + "/s"
                    telemetry != null && telemetry.speed != "--" -> telemetry.speed
                    else -> "--"
                }

                val eta = when {
                    stage != WorkerStage.DOWNLOADING -> "--"
                    etaText != null -> etaText
                    telemetry != null && telemetry.eta != "--" -> telemetry.eta
                    else -> "--"
                }

                val peers = when {
                    qbtInfo != null -> qbtInfo.numLeechs + qbtInfo.numSeeds
                    telemetry != null -> telemetry.peers
                    else -> 0
                }

                TorrentWorker(
                    runId = run.id,
                    jobId = null,
                    torrentName = name,
                    imdbId = telemetry?.imdbId,
                    infoHash = qbtInfo?.hash,
                    stage = stage,
                    progressPercent = progressPercent,
                    downloadedSize = downloadedSize,
                    totalSize = totalSize,
                    downloadSpeed = speed,
                    eta = eta,
                    peerCount = peers,
                    tunnelUrl = effectiveTunnelUrl,
                    qbtPassword = effectiveQbtPassword,
                    createdAt = run.createdAt,
                    startedAt = run.runStartedAt,
                    htmlUrl = run.htmlUrl
                )
            }

            Result.success(workers)
        } finally {
            isFetching = false
        }
    }

    suspend fun validateConnection(token: String, owner: String, repo: String): Result<String> {
        return apiClient.validateConnection(token, owner, repo)
    }
}
