package dev.bartuzen.qbitcontroller.data.repositories.github

import dev.bartuzen.qbitcontroller.model.Category
import dev.bartuzen.qbitcontroller.model.MainData
import dev.bartuzen.qbitcontroller.model.ServerState
import dev.bartuzen.qbitcontroller.model.Torrent
import dev.bartuzen.qbitcontroller.model.TorrentState
import dev.bartuzen.qbitcontroller.model.github.GitHubConfig
import dev.bartuzen.qbitcontroller.model.github.TorrentWorker
import dev.bartuzen.qbitcontroller.model.github.WorkerStage
import dev.bartuzen.qbitcontroller.network.github.GitHubApiClient
import dev.bartuzen.qbitcontroller.network.github.LiveTelemetryClient
import dev.bartuzen.qbitcontroller.network.github.QBTorrentFile
import dev.bartuzen.qbitcontroller.network.github.QBTorrentInfo
import dev.bartuzen.qbitcontroller.network.github.QBittorrentTunnelClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

class GitHubWorkerRepository(
    private val gitHubApiClient: GitHubApiClient,
    private val liveTelemetryClient: LiveTelemetryClient,
    private val qbtTunnelClient: QBittorrentTunnelClient,
) {
    private val customTitleCache = mutableMapOf<Long, String>()
    private val knownWorkers = mutableMapOf<Long, TorrentWorker>()
    private val workerForHash = mutableMapOf<String, TorrentWorker>()

    val cachedWorkers: List<TorrentWorker>
        get() = knownWorkers.values.sortedByDescending { it.runId }

    suspend fun dispatchTorrent(
        config: GitHubConfig,
        magnet: String,
        imdbId: String? = null,
        torrentName: String? = null
    ): Result<Unit> {
        return gitHubApiClient.dispatchWorkflow(
            token = config.token,
            owner = config.owner,
            repo = config.repo,
            workflowFile = config.workflowFile,
            branch = config.branch,
            magnet = magnet,
            imdbId = imdbId,
            torrentName = torrentName
        )
    }

    suspend fun cancelWorker(config: GitHubConfig, runId: Long): Result<Unit> {
        return gitHubApiClient.cancelWorkflowRun(
            token = config.token,
            owner = config.owner,
            repo = config.repo,
            runId = runId
        )
    }

    fun getWorkerByHashOrRunId(identifier: String): TorrentWorker? {
        val runId = identifier.removePrefix("worker_").toLongOrNull()
        if (runId != null && knownWorkers.containsKey(runId)) {
            return knownWorkers[runId]
        }
        return workerForHash[identifier] ?: knownWorkers.values.find { it.infoHash.equals(identifier, ignoreCase = true) }
    }

    suspend fun getTorrentFiles(worker: TorrentWorker): Result<List<QBTorrentFile>> {
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash ?: ""
        if (tunnelUrl.isNullOrBlank() || password.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Direct connection not yet established to worker."))
        }
        return qbtTunnelClient.getTorrentFiles(tunnelUrl, password, hash)
    }

    suspend fun renameTorrent(worker: TorrentWorker, newName: String): Result<Unit> {
        customTitleCache[worker.runId] = newName
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash
        if (!tunnelUrl.isNullOrBlank() && !password.isNullOrBlank() && !hash.isNullOrBlank()) {
            return qbtTunnelClient.renameTorrent(tunnelUrl, password, hash, newName)
        }
        return Result.success(Unit)
    }

    suspend fun renameFile(worker: TorrentWorker, oldPath: String, newPath: String): Result<Unit> {
        val tunnelUrl = worker.tunnelUrl
        val password = worker.qbtPassword
        val hash = worker.infoHash
        if (tunnelUrl.isNullOrBlank() || password.isNullOrBlank() || hash.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Direct connection not active."))
        }
        return qbtTunnelClient.renameFile(tunnelUrl, password, hash, oldPath, newPath)
    }

    suspend fun fetchWorkers(config: GitHubConfig): Result<List<TorrentWorker>> = coroutineScope {
        try {
            val runsResult = gitHubApiClient.getWorkflowRuns(
                token = config.token,
                owner = config.owner,
                repo = config.repo,
                workflowFile = config.workflowFile
            )

            if (runsResult.isFailure) {
                return@coroutineScope Result.failure(runsResult.exceptionOrNull() ?: Exception("Failed to fetch workflow runs"))
            }

            val runs = runsResult.getOrDefault(emptyList())
            val activeRuns = runs.filter { it.conclusion == null }

            // 1. Fetch live telemetry for active runs concurrently
            val telemetryDeferreds = activeRuns.map { run ->
                async { run.id to liveTelemetryClient.fetchLiveTelemetry(run.id) }
            }
            val telemetryMap = telemetryDeferreds.awaitAll().toMap()

            // 2. Fetch live qBittorrent info from runner tunnels for runs where tunnel is up
            val qbtInfoDeferreds = activeRuns.map { run ->
                val telemetry = telemetryMap[run.id]
                val tunnelUrl = telemetry?.tunnelUrl
                val password = telemetry?.qbtPassword
                async {
                    if (!tunnelUrl.isNullOrBlank() && !password.isNullOrBlank()) {
                        run.id to qbtTunnelClient.getTorrentInfo(tunnelUrl, password).getOrNull()
                    } else {
                        run.id to null
                    }
                }
            }
            val qbtInfoMap = qbtInfoDeferreds.awaitAll().toMap()

            val workers = activeRuns.mapNotNull { run ->
                val telemetry = telemetryMap[run.id]
                val qbtInfo = qbtInfoMap[run.id]

                val effectiveTunnelUrl = telemetry?.tunnelUrl
                val effectiveQbtPassword = telemetry?.qbtPassword

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
                    else -> WorkerStage.QUEUED
                }

                if (stage == WorkerStage.COMPLETED || stage == WorkerStage.CANCELLED || stage == WorkerStage.FAILED) {
                    return@mapNotNull null
                }

                // Prefer actual torrent name returned by qBittorrent, otherwise cached or clean display title
                val title = customTitleCache[run.id]
                    ?: qbtInfo?.name?.takeIf { it.isNotBlank() }
                    ?: run.displayTitle?.takeIf { it.isNotBlank() && it != config.workflowFile }
                    ?: telemetry?.imdbId?.takeIf { it.isNotBlank() }
                    ?: "Torrent Worker #${run.id}"

                val progress = when {
                    stage == WorkerStage.COMPLETED || stage == WorkerStage.UPLOADING_HF -> 100
                    qbtInfo != null -> (qbtInfo.progress * 100).toInt()
                    telemetry != null -> telemetry.percent
                    else -> 0
                }

                val dlSize = when {
                    qbtInfo != null -> formatBytesToString(qbtInfo.downloaded)
                    telemetry != null && telemetry.downloaded != "--" -> telemetry.downloaded
                    else -> "--"
                }

                val totSize = when {
                    qbtInfo != null -> formatBytesToString(qbtInfo.totalSize.takeIf { it > 0 } ?: qbtInfo.size)
                    telemetry != null && telemetry.total != "--" -> telemetry.total
                    else -> "--"
                }

                val speed = when {
                    stage != WorkerStage.DOWNLOADING -> "--"
                    qbtInfo != null -> formatBytesToString(qbtInfo.dlSpeed) + "/s"
                    telemetry != null && telemetry.speed != "--" -> telemetry.speed
                    else -> "--"
                }

                val eta = when {
                    stage != WorkerStage.DOWNLOADING -> "--"
                    qbtInfo != null -> formatEtaToString(qbtInfo.eta)
                    telemetry != null && telemetry.eta != "--" -> telemetry.eta
                    else -> "--"
                }

                val peers = when {
                    qbtInfo != null -> qbtInfo.numLeechs + qbtInfo.numSeeds
                    telemetry != null -> telemetry.peers
                    else -> 0
                }

                val worker = TorrentWorker(
                    runId = run.id,
                    jobId = null,
                    torrentName = title,
                    imdbId = telemetry?.imdbId,
                    infoHash = qbtInfo?.hash,
                    stage = stage,
                    progressPercent = progress,
                    downloadedSize = dlSize,
                    totalSize = totSize,
                    downloadSpeed = speed,
                    eta = eta,
                    peerCount = peers,
                    tunnelUrl = effectiveTunnelUrl,
                    qbtPassword = effectiveQbtPassword,
                    createdAt = run.createdAt,
                    startedAt = run.runStartedAt,
                    htmlUrl = run.htmlUrl
                )

                knownWorkers[run.id] = worker
                if (!worker.infoHash.isNullOrBlank()) {
                    workerForHash[worker.infoHash] = worker
                }
                worker
            }

            Result.success(workers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAggregatedMainData(config: GitHubConfig): Result<MainData> = withContext(Dispatchers.Default) {
        val workersResult = fetchWorkers(config)
        if (workersResult.isFailure) {
            return@withContext Result.failure(workersResult.exceptionOrNull() ?: Exception("Error fetching workers"))
        }

        val workers = workersResult.getOrDefault(emptyList())
        val torrentList = mutableListOf<Torrent>()
        val categoriesSet = mutableSetOf<String>()
        val tagsSet = mutableSetOf<String>()
        var totalDlSpeed = 0L
        var totalUlSpeed = 0L

        workers.forEach { worker ->
            val workerTag = "Worker #${worker.runId}"
            val stageTag = worker.stage.label
            tagsSet.add(workerTag)
            tagsSet.add(stageTag)

            val category = worker.imdbId
            if (!category.isNullOrBlank()) {
                categoriesSet.add(category)
            }

            val torrentState = when (worker.stage) {
                WorkerStage.QUEUED, WorkerStage.SETTING_UP -> TorrentState.QUEUED_DL
                WorkerStage.DOWNLOADING -> TorrentState.DOWNLOADING
                WorkerStage.UPLOADING_HF -> TorrentState.UPLOADING
                WorkerStage.COMPLETED -> TorrentState.PAUSED_UP
                WorkerStage.CANCELLED -> TorrentState.PAUSED_DL
                WorkerStage.FAILED -> TorrentState.ERROR
            }

            val speedBytes = parseSpeedToBytes(worker.downloadSpeed)
            if (worker.stage == WorkerStage.DOWNLOADING) {
                totalDlSpeed += speedBytes
            }

            val approxTotalBytes = parseSizeToBytes(worker.totalSize)
            val approxDlBytes = parseSizeToBytes(worker.downloadedSize)

            val torrentHash = worker.infoHash ?: "worker_${worker.runId}"

            val syntheticTorrent = Torrent(
                hash = torrentHash,
                hashV1 = null,
                hashV2 = null,
                name = worker.torrentName,
                state = torrentState,
                additionDate = parseDateToEpoch(worker.createdAt),
                completionDate = if (worker.stage == WorkerStage.COMPLETED) Clock.System.now() else null,
                completed = approxDlBytes,
                size = approxTotalBytes,
                eta = parseEtaToSeconds(worker.eta),
                downloadSpeed = speedBytes,
                uploadSpeed = 0L,
                downloadSpeedLimit = 0,
                uploadSpeedLimit = 0,
                progress = worker.progressPercent / 100.0,
                priority = 1,
                connectedSeeds = 0,
                connectedLeeches = worker.peerCount,
                totalSeeds = 0,
                totalLeeches = worker.peerCount,
                savePath = "/downloads",
                downloadPath = null,
                category = category,
                tags = listOf(workerTag, stageTag),
                isSequentialDownloadEnabled = false,
                isFirstLastPiecesPrioritized = false,
                isAutomaticTorrentManagementEnabled = false,
                isForceStartEnabled = false,
                isSuperSeedingEnabled = false,
                magnetUri = "",
                timeActive = 0L,
                downloaded = approxDlBytes,
                downloadedSession = approxDlBytes,
                uploaded = 0L,
                uploadedSession = 0L,
                ratio = 0.0,
                lastActivity = Clock.System.now(),
                lastSeenComplete = null,
                ratioLimit = -1.0,
                seedingTimeLimit = -1,
                inactiveSeedingTimeLimit = -1,
                seedingTime = 0,
                trackerCount = 0,
                isPrivate = false,
                popularity = null,
                availability = 1.0,
                workerRunId = worker.runId,
                workerTunnelUrl = worker.tunnelUrl,
                workerPassword = worker.qbtPassword,
                workerStage = worker.stage.label,
            )

            torrentList.add(syntheticTorrent)
        }

        val categories = categoriesSet.map { Category(name = it, savePath = "/downloads/$it") }

        val serverState = ServerState(
            allTimeUpload = 0L,
            allTimeDownload = torrentList.sumOf { it.downloaded },
            areSubcategoriesEnabled = false,
            globalRatio = "0.0",
            sessionWaste = 0L,
            connectedPeers = workers.sumOf { it.peerCount.toLong() },
            readCacheHits = "0",
            bufferSize = 0L,
            writeCacheOverload = "0",
            readCacheOverload = "0",
            queuedIOJobs = 0L,
            averageTimeInQueue = 0L,
            queuedSize = 0L,
            downloadSession = torrentList.sumOf { it.downloaded },
            downloadSpeed = totalDlSpeed,
            downloadSpeedLimit = 0,
            uploadSession = 0L,
            uploadSpeed = totalUlSpeed,
            uploadSpeedLimit = 0,
            useAlternativeSpeedLimits = false,
            isQueueingEnabled = true,
            freeSpace = 100_000_000_000L
        )

        val mainData = MainData(
            rid = 1,
            serverState = serverState,
            torrents = torrentList,
            categories = categories,
            tags = tagsSet.sorted(),
            trackers = emptyMap()
        )

        Result.success(mainData)
    }

    private fun formatBytesToString(v: Long): String {
        return when {
            v >= 1_073_741_824L -> {
                val gib = v / 1_073_741_824.0
                "${((gib * 100).toLong() / 100.0)} GiB"
            }
            v >= 1_048_576L -> {
                val mib = v / 1_048_576.0
                "${((mib * 10).toLong() / 10.0)} MiB"
            }
            v >= 1024L -> {
                val kib = v / 1024.0
                "${((kib * 10).toLong() / 10.0)} KiB"
            }
            else -> "$v B"
        }
    }

    private fun formatEtaToString(e: Long): String {
        return when {
            e >= 8640000 -> "--"
            e >= 3600 -> "${e / 3600}h ${(e % 3600) / 60}m"
            e >= 60 -> "${e / 60}m ${e % 60}s"
            else -> "${e}s"
        }
    }

    private fun parseSpeedToBytes(speedStr: String): Long {
        if (speedStr.isBlank() || speedStr == "--") return 0L
        val clean = speedStr.removeSuffix("/s").trim()
        val num = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0L
        return when {
            clean.contains("GiB", ignoreCase = true) || clean.contains("GB", ignoreCase = true) -> (num * 1024 * 1024 * 1024).toLong()
            clean.contains("MiB", ignoreCase = true) || clean.contains("MB", ignoreCase = true) -> (num * 1024 * 1024).toLong()
            clean.contains("KiB", ignoreCase = true) || clean.contains("KB", ignoreCase = true) -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun parseSizeToBytes(sizeStr: String): Long {
        if (sizeStr.isBlank() || sizeStr == "--") return 0L
        val num = sizeStr.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0L
        return when {
            sizeStr.contains("GiB", ignoreCase = true) || sizeStr.contains("GB", ignoreCase = true) -> (num * 1024 * 1024 * 1024).toLong()
            sizeStr.contains("MiB", ignoreCase = true) || sizeStr.contains("MB", ignoreCase = true) -> (num * 1024 * 1024).toLong()
            sizeStr.contains("KiB", ignoreCase = true) || sizeStr.contains("KB", ignoreCase = true) -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun parseEtaToSeconds(etaStr: String): Int? {
        if (etaStr.isBlank() || etaStr == "--" || etaStr == "unknown") return null
        var totalSec = 0
        val parts = etaStr.split(" ")
        for (part in parts) {
            if (part.endsWith("h")) {
                val h = part.removeSuffix("h").toIntOrNull() ?: 0
                totalSec += h * 3600
            } else if (part.endsWith("m")) {
                val m = part.removeSuffix("m").toIntOrNull() ?: 0
                totalSec += m * 60
            } else if (part.endsWith("s")) {
                val s = part.removeSuffix("s").toIntOrNull() ?: 0
                totalSec += s
            }
        }
        return if (totalSec > 0) totalSec else null
    }

    private fun parseDateToEpoch(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return Clock.System.now().toEpochMilliseconds() / 1000
        return try {
            Instant.parse(dateStr).toEpochMilliseconds() / 1000
        } catch (_: Exception) {
            Clock.System.now().toEpochMilliseconds() / 1000
        }
    }
}
