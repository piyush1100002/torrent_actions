package com.torrentactions.app.data.parser

enum class WorkerStage {
    QUEUED,
    SETTING_UP,
    DOWNLOADING,
    UPLOADING_HF,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ParsedProgress(
    val stage: WorkerStage = WorkerStage.QUEUED,
    val progressPercent: Int = 0,
    val downloadedSize: String = "--",
    val totalSize: String = "--",
    val downloadSpeed: String = "--",
    val eta: String = "--",
    val peerCount: Int = 0,
    val infoHash: String? = null,
    val torrentName: String? = null,
    val imdbId: String? = null,
    val tunnelUrl: String? = null,
    val qbtPassword: String? = null,
    val lastLogLine: String = ""
)

object Aria2LogParser {

    // Matches aria2 console progress variants, including ETA-less output:
    // [#b8d264 450MiB/1.5GiB(29%) CN:16 DL:18.2MiB ETA:1m10s]
    // [#b8d264 0B/0B CN:1]
    private val aria2ProgressRegex = Regex(
        """\[#(?:[a-f0-9]+\s+)?([\d\.]+[A-Za-z]+)\/([\d\.]+[A-Za-z]+)\((\d+)%\)\s+CN:(\d+)(?:\s+DL:([\d\.]+[A-Za-z]+(?:/s)?))?(?:\s+ETA:\s*([^\]]+?))?\]""",
        RegexOption.IGNORE_CASE
    )

    // Matches qBittorrent progress output:
    // qBittorrent: 21.8% | 440.7 MiB / 1.99 GiB | 3.6 MiB/s | ETA 10m 10s | seeds 2, peers 1 | downloading
    private val qbittorrentProgressRegex = Regex(
        """qBittorrent:\s*([\d\.]+)%\s*\|\s*([^\|]+?)\s*/\s*([^\|]+?)\s*\|\s*([^\|]+?)\s*\|\s*ETA\s*([^\|]+?)\s*\|\s*seeds\s*(\d+),\s*peers\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    // Matches user input log: Source: magnet:... or IMDb ID: tt...
    private val imdbRegex = Regex("""IMDb ID:\s*(tt\d+)""", RegexOption.IGNORE_CASE)
    private val sourceRegex = Regex("""Source:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val tunnelRegex = Regex("""Cloudflare tunnel URL:\s*(https://[a-zA-Z0-9\-]+\.trycloudflare\.com)""", RegexOption.IGNORE_CASE)
    private val qbtPasswordRegex = Regex("""qBittorrent WebUI Password:\s*([^\r\n\s]+)""", RegexOption.IGNORE_CASE)
    private val qbtSessionPasswordRegex = Regex("""temporary password is provided for this session:\s*([^\r\n\s]+)""", RegexOption.IGNORE_CASE)

    fun parseLogs(rawLogs: String, jobStatus: String?, jobConclusion: String?): ParsedProgress {
        if (rawLogs.isBlank()) {
            return determineStageFromStatus(jobStatus, jobConclusion)
        }

        val lines = rawLogs.lines()
        var extractedImdb: String? = null
        var extractedSource: String? = null
        var extractedTunnelUrl: String? = null
        var extractedPassword: String? = null
        var lastAria2Match: MatchResult? = null
        var lastQbittorrentMatch: MatchResult? = null
        var isAria2Done = false
        var isQbittorrentDone = false
        var isHfUploading = false
        var isHfDone = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (imdbRegex.containsMatchIn(trimmed)) {
                extractedImdb = imdbRegex.find(trimmed)?.groupValues?.get(1)
            }
            if (sourceRegex.containsMatchIn(trimmed)) {
                extractedSource = sourceRegex.find(trimmed)?.groupValues?.get(1)
            }
            if (tunnelRegex.containsMatchIn(trimmed)) {
                val found = tunnelRegex.find(trimmed)?.groupValues?.get(1)
                if (!found.isNullOrBlank() && !found.contains("not established", ignoreCase = true)) {
                    extractedTunnelUrl = found
                }
            }
            if (qbtPasswordRegex.containsMatchIn(trimmed)) {
                extractedPassword = qbtPasswordRegex.find(trimmed)?.groupValues?.get(1)
            } else if (qbtSessionPasswordRegex.containsMatchIn(trimmed)) {
                extractedPassword = qbtSessionPasswordRegex.find(trimmed)?.groupValues?.get(1)
            }

            val aria2Match = aria2ProgressRegex.find(trimmed)
            if (aria2Match != null) {
                lastAria2Match = aria2Match
            }

            val qbMatch = qbittorrentProgressRegex.find(trimmed)
            if (qbMatch != null) {
                lastQbittorrentMatch = qbMatch
            }

            if (trimmed.contains("Download complete", ignoreCase = true) ||
                trimmed.contains("Download finished successfully", ignoreCase = true) ||
                trimmed.contains("qBittorrent reports the torrent is complete", ignoreCase = true) ||
                trimmed.contains("qBittorrent completed the torrent job", ignoreCase = true)
            ) {
                isAria2Done = true
                isQbittorrentDone = true
            }

            // Only mark HF uploading if the upload step is actually executing, NOT from static step names
            if (trimmed == "Uploading files to Hugging Face" ||
                trimmed.startsWith("Uploading files to Hugging Face", ignoreCase = true) ||
                trimmed.startsWith("⏳  Push downloaded media to Hugging Face", ignoreCase = true) ||
                trimmed.contains("Pushing to Hugging Face dataset", ignoreCase = true)
            ) {
                isHfUploading = true
            }

            if (trimmed.contains("Upload complete", ignoreCase = true) ||
                trimmed.contains("✅ Upload complete", ignoreCase = true) ||
                trimmed.contains("✅  Push downloaded media to Hugging Face", ignoreCase = true)
            ) {
                isHfDone = true
            }
        }

        // Determine current stage
        val stage = when {
            jobConclusion == "success" || isHfDone -> WorkerStage.COMPLETED
            jobConclusion == "cancelled" -> WorkerStage.CANCELLED
            jobConclusion == "failure" || jobConclusion == "timed_out" -> WorkerStage.FAILED
            isHfUploading -> WorkerStage.UPLOADING_HF
            (isAria2Done || isQbittorrentDone) && (lastQbittorrentMatch != null || lastAria2Match != null) -> WorkerStage.UPLOADING_HF
            lastQbittorrentMatch != null -> WorkerStage.DOWNLOADING
            lastAria2Match != null -> WorkerStage.DOWNLOADING
            rawLogs.contains("Starting torrent download", ignoreCase = true) ||
                rawLogs.contains("Adding magnet URL to qBittorrent", ignoreCase = true) ||
                rawLogs.contains("⏳  Torrent download with qBittorrent", ignoreCase = true) -> WorkerStage.DOWNLOADING
            rawLogs.contains("Waiting for qBittorrent Web API", ignoreCase = true) ||
                rawLogs.contains("Starting qBittorrent-nox", ignoreCase = true) ||
                rawLogs.contains("Install torrent download tools", ignoreCase = true) ||
                rawLogs.contains("⏳  Install torrent download tools", ignoreCase = true) ||
                rawLogs.contains("⏳  Prepare download folder", ignoreCase = true) -> WorkerStage.SETTING_UP
            rawLogs.contains("⏳  Push downloaded media to Hugging Face", ignoreCase = true) -> WorkerStage.UPLOADING_HF
            else -> WorkerStage.QUEUED
        }

        val lastLine = lines.findLast { it.isNotBlank() } ?: ""

        // Handle qBittorrent progress format
        if (lastQbittorrentMatch != null) {
            val groups = lastQbittorrentMatch.groupValues
            val percent = groups[1].toFloatOrNull()?.toInt() ?: 0
            val downloaded = groups[2].trim()
            val total = groups[3].trim()
            val speed = groups[4].trim()
            val eta = groups[5].trim()
            val seeds = groups[6].toIntOrNull() ?: 0
            val peers = groups[7].toIntOrNull() ?: 0

            return ParsedProgress(
                stage = stage,
                progressPercent = if (stage == WorkerStage.COMPLETED) 100 else if (stage == WorkerStage.UPLOADING_HF) 100 else percent,
                downloadedSize = downloaded,
                totalSize = total,
                downloadSpeed = if (stage == WorkerStage.DOWNLOADING && speed.isNotBlank()) speed else "--",
                eta = if (stage == WorkerStage.DOWNLOADING && eta.isNotBlank()) eta else "--",
                peerCount = seeds + peers,
                imdbId = extractedImdb,
                tunnelUrl = extractedTunnelUrl,
                qbtPassword = extractedPassword,
                lastLogLine = lastLine
            )
        }

        if (lastAria2Match != null) {
            val groups = lastAria2Match.groupValues
            val downloaded = groups[1]
            val total = groups[2]
            val percent = groups[3].toIntOrNull() ?: 0
            val peers = groups[4].toIntOrNull() ?: 0
            var speed = groups.getOrNull(5)?.trim().orEmpty()
            if (speed.isNotBlank() && !speed.endsWith("/s", ignoreCase = true)) speed += "/s"
            val eta = groups.getOrNull(6)?.trim().orEmpty()

            return ParsedProgress(
                stage = stage,
                progressPercent = if (stage == WorkerStage.COMPLETED) 100 else if (stage == WorkerStage.UPLOADING_HF) 100 else percent,
                downloadedSize = downloaded,
                totalSize = total,
                downloadSpeed = if (stage == WorkerStage.DOWNLOADING && speed.isNotBlank()) speed else "--",
                eta = if (stage == WorkerStage.DOWNLOADING && eta.isNotBlank()) eta else "--",
                peerCount = peers,
                imdbId = extractedImdb,
                tunnelUrl = extractedTunnelUrl,
                qbtPassword = extractedPassword,
                lastLogLine = lastLine
            )
        }

        return ParsedProgress(
            stage = stage,
            progressPercent = if (stage == WorkerStage.COMPLETED) 100 else if (stage == WorkerStage.UPLOADING_HF) 100 else 0,
            imdbId = extractedImdb,
            tunnelUrl = extractedTunnelUrl,
            qbtPassword = extractedPassword,
            lastLogLine = lastLine
        )
    }

    private fun determineStageFromStatus(status: String?, conclusion: String?): ParsedProgress {
        val stage = when (conclusion) {
            "success" -> WorkerStage.COMPLETED
            "failure" -> WorkerStage.FAILED
            "cancelled" -> WorkerStage.CANCELLED
            else -> when (status) {
                "in_progress" -> WorkerStage.DOWNLOADING
                "queued" -> WorkerStage.QUEUED
                else -> WorkerStage.QUEUED
            }
        }
        return ParsedProgress(
            stage = stage,
            progressPercent = if (stage == WorkerStage.COMPLETED) 100 else 0
        )
    }
}
